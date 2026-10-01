package io.vela.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Injected dispatchers so tests can swap them for a TestDispatcher. */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}

/**
 * Application-wide scope for work that must outlive a screen (scans, downloads, play sessions).
 * A failure in one job is reported to [onUncaught] instead of killing the process: a disk-full
 * SQLiteException in a background scrape must not take the frontend down with it.
 */
class ApplicationScope(dispatchers: DispatcherProvider, onUncaught: (Throwable) -> Unit = {}) :
    CoroutineScope by CoroutineScope(SupervisorJob() + dispatchers.default + CoroutineExceptionHandler { _, e -> onUncaught(e) })
