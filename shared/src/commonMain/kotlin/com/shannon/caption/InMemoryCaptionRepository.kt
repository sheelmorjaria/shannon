package com.shannon.caption

import com.shannon.domain.repository.CaptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [CaptionRepository] for the current call/session.
 *
 * [upsert] de-duplicates by (seq, sourceHash) so a finalized caption replaces its earlier
 * partial (final=false) with the same seq — see tasks 4.4.
 */
class InMemoryCaptionRepository : CaptionRepository {
    private val _captions = MutableStateFlow<List<Caption>>(emptyList())

    override fun observeCaptions(): Flow<List<Caption>> = _captions.asStateFlow()

    override fun addCaption(caption: Caption) {
        _captions.value = _captions.value + caption
    }

    override fun upsert(caption: Caption) {
        val current = _captions.value
        val index = current.indexOfFirst { it.seq == caption.seq && it.sourceHash == caption.sourceHash }
        _captions.value = if (index >= 0) {
            current.toMutableList().apply { this[index] = caption }
        } else {
            current + caption
        }
    }

    override fun clear() {
        _captions.value = emptyList()
    }
}
