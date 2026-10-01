package io.vela

import io.vela.core.common.ApplicationScope
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.model.ScanProgress
import io.vela.core.model.ScanResult
import io.vela.core.scraper.NetworkStatus
import io.vela.core.scraper.store.PlatformIconStore
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import io.vela.core.data.repository.ThemeRepository

/**
 * Process-wide background work kept out of the Activity so rotation and re-launches never repeat
 * it: mirror installed apps and rescan folders on start (when enabled), fetch artwork for newly
 * found games after *any* scan (startup, Home "rescan", Settings) once setup is complete, and
 * keep the platform icons of the active theme downloaded.
 * The setup flow's own scan is excluded on purpose: its last step asks the user whether to fetch
 * artwork.
 */
@Singleton
class StartupCoordinator @Inject constructor(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val apps: AppsRepository,
    private val scrape: ScrapeRepository,
    private val themes: ThemeRepository,
    private val platformIcons: PlatformIconStore,
    private val network: NetworkStatus,
    private val scope: ApplicationScope,
) {
    private var started = false

    fun onAppStarted() {
        if (started) return
        started = true
        scope.launch { autoScrapeAfterScans() }
        scope.launch { syncPlatformIcons() }
        scope.launch { startupWork() }
    }

    private suspend fun autoScrapeAfterScans() {
        scansToScrape(settings.settings.map { it.setupCompleted }, library.scanProgress).collect { result ->
            if (result.added > 0 && settings.current().scraping.autoScrapeNewGames) {
                Timber.i("Scan added %d games, fetching artwork", result.added)
                scrape.scrapeMissingInBackground()
            }
        }
    }

    /** Re-syncs when the theme (icon set) or the Wi-Fi-only preference changes, and when connectivity comes back. */
    @OptIn(FlowPreview::class)
    private suspend fun syncPlatformIcons() {
        val prefs = combine(settings.settings, themes.catalog) { s, catalog -> catalog.byId(s.themeId).platformIcons to s.scraping.wifiOnly }.distinctUntilChanged()
        val connectivity = network.changes.onStart { emit(Unit) }
        combine(prefs, connectivity) { p, _ -> p }
            .debounce(1500)
            .collectLatest { (spec, wifiOnly) ->
                runCatching { platformIcons.sync(spec, allowNetwork = !wifiOnly || network.isUnmetered()) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else Timber.w(it, "Platform icon sync failed") }
            }
    }

    private suspend fun startupWork() {
        val prefs = settings.current()
        if (!prefs.setupCompleted) return
        runCatching { apps.syncInstalled() }.onFailure { Timber.w(it, "App sync failed") }
        if (prefs.scanOnStartup) library.scanInBackground()
    }
}

/**
 * Finished scans whose new games should get artwork: every scan once setup is complete, except the
 * setup flow's own. [progress] is a StateFlow, so when setup completes in this process its current
 * value is the setup scan's result and must not count.
 */
internal fun scansToScrape(setupCompleted: Flow<Boolean>, progress: StateFlow<ScanProgress>): Flow<ScanResult> = flow {
    val completedAtStart = setupCompleted.first()
    if (!completedAtStart) setupCompleted.first { it }
    val scans = if (completedAtStart) progress else progress.drop(1)
    emitAll(scans.filterIsInstance<ScanProgress.Finished>().map { it.result })
}
