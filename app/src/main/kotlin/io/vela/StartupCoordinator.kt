package io.vela

import io.vela.core.common.ApplicationScope
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.model.ScanProgress
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide background work kept out of the Activity so rotation and re-launches never repeat
 * it: mirror installed apps and rescan folders on start (when enabled), and fetch artwork for
 * newly found games after *any* scan (startup, Home "rescan", Settings) once setup is complete.
 * The setup flow's own scan is excluded on purpose: its last step asks the user whether to fetch
 * artwork.
 */
@Singleton
class StartupCoordinator @Inject constructor(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val apps: AppsRepository,
    private val scrape: ScrapeRepository,
    private val scope: ApplicationScope,
) {
    private var started = false

    fun onAppStarted() {
        if (started) return
        started = true
        scope.launch { autoScrapeAfterScans() }
        scope.launch { startupWork() }
    }

    private suspend fun autoScrapeAfterScans() {
        settings.settings.map { it.setupCompleted }.filter { it }.first()
        library.scanProgress.filterIsInstance<ScanProgress.Finished>().collect { finished ->
            if (finished.result.added > 0 && settings.current().scraping.autoScrapeNewGames) {
                Timber.i("Scan added %d games, fetching artwork", finished.result.added)
                scrape.scrapeMissingInBackground()
            }
        }
    }

    private suspend fun startupWork() {
        val prefs = settings.current()
        if (!prefs.setupCompleted) return
        runCatching { apps.syncInstalled() }.onFailure { Timber.w(it, "App sync failed") }
        if (prefs.scanOnStartup) library.scanInBackground()
    }
}
