package com.shannon.caption

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryCaptionRepositoryTest {
    private fun cap(seq: Long, final: Boolean = true, src: String = "src") =
        Caption("t$seq", "en", isFinal = final, seq = seq, sourceHash = src)

    @Test
    fun add_appends() = runTest {
        val repo = InMemoryCaptionRepository()
        repo.addCaption(cap(1)); repo.addCaption(cap(2))
        assertEquals(listOf(1L, 2L), repo.observeCaptions().first().map { it.seq })
    }

    @Test
    fun upsert_replaces_same_seq_and_source() = runTest {
        val repo = InMemoryCaptionRepository()
        repo.upsert(cap(1, final = false))
        repo.upsert(cap(1, final = true)) // partial -> final, same seq
        val list = repo.observeCaptions().first()
        assertEquals(1, list.size)
        assertEquals(true, list[0].isFinal)
    }

    @Test
    fun upsert_keeps_distinct_seqs() = runTest {
        val repo = InMemoryCaptionRepository()
        repo.upsert(cap(1)); repo.upsert(cap(1)); repo.upsert(cap(2))
        assertEquals(2, repo.observeCaptions().first().size)
    }

    @Test
    fun clear_empties() = runTest {
        val repo = InMemoryCaptionRepository()
        repo.addCaption(cap(1)); repo.clear()
        assertEquals(0, repo.observeCaptions().first().size)
    }
}
