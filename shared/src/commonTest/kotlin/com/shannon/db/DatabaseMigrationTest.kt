package com.shannon.db

import app.cash.sqldelight.driver.jdbc.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for database migration system.
 * Verifies version tracking, migration execution, and data preservation.
 */
class DatabaseMigrationTest {

    private fun createInMemoryDatabase(): ShannonDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        return ShannonDatabase(driver)
    }

    @Test
    fun `fresh database starts at version 0 before migration`() = runTest {
        val database = createInMemoryDatabase()
        val migrationManager = DatabaseMigrationManager(database)

        val currentVersion = migrationManager.getCurrentVersion()
        assertEquals(0, currentVersion, "Fresh database should start at version 0")
    }

    @Test
    fun `fresh database migrates to current version`() = runTest {
        val database = createInMemoryDatabase()
        val migrationManager = DatabaseMigrationManager(database)

        val neededMigration = migrationManager.needsMigration()
        assertTrue(neededMigration, "Fresh database should need migration")

        val applied = migrationManager.migrate()
        assertTrue(applied, "Migration should be applied")

        val currentVersion = migrationManager.getCurrentVersion()
        assertEquals(DatabaseMigrationManager.CURRENT_VERSION, currentVersion,
            "Database should be at current version after migration")
    }

    @Test
    fun `already migrated database does not migrate again`() = runTest {
        val database = createInMemoryDatabase()
        val migrationManager = DatabaseMigrationManager(database)

        // First migration
        migrationManager.migrate()
        val versionAfterFirst = migrationManager.getCurrentVersion()
        assertEquals(DatabaseMigrationManager.CURRENT_VERSION, versionAfterFirst)

        // Second migration attempt
        val needsMigration = migrationManager.needsMigration()
        assertFalse(needsMigration, "Already migrated database should not need migration")

        val applied = migrationManager.migrate()
        assertFalse(applied, "Migration should not be applied again")

        val versionAfterSecond = migrationManager.getCurrentVersion()
        assertEquals(DatabaseMigrationManager.CURRENT_VERSION, versionAfterSecond,
            "Version should remain current after redundant migration attempt")
    }

    @Test
    fun `migration history is tracked correctly`() = runTest {
        val database = createInMemoryDatabase()
        val migrationManager = DatabaseMigrationManager(database)

        migrationManager.migrate()

        val history = migrationManager.getMigrationHistory()
        assertTrue(history.isNotEmpty(), "Migration history should not be empty")

        val firstMigration = history.first()
        assertEquals(1, firstMigration.version, "First migration should be version 1")
        assertTrue(firstMigration.description.isNotEmpty(),
            "Migration should have description")
        assertTrue(firstMigration.appliedAt > 0, "Migration should have timestamp")
    }

    @Test
    fun `database integrity check passes after migration`() = runTest {
        val database = createInMemoryDatabase()
        val migrationManager = DatabaseMigrationManager(database)

        migrationManager.migrate()

        val integrity = migrationManager.verifyDatabaseIntegrity()
        assertTrue(integrity, "Database integrity check should pass after migration")
    }

    @Test
    fun `data is preserved during migration`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        val database = ShannonDatabase(driver)

        // Insert test data before migration
        database.messageQueries.insert(
            id = "test_message_1",
            destination_hash = "contact_1",
            source_hash = "local_identity",
            content = "Test message before migration",
            timestamp = System.currentTimeMillis(),
            state = "SENT",
            is_outgoing = 1L
        )

        database.contactQueries.insert(
            destination_hash = "contact_1",
            display_name = "Test Contact",
            public_key = null
        )

        // Run migration
        val migrationManager = DatabaseMigrationManager(database)
        migrationManager.migrate()

        // Verify data still exists
        val messages = database.messageQueries.selectByContact(
            contactHash = "contact_1",
            contactHash = "contact_1",
            limit = 10,
            offset = 0
        ).executeAsList()

        assertTrue(messages.isNotEmpty(), "Messages should be preserved")
        assertEquals("Test message before migration", messages.first().content,
            "Message content should be preserved")

        val contacts = database.contactQueries.selectAll().executeAsList()
        assertTrue(contacts.isNotEmpty(), "Contacts should be preserved")
        assertEquals("Test Contact", contacts.first().display_name,
            "Contact data should be preserved")
    }

    @Test
    fun `migration handles database with existing metadata`() = runTest {
        val database = createInMemoryDatabase()

        // Simulate existing database with old metadata
        database.databaseMetadataQueries.setDatabaseVersion("0")

        val migrationManager = DatabaseMigrationManager(database)
        val currentVersion = migrationManager.getCurrentVersion()
        assertEquals(0, currentVersion, "Should read existing version 0")

        migrationManager.migrate()
        assertEquals(DatabaseMigrationManager.CURRENT_VERSION,
            migrationManager.getCurrentVersion(), "Should migrate to current version")
    }

    @Test
    fun `concurrent migrations are handled safely`() = runTest {
        val database = createInMemoryDatabase()

        // Create multiple migration managers (simulating concurrent access)
        val manager1 = DatabaseMigrationManager(database)
        val manager2 = DatabaseMigrationManager(database)

        // Both should recognize migration is needed
        assertTrue(manager1.needsMigration(), "Manager 1 should need migration")
        assertTrue(manager2.needsMigration(), "Manager 2 should need migration")

        // First manager migrates
        val applied1 = manager1.migrate()
        assertTrue(applied1, "First migration should succeed")

        // Second manager should see database as up to date
        val needsMigration2 = manager2.needsMigration()
        assertFalse(needsMigration2, "Second manager should see no migration needed")

        val applied2 = manager2.migrate()
        assertFalse(applied2, "Second migration should not be applied")
    }

    @Test
    fun `database version survives database recreation`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        val database = ShannonDatabase(driver)

        val migrationManager = DatabaseMigrationManager(database)
        migrationManager.migrate()

        val versionBefore = migrationManager.getCurrentVersion()
        assertEquals(DatabaseMigrationManager.CURRENT_VERSION, versionBefore)

        // Simulate database close and reopen
        val database2 = ShannonDatabase(driver)
        val migrationManager2 = DatabaseMigrationManager(database2)

        val versionAfter = migrationManager2.getCurrentVersion()
        assertEquals(versionBefore, versionAfter,
            "Version should persist across database instances")
    }

    @Test
    fun `migration preserves foreign key relationships`() = runTest {
        val database = createInMemoryDatabase()

        // Insert related data
        val contactHash = "test_contact"
        database.contactQueries.insert(
            destination_hash = contactHash,
            display_name = "Test Contact",
            public_key = null
        )

        database.messageQueries.insert(
            id = "msg_1",
            destination_hash = contactHash,
            source_hash = "local",
            content = "Test message",
            timestamp = System.currentTimeMillis(),
            state = "SENT",
            is_outgoing = 1L
        )

        // Run migration
        val migrationManager = DatabaseMigrationManager(database)
        migrationManager.migrate()

        // Verify relationships still work
        val messages = database.messageQueries.selectByContact(
            contactHash = contactHash,
            contactHash = contactHash,
            limit = 10,
            offset = 0
        ).executeAsList()

        assertTrue(messages.isNotEmpty(), "Messages should still be queryable by contact")

        val contact = database.contactQueries.selectByHash(contactHash).executeAsOneOrNull()
        assertNotNull(contact, "Contact should still exist")
        assertEquals(contactHash, contact.destination_hash)
    }

    @Test
    fun `migration handles large datasets efficiently`() = runTest {
        val database = createInMemoryDatabase()

        // Insert large dataset
        val messageCount = 1000
        repeat(messageCount) { i ->
            database.messageQueries.insert(
                id = "bulk_msg_$i",
                destination_hash = "bulk_contact",
                source_hash = "local",
                content = "Bulk message $i",
                timestamp = System.currentTimeMillis(),
                state = "SENT",
                is_outgoing = 1L
            )
        }

        // Run migration
        val migrationManager = DatabaseMigrationManager(database)
        val startTime = System.currentTimeMillis()
        migrationManager.migrate()
        val migrationTime = System.currentTimeMillis() - startTime

        // Migration should complete reasonably fast even with large dataset
        assertTrue(migrationTime < 5000, "Migration should complete in under 5 seconds")

        // Verify all data is preserved
        val messages = database.messageQueries.selectByContact(
            contactHash = "bulk_contact",
            contactHash = "bulk_contact",
            limit = messageCount + 10,
            offset = 0
        ).executeAsList()

        assertEquals(messageCount, messages.size, "All messages should be preserved")
    }

    @Test
    fun `integrity check catches corrupted database`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        val database = ShannonDatabase(driver)

        // Run successful migration
        val migrationManager = DatabaseMigrationManager(database)
        migrationManager.migrate()
        assertTrue(migrationManager.verifyDatabaseIntegrity(),
            "Fresh database should pass integrity check")

        // Simulate corruption by dropping a table
        driver.execute(null, "DROP TABLE message", 0)

        // Integrity check should fail
        val integrityAfter = migrationManager.verifyDatabaseIntegrity()
        assertFalse(integrityAfter, "Corrupted database should fail integrity check")
    }
}