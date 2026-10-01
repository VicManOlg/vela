package io.vela.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * When to take the full-screen launch overlay down: once the UI has gone away (the emulator took
 * the screen) and come back, or after [timeoutMs] if the emulator never showed up. Lives in the
 * ViewModel, so an Activity recreated while the emulator runs still knows it went away.
 */
internal class LaunchHandoff<T : Any>(
    private val scope: CoroutineScope,
    private val launching: StateFlow<T?>,
    private val clear: () -> Unit,
    private val timeoutMs: Long = 15_000,
    private val settleMs: Long = 350,
) {
    private var wentAway = false
    private var settle: Job? = null

    init {
        scope.launch {
            launching.collectLatest { current ->
                wentAway = false
                if (current == null) return@collectLatest
                delay(timeoutMs)
                clearIfStill(current)
            }
        }
    }

    fun onPaused() {
        if (launching.value != null) wentAway = true
    }

    fun onResumed() {
        val current = launching.value ?: return
        if (!wentAway) return
        settle?.cancel()
        settle = scope.launch {
            delay(settleMs)
            clearIfStill(current)
        }
    }

    private fun clearIfStill(current: T) {
        if (launching.value === current) clear()
    }
}
