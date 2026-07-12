package com.shannon.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.shannon.appContext

/**
 * Android `actual` for [DatabaseDriverFactory]. Mirrors the desktop actual's minimal shape:
 * no constructor parameters (the `expect` declares none) and a synchronous [createDriver]
 * returning [SqlDriver]. Uses the process-wide [appContext] with [AndroidSqliteDriver].
 *
 * (Migration logic is intentionally omitted to match the desktop actual; SQLDelight creates the
 * schema when the database file is absent. The earlier androidMain version here never compiled —
 * it predated this expect/actual contract and referenced a since-removed query.)
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = ShannonDatabase.Schema,
            context = appContext,
            name = "shannon.db",
        )
}
