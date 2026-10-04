package io.vela.core.scraper

import io.vela.core.model.ScrapeProgress
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.ApplicationScope
import io.vela.core.common.DispatcherProvider
import io.vela.core.common.Outcome
import io.vela.core.common.VelaError
import io.vela.core.database.dao.GameDao
import io.vela.core.database.dao.MetadataDao
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.GameMetadataEntity
import io.vela.core.database.entity.LocationType
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import io.vela.core.model.MetadataMatch
import io.vela.core.model.MetadataQuery
import io.vela.core.model.PlatformId
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.store.ArtworkStore
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs scraping jobs sequentially with a small delay between requests. Metadata comes from the
 * configured metadata provider; artwork is collected from every configured artwork provider in
 * order, stopping per type at the first successful download. Negative results are remembered
 * for the session so a 5,000-game library never hammers a provider twice.
 */
@Singleton
class ScrapeService @Inject constructor(
    private val providers: ProviderRegistry,
    private val gameDao: GameDao,
    private val metadataDao: MetadataDao,
    private val artworkStore: ArtworkStore,
    private val settings: SettingsRepository,
    private val platforms: PlatformCatalog,
    private val scope: ApplicationScope,
    private val dispatchers: DispatcherProvider,
    private val network: NetworkStatus,
) {
    private val _progress = MutableStateFlow<ScrapeProgress>(ScrapeProgress.Idle)
    val progress: StateFlow<ScrapeProgress> = _progress

    private val mutex = Mutex()
    private val startLock = Any()
    private var job: Job? = null
    private val notFound: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val attemptedThisPass: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * One batch of "every visible ROM lacking artwork or metadata", up to [deadlineMs] (wall clock).
     * Runs from [ScrapeWorker]; ask [ScrapeScheduler] to start it.
     * @return true when games are left for a next batch.
     */
    suspend fun scrapeMissing(deadlineMs: Long = Long.MAX_VALUE): Boolean = stopOnFailure(default = false) {
        val prefs = settings.current().scraping
        val wantsLogos = providers.all.any { it.isAvailable(prefs) && ArtworkType.LOGO in it.info.artworkTypes }
        // Games tried earlier in this pass (and still lacking art) wait for the next pass, or a
        // provider error would make every batch retry them and the chain never end.
        // A game with box art but no background is asked again, once a week: providers gain art.
        val ids = gameDao.idsNeedingScrape(wantsLogos, retryBefore = System.currentTimeMillis() - BACKGROUND_RETRY_MS)
            .filterNot { it in notFound || it in attemptedThisPass }
        Timber.i("Scrape queue: %d games", ids.size)
        scrapeGames(ids.map(::GameId), deadlineMs) == BatchEnd.DEADLINE
    }

    /** A new pass (Settings, a scan, setup) retries everything still missing. */
    fun beginPass() = attemptedThisPass.clear()

    /** Shown while the work waits for its network constraint. */
    fun markWaitingForWifi() {
        if (_progress.value !is ScrapeProgress.Running) _progress.value = ScrapeProgress.Stopped("Waiting for Wi-Fi")
    }

    /** The system stopped the work (connection lost, battery); WorkManager resumes it later. */
    fun markPausedIfRunning() {
        if (_progress.value is ScrapeProgress.Running) _progress.value = ScrapeProgress.Stopped("Paused, resumes when the connection allows")
    }

    fun scrapeInBackground(ids: List<GameId>) = startInBackground { stopOnFailure(Unit) { scrapeGames(ids) } }

    /** Setup, the startup coordinator and Settings may all ask at once: only one run starts. */
    private fun startInBackground(block: suspend () -> Unit) = synchronized(startLock) {
        if (isRunning) return@synchronized
        job = scope.launch { block() }
    }

    /** A run that dies (database or storage error) ends as Stopped, not stuck on Running. */
    private suspend fun <T> stopOnFailure(default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.e(e, "Background scrape failed")
        _progress.value = ScrapeProgress.Stopped("Artwork fetch failed: ${e.message ?: e::class.simpleName}")
        default
    }

    private enum class BatchEnd { DONE, DEADLINE, STOPPED }

    fun cancel() = synchronized(startLock) {
        job?.cancel()
        // Forget it now: a new start must not be ignored while the cancelled run winds down.
        job = null
        _progress.value = ScrapeProgress.Idle
    }

    private suspend fun scrapeGames(ids: List<GameId>, deadlineMs: Long = Long.MAX_VALUE): BatchEnd = mutex.withLock {
        withContext(dispatchers.io) {
            val prefs = settings.current().scraping
            if (prefs.wifiOnly && !network.isUnmetered()) {
                Timber.i("Scrape postponed: Wi-Fi only and the connection is metered or absent")
                _progress.value = ScrapeProgress.Stopped("Waiting for Wi-Fi")
                return@withContext BatchEnd.STOPPED
            }
            var ok = 0
            var failed = 0
            var missing = 0
            ids.forEachIndexed { index, id ->
                if (System.currentTimeMillis() >= deadlineMs) return@withContext BatchEnd.DEADLINE
                attemptedThisPass += id.value
                val entity = gameDao.byId(id.value) ?: return@forEachIndexed
                _progress.value = ScrapeProgress.Running(index, ids.size, entity.title)
                when (val outcome = scrapeOne(entity, prefs)) {
                    is Outcome.Success -> if (outcome.value) ok++ else missing++
                    is Outcome.Failure -> {
                        failed++
                        val err = outcome.error
                        if (err is VelaError.RateLimited || err is VelaError.Unauthorized) {
                            _progress.value = ScrapeProgress.Stopped(err.message)
                            return@withContext BatchEnd.STOPPED
                        }
                    }
                }
                delay(REQUEST_SPACING_MS)
            }
            _progress.value = ScrapeProgress.Finished(ok, failed, missing)
            BatchEnd.DONE
        }
    }

    /** Scrapes a single game now (detail screen "Refresh metadata"). */
    suspend fun scrapeGame(id: GameId, overwrite: Boolean = true): Outcome<Boolean> = withContext(dispatchers.io) {
        val entity = gameDao.byId(id.value) ?: return@withContext Outcome.success(false)
        scrapeOne(entity, settings.current().scraping.copy(overwriteExisting = overwrite), force = true)
    }

    /** @return success(true) when something was stored, success(false) when nothing matched. */
    private suspend fun scrapeOne(game: GameEntity, prefs: ScrapingSettings, force: Boolean = false): Outcome<Boolean> {
        if (!force && game.id in notFound) return Outcome.success(false)
        if (game.locationType == LocationType.ANDROID_APP) return Outcome.success(false)

        val query = MetadataQuery(
            gameId = GameId(game.id),
            platformId = PlatformId(game.platformId),
            title = game.title,
            fileName = game.fileName,
            fileSize = game.fileSize.takeIf { it > 0 },
            preferredRegions = prefs.preferredRegions,
            language = prefs.language,
        )

        var stored = false
        val metadataProvider = providers[prefs.metadataProviderId]?.takeIf { it.isAvailable(prefs) }
        // The user's order first, then every other provider that is ready (a key was entered).
        val artworkProviders = (prefs.artworkProviderIds + providers.all.map { it.info.id }).distinct()
            .mapNotNull { providers[it] }.filter { it.isAvailable(prefs) }
        val resultsByProvider = HashMap<String, List<MetadataMatch>>()

        suspend fun matchesFor(provider: MetadataProvider): Outcome<List<MetadataMatch>> {
            resultsByProvider[provider.info.id]?.let { return Outcome.success(it) }
            return provider.search(query, prefs).also { r -> r.getOrNull()?.let { resultsByProvider[provider.info.id] = it } }
        }

        if (metadataProvider != null) {
            val existing = metadataDao.metadata(game.id)
            if (existing == null || prefs.overwriteExisting) {
                when (val r = matchesFor(metadataProvider)) {
                    is Outcome.Failure -> return r
                    is Outcome.Success -> {
                        val best = r.value.maxByOrNull { it.score }?.takeIf { it.metadata.hasContent() }
                        if (best != null) {
                            // A refresh fills gaps and updates what the provider knows; it never blanks
                            // fields another provider (or Wikipedia) filled earlier.
                            metadataDao.upsertMetadata(best.metadata.toEntity(game.id).fillingFrom(existing))
                            stored = true
                        } else if (existing == null) {
                            // Remember the attempt so the game is not asked about on every run.
                            metadataDao.upsertMetadata(GameMetadataEntity(gameId = game.id, providerId = metadataProvider.info.id, scrapedAt = System.currentTimeMillis()))
                        }
                    }
                }
            }
        }

        val wanted = ArtworkType.entries.filter { it != ArtworkType.VIDEO || prefs.downloadVideos }
        val have = metadataDao.artwork(game.id).map { it.type }.toSet()
        val missingTypes = wanted.filter { prefs.overwriteExisting || it.name !in have }.toMutableSet()
        for (provider in artworkProviders) {
            if (missingTypes.isEmpty()) break
            val matches = when (val r = matchesFor(provider)) {
                is Outcome.Failure -> if (r.error is VelaError.RateLimited || r.error is VelaError.Unauthorized) return r else continue
                is Outcome.Success -> r.value.sortedByDescending { it.score }
            }
            for (match in matches) {
                val candidates = match.artwork.filter { it.type in missingTypes }.distinctBy { it.type }
                for (candidate in candidates) {
                    if (artworkStore.download(GameId(game.id), candidate, provider.info.id, prefs.overwriteExisting)) {
                        missingTypes -= candidate.type
                        stored = true
                    }
                }
                if (missingTypes.none { it != ArtworkType.VIDEO }) break
            }
        }

        // Stamp the attempt, so a game no provider has a background for waits a week, not a pass.
        val now = System.currentTimeMillis()
        if (metadataDao.touchScraped(game.id, now) == 0) {
            metadataDao.upsertMetadata(GameMetadataEntity(gameId = game.id, providerId = metadataProvider?.info?.id ?: "none", scrapedAt = now))
        }
        if (!stored) notFound += game.id
        return Outcome.success(stored)
    }

    private fun io.vela.core.model.GameMetadata.hasContent() =
        !description.isNullOrBlank() || !developer.isNullOrBlank() || !publisher.isNullOrBlank() || !releaseDate.isNullOrBlank() || genres.isNotEmpty() || !franchise.isNullOrBlank()

    private fun GameMetadataEntity.fillingFrom(old: GameMetadataEntity?): GameMetadataEntity {
        if (old == null) return this
        return copy(
            title = title ?: old.title, description = description ?: old.description, developer = developer ?: old.developer,
            publisher = publisher ?: old.publisher, releaseDate = releaseDate ?: old.releaseDate, genres = genres ?: old.genres,
            players = players ?: old.players, rating = rating ?: old.rating, region = region ?: old.region,
            franchise = franchise ?: old.franchise, ageRating = ageRating ?: old.ageRating, sourceUrl = sourceUrl ?: old.sourceUrl,
        )
    }

    private fun io.vela.core.model.GameMetadata.toEntity(gameId: Long) = GameMetadataEntity(
        gameId = gameId, title = title, description = description, developer = developer, publisher = publisher,
        releaseDate = releaseDate, genres = genres.joinToString("|").ifEmpty { null }, players = players, rating = rating,
        region = region, franchise = franchise, ageRating = ageRating, sourceUrl = sourceUrl,
        providerId = providerId, providerGameId = providerGameId, scrapedAt = scrapedAt ?: System.currentTimeMillis(),
    )

    private companion object {
        const val REQUEST_SPACING_MS = 350L
    }
}

private const val BACKGROUND_RETRY_MS = 7L * 24 * 60 * 60 * 1000
