package com.shannon.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightConfigRepositoryTest {

    private fun createRepo(): SqlDelightConfigRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        return SqlDelightConfigRepository(ShannonDatabase(driver))
    }

    @Test
    fun `setString and getString round-trip`() = runTest {
        val repo = createRepo()
        repo.setString("transport_host", "reticulum.local")
        assertEquals("reticulum.local", repo.getString("transport_host"))
    }

    @Test
    fun `getString returns null for unknown key`() {
        val repo = createRepo()
        assertNull(repo.getString("nonexistent"))
    }

    @Test
    fun `setString overwrites existing value`() {
        val repo = createRepo()
        repo.setString("transport_host", "old.example.com")
        repo.setString("transport_host", "new.example.com")
        assertEquals("new.example.com", repo.getString("transport_host"))
    }

    @Test
    fun `triggerNetworkRestart does not throw`() = runTest {
        val repo = createRepo()
        repo.triggerNetworkRestart()
    }
}
