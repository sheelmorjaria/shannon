package com.shannon.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shannon.domain.model.Contact
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightContactRepositoryTest {

    private fun createRepo(): SqlDelightContactRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        return SqlDelightContactRepository(ShannonDatabase(driver))
    }

    @Test
    fun `save and retrieve contact`() = runTest {
        val repo = createRepo()
        val contact = Contact(
            destinationHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
            displayName = "Alice"
        )
        repo.saveContact(contact)

        val retrieved = repo.getContact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        assertEquals("Alice", retrieved?.displayName)
        assertEquals("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", retrieved?.destinationHash)
    }

    @Test
    fun `getContacts returns all contacts ordered by name`() = runTest {
        val repo = createRepo()
        repo.saveContact(Contact("11111111111111111111111111111111", "Charlie"))
        repo.saveContact(Contact("22222222222222222222222222222222", "Alice"))
        repo.saveContact(Contact("33333333333333333333333333333333", "Bob"))

        val contacts = repo.getContacts()
        assertEquals(3, contacts.size)
        assertEquals("Alice", contacts[0].displayName)
        assertEquals("Bob", contacts[1].displayName)
        assertEquals("Charlie", contacts[2].displayName)
    }

    @Test
    fun `saveContact with same hash updates`() = runTest {
        val repo = createRepo()
        repo.saveContact(Contact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", "Alice"))
        repo.saveContact(Contact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", "Alice Updated"))

        val retrieved = repo.getContact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        assertEquals("Alice Updated", retrieved?.displayName)
        assertEquals(1, repo.getContacts().size)
    }

    @Test
    fun `deleteContact removes from database`() = runTest {
        val repo = createRepo()
        repo.saveContact(Contact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", "Alice"))
        repo.deleteContact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        assertNull(repo.getContact("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"))
    }

    @Test
    fun `getContact returns null for unknown hash`() = runTest {
        val repo = createRepo()
        assertNull(repo.getContact("99999999999999999999999999999999"))
    }
}
