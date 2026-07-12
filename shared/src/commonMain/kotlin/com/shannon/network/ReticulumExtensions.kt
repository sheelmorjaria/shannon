package com.shannon.network

/**
 * Extension functions and utilities for Reticulum-kt library.
 *
 * These will be implemented when we integrate the actual reticulum-kt API.
 */

// TODO: Add actual reticulum-kt integration methods when the API is fully explored

/**
 * Convert a hex string to byte array.
 */
fun hexToBytes(hex: String): ByteArray {
    return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * Convert a byte array to hex string.
 */
fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
}