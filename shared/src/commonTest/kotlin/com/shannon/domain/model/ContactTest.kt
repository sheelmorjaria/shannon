package com.shannon.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContactTest {

    @Test
    fun `valid contact created with correct destination hash`() {
        val hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4" // 32 hex chars = 16 bytes
        val contact = Contact(
            destinationHash = hash,
            displayName = "Alice"
        )

        assertEquals(hash, contact.destinationHash)
        assertEquals("Alice", contact.displayName)
    }

    @Test
    fun `display name defaults to truncated hash when empty`() {
        val hash = "abcdef1234567890abcdef1234567890"
        val contact = Contact(
            destinationHash = hash,
            displayName = ""
        )

        assertEquals("abcdef12", contact.effectiveDisplayName)
    }

    @Test
    fun `rejects destination hash that is too short`() {
        assertFailsWith<IllegalArgumentException> {
            Contact(
                destinationHash = "a1b2c3",
                displayName = "Bad"
            )
        }
    }

    @Test
    fun `rejects destination hash that is too long`() {
        assertFailsWith<IllegalArgumentException> {
            Contact(
                destinationHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4a1",
                displayName = "Bad"
            )
        }
    }

    @Test
    fun `rejects non-hex characters in destination hash`() {
        assertFailsWith<IllegalArgumentException> {
            Contact(
                destinationHash = "g1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
                displayName = "Bad"
            )
        }
    }

    @Test
    fun `accepts uppercase hex in destination hash`() {
        val hash = "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4"
        val contact = Contact(
            destinationHash = hash,
            displayName = "Valid"
        )
        assertEquals(hash, contact.destinationHash)
    }
}
