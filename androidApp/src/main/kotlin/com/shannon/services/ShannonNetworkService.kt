package com.shannon.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shannon.MainActivity
import com.shannon.R
import com.shannon.di.AndroidReticulumConfig
import com.shannon.network.ConnectionStatus
import com.shannon.network.ReticulumClient
import com.shannon.network.ReticulumClientImpl
import com.shannon.network.ReticulumConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that manages the Reticulum network connection.
 * Keeps the network client alive even when the app is in the background.
 */
class ShannonNetworkService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationJob: Job? = null

    private val reticulumClient: ReticulumClient by inject()
    private val androidConfig: AndroidReticulumConfig by inject()

    private var isServiceInitialized = false
    private var currentStatus = ConnectionStatus.DISCONNECTED

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeNetworkClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle service restart by Android system
        if (!isServiceInitialized) {
            initializeNetworkClient()
        }

        // Handle notification actions
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    try {
                        reticulumClient.disconnect()
                    } catch (e: Exception) {
                        // Log error but don't crash
                    }
                }
            }
            ACTION_CONNECT -> {
                serviceScope.launch {
                    try {
                        // Reconnect using last known connection details
                        // This would require storing connection details
                        reticulumClient.connect("default_host", 4242)
                    } catch (e: Exception) {
                        // Log error but don't crash
                    }
                }
            }
        }

        // If service gets killed, restart with intent
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        cleanupNetworkClient()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Initialize the network client and start monitoring connection status.
     */
    private fun initializeNetworkClient() {
        if (isServiceInitialized) return

        serviceScope.launch {
            try {
                // Create Reticulum configuration for Android
                val config = ReticulumConfig(
                    configDir = androidConfig.configDir,
                    identityPath = "${androidConfig.identityDir}/identity",
                    enableRetries = androidConfig.enableAutoReconnect,
                    reconnectDelayMs = androidConfig.reconnectIntervalMs,
                    healthCheckIntervalMs = androidConfig.healthCheckIntervalMs
                )

                // Note: We're using the injected client which should already be configured
                // In production, you might want to pass config to the client

                // Start monitoring connection status for notification updates
                monitorConnectionStatus()

                isServiceInitialized = true

            } catch (e: Exception) {
                handleNetworkError(e)
            }
        }
    }

    /**
     * Monitor connection status and update notification accordingly.
     */
    private fun monitorConnectionStatus() {
        notificationJob = serviceScope.launch {
            reticulumClient.observeStatus().collect { status ->
                currentStatus = status
                updateNotification(status)
            }
        }
    }

    /**
     * Cleanup network client resources.
     */
    private fun cleanupNetworkClient() {
        notificationJob?.cancel()
        serviceScope.launch {
            try {
                if (reticulumClient is ReticulumClientImpl) {
                    (reticulumClient as ReticulumClientImpl).cleanup()
                }
            } catch (e: Exception) {
                // Log error but don't crash during cleanup
            }
        }
        isServiceInitialized = false
    }

    /**
     * Create initial notification for foreground service.
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create action buttons for notification
        val disconnectIntent = Intent(this, ShannonNetworkService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, androidConfig.notificationChannelId)
            .setContentTitle(androidConfig.notificationTitle)
            .setContentText("Initializing network connection…")
            .setSmallIcon(androidConfig.notificationIconResId)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_network_notification,
                "Disconnect",
                disconnectPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification based on connection status.
     */
    private fun updateNotification(status: ConnectionStatus) {
        val (title, text) = when (status) {
            ConnectionStatus.CONNECTED -> "Connected" to "Network connection active"
            ConnectionStatus.CONNECTING -> "Connecting…" to "Establishing network connection"
            ConnectionStatus.RECONNECTING -> "Reconnecting…" to "Restoring network connection"
            ConnectionStatus.DISCONNECTED -> "Disconnected" to "No network connection"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, androidConfig.notificationChannelId)
            .setContentTitle("$androidConfig.notificationTitle - $title")
            .setContentText(text)
            .setSmallIcon(androidConfig.notificationIconResId)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Handle network errors.
     */
    private fun handleNetworkError(error: Throwable) {
        // Log error and update notification
        updateNotification(ConnectionStatus.DISCONNECTED)

        // In production, you might want to:
        // 1. Log to crash reporting service
        // 2. Show user-facing error message
        // 3. Implement retry logic
    }

    /**
     * Get current connection status.
     */
    suspend fun getConnectionStatus(): ConnectionStatus {
        return currentStatus
    }

    /**
     * Binder class for clients to bind to this service.
     */
    inner class LocalBinder : Binder() {
        fun getService(): ShannonNetworkService = this@ShannonNetworkService
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT = "com.shannon.ACTION_DISCONNECT"
        const val ACTION_CONNECT = "com.shannon.ACTION_CONNECT"

        /**
         * Start the network service.
         */
        fun startService(context: Context) {
            val intent = Intent(context, ShannonNetworkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the network service.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, ShannonNetworkService::class.java)
            context.stopService(intent)
        }
    }
}