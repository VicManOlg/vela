package io.vela

import io.vela.core.common.ApplicationScope
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.model.ScanProgress
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Work that runs once per process start after setup: mirror installed apps, rescan folders when
 * enabled, then fetch artwork for anything new. Kept out of the Activity so rotation and
 * re-launches never repeat it.
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
        scope.launch {
            val prefs = settings.current()
            if (!prefs.setupCompleted) return@launch
            runCatching { apps.syncInstalled() }.onFailure { Timber.w(it, "App sync failed") }
            if (prefs.scanOnStartup) {
                library.scanInBackground()
                val finished = library.scanProgress.filterIsInstance<ScanProgress.Finished>().first()
                if (finished.result.added > 0 && prefs.scraping.autoScrapeNewGames) scrape.scrapeMissingInBackground()
            }
        }
    }
}
