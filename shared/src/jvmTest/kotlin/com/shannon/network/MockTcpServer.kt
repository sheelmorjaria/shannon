package com.shannon.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mock TCP server for testing network clients.
 * Simulates a Reticulum transport node for integration testing.
 */
class MockTcpServer(
    private val port: Int = 0, // 0 = auto-assign available port
    private val simulateLatency: Boolean = false,
    private val latencyMs: Long = 100,
    private val simulateErrors: Boolean = false,
    private val errorRate: Double = 0.1
) {

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val connections = ConcurrentLinkedQueue<Socket>()
    private val receivedPackets = ConcurrentLinkedQueue<ByteArray>()

    /**
     * Get the actual port the server is listening on.
     * Useful when port is set to 0 (auto-assign).
     */
    val actualPort: Int
        get() = serverSocket?.localPort ?: -1

    /**
     * Check if server is currently running.
     */
    val isRunning: Boolean
        get() = isRunning.get()

    /**
     * Get the number of active connections.
     */
    val connectionCount: Int
        get() = connections.size

    /**
     * Start the mock server.
     */
    fun start() {
        if (isRunning.get()) {
            throw IllegalStateException("Server is already running")
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning.set(true)

            serverThread = Thread {
                runBlocking {
                    while (isRunning.get()) {
                        try {
                            val clientSocket = serverSocket?.accept()
                            clientSocket?.let {
                                handleConnection(it)
                            }
                        } catch (e: SocketException) {
                            if (isRunning.get()) {
                                println("Server socket error: ${e.message}")
                            }
                        } catch (e: IOException) {
                            if (isRunning.get()) {
                                println("Server I/O error: ${e.message}")
                            }
                        }
                    }
                }
            }.apply { start() }

        } catch (e: IOException) {
            throw RuntimeException("Failed to start server: ${e.message}", e)
        }
    }

    /**
     * Stop the mock server and close all connections.
     */
    fun stop() {
        isRunning.set(false)

        // Close all client connections
        connections.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
        connections.clear()

        // Close server socket
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // Ignore close errors
        }

        // Wait for server thread to finish
        serverThread?.join(1000)
        serverThread = null
        serverSocket = null
    }

    /**
     * Handle a client connection.
     */
    private suspend fun handleConnection(socket: Socket) {
        connections.add(socket)

        try {
            val inputStream = socket.getInputStream()
            val buffer = ByteArray(4096)

            while (isRunning.get() && !socket.isClosed) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val packet = buffer.copyOfRange(0, bytesRead)
                        receivedPackets.add(packet)

                        // Simulate latency if enabled
                        if (simulateLatency) {
                            delay(latencyMs)
                        }

                        // Simulate random errors if enabled
                        if (simulateErrors && Math.random() < errorRate) {
                            throw IOException("Simulated network error")
                        }

                        // Echo response back to client
                        sendResponse(socket, createAckPacket())
                    }
                } catch (e: SocketException) {
                    // Connection closed by client
                    break
                } catch (e: IOException) {
                    if (isRunning.get()) {
                        println("Connection error: ${e.message}")
                    }
                    break
                }
            }
        } finally {
            connections.remove(socket)
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
    }

    /**
     * Send response packet to client.
     */
    private suspend fun sendResponse(socket: Socket, data: ByteArray) {
        try {
            val outputStream = socket.getOutputStream()
            outputStream.write(data)
            outputStream.flush()
        } catch (e: IOException) {
            println("Error sending response: ${e.message}")
        }
    }

    /**
     * Create an acknowledgment packet.
     */
    private fun createAckPacket(): ByteArray {
        return "ACK".toByteArray()
    }

    /**
     * Check if any data has been received.
     */
    fun hasData(): Boolean {
        return receivedPackets.isNotEmpty()
    }

    /**
     * Get all received packets.
     */
    fun getReceivedPackets(): List<ByteArray> {
        return receivedPackets.toList()
    }

    /**
     * Get the most recently received packet.
     */
    fun getLastPacket(): ByteArray? {
        return receivedPackets.poll()
    }

    /**
     * Wait for a packet to be received.
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The received packet, or null if timeout
     */
    suspend fun waitForPacket(timeoutMs: Long = 5000): ByteArray? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val packet = receivedPackets.poll()
            if (packet != null) {
                return packet
            }
            delay(100)
        }
        return null
    }

    /**
     * Clear all received packets.
     */
    fun clearPackets() {
        receivedPackets.clear()
    }

    /**
     * Check if a client is connected.
     */
    fun hasConnection(): Boolean {
        return connections.isNotEmpty()
    }

    /**
     * Disconnect all clients.
     */
    fun disconnectAllClients() {
        connections.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
        connections.clear()
    }

    /**
     * Simulate server failure.
     * Closes server socket without cleanup, simulating a crash.
     */
    fun simulateFailure() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // Ignore close errors
        }
        isRunning.set(false)
    }

    /**
     * Restart the server after a failure.
     */
    fun restart() {
        if (isRunning.get()) {
            stop()
        }
        start()
    }
}

/**
 * Builder class for creating MockTcpServer with custom configuration.
 */
class MockTcpServerBuilder {
    private var port: Int = 0
    private var simulateLatency: Boolean = false
    private var latencyMs: Long = 100
    private var simulateErrors: Boolean = false
    private var errorRate: Double = 0.1

    fun setPort(port: Int) = apply { this.port = port }
    fun enableLatency(enabled: Boolean = true, latencyMs: Long = 100) = apply {
        this.simulateLatency = enabled
        this.latencyMs = latencyMs
    }
    fun enableErrors(enabled: Boolean = true, errorRate: Double = 0.1) = apply {
        this.simulateErrors = enabled
        this.errorRate = errorRate
    }

    fun build(): MockTcpServer {
        return MockTcpServer(
            port = port,
            simulateLatency = simulateLatency,
            latencyMs = latencyMs,
            simulateErrors = simulateErrors,
            errorRate = errorRate
        )
    }
}

/**
 * Extension function to create a MockTcpServer with a builder DSL.
 */
fun mockTcpServer(block: MockTcpServerBuilder.() -> Unit): MockTcpServer {
    return MockTcpServerBuilder().apply(block).build()
}