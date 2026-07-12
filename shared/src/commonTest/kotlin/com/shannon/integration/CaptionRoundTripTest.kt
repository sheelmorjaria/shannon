package com.shannon.integration

import com.shannon.caption.CaptionTransport
import com.shannon.caption.InMemoryCaptionRepository
import com.shannon.speech.Transcript
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionRoundTripTest {

    private val hashA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val hashB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    @Test
    fun `caption sent by A is received by B over the in-memory network`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        val clientB = network.clientB(hashB)
        network.connect()

        val repoB = InMemoryCaptionRepository()
        val transportA = CaptionTransport(clientA, InMemoryCaptionRepository())
        val transportB = CaptionTransport(clientB, repoB)
        val receiveJob = transportB.startReceiving(this)

        transportA.send(
            Transcript("Hola", "es", translated = "Hello", isFinal = true, seq = 1),
            sourceHash = hashA,
            destinationHash = hashB,
        )
        testScheduler.advanceUntilIdle()

        val captions = repoB.observeCaptions().first()
        assertEquals(1, captions.size)
        assertEquals(hashA, captions[0].sourceHash)
        assertEquals("Hola", captions[0].text)
        assertEquals("Hello", captions[0].translated)

        receiveJob.cancel()
    }
}
