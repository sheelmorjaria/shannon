package com.shannon.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real implementation of AudioPacketCollector that sends LXST AUDIO packets via ReticulumClient.
 * Designed for real-time audio streaming with high-priority packet transmission.
 */
class RealAudioPacketCollector(
    private val client: ReticulumClient,
    private val localHash: String,
    private val destinationHash: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : com.shannon.audio.AudioPacketCollector {

    private val packetMutex = Mutex()
    private var packetsSent = 0L
    private var packetsDropped = 0L
    private var lastSendTime = 0L

    /**
     * Collect an audio packet and send it immediately via ReticulumClient.
     * Audio packets are sent with HIGH priority and minimal buffering.
     */
    override fun collect(packet: LxstPacket) {
        scope.launch {
            try {
                // Update packet with correct destination and source hashes
                val audioPacket = packet.copy(
                    destinationHash = destinationHash,
                    sourceHash = localHash,
                    type = LxstPacketType.AUDIO // Ensure type is AUDIO
                )

                // Send immediately without queueing - audio requires real-time delivery
                client.sendLxstPacket(audioPacket)

                // Track statistics
                packetMutex.withLock {
                    packetsSent++
                    lastSendTime = System.currentTimeMillis()
                }

            } catch (e: Exception) {
                // Track packet drops but don't crash the audio pipeline
                packetMutex.withLock {
                    packetsDropped++
                }

                // Log error but continue audio stream
                println("Audio packet send failed: ${e.message}")
            }
        }
    }

    /**
     * Get statistics about audio packet transmission.
     */
    fun getStatistics(): AudioStatistics {
        return packetMutex.withLock {
            AudioStatistics(
                packetsSent = packetsSent,
                packetsDropped = packetsDropped,
                dropRate = if (packetsSent + packetsDropped > 0) {
                    packetsDropped.toDouble() / (packetsSent + packetsDropped)
                } else 0.0,
                lastSendTime = lastSendTime
            )
        }
    }

    /**
     * Reset statistics counters.
     */
    fun resetStatistics() {
        packetMutex.withLock {
            packetsSent = 0L
            packetsDropped = 0L
            lastSendTime = 0L
        }
    }
}

/**
 * Statistics about audio packet transmission.
 */
data class AudioStatistics(
    val packetsSent: Long,
    val packetsDropped: Long,
    val dropRate: Double, // 0.0 to 1.0
    val lastSendTime: Long
) {
    val totalPackets: Long get() = packetsSent + packetsDropped

    val successRate: Double get() = if (totalPackets > 0) {
        1.0 - dropRate
    } else 1.0
}