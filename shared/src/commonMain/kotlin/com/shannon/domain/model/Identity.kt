package com.shannon.domain.model

import kotlin.random.Random

/**
 * Represents a Reticulum identity with a public/private key pair.
 * Keys are 256-bit (32 bytes) as per Reticulum's identity model.
 */
data class Identity(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    companion object {
        /** Generate a new random identity with 256-bit keys. */
        fun generate(): Identity {
            val publicKey = Random.Default.nextBytes(32)
            val privateKey = Random.Default.nextBytes(32)
            return Identity(publicKey, privateKey)
        }

        /** Deserialize an identity from its byte representation. */
        fun fromBytes(bytes: ByteArray): Identity {
            require(bytes.size == 64) { "Identity bytes must be exactly 64 bytes (32 public + 32 private)" }
            return Identity(
                publicKey = bytes.copyOfRange(0, 32),
                privateKey = bytes.copyOfRange(32, 64)
            )
        }
    }

    /** Serialize this identity to a 64-byte array. */
    fun toBytes(): ByteArray {
        return publicKey + privateKey
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Identity) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        return publicKey.contentHashCode()
    }
}
