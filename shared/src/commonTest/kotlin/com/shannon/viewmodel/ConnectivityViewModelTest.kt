package com.shannon.viewmodel

import app.cash.turbine.test
import com.shannon.domain.repository.ConfigRepository
import com.shannon.network.ConnectionStatus
import com.shannon.network.FakeReticulumClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fake ConfigRepository for testing.
 */
class FakeConfigRepository : ConfigRepository {
    internal val _configs = mutableMapOf<String, String>()

    var saveCallCount = 0
        private set
    var lastKey: String? = null
        private set
    var lastValue: String? = null
        private set
    var restartTriggered = false

    override fun getString(key: String): String? = _configs[key]

    override fun setString(key: String, value: String) {
        saveCallCount++
        lastKey = key
        lastValue = value
        _configs[key] = value
    }

    override suspend fun triggerNetworkRestart() {
        restartTriggered = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityViewModelTest {

    @Test
    fun `network status reflects disconnected state`() = runTest {
        val client = FakeReticulumClient()
        client.simulateStatusChange(ConnectionStatus.DISCONNECTED)
        val configRepo = FakeConfigRepository()
        val vm = ConnectivityViewModel(client, configRepo, backgroundScope)
        vm.startObserving()

        vm.connectivityState.test {
            val state = awaitItem()
            assertEquals(ConnectionStatus.DISCONNECTED, state.status)
            assertTrue(state.statusText.contains("No Connection", ignoreCase = true))
        }
    }

    @Test
    fun `network status reflects connected state`() = runTest {
        val client = FakeReticulumClient()
        val configRepo = FakeConfigRepository()
        val vm = ConnectivityViewModel(client, configRepo, backgroundScope)
        vm.startObserving()

        vm.connectivityState.test {
            // Initial state is DISCONNECTED
            assertEquals(ConnectionStatus.DISCONNECTED, awaitItem().status)

            // Simulate connecting
            client.simulateStatusChange(ConnectionStatus.CONNECTED)
            val connected = awaitItem()
            assertEquals(ConnectionStatus.CONNECTED, connected.status)
        }
    }

    @Test
    fun `saving transport node persists config and triggers restart`() = runTest {
        val client = FakeReticulumClient()
        val configRepo = FakeConfigRepository()
        val vm = ConnectivityViewModel(client, configRepo, backgroundScope)
        vm.startObserving()

        vm.saveTransportNode("reticulum.example.com", 4242)

        // Verify config was saved (two keys: host and port)
        assertTrue(configRepo.saveCallCount >= 2)
        assertEquals("reticulum.example.com", configRepo._configs["transport_host"])
        assertEquals("4242", configRepo._configs["transport_port"])

        // Verify restart was triggered
        assertTrue(configRepo.restartTriggered)
    }
}
