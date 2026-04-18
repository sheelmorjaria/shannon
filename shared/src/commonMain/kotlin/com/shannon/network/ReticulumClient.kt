package com.shannon.network

import kotlinx.coroutines.flow.Flow

/**
 * Represents the connection state to the Reticulum network.
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Abstract interface for interacting with the Reticulum network.
 * Implementations may use reticulum-kt, a Python bridge, or mocks for testing.
 */
interface ReticulumClient {
    /** Observe the current connection status. */
    fun observeStatus(): Flow<ConnectionStatus>

    /** Announce this node's identity on the network. */
    suspend fun announce()

    /** Send an LXMF packet to a destination. */
    suspend fun sendLxmfPacket(packet: LxmfPacket)

    /** Observe incoming LXMF packets. */
    fun observeIncomingPackets(): Flow<LxmfPacket>

    /** Send an LXST (voice) signaling packet. */
    suspend fun sendLxstPacket(packet: LxstPacket)

    /** Observe incoming LXST signaling packets. */
    fun observeIncomingLxstPackets(): Flow<LxstPacket>

    /** Connect to a transport node. */
    suspend fun connect(host: String, port: Int)

    /** Disconnect from the network. */
    suspend fun disconnect()

    /** Get the local identity hash. */
    fun getLocalIdentityHash(): String?

    /** Get current connection information. */
    fun getConnectionInfo(): ConnectionInfo?
}

/**
 * Connection information details.
 */
data class ConnectionInfo(
    val host: String,
    val port: Int,
    val status: ConnectionStatus,
    val identityHash: String?
)

/**
 * An LXMF messaging packet.
 */
data class LxmfPacket(
    val destinationHash: String,
    val sourceHash: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: ByteArray? = null
)

/**
 * An LXST voice signaling packet.
 */
data class LxstPacket(
    val destinationHash: String,
    val sourceHash: String,
    val type: LxstPacketType,
    val payload: ByteArray? = null
)

enum class LxstPacketType {
    SETUP,      // Call initiation
    ACCEPT,     // Call accepted
    REJECT,     // Call rejected
    BUSY,       // Destination busy
    HANGUP,     // Call ended
    AUDIO       // Audio data
}
