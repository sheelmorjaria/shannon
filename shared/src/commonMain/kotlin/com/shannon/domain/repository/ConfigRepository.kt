package com.shannon.domain.repository

/**
 * Repository for persistent configuration (transport nodes, settings, etc).
 */
interface ConfigRepository {
    fun getString(key: String): String?
    fun setString(key: String, value: String)
    suspend fun triggerNetworkRestart()
}
