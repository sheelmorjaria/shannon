package com.shannon.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class IdentityTest {

    @Test
    fun `identity generates valid 256-bit key pair`() {
        val identity = Identity.generate()

        // 256 bits = 32 bytes
        assertEquals(32, identity.publicKey.size)
        assertEquals(32, identity.privateKey.size)
    }

    @Test
    fun `two generated identities are different`() {
        val identity1 = Identity.generate()
        val identity2 = Identity.generate()

        // Key pairs should be unique (extremely unlikely collision)
        assertTrue(
            !identity1.publicKey.contentEquals(identity2.publicKey),
            "Two generated identities should have different public keys"
        )
    }

    @Test
    fun `serialization round-trip preserves key material`() {
        val original = Identity.generate()
        val bytes = original.toBytes()
        val restored = Identity.fromBytes(bytes)

        assertContentEquals(original.publicKey, restored.publicKey)
        assertContentEquals(original.privateKey, restored.privateKey)
    }

    @Test
    fun `identity equality based on public key`() {
        val identity = Identity.generate()
        val restored = Identity.fromBytes(identity.toBytes())

        assertEquals(identity, restored)
    }
}
