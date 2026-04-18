package com.shannon.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:shannon.db")

        // Only create schema if database doesn't exist
        val databaseFile = java.io.File("shannon.db")
        if (!databaseFile.exists()) {
            ShannonDatabase.Schema.create(driver)
        }

        return driver
    }
}
