package io.vela.core.scraper

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.vela.core.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the "fetch missing artwork" pass as unique WorkManager work: it survives the process being
 * killed while an emulator runs, and waits for its network (Wi-Fi only, or any connection) instead
 * of giving up. A long library is done in batches, each chained after the previous one.
 */
@Singleton
class ScrapeScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settings: SettingsRepository,
    private val service: ScrapeService,
    private val network: NetworkStatus,
) {
    /** Starts the pass unless one is already queued or running. */
    fun scrapeMissing() {
        service.beginPass()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request())
        if (wifiOnly() && !network.isUnmetered()) service.markWaitingForWifi()
    }

    /** Called by the running batch when games are left: the next batch runs right after it. */
    internal fun scheduleNextBatch() {
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
        service.cancel()
    }

    private fun wifiOnly(): Boolean = settings.loaded.scraping.wifiOnly

    private fun request() = OneTimeWorkRequestBuilder<ScrapeWorker>()
        .setConstraints(Constraints(requiredNetworkType = if (wifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED))
        .build()

    private companion object {
        const val WORK_NAME = "scrape-missing"
    }
}
