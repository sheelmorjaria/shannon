package com.shannon.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shannon.db.ShannonDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseTest {

    private fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        return driver
    }

    @Test
    fun `insert and query message`() {
        val driver = createDriver()
        val db = ShannonDatabase(driver)

        db.messageQueries.insert(
            id = "msg-1",
            destination_hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
            source_hash = "1234567890abcdef1234567890abcdef",
            content = "Hello world",
            timestamp = 1000L,
            state = "DRAFT",
            is_outgoing = 1L
        )

        val messages = db.messageQueries.selectAllByContact(
            destination_hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
            source_hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"
        ).executeAsList()

        assertEquals(1, messages.size)
        assertEquals("Hello world", messages[0].content)
        assertEquals("msg-1", messages[0].id)
    }

    @Test
    fun `pagination returns correct page`() {
        val driver = createDriver()
        val db = ShannonDatabase(driver)
        val hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

        // Insert 50 messages with different timestamps
        for (i in 1..50) {
            db.messageQueries.insert(
                id = "msg-$i",
                destination_hash = hash,
                source_hash = "1234567890abcdef1234567890abcdef",
                content = "Message $i",
                timestamp = i.toLong(),
                state = "SENT",
                is_outgoing = 1L
            )
        }

        // Query first page of 20
        val page1 = db.messageQueries.selectByContact(hash, hash, 20L, 0L).executeAsList()

        assertEquals(20, page1.size)
        // Should be newest first (timestamp DESC)
        assertEquals(50L, page1[0].timestamp)
        assertEquals(31L, page1[19].timestamp)

        // Query second page
        val page2 = db.messageQueries.selectByContact(hash, hash, 20L, 20L).executeAsList()

        assertEquals(20, page2.size)
        assertEquals(30L, page2[0].timestamp)

        // Third page has remaining 10
        val page3 = db.messageQueries.selectByContact(hash, hash, 20L, 40L).executeAsList()

        assertEquals(10, page3.size)
    }

    @Test
    fun `delete message by id`() {
        val driver = createDriver()
        val db = ShannonDatabase(driver)
        val hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

        db.messageQueries.insert(
            id = "msg-to-delete",
            destination_hash = hash,
            source_hash = "1234567890abcdef1234567890abcdef",
            content = "Delete me",
            timestamp = 100L,
            state = "DRAFT",
            is_outgoing = 1L
        )

        // Verify it exists
        val before = db.messageQueries.selectAllByContact(hash, hash).executeAsList()
        assertEquals(1, before.size)

        // Delete
        db.messageQueries.deleteById("msg-to-delete")

        // Verify it's gone
        val after = db.messageQueries.selectAllByContact(hash, hash).executeAsList()
        assertTrue(after.isEmpty())
    }

    @Test
    fun `update message state`() {
        val driver = createDriver()
        val db = ShannonDatabase(driver)
        val hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

        db.messageQueries.insert(
            id = "msg-state",
            destination_hash = hash,
            source_hash = "1234567890abcdef1234567890abcdef",
            content = "State test",
            timestamp = 100L,
            state = "DRAFT",
            is_outgoing = 1L
        )

        db.messageQueries.updateState(state = "SENT", id = "msg-state")

        val msg = db.messageQueries.selectAllByContact(hash, hash).executeAsList().first()
        assertEquals("SENT", msg.state)
    }

    @Test
    fun `contact CRUD operations`() {
        val driver = createDriver()
        val db = ShannonDatabase(driver)

        // Insert
        db.contactQueries.insert(
            destination_hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
            display_name = "Alice",
            public_key = null
        )

        // Query all
        val all = db.contactQueries.selectAll().executeAsList()
        assertEquals(1, all.size)
        assertEquals("Alice", all[0].display_name)

        // Query by hash
        val alice = db.contactQueries.selectByHash("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4").executeAsOne()
        assertEquals("Alice", alice.display_name)

        // Update (upsert)
        db.contactQueries.insert(
            destination_hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
            display_name = "Alice Updated",
            public_key = null
        )
        val updated = db.contactQueries.selectByHash("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4").executeAsOne()
        assertEquals("Alice Updated", updated.display_name)

        // Delete
        db.contactQueries.deleteByHash("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        assertNull(db.contactQueries.selectByHash("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4").executeAsOneOrNull())
    }

    @Test
    fun `contacts ordered by display name`() {
        val driver = createDriver()
        val db = ShannonDatabase(driver)

        db.contactQueries.insert("11111111111111111111111111111111", "Charlie", null)
        db.contactQueries.insert("22222222222222222222222222222222", "Alice", null)
        db.contactQueries.insert("33333333333333333333333333333333", "Bob", null)

        val contacts = db.contactQueries.selectAll().executeAsList()
        assertEquals("Alice", contacts[0].display_name)
        assertEquals("Bob", contacts[1].display_name)
        assertEquals("Charlie", contacts[2].display_name)
    }
}
