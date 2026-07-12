package com.shannon.domain.model

/**
 * A contact in the address book, identified by a Reticulum destination hash.
 * Destination hashes are 16 bytes represented as 32 hex characters.
 */
data class Contact(
    val destinationHash: String,
    val displayName: String,
    val publicKey: ByteArray? = null
) {
    init {
        require(destinationHash.length == 32) {
            "Destination hash must be exactly 32 hex characters, got ${destinationHash.length}"
        }
        require(destinationHash.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Destination hash must contain only hex characters (0-9, a-f)"
        }
    }

    /** Returns the display name, or a truncated hash if no name is set. */
    val effectiveDisplayName: String
        get() = displayName.ifEmpty { destinationHash.take(8) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return destinationHash == other.destinationHash
    }

    override fun hashCode(): Int {
        return destinationHash.hashCode()
    }
}
