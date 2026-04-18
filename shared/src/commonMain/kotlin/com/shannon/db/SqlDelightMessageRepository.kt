package com.shannon.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.domain.repository.MessageRepository
import com.shannon.network.LxmfPacket
import com.shannon.network.ReticulumClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SqlDelightMessageRepository(
    private val database: ShannonDatabase,
    private val client: ReticulumClient,
    private val localHash: String,
    private val scope: CoroutineScope
) : MessageRepository {

    private var listenJob: Job? = null

    fun startListening() {
        listenJob = scope.launch {
            client.observeIncomingPackets().collect { packet ->
                val msg = Message(
                    destinationHash = packet.sourceHash,
                    content = packet.content,
                    timestamp = packet.timestamp,
                    state = MessageState.SENT,
                    isOutgoing = false
                )
                saveMessageWithSource(msg, sourceHash = packet.sourceHash)
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
    }

    override fun observeMessages(contactHash: String): Flow<List<Message>> {
        return database.messageQueries
            .selectAllByContact(contactHash, contactHash)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomainMessage() } }
    }

    override suspend fun getMessages(contactHash: String, limit: Long, offset: Long): List<Message> {
        return database.messageQueries
            .selectByContact(contactHash, contactHash, limit, offset)
            .executeAsList()
            .map { it.toDomainMessage() }
    }

    override suspend fun saveMessage(message: Message) {
        database.messageQueries.insert(
            id = message.id,
            destination_hash = if (message.isOutgoing) message.destinationHash else localHash,
            source_hash = if (message.isOutgoing) localHash else message.destinationHash,
            content = message.content,
            timestamp = message.timestamp,
            state = message.state.name,
            is_outgoing = if (message.isOutgoing) 1L else 0L
        )
    }

    /** Save an incoming message with explicit source hash from the packet. */
    private suspend fun saveMessageWithSource(message: Message, sourceHash: String) {
        database.messageQueries.insert(
            id = message.id,
            destination_hash = localHash,
            source_hash = sourceHash,
            content = message.content,
            timestamp = message.timestamp,
            state = message.state.name,
            is_outgoing = 0L
        )
    }

    override suspend fun handleIncomingPacket(packet: LxmfPacket) {
        // Create Message from incoming LxmfPacket
        val message = Message(
            destinationHash = packet.sourceHash,
            content = packet.content,
            timestamp = packet.timestamp,
            state = MessageState.SENT, // Incoming messages are already "sent" when received
            isOutgoing = false
        )

        // Save with explicit source hash from packet
        saveMessageWithSource(message, sourceHash = packet.sourceHash)
    }

    override suspend fun updateMessage(message: Message) {
        database.messageQueries.updateState(
            state = message.state.name,
            id = message.id
        )
    }

    override suspend fun deleteMessage(id: String) {
        database.messageQueries.deleteById(id)
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
}

private fun com.shannon.db.Message.toDomainMessage(): Message {
    return Message(
        id = id,
        destinationHash = destination_hash,
        content = content,
        timestamp = timestamp,
        state = MessageState.valueOf(state),
        isOutgoing = is_outgoing == 1L
    )
}
