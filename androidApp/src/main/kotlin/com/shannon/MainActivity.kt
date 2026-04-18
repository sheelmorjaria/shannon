package com.shannon

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shannon.notifications.DeepLinkDestination
import com.shannon.notifications.NotificationDeepLinkHandler
import com.shannon.network.ConnectionStatus
import com.shannon.services.ShannonNetworkService
import com.shannon.viewmodel.ConnectivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Main Activity for Shannon Android app.
 * Handles UI rendering, permissions, and service binding.
 */
class MainActivity : ComponentActivity() {

    private val connectivityViewModel: ConnectivityViewModel by viewModel()
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var serviceBound = false
    private var networkService: ShannonNetworkService? = null

    // Deep-link handling
    private val notificationDeepLinkHandler by lazy {
        NotificationDeepLinkHandler(this)
    }

    // Current navigation state (can be updated by deep-links)
    var currentConversationHash: String? = null
        private set

    var pendingReply: Triple<String, String, Int>? = null // contactHash, messageContent, notificationId
        private set

    // Permission request launcher for Android 13+ notification permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, request additional permissions
            requestBluetoothPermissions()
        } else {
            // Permission denied, show explanation
            showPermissionDeniedMessage()
        }
    }

    // Bluetooth permissions launcher for Android 12+
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // All Bluetooth permissions granted, start service
            startNetworkService()
        } else {
            // Some permissions denied, start service with limited functionality
            startNetworkService()
        }
    }

    // Service connection for binding to network service
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ShannonNetworkService.LocalBinder
            networkService = binder.getService()
            serviceBound = true

            // Start monitoring connection status from service
            monitorServiceConnection()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            networkService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle deep-link intents from notifications
        handleIntent(intent)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        } else {
            // Start service directly for older Android versions
            startNetworkService()
        }

        // Set up Compose UI
        setContent {
            MaterialTheme {
                ShannonApp(connectivityViewModel, currentConversationHash, pendingReply)
            }
        }

        // Start observing network status
        connectivityViewModel.startObserving()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intents (e.g., when app is already running and notification is tapped)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Bind to network service
        Intent(this, ShannonNetworkService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from service
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * Request POST_NOTIFICATIONS permission for Android 13+
     */
    private fun requestNotificationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, request Bluetooth permissions
                requestBluetoothPermissions()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Show rationale to user
                showPermissionRationale()
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Request Bluetooth permissions for Android 12+
     */
    private fun requestBluetoothPermissions() {
        val bluetoothPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) needs BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            bluetoothPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            bluetoothPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Older Android versions use legacy BLUETOOTH permission
            bluetoothPermissions.add(Manifest.permission.BLUETOOTH)
            bluetoothPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Check which permissions are already granted
        val permissionsToRequest = bluetoothPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        when {
            permissionsToRequest.isEmpty() -> {
                // All permissions already granted, start service
                startNetworkService()
            }
            else -> {
                // Request missing permissions
                bluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    /**
     * Start the network foreground service
     */
    private fun startNetworkService() {
        val intent = Intent(this, ShannonNetworkService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * Show permission rationale to user
     */
    private fun showPermissionRationale() {
        // TODO: Show proper UI dialog explaining why permission is needed
        // For now, just request it
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Show message when permission is denied
     */
    private fun showPermissionDeniedMessage() {
        // TODO: Show proper UI message
        // For now, just start service without notifications
        startNetworkService()
    }

    /**
     * Monitor service connection status and update UI accordingly
     */
    private fun monitorServiceConnection() {
        mainScope.launch {
            try {
                networkService?.let { service ->
                    // Service is now available, can access its methods
                    // For example, you could check initial connection status
                    val status = service.getConnectionStatus()
                    // Update UI based on status
                }
            } catch (e: Exception) {
                // Handle errors gracefully
            }
        }
    }

    /**
     * Handle deep-link intents from notifications.
     */
    private fun handleIntent(intent: Intent) {
        val destination = notificationDeepLinkHandler.handleDeepLinkIntent(intent)

        when (destination) {
            is DeepLinkDestination.Conversation -> {
                // Navigate to specific conversation
                currentConversationHash = destination.contactHash
                println("Deep-link: Opening conversation with ${destination.contactHash}")
            }
            is DeepLinkDestination.VoiceCall -> {
                // Handle incoming voice call
                currentConversationHash = destination.callerHash
                println("Deep-link: Handling voice call from ${destination.callerHash}")
                // TODO: Trigger voice call UI
            }
            is DeepLinkDestination.AcceptCall -> {
                // Accept voice call
                println("Deep-link: Accepting call from ${destination.callerHash}")
                // TODO: Accept call via VoiceCallManager
            }
            is DeepLinkDestination.RejectCall -> {
                // Reject voice call
                println("Deep-link: Rejecting call from ${destination.callerHash}")
                // TODO: Reject call via VoiceCallManager
            }
            is DeepLinkDestination.Reply -> {
                // Handle reply action
                currentConversationHash = destination.contactHash
                pendingReply = Triple(
                    destination.contactHash,
                    destination.messageContent,
                    destination.notificationId
                )
                println("Deep-link: Reply to ${destination.contactHash}: ${destination.messageContent}")
            }
            null -> {
                // No deep-link, default navigation
                currentConversationHash = null
                pendingReply = null
            }
        }
    }

    /**
     * Clear deep-link state after navigation.
     */
    fun clearDeepLinkState() {
        currentConversationHash = null
        pendingReply = null
    }

    /**
     * Show message notification (called from repositories when new message received).
     */
    fun showNewMessageNotification(
        contactHash: String,
        contactName: String,
        messageContent: String,
        channelId: String
    ) {
        notificationDeepLinkHandler.showMessageNotification(
            contactHash = contactHash,
            contactName = contactName,
            messageContent = messageContent,
            channelId = channelId
        )
    }

    /**
     * Show incoming voice call notification.
     */
    fun showVoiceCallNotification(
        callerHash: String,
        callerName: String,
        channelId: String
    ) {
        notificationDeepLinkHandler.showVoiceCallNotification(
            callerHash = callerHash,
            callerName = callerName,
            channelId = channelId
        )
    }

    /**
     * Get current connection status from service
     */
    private suspend fun getServiceConnectionStatus(): ConnectionStatus? {
        return networkService?.getConnectionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        connectivityViewModel.stopObserving()
    }
}

/**
 * Main Compose UI for Shannon app
 */
@Composable
fun ShannonApp(
    connectivityViewModel: ConnectivityViewModel,
    currentConversationHash: String? = null,
    pendingReply: Triple<String, String, Int>? = null
) {
    val uiState by connectivityViewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        ChatScreen(
            modifier = Modifier.padding(paddingValues),
            connectionStatus = uiState.connectionStatus,
            currentConversationHash = currentConversationHash,
            pendingReply = pendingReply,
            onConnectClick = { /* TODO: Implement connect UI */ },
            onDisconnectClick = { /* TODO: Implement disconnect UI */ },
            onConversationOpened = { /* TODO: Track conversation changes */ }
        )
    }
}

/**
 * Simple chat screen placeholder
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    connectionStatus: String,
    currentConversationHash: String? = null,
    pendingReply: Triple<String, String, Int>? = null,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onConversationOpened: (String) -> Unit = {}
) {
    // TODO: Implement proper chat UI with message list, input field, etc.

    // Handle deep-link navigation
    val conversationText = when {
        currentConversationHash != null -> " | Conversation: $currentConversationHash"
        pendingReply != null -> " | Reply: ${pendingReply!!.second}"
        else -> ""
    }

    Text(
        text = "Shannon Messenger - Status: $connectionStatus$conversationText",
        modifier = modifier
    )
}