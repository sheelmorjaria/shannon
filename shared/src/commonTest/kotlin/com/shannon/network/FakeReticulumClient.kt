package com.shannon.network

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A fake ReticulumClient for testing. Records all calls and allows
 * simulating incoming packets and connection state changes.
 */
class FakeReticulumClient : ReticulumClient {

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val _incomingLxmf = Channel<LxmfPacket>(Channel.UNLIMITED)
    private val _incomingLxst = Channel<LxstPacket>(Channel.UNLIMITED)

    /** Record of all announce() calls. */
    val announceCalls = mutableListOf<Unit>()

    /** Record of all sent LXMF packets. */
    val sentLxmfPackets = mutableListOf<LxmfPacket>()

    /** Record of all sent LXST packets. */
    val sentLxstPackets = mutableListOf<LxstPacket>()

    /** Record of connect() calls. */
    val connectCalls = mutableListOf<Pair<String, Int>>()

    /** Whether disconnect() was called. */
    var disconnectCalled = false
        private set

    /** Set to true to make sendLxmfPacket throw. */
    var shouldFailSend = false

    // --- ReticulumClient implementation ---

    override fun observeStatus(): Flow<ConnectionStatus> = _status.asStateFlow()

    override suspend fun announce() {
        announceCalls.add(Unit)
    }

    override suspend fun sendLxmfPacket(packet: LxmfPacket) {
        if (shouldFailSend) throw RuntimeException("Network unreachable")
        sentLxmfPackets.add(packet)
    }

    override fun observeIncomingPackets(): Flow<LxmfPacket> = _incomingLxmf.receiveAsFlow()

    override suspend fun sendLxstPacket(packet: LxstPacket) {
        sentLxstPackets.add(packet)
    }

    override fun observeIncomingLxstPackets(): Flow<LxstPacket> = _incomingLxst.receiveAsFlow()

    override suspend fun connect(host: String, port: Int) {
        connectCalls.add(host to port)
        _status.value = ConnectionStatus.CONNECTING
        _status.value = ConnectionStatus.CONNECTED
    }

    override suspend fun disconnect() {
        disconnectCalled = true
        _status.value = ConnectionStatus.DISCONNECTED
    }

    // --- Test helpers ---

    /** Simulate an incoming LXMF packet. */
    suspend fun simulateIncomingLxmf(packet: LxmfPacket) {
        _incomingLxmf.send(packet)
    }

    /** Simulate an incoming LXST packet. */
    suspend fun simulateIncomingLxst(packet: LxstPacket) {
        _incomingLxst.send(packet)
    }

    /** Simulate a connection status change. */
    fun simulateStatusChange(status: ConnectionStatus) {
        _status.value = status
    }
}
