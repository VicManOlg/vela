package io.vela.core.scraper

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * One batch of the missing-artwork pass. Batches stay well under WorkManager's 10-minute limit
 * for regular work, so no foreground service or notification is needed.
 */
@HiltWorker
internal class ScrapeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: ScrapeService,
    private val scheduler: ScrapeScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val more = service.scrapeMissing(deadlineMs = System.currentTimeMillis() + BATCH_MS)
        if (more) scheduler.scheduleNextBatch()
        Result.success()
    } catch (e: CancellationException) {
        // Stopped by the system (constraints, battery) or cancelled by the user, who resets the
        // progress to Idle first, so only a run still showing progress is marked paused.
        service.markPausedIfRunning()
        throw e
    }

    private companion object {
        const val BATCH_MS = 8 * 60 * 1000L
    }
}
