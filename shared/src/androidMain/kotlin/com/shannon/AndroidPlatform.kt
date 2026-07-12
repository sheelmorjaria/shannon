package com.shannon

import android.content.Context

/**
 * Process-wide Android [Context] for shared `androidMain` code that needs one but must match a
 * commonMain `expect` whose constructor takes no parameters (notably the `actual class DatabaseDriverFactory`,
 * which mirrors the no-arg desktop actual). Set once from `ShannonApplication.onCreate`.
 */
lateinit var appContext: Context
