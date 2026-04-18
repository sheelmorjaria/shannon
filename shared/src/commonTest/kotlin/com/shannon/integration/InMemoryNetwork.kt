package com.shannon.integration

import com.shannon.network.ConnectionStatus
import com.shannon.network.LxmfPacket
import com.shannon.network.LxstPacket
import com.shannon.network.ReticulumClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * In-memory network that connects two ReticulumClients.
 * Packets sent by client A are delivered to client B's incoming channel, and vice versa.
 * Supports simulating connection drops and reconnections.
 */
class InMemoryNetwork {

    private val _channelAToB = Channel<LxmfPacket>(Channel.UNLIMITED)
    private val _channelBToA = Channel<LxmfPacket>(Channel.UNLIMITED)
    private val _lxstAToB = Channel<LxstPacket>(Channel.UNLIMITED)
    private val _lxstBToA = Channel<LxstPacket>(Channel.UNLIMITED)

    private val _statusA = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val _statusB = MutableStateFlow(ConnectionStatus.DISCONNECTED)

    /** Whether the link is currently active. When false, sends are queued but not delivered. */
    var linkActive = true

    /** Pending packets queued during disconnection. */
    val pendingPacketsA = mutableListOf<LxmfPacket>()
    val pendingPacketsB = mutableListOf<LxmfPacket>()

    /** Create client A's interface to this network. */
    fun clientA(localHash: String): InMemoryClient {
        return InMemoryClient(
            localHash = localHash,
            statusFlow = _statusA,
            incomingLxmf = _channelBToA,  // A receives what B sends
            outgoingLxmf = _channelAToB,  // A sends to B
            incomingLxst = _lxstBToA,
            outgoingLxst = _lxstAToB,
            pendingPackets = pendingPacketsA,
            network = this
        )
    }

    /** Create client B's interface to this network. */
    fun clientB(localHash: String): InMemoryClient {
        return InMemoryClient(
            localHash = localHash,
            statusFlow = _statusB,
            incomingLxmf = _channelAToB,  // B receives what A sends
            outgoingLxmf = _channelBToA,  // B sends to A
            incomingLxst = _lxstAToB,
            outgoingLxst = _lxstBToA,
            pendingPackets = pendingPacketsB,
            network = this
        )
    }

    /** Connect both clients (simulate link up). */
    fun connect() {
        _statusA.value = ConnectionStatus.CONNECTING
        _statusB.value = ConnectionStatus.CONNECTING
        _statusA.value = ConnectionStatus.CONNECTED
        _statusB.value = ConnectionStatus.CONNECTED
        linkActive = true
    }

    /** Disconnect both clients (simulate link drop). */
    fun disconnect() {
        linkActive = false
        _statusA.value = ConnectionStatus.RECONNECTING
        _statusB.value = ConnectionStatus.RECONNECTING
    }

    /** Reconnect and flush pending packets. */
    suspend fun reconnect() {
        linkActive = true
        _statusA.value = ConnectionStatus.CONNECTED
        _statusB.value = ConnectionStatus.CONNECTED
    }

    /** Flush pending packets from A to B. */
    suspend fun flushPendingA() {
        for (packet in pendingPacketsA.toList()) {
            _channelAToB.send(packet)
        }
        pendingPacketsA.clear()
    }

    /** Flush pending packets from B to A. */
    suspend fun flushPendingB() {
        for (packet in pendingPacketsB.toList()) {
            _channelBToA.send(packet)
        }
        pendingPacketsB.clear()
    }
}

/**
 * A ReticulumClient that sends through an in-memory channel.
 */
class InMemoryClient(
    private val localHash: String,
    private val statusFlow: MutableStateFlow<ConnectionStatus>,
    private val incomingLxmf: Channel<LxmfPacket>,
    private val outgoingLxmf: Channel<LxmfPacket>,
    private val incomingLxst: Channel<LxstPacket>,
    private val outgoingLxst: Channel<LxstPacket>,
    private val pendingPackets: MutableList<LxmfPacket>,
    private val network: InMemoryNetwork
) : ReticulumClient {

    val sentPackets = mutableListOf<LxmfPacket>()

    override fun observeStatus(): Flow<ConnectionStatus> = statusFlow.asStateFlow()

    override suspend fun announce() {
        // no-op in test
    }

    override suspend fun sendLxmfPacket(packet: LxmfPacket) {
        sentPackets.add(packet)
        if (network.linkActive) {
            outgoingLxmf.send(packet)
        } else {
            pendingPackets.add(packet)
        }
    }

    override fun observeIncomingPackets(): Flow<LxmfPacket> = incomingLxmf.receiveAsFlow()

    override suspend fun sendLxstPacket(packet: LxstPacket) {
        if (network.linkActive) {
            outgoingLxst.send(packet)
        }
    }

    override fun observeIncomingLxstPackets(): Flow<LxstPacket> = incomingLxst.receiveAsFlow()

    override suspend fun connect(host: String, port: Int) {
        statusFlow.value = ConnectionStatus.CONNECTING
        statusFlow.value = ConnectionStatus.CONNECTED
    }

    override suspend fun disconnect() {
        statusFlow.value = ConnectionStatus.DISCONNECTED
    }
}
