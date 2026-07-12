package com.shannon.db

import com.shannon.domain.repository.ConfigRepository

class SqlDelightConfigRepository(
    private val database: ShannonDatabase
) : ConfigRepository {

    override fun getString(key: String): String? {
        return database.configQueries
            .selectByKey(key)
            .executeAsOneOrNull()
            ?.value_
    }

    override fun setString(key: String, value: String) {
        database.configQueries.insert(key, value)
    }

    override suspend fun triggerNetworkRestart() {
        // No-op until real ReticulumClient is wired
    }
}
