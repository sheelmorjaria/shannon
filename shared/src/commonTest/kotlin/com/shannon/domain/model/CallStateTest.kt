package com.shannon.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallStateTest {

    @Test
    fun `call starts in IDLE state`() {
        val call = Call()
        assertEquals(CallState.IDLE, call.state)
    }

    @Test
    fun `transition from IDLE to RINGING on incoming call`() {
        val call = Call()
        call.incomingRing("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        assertEquals(CallState.RINGING, call.state)
    }

    @Test
    fun `transition from IDLE to OUTGOING on outgoing call`() {
        val call = Call()
        call.initiate("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        assertEquals(CallState.OUTGOING, call.state)
    }

    @Test
    fun `transition from RINGING to CONNECTED on accept`() {
        val call = Call()
        call.incomingRing("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        call.accept()
        assertEquals(CallState.CONNECTED, call.state)
    }

    @Test
    fun `transition from RINGING to IDLE on reject`() {
        val call = Call()
        call.incomingRing("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        call.reject()
        assertEquals(CallState.IDLE, call.state)
    }

    @Test
    fun `transition from OUTGOING to CONNECTED on remote accept`() {
        val call = Call()
        call.initiate("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        call.remoteAccept()
        assertEquals(CallState.CONNECTED, call.state)
    }

    @Test
    fun `transition from CONNECTED to IDLE on hangup`() {
        val call = Call()
        call.initiate("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        call.remoteAccept()
        call.hangup()
        assertEquals(CallState.IDLE, call.state)
    }

    @Test
    fun `reject from CONNECTED state is invalid`() {
        val call = Call()
        call.initiate("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")
        call.remoteAccept()
        assertFailsWith<IllegalStateException> {
            call.reject()
        }
    }

    @Test
    fun `hangup from IDLE state is invalid`() {
        val call = Call()
        assertFailsWith<IllegalStateException> {
            call.hangup()
        }
    }

    @Test
    fun `call stores peer destination hash`() {
        val hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"
        val call = Call()
        call.initiate(hash)
        assertEquals(hash, call.peerHash)
    }
}
