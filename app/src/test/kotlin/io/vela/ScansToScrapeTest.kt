package io.vela

import com.google.common.truth.Truth.assertThat
import io.vela.core.model.ScanProgress
import io.vela.core.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScansToScrapeTest {

    private fun finished(added: Int) = ScanProgress.Finished(ScanResult(added, 0, 0, 0, 0))

    @Test
    fun `the setup scan is skipped, later scans count`() = runTest(UnconfinedTestDispatcher()) {
        val setupCompleted = MutableStateFlow(false)
        val progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
        val seen = mutableListOf<ScanResult>()
        backgroundScope.launch { scansToScrape(setupCompleted, progress).toList(seen) }

        progress.value = finished(12)
        setupCompleted.value = true
        progress.value = finished(3)

        assertThat(seen.map { it.added }).containsExactly(3)
    }

    @Test
    fun `with setup already done, the first scan of the process counts`() = runTest(UnconfinedTestDispatcher()) {
        val progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
        val seen = mutableListOf<ScanResult>()
        backgroundScope.launch { scansToScrape(MutableStateFlow(true), progress).toList(seen) }

        progress.value = finished(5)

        assertThat(seen.map { it.added }).containsExactly(5)
    }
}
