package com.shannon.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android-specific implementation of DatabaseDriverFactory using SQLDelight Android driver.
 * Uses Android's SQLite implementation with proper context and file paths.
 * Includes automatic migration support and database health monitoring.
 */
class DatabaseDriverFactory(private val context: Context) {

    suspend fun createDriver(): AndroidSqliteDriver = withContext(Dispatchers.IO) {
        // Create database file in app's data directory
        val databaseFile = context.getDatabasePath("shannon.db")

        // Ensure parent directory exists
        databaseFile.parentFile?.mkdirs()

        // Check if database exists
        val isFreshDatabase = !databaseFile.exists()

        val driver = AndroidSqliteDriver(
            schema = ShannonDatabase.Schema,
            context = context,
            name = "shannon.db",
            // Use WAL mode for better concurrency
            // This is safe for Android as it handles file locking properly
        )

        // Initialize database and run migrations if needed
        if (isFreshDatabase) {
            initializeFreshDatabase(driver)
        } else {
            runMigrationsIfNeeded(driver)
        }

        return@withContext driver
    }

    /**
     * Initialize a fresh database with metadata.
     */
    private suspend fun initializeFreshDatabase(driver: AndroidSqliteDriver) {
        val database = ShannonDatabase(driver)
        try {
            // Set initial database version
            database.databaseMetadataQueries.initializeDatabase()

            println("Fresh database initialized at version ${DatabaseMigrationManager.CURRENT_VERSION}")
        } catch (e: Exception) {
            println("Error initializing fresh database: ${e.message}")
            throw e
        }
    }

    /**
     * Run database migrations if needed.
     */
    private suspend fun runMigrationsIfNeeded(driver: AndroidSqliteDriver) {
        val database = ShannonDatabase(driver)
        try {
            val migrationManager = DatabaseMigrationManager(database)

            if (migrationManager.needsMigration()) {
                println("Database needs migration, running migrations...")
                val migrationsApplied = migrationManager.migrate()

                if (migrationsApplied) {
                    println("Database migrations completed successfully")
                }

                // Verify database integrity after migrations
                if (!migrationManager.verifyDatabaseIntegrity()) {
                    throw MigrationException("Database integrity check failed after migration")
                }
            } else {
                println("Database is up to date at version ${DatabaseMigrationManager.CURRENT_VERSION}")
            }
        } catch (e: Exception) {
            println("Error running database migrations: ${e.message}")
            throw e
        }
    }

    /**
     * Get the database file for backup or migration purposes.
     */
    fun getDatabaseFile(): File {
        return context.getDatabasePath("shannon.db")
    }

    /**
     * Delete the database file.
     * Use with caution - this will permanently delete all data.
     */
    fun deleteDatabase() {
        context.deleteDatabase("shannon.db")
    }

    /**
     * Check if database exists.
     */
    fun databaseExists(): Boolean {
        val databaseFile = context.getDatabasePath("shannon.db")
        return databaseFile.exists()
    }

    /**
     * Get database size in bytes.
     */
    fun getDatabaseSize(): Long {
        val databaseFile = context.getDatabasePath("shannon.db")
        return if (databaseFile.exists()) {
            databaseFile.length()
        } else {
            0L
        }
    }

    /**
     * Get database directory for Reticulum configuration and identity files.
     */
    fun getAppDataDirectory(): File {
        // Use app's internal storage for Reticulum files
        return context.filesDir
    }

    /**
     * Get Reticulum configuration directory.
     */
    fun getReticulumConfigDir(): File {
        val reticulumDir = File(context.filesDir, ".reticulum")
        if (!reticulumDir.exists()) {
            reticulumDir.mkdirs()
        }
        return reticulumDir
    }

    /**
     * Get identity storage directory.
     */
    fun getIdentityDir(): File {
        val identityDir = File(context.filesDir, "shannon/identities")
        if (!identityDir.exists()) {
            identityDir.mkdirs()
        }
        return identityDir
    }
}