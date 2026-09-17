package io.vela.core.common

import kotlinx.coroutines.CoroutineDispatcher
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

/** Application-wide scope for work that must outlive a screen (scans, downloads, play sessions). */
class ApplicationScope(dispatchers: DispatcherProvider) :
    CoroutineScope by CoroutineScope(SupervisorJob() + dispatchers.default)
