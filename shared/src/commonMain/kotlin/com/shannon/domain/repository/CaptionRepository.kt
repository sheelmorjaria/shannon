package com.shannon.domain.repository

import com.shannon.caption.Caption
import kotlinx.coroutines.flow.Flow

/**
 * Holds live captions for the current call/session.
 * Mirrors the [MessageRepository] repository pattern.
 */
interface CaptionRepository {
    /** Observe the current ordered list of captions. */
    fun observeCaptions(): Flow<List<Caption>>

    /** Add a received or locally produced caption. */
    fun addCaption(caption: Caption)

    /** Replace an interim caption sharing the same [Caption.seq], or append if new. */
    fun upsert(caption: Caption)

    /** Clear all captions (e.g. when a call ends). */
    fun clear()
}
