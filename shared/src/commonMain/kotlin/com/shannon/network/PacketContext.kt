package com.shannon.network

/**
 * Packet context types for different communication protocols.
 */
enum class PacketContext {
    LXMF,      // LXMF messaging
    LXST,      // LXST voice signaling
    DATA       // Generic data packets
}