package com.shannon.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReticulumClientTest {

    @Test
    fun `announce is called on initialize`() = runTest {
        val client = FakeReticulumClient()
        val manager = ReticulumManager(client)

        manager.initialize()

        assertEquals(1, client.announceCalls.size, "announce() should be called once on init")
    }

    @Test
    fun `connect stores host and port`() = runTest {
        val client = FakeReticulumClient()
        val manager = ReticulumManager(client)

        manager.connect("reticulum.local", 4242)

        assertEquals(1, client.connectCalls.size)
        assertEquals("reticulum.local" to 4242, client.connectCalls[0])
    }

    @Test
    fun `disconnect propagates to client`() = runTest {
        val client = FakeReticulumClient()
        val manager = ReticulumManager(client)

        manager.disconnect()

        assertTrue(client.disconnectCalled)
    }
}
