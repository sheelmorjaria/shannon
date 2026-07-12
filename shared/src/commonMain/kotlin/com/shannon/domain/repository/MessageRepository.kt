package com.shannon.domain.repository

import com.shannon.domain.model.Message
import com.shannon.network.LxmfPacket
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for message persistence and retrieval.
 */
interface MessageRepository {
    /** Observe all messages for a given contact, ordered newest first. */
    fun observeMessages(contactHash: String): Flow<List<Message>>

    /** Get a paginated list of messages for a contact. */
    suspend fun getMessages(contactHash: String, limit: Long = 20, offset: Long = 0): List<Message>

    /** Save a new message. */
    suspend fun saveMessage(message: Message)

    /** Update an existing message (e.g., state transition). */
    suspend fun updateMessage(message: Message)

    /** Delete a message by ID. */
    suspend fun deleteMessage(id: String)

    /** Send a message through the network layer. */
    suspend fun send(message: Message): Message

    /** Handle an incoming packet from the network layer. */
    suspend fun handleIncomingPacket(packet: LxmfPacket)
}
