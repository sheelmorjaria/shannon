package com.shannon.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Database version and migration information.
 */
data class DatabaseVersion(
    val version: Int,
    val description: String,
    val migrationBlock: (ShannonDatabase) -> Unit
)

/**
 * Migration record from database.
 */
data class MigrationRecord(
    val version: Int,
    val appliedAt: Long,
    val description: String
)

/**
 * Manages database schema migrations for ShannonDatabase.
 * Handles version tracking, schema changes, and data preservation.
 */
class DatabaseMigrationManager(private val database: ShannonDatabase) {

    companion object {
        // Current database version
        const val CURRENT_VERSION = 1

        // Migration definitions
        private val migrations = listOf(
            // Version 1: Initial schema (current baseline)
            DatabaseVersion(
                version = 1,
                description = "Initial schema with messages, contacts, and config tables"
            ) { db ->
                // Initial schema is created automatically by SQLDelight
                // This migration just sets the version
                db.databaseMetadataQueries.setDatabaseVersion("1")
            }
        )

        // Future migrations will be added here:
        // DatabaseVersion(2, "Add message delivery receipts") { db -> ... }
        // DatabaseVersion(3, "Add user preferences table") { db -> ... }
    }

    /**
     * Get the current database version from metadata.
     * Returns 0 if version not found (fresh database).
     */
    suspend fun getCurrentVersion(): Int = withContext(Dispatchers.IO) {
        try {
            database.databaseMetadataQueries.getDatabaseVersion().executeAsOne()
                .let { versionStr ->
                    versionStr.toIntOrNull() ?: 0
                }
        } catch (e: Exception) {
            // Fresh database or error reading version
            0
        }
    }

    /**
     * Check if database needs migration.
     */
    suspend fun needsMigration(): Boolean {
        val currentVersion = getCurrentVersion()
        return currentVersion < CURRENT_VERSION
    }

    /**
     * Run all pending migrations to bring database to current version.
     * Returns true if migrations were applied, false if already up to date.
     */
    suspend fun migrate(): Boolean = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersion()

        if (currentVersion >= CURRENT_VERSION) {
            return@withContext false // Already up to date
        }

        // Run migrations in sequence
        for (migration in migrations) {
            if (migration.version > currentVersion) {
                applyMigration(migration)
            }
        }

        return@withContext true
    }

    /**
     * Apply a single migration.
     */
    private suspend fun applyMigration(migration: DatabaseVersion) {
        try {
            // Begin transaction for atomic migration
            // Note: SQLDelight doesn't have built-in transaction support,
            // so we rely on the underlying driver's transaction handling

            // Run migration block
            migration.migrationBlock(database)

            // Log migration
            database.databaseMetadataQueries.logMigration(
                version = migration.version.toLong(),
                appliedAt = System.currentTimeMillis(),
                description = migration.description
            )

            println("Database migration applied: Version ${migration.version} - ${migration.description}")

        } catch (e: Exception) {
            throw MigrationException("Failed to apply migration version ${migration.version}: ${e.message}", e)
        }
    }

    /**
     * Get migration history.
     */
    suspend fun getMigrationHistory(): List<MigrationRecord> = withContext(Dispatchers.IO) {
        try {
            database.databaseMetadataQueries.getMigrationHistory().executeAsList()
                .map { MigrationRecord(
                    version = it.version.toInt(),
                    appliedAt = it.appliedAt,
                    description = it.description ?: ""
                )}
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Verify database integrity after migration.
     * Checks that all expected tables exist and are accessible.
     */
    suspend fun verifyDatabaseIntegrity(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Test basic operations on each table
            database.messageQueries.selectByContact(
                contactHash = "test",
                contactHash = "test",
                limit = 1,
                offset = 0
            ).executeAsList() // Should work even if empty

            database.contactQueries.selectAll().executeAsList()
            database.configQueries.selectByKey("test").executeAsList()

            // Verify version is set correctly
            val version = getCurrentVersion()
            version == CURRENT_VERSION

        } catch (e: Exception) {
            println("Database integrity check failed: ${e.message}")
            false
        }
    }

    /**
     * Rollback to a specific version (DANGEROUS - use with caution).
     * This recreates tables from scratch and may lose data.
     */
    suspend fun rollbackToVersion(targetVersion: Int) = withContext(Dispatchers.IO) {
        require(targetVersion > 0) { "Target version must be greater than 0" }
        require(targetVersion < CURRENT_VERSION) { "Can only rollback to earlier versions" }

        // WARNING: This is a destructive operation
        // In production, you'd implement proper rollback migrations
        throw MigrationException("Rollback not implemented - would cause data loss")
    }
}

/**
 * Exception thrown when migration fails.
 */
class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)