package com.shannon.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import network.reticulum.Leticulum
import network.reticulum.identity.Identity
import network.reticulum.destination.Destination
import network.reticulum.common.DestinationDirection
import network.reticulum.common.DestinationType
import network.reticulum.interface.TCPInterface
import network.reticulum.packet.Packet
import network.lxmf.LXMFMessage
import network.lxmf.LXMFMessageDeliveryStatus
import kotlinx.coroutines.delay
import com.shannon.domain.repository.MessageRepository

/**
 * Real implementation of ReticulumClient using the reticulum-kt library.
 * Wraps the native Reticulum functionality and adapts it to our architecture.
 */
class ReticulumClientImpl(
    private val config: ReticulumConfig = ReticulumConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val messageRepository: MessageRepository? = null,
    private val audioEngine: com.shannon.audio.AudioEngine? = null
) : ReticulumClient {

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val _incomingLxmf = Channel<LxmfPacket>(Channel.UNLIMITED)
    private val _incomingLxst = Channel<LxstPacket>(Channel.UNLIMITED)

    // Real Reticulum-kt instances
    private var reticulumInstance: Leticulum? = null
    private var identity: Identity? = null
    private var lxmDestination: Destination? = null
    private var lxstDestination: Destination? = null // Separate destination for LXST signaling
    private var tcpInterface: TCPInterface? = null
    private var connectionJob: Job? = null
    private var packetListenerJob: Job? = null

    // Connection state
    private var currentHost: String? = null
    private var currentPort: Int? = null

    // Audio handling
    private var audioPacketsReceived = 0L
    private var audioPacketsProcessed = 0L
    private var audioProcessingStats = AudioProcessingStats()

    override fun observeStatus(): Flow<ConnectionStatus> = _status.asStateFlow()

    override suspend fun announce() {
        try {
            lxmDestination?.announce(appName = "shannon.lxmf")
        } catch (e: Exception) {
            throw NetworkException("Failed to announce: ${e.message}", e)
        }
    }

    override suspend fun sendLxmfPacket(packet: LxmfPacket) {
        ensureConnected()

        try {
            // Create LXMF message from our domain model
            val lxmfMessage = LXMFMessage(
                destination = packet.destinationHash,
                source = identity?.hash ?: "",
                content = packet.content.encodeToByteArray(),
                timestamp = packet.timestamp
            )

            // Send via the identity
            identity?.send(lxmfMessage)

        } catch (e: Exception) {
            throw NetworkException("Failed to send LXMF packet: ${e.message}", e)
        }
    }

    override fun observeIncomingPackets(): Flow<LxmfPacket> {
        return _incomingLxmf.consumeAsFlow()
    }

    override suspend fun sendLxstPacket(packet: LxstPacket) {
        ensureConnected()

        try {
            // Create a custom packet for LXST signaling
            val payload = serializeLxstPacket(packet)

            // Send directly via Reticulum packet
            val destinationBytes = packet.destinationHash.toByteArray()
            reticulumInstance?.sendPacket(
                destination = destinationBytes,
                payload = payload,
                context = "lxst.signaling"
            )

        } catch (e: Exception) {
            throw NetworkException("Failed to send LXST packet: ${e.message}", e)
        }
    }

    override fun observeIncomingLxstPackets(): Flow<LxstPacket> {
        return _incomingLxst.consumeAsFlow()
    }

    override suspend fun connect(host: String, port: Int) {
        if (_status.value == ConnectionStatus.CONNECTED) {
            disconnect()
        }

        try {
            _status.value = ConnectionStatus.CONNECTING
            currentHost = host
            currentPort = port

            // Initialize Reticulum with the target configuration
            initializeReticulum()

            // Connect to the specified host and port
            connectionJob = scope.launch {
                try {
                    // Connect via TCP interface
                    connectToTcpInterface(host, port)

                    _status.value = ConnectionStatus.CONNECTED

                    // Start listening for incoming packets
                    startPacketListener()

                } catch (e: Exception) {
                    _status.value = ConnectionStatus.RECONNECTING
                    throw NetworkException("Connection failed: ${e.message}", e)
                }
            }

            // Wait for connection to complete
            connectionJob?.join()

        } catch (e: Exception) {
            _status.value = ConnectionStatus.DISCONNECTED
            throw NetworkException("Failed to connect to $host:$port", e)
        }
    }

    override suspend fun disconnect() {
        try {
            connectionJob?.cancel()
            packetListenerJob?.cancel()

            reticulumInstance?.let { instance ->
                // Stop Reticulum and cleanup resources
                instance.stop()
            }

            reticulumInstance = null
            identity = null
            lxmDestination = null
            tcpInterface = null

            _status.value = ConnectionStatus.DISCONNECTED

        } catch (e: Exception) {
            throw NetworkException("Failed to disconnect: ${e.message}", e)
        }
    }

    /**
     * Cleanup resources when the client is no longer needed.
     */
    suspend fun cleanup() {
        disconnect()
        _incomingLxmf.close()
        _incomingLxst.close()
    }

    // Private implementation methods

    private suspend fun initializeReticulum() {
        try {
            // Start Reticulum with configuration directory
            reticulumInstance = Leticulum.start(configPath = config.configDir)

            // Create or load identity
            identity = if (config.identityPath != null) {
                Identity.loadFromFile(config.identityPath!!)
            } else {
                Identity.create()
            }

            // Create LXMF destination for messaging
            lxmDestination = Destination.create(
                identity = identity!!,
                direction = DestinationDirection.IN,
                type = DestinationType.SINGLE,
                aspects = listOf("lxmf", "shannon")
            )

            // Set up packet handler for incoming messages
            setupPacketHandler()

        } catch (e: Exception) {
            throw NetworkException("Failed to initialize Reticulum: ${e.message}", e)
        }
    }

    private suspend fun connectToTcpInterface(host: String, port: Int) {
        // Create TCP interface configuration
        tcpInterface = TCPInterface(
            host = host,
            port = port,
            connectTimeout = 10000 // 10 second timeout
        )

        // Add interface to Reticulum instance
        reticulumInstance?.addInterface(tcpInterface!!)

        // Wait for interface to be ready
        delay(1000) // Give time for connection to establish
    }

    private fun setupPacketHandler() {
        // Register handler for incoming LXMF packets
        lxmDestination?.setPacketHandler { packet ->
            scope.launch {
                try {
                    val lxmfPacket = parseIncomingLxmf(packet)

                    // Send to channel for existing observers
                    _incomingLxmf.send(lxmfPacket)

                    // Also handle via repository for database persistence
                    messageRepository?.handleIncomingPacket(lxmfPacket)
                } catch (e: Exception) {
                    // Log error but don't crash on malformed packets
                    println("Error processing incoming LXMF packet: ${e.message}")
                }
            }
        }

        // Register handler for incoming LXST packets (signaling + audio)
        lxstDestination?.setPacketHandler { packet ->
            scope.launch {
                try {
                    val lxstPacket = parseIncomingLxst(packet)

                    when (lxstPacket.type) {
                        LxstPacketType.AUDIO -> {
                            // Route audio packets directly to AudioEngine for real-time playback
                            handleIncomingAudioPacket(lxstPacket)
                        }
                        else -> {
                            // Send signaling packets to channel
                            _incomingLxst.send(lxstPacket)
                        }
                    }
                } catch (e: Exception) {
                    println("Error processing incoming LXST packet: ${e.message}")
                }
            }
        }
    }

    private fun startPacketListener() {
        packetListenerJob = scope.launch {
            // Monitor connection health and handle reconnection
            while (_status.value == ConnectionStatus.CONNECTED) {
                try {
                    // Check connection health
                    if (!isConnectionHealthy()) {
                        _status.value = ConnectionStatus.RECONNECTING
                        attemptReconnection()
                    }
                    kotlinx.coroutines.delay(config.healthCheckIntervalMs)
                } catch (e: Exception) {
                    _status.value = ConnectionStatus.RECONNECTING
                }
            }
        }
    }

    private suspend fun attemptReconnection() {
        val host = currentHost ?: return
        val port = currentPort ?: return

        try {
            disconnect()
            kotlinx.coroutines.delay(config.reconnectDelayMs)
            connect(host, port)
        } catch (e: Exception) {
            _status.value = ConnectionStatus.DISCONNECTED
        }
    }

    private fun ensureConnected() {
        if (_status.value != ConnectionStatus.CONNECTED) {
            throw NetworkException("Not connected to Reticulum network")
        }
    }

    private fun isConnectionHealthy(): Boolean {
        // Check if the TCP interface is still active and connected
        return tcpInterface?.isConnected == true && _status.value == ConnectionStatus.CONNECTED
    }

    // Adapter methods for library integration

    private fun parseIncomingLxmf(packet: Packet): LxmfPacket {
        // Parse incoming packet from library format to our domain model
        return LxmfPacket(
            destinationHash = packet.destinationHash.toHexString(),
            sourceHash = packet.sourceHash.toHexString(),
            content = packet.payload.decodeToString(),
            timestamp = packet.timestamp
        )
    }

    /**
     * Handle incoming LXST packet from library format to our domain model.
     */
    private fun parseIncomingLxst(packet: Packet): LxstPacket {
        val payloadString = packet.payload.decodeToString()
        val parts = payloadString.split(":", limit = 3)

        val packetType = when (parts.getOrNull(0)) {
            "SETUP" -> LxstPacketType.SETUP
            "ACCEPT" -> LxstPacketType.ACCEPT
            "REJECT" -> LxstPacketType.REJECT
            "BUSY" -> LxstPacketType.BUSY
            "HANGUP" -> LxstPacketType.HANGUP
            "AUDIO" -> LxstPacketType.AUDIO
            else -> LxstPacketType.SETUP // Default fallback
        }

        return LxstPacket(
            destinationHash = parts.getOrNull(1) ?: "",
            sourceHash = parts.getOrNull(2) ?: "",
            type = packetType,
            payload = if (packetType == LxstPacketType.AUDIO) {
                packet.payload // Keep binary payload for AUDIO packets
            } else {
                null // Signaling packets don't need payload
            }
        )
    }

    /**
     * Handle incoming audio packet for real-time playback.
     * Routes to AudioEngine with minimal latency.
     */
    private fun handleIncomingAudioPacket(packet: LxstPacket) {
        audioPacketsReceived++

        try {
            // Route to audio engine for immediate playback
            audioEngine?.let { engine ->
                packet.payload?.let { audioData ->
                    engine.onAudioPacketReceived(audioData)
                    audioPacketsProcessed++

                    // Update processing statistics
                    audioProcessingStats = audioProcessingStats.copy(
                        packetsReceived = audioPacketsReceived,
                        packetsProcessed = audioPacketsProcessed,
                        lastProcessedTime = System.currentTimeMillis(),
                        processingRate = if (audioPacketsReceived > 0) {
                            audioPacketsProcessed.toDouble() / audioPacketsReceived
                        } else 0.0
                    )
                }
            }

            // Also send to channel for observers (like VoiceCallManager)
            _incomingLxst.send(packet)

        } catch (e: Exception) {
            println("Error handling incoming audio packet: ${e.message}")
            // Don't crash on malformed audio packets - just drop them
        }
    }

    /**
     * Get audio processing statistics.
     */
    fun getAudioProcessingStats(): AudioProcessingStats {
        return audioProcessingStats
    }

    private fun serializeLxstPacket(packet: LxstPacket): ByteArray {
        // Serialize LxstPacket to bytes for transmission
        val data = "${packet.type.name}:${packet.destinationHash}:${packet.sourceHash}"
        return data.encodeToByteArray()
    }

}


/**
 * Configuration for ReticulumClientImpl.
 */
data class ReticulumConfig(
    val configDir: String = "~/.reticulum",
    val identityPath: String? = null,
    val healthCheckIntervalMs: Long = 30000,
    val reconnectDelayMs: Long = 5000,
    val enableRetries: Boolean = true,
    val maxRetries: Int = 3
)

/**
 * Statistics about audio packet processing.
 */
data class AudioProcessingStats(
    val packetsReceived: Long = 0L,
    val packetsProcessed: Long = 0L,
    val lastProcessedTime: Long = 0L,
    val processingRate: Double = 0.0 // 0.0 to 1.0 (success rate)
) {
    val packetsDropped: Long get() = packetsReceived - packetsProcessed

    val dropRate: Double get() = if (packetsReceived > 0) {
        packetsDropped.toDouble() / packetsReceived
    } else 0.0
}

/**
 * Exception thrown for network-related errors.
 */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)