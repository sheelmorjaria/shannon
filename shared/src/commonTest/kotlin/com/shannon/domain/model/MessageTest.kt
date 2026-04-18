package com.shannon.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageTest {

    private val validDestination = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

    @Test
    fun `message constructed with correct fields`() {
        val beforeTime = System.currentTimeMillis()
        val msg = Message(
            destinationHash = validDestination,
            content = "Hello world",
            timestamp = beforeTime
        )

        assertEquals(validDestination, msg.destinationHash)
        assertEquals("Hello world", msg.content)
        assertEquals(beforeTime, msg.timestamp)
        assertEquals(MessageState.DRAFT, msg.state)
        assertTrue(msg.isOutgoing)
        assertTrue(msg.id.isNotEmpty())
    }

    @Test
    fun `state transitions from DRAFT to QUEUED`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis()
        )
        assertEquals(MessageState.DRAFT, msg.state)

        val queued = msg.transitionTo(MessageState.QUEUED)
        assertEquals(MessageState.QUEUED, queued.state)
    }

    @Test
    fun `state transitions from QUEUED to SENDING`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            state = MessageState.QUEUED
        )
        val sending = msg.transitionTo(MessageState.SENDING)
        assertEquals(MessageState.SENDING, sending.state)
    }

    @Test
    fun `state transitions from SENDING to SENT`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            state = MessageState.SENDING
        )
        val sent = msg.transitionTo(MessageState.SENT)
        assertEquals(MessageState.SENT, sent.state)
    }

    @Test
    fun `state transitions from SENDING to FAILED`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            state = MessageState.SENDING
        )
        val failed = msg.transitionTo(MessageState.FAILED)
        assertEquals(MessageState.FAILED, failed.state)
    }

    @Test
    fun `invalid transition from SENT to DRAFT rejected`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            state = MessageState.SENT
        )
        assertFailsWith<IllegalStateException> {
            msg.transitionTo(MessageState.DRAFT)
        }
    }

    @Test
    fun `invalid transition from DRAFT to SENT rejected`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            state = MessageState.DRAFT
        )
        assertFailsWith<IllegalStateException> {
            msg.transitionTo(MessageState.SENT)
        }
    }

    @Test
    fun `failed message can be retried from FAILED to QUEUED`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            state = MessageState.FAILED
        )
        val retried = msg.transitionTo(MessageState.QUEUED)
        assertEquals(MessageState.QUEUED, retried.state)
    }

    @Test
    fun `transition preserves all other fields`() {
        val msg = Message(
            destinationHash = validDestination,
            content = "Preserve me",
            timestamp = 12345L,
            state = MessageState.DRAFT
        )
        val next = msg.transitionTo(MessageState.QUEUED)

        assertEquals(msg.id, next.id)
        assertEquals(msg.destinationHash, next.destinationHash)
        assertEquals(msg.content, next.content)
        assertEquals(msg.timestamp, next.timestamp)
    }
}
