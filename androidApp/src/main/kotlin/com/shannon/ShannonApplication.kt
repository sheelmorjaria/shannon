package com.shannon

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.shannon.appContext
import com.shannon.di.androidModule
import com.shannon.di.captionModule
import com.shannon.di.databaseModule
import com.shannon.di.messageRepositoryModule
import com.shannon.di.networkModule
import com.shannon.di.repositoryModule
import com.shannon.di.viewModelModule
import com.shannon.speech.SpeechEngine
import com.shannon.speech.SherpaSpeechEngine
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

/**
 * Shannon Application class.
 * Initializes Koin dependency injection and sets up Android-specific components.
 */
class ShannonApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channel for Android 8.0+
        createNotificationChannel()

        // Initialize Koin DI
        startKoin {
            // Use Android logger for debugging
            androidLogger(Level.ERROR)

            // Use Android context
            androidContext(this@ShannonApplication)

            // Load modules
            modules(
                androidModule,                    // Android-specific implementations
                databaseModule,                   // Database configuration
                repositoryModule,                 // Repository implementations
                messageRepositoryModule(getLocalIdentityHash()), // Message repository with identity
                viewModelModule,                  // ViewModels
                networkModule(), // Real Reticulum client
                captionModule(), // §2.1: captions + (stub) speech-engine binding
                module {
                    // §2.1: override the stub SpeechEngine with on-device Sherpa-ONNX (STT + TTS + VAD);
                    // model cache rooted at the app's internal files dir (Context.getFilesDir()).
                    single<SpeechEngine> { SherpaSpeechEngine(java.io.File(appContext.filesDir, "sherpa")) }
                },
            )
        }
    }

    /**
     * Create notification channel for foreground service (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "shannon_network_channel"
            val channelName = "Network Service"
            val channelDescription = "Shows network connection status"
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Get or create local identity hash for this device.
     * In production, this would be loaded from secure storage or created on first launch.
     */
    private fun getLocalIdentityHash(): String {
        // TODO: Implement proper identity storage and retrieval
        // For now, return a placeholder that will be replaced with actual identity
        return getSharedPreferences("shannon_prefs", MODE_PRIVATE)
            .getString("local_identity_hash", null)
            ?: generateInitialIdentity()
    }

    /**
     * Generate initial identity and store it.
     */
    private fun generateInitialIdentity(): String {
        // TODO: Implement actual identity generation using Reticulum Identity
        val initialHash = "generated_identity_${System.currentTimeMillis()}"

        getSharedPreferences("shannon_prefs", MODE_PRIVATE)
            .edit()
            .putString("local_identity_hash", initialHash)
            .apply()

        return initialHash
    }
}