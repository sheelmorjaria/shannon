package com.shannon.network

import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Concrete MessageRepository that bridges domain models with the Reticulum network.
 * In production, this would also persist to the local database.
 * For Phase 2, it manages messages in memory and communicates via ReticulumClient.
 */
class MessageRepositoryImpl(
    private val client: ReticulumClient,
    private val localHash: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : MessageRepository {

    private val _messagesByContact = mutableMapOf<String, MutableList<Message>>()
    private val _messageFlows = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private var listenJob: Job? = null

    /**
     * Start listening for incoming packets. Call this after initialization.
     * Separated from init to allow test control over coroutine lifecycle.
     */
    fun startListening() {
        listenJob = scope.launch {
            client.observeIncomingPackets().collect { packet ->
                val msg = Message(
                    destinationHash = packet.destinationHash,
                    content = packet.content,
                    timestamp = packet.timestamp,
                    state = MessageState.SENT,
                    isOutgoing = false
                )
                val contactKey = packet.sourceHash
                synchronized(_messagesByContact) {
                    _messagesByContact.getOrPut(contactKey) { mutableListOf() }.add(0, msg)
                }
                getMessageFlow(contactKey).value = _messagesByContact[contactKey]?.toList() ?: emptyList()
            }
        }
    }

    /** Stop listening for incoming packets. */
    fun stopListening() {
        listenJob?.cancel()
    }

    private fun getMessageFlow(contactHash: String): MutableStateFlow<List<Message>> {
        return synchronized(_messageFlows) {
            _messageFlows.getOrPut(contactHash) {
                MutableStateFlow(_messagesByContact[contactHash]?.toList() ?: emptyList())
            }
        }
    }

    override fun observeMessages(contactHash: String): Flow<List<Message>> {
        return getMessageFlow(contactHash).asStateFlow()
    }

    override suspend fun getMessages(contactHash: String, limit: Long, offset: Long): List<Message> {
        synchronized(_messagesByContact) {
            val all = _messagesByContact[contactHash]?.toList() ?: emptyList()
            return all.drop(offset.toInt()).take(limit.toInt())
        }
    }

    override suspend fun saveMessage(message: Message) {
        val contactKey = message.destinationHash
        synchronized(_messagesByContact) {
            _messagesByContact.getOrPut(contactKey) { mutableListOf() }.add(0, message)
        }
        getMessageFlow(contactKey).value = _messagesByContact[contactKey]?.toList() ?: emptyList()
    }

    override suspend fun updateMessage(message: Message) {
        val contactKey = message.destinationHash
        synchronized(_messagesByContact) {
            val list = _messagesByContact[contactKey]
            val idx = list?.indexOfFirst { it.id == message.id }
            if (idx != null && idx >= 0) {
                list[idx] = message
            }
        }
        getMessageFlow(contactKey).value = _messagesByContact[contactKey]?.toList() ?: emptyList()
    }

    override suspend fun deleteMessage(id: String) {
        synchronized(_messagesByContact) {
            for ((_, list) in _messagesByContact) {
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    list.removeAt(idx)
                    break
                }
            }
        }
        _messageFlows.forEach { (hash, flow) ->
            flow.value = _messagesByContact[hash]?.toList() ?: emptyList()
        }
    }

    override suspend fun send(message: Message): Message {
        var current = message.transitionTo(MessageState.QUEUED)
        saveMessage(current)

        current = current.transitionTo(MessageState.SENDING)
        updateMessage(current)

        return try {
            client.sendLxmfPacket(
                LxmfPacket(
                    destinationHash = current.destinationHash,
                    sourceHash = localHash,
                    content = current.content,
                    timestamp = current.timestamp
                )
            )
            val sent = current.transitionTo(MessageState.SENT)
            updateMessage(sent)
            sent
        } catch (e: Exception) {
            val failed = current.transitionTo(MessageState.FAILED)
            updateMessage(failed)
            failed
        }
    }

    override suspend fun handleIncomingPacket(packet: LxmfPacket) {
        val msg = Message(
            destinationHash = packet.destinationHash,
            content = packet.content,
            timestamp = packet.timestamp,
            state = MessageState.SENT,
            isOutgoing = false
        )
        saveMessage(msg)
    }
}
