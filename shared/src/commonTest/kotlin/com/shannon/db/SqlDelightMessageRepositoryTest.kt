package com.shannon.db

import app.cash.turbine.test
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.network.FakeReticulumClient
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightMessageRepositoryTest {

    private val localHash = "1234567890abcdef1234567890abcdef"
    private val remoteHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

    private fun createRepo(
        client: FakeReticulumClient = FakeReticulumClient(),
        testScope: TestScope
    ): SqlDelightMessageRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        return SqlDelightMessageRepository(ShannonDatabase(driver), client, localHash, testScope)
    }

    @Test
    fun `save and retrieve message`() = runTest {
        val repo = createRepo(testScope = this)
        val msg = Message(
            destinationHash = remoteHash,
            content = "Hello",
            timestamp = 1000L,
            state = MessageState.SENT,
            isOutgoing = true
        )
        repo.saveMessage(msg)

        val messages = repo.getMessages(remoteHash)
        assertEquals(1, messages.size)
        assertEquals("Hello", messages[0].content)
    }

    @Test
    fun `updateMessage persists state change`() = runTest {
        val repo = createRepo(testScope = this)
        val msg = Message(
            destinationHash = remoteHash,
            content = "State test",
            timestamp = 1000L,
            state = MessageState.DRAFT
        )
        repo.saveMessage(msg)
        val updated = msg.transitionTo(MessageState.QUEUED)
        repo.updateMessage(updated)

        val messages = repo.getMessages(remoteHash)
        assertEquals(MessageState.QUEUED, messages[0].state)
    }

    @Test
    fun `deleteMessage removes from database`() = runTest {
        val repo = createRepo(testScope = this)
        val msg = Message(
            destinationHash = remoteHash,
            content = "Delete me",
            timestamp = 1000L
        )
        repo.saveMessage(msg)
        assertEquals(1, repo.getMessages(remoteHash).size)

        repo.deleteMessage(msg.id)
        assertEquals(0, repo.getMessages(remoteHash).size)
    }

    @Test
    fun `pagination with limit and offset`() = runTest {
        val repo = createRepo(testScope = this)
        for (i in 1..50) {
            repo.saveMessage(Message(
                destinationHash = remoteHash,
                content = "Msg $i",
                timestamp = i.toLong(),
                state = MessageState.SENT
            ))
        }

        val page1 = repo.getMessages(remoteHash, limit = 20, offset = 0)
        assertEquals(20, page1.size)
        // Newest first
        assertEquals(50L, page1[0].timestamp)

        val page3 = repo.getMessages(remoteHash, limit = 20, offset = 40)
        assertEquals(10, page3.size)
    }

    @Test
    fun `send transitions through states and calls network`() = runTest {
        val client = FakeReticulumClient()
        val repo = createRepo(client, this)

        val msg = Message(
            destinationHash = remoteHash,
            content = "Send test",
            timestamp = System.currentTimeMillis()
        )
        val sent = repo.send(msg)

        assertEquals(MessageState.SENT, sent.state)
        assertEquals(1, client.sentLxmfPackets.size)
        assertEquals("Send test", client.sentLxmfPackets[0].content)
    }

    @Test
    fun `send catches network error`() = runTest {
        val client = FakeReticulumClient()
        client.shouldFailSend = true
        val repo = createRepo(client, this)

        val msg = Message(
            destinationHash = remoteHash,
            content = "Will fail",
            timestamp = System.currentTimeMillis()
        )
        val result = repo.send(msg)
        assertEquals(MessageState.FAILED, result.state)
    }

    @Test
    fun `incoming packets are persisted`() = runTest {
        val client = FakeReticulumClient()
        val repo = createRepo(client, this)
        repo.startListening()

        client.simulateIncomingLxmf(
            com.shannon.network.LxmfPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                content = "Hello from remote"
            )
        )
        testScheduler.advanceUntilIdle()

        val messages = repo.getMessages(remoteHash)
        assertEquals(1, messages.size)
        assertEquals("Hello from remote", messages[0].content)
        assertTrue(!messages[0].isOutgoing)

        repo.stopListening()
    }

    @Test
    fun `messages survive repository re-creation`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        val db = ShannonDatabase(driver)
        val client = FakeReticulumClient()

        // Save with first instance
        val repo1 = SqlDelightMessageRepository(db, client, localHash, this)
        repo1.saveMessage(Message(
            destinationHash = remoteHash,
            content = "Persistent",
            timestamp = 1000L,
            state = MessageState.SENT
        ))

        // Create new instance with same database
        val repo2 = SqlDelightMessageRepository(db, client, localHash, this)
        val messages = repo2.getMessages(remoteHash)
        assertEquals(1, messages.size)
        assertEquals("Persistent", messages[0].content)
    }
}
