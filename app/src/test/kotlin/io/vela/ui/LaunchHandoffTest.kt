package io.vela.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchHandoffTest {

    private class Launch(val title: String)

    @Test
    fun `coming back from the emulator clears the overlay`() = runTest {
        val launching = MutableStateFlow<Launch?>(null)
        val handoff = LaunchHandoff(backgroundScope, launching, clear = { launching.value = null })
        launching.value = Launch("Chrono Trigger")
        runCurrent()

        handoff.onResumed() // never left: stays
        advanceTimeBy(1_000)
        assertThat(launching.value).isNotNull()

        handoff.onPaused()
        handoff.onResumed()
        advanceTimeBy(400)
        assertThat(launching.value).isNull()
    }

    @Test
    fun `an emulator that never shows up times out`() = runTest {
        val launching = MutableStateFlow<Launch?>(null)
        LaunchHandoff(backgroundScope, launching, clear = { launching.value = null })
        launching.value = Launch("Chrono Trigger")
        runCurrent()

        advanceTimeBy(14_000)
        assertThat(launching.value).isNotNull()
        advanceTimeBy(1_500)
        assertThat(launching.value).isNull()
    }

    @Test
    fun `a newer launch is not cleared by the previous one's timers`() = runTest {
        val launching = MutableStateFlow<Launch?>(null)
        val handoff = LaunchHandoff(backgroundScope, launching, clear = { launching.value = null })
        launching.value = Launch("A")
        runCurrent()
        handoff.onPaused()
        handoff.onResumed()
        val second = Launch("B")
        launching.value = second
        advanceTimeBy(1_000)
        assertThat(launching.value).isSameInstanceAs(second)
    }
}
