package com.shannon.network

/**
 * Manages the Reticulum network lifecycle.
 * Handles initialization, connection, and teardown.
 */
class ReticulumManager(
    private val client: ReticulumClient
) {
    /** Initialize the network client and announce presence. */
    suspend fun initialize() {
        client.announce()
    }

    /** Connect to a transport node. */
    suspend fun connect(host: String, port: Int) {
        client.connect(host, port)
    }

    /** Disconnect from the network. */
    suspend fun disconnect() {
        client.disconnect()
    }
}
