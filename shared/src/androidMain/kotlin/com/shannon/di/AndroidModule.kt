package com.shannon.di

import android.content.Context
import com.shannon.appContext
import com.shannon.db.DatabaseDriverFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android-specific dependency injection module.
 * Provides Android platform implementations and configurations.
 */
val androidModule = module {
    // Provide Android Context
    single { androidContext() }

    // Provide Android-specific DatabaseDriverFactory (no-arg actual; uses process-wide appContext)
    single<DatabaseDriverFactory> { DatabaseDriverFactory() }

    // Provide CoroutineScope for Android with proper error handling
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // Android-specific configuration for Reticulum
    factory {
        val filesDir = appContext.filesDir
        AndroidReticulumConfig(
            configDir = java.io.File(filesDir, ".reticulum").apply { mkdirs() }.absolutePath,
            identityDir = java.io.File(filesDir, "shannon/identities").apply { mkdirs() }.absolutePath,
            enableNotifications = true,
            notificationTitle = "Shannon Network",
            notificationChannelId = "shannon_network_channel",
            notificationChannelName = "Network Service",
            notificationChannelDescription = "Shows network connection status"
        )
    }
}

/**
 * Android-specific configuration for Reticulum client.
 */
data class AndroidReticulumConfig(
    val configDir: String,
    val identityDir: String,
    val enableNotifications: Boolean = true,
    val notificationTitle: String = "Shannon Network",
    val notificationChannelId: String = "shannon_network_channel",
    val notificationChannelName: String = "Network Service",
    val notificationChannelDescription: String = "Shows network connection status",
    val notificationIconResId: Int = android.R.drawable.ic_dialog_info,
    val enableAutoReconnect: Boolean = true,
    val reconnectIntervalMs: Long = 5000,
    val healthCheckIntervalMs: Long = 30000
)