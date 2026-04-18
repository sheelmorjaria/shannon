package com.shannon.viewmodel

import com.shannon.domain.repository.ConfigRepository
import com.shannon.network.ConnectionStatus
import com.shannon.network.ReticulumClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the connectivity/network status bar.
 */
data class ConnectivityUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusText: String = statusDisplayText(status)
)

private fun statusDisplayText(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.DISCONNECTED -> "No Connection"
        ConnectionStatus.CONNECTING -> "Connecting..."
        ConnectionStatus.CONNECTED -> "Connected"
        ConnectionStatus.RECONNECTING -> "Reconnecting..."
    }
}

/**
 * ViewModel for monitoring and managing network connectivity.
 *
 * @param observeScope Scope for long-lived observation. In tests, pass `TestScope.backgroundScope`.
 */
class ConnectivityViewModel(
    private val client: ReticulumClient,
    private val configRepository: ConfigRepository,
    private val observeScope: CoroutineScope
) {
    private val _connectivityState = MutableStateFlow(ConnectivityUiState())
    val connectivityState = _connectivityState.asStateFlow()

    private var observeJob: Job? = null

    /** Start observing connection status. Call after construction. */
    fun startObserving() {
        observeJob = observeScope.launch {
            client.observeStatus().collect { status ->
                _connectivityState.value = ConnectivityUiState(
                    status = status,
                    statusText = statusDisplayText(status)
                )
            }
        }
    }

    /**
     * Save a new transport node configuration and trigger a network restart.
     */
    suspend fun saveTransportNode(host: String, port: Int) {
        configRepository.setString("transport_host", host)
        configRepository.setString("transport_port", port.toString())
        configRepository.triggerNetworkRestart()
    }
}
