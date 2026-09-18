package io.vela.core.scraper

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

sealed interface ScrapeProgress {
    data object Idle : ScrapeProgress
    data class Running(val done: Int, val total: Int, val currentTitle: String) : ScrapeProgress
    data class Finished(val scraped: Int, val failed: Int, val notFound: Int) : ScrapeProgress
    data class Stopped(val reason: String) : ScrapeProgress
}

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
) {
    private val _progress = MutableStateFlow<ScrapeProgress>(ScrapeProgress.Idle)
    val progress: StateFlow<ScrapeProgress> = _progress

    private val mutex = Mutex()
    private var job: Job? = null
    private val notFound = HashSet<Long>()

    val isRunning: Boolean get() = job?.isActive == true

    /** Scrapes every visible ROM lacking box art, in the background. */
    fun scrapeMissingInBackground() {
        if (isRunning) return
        job = scope.launch {
            val ids = gameDao.idsMissingBoxArt()
            Timber.i("Scrape queue: %d games", ids.size)
            scrapeGames(ids.map(::GameId))
        }
    }

    fun scrapeInBackground(ids: List<GameId>) {
        if (isRunning) return
        job = scope.launch { scrapeGames(ids) }
    }

    fun cancel() {
        job?.cancel()
        _progress.value = ScrapeProgress.Idle
    }

    suspend fun scrapeGames(ids: List<GameId>) = mutex.withLock {
        withContext(dispatchers.io) {
            val prefs = settings.current().scraping
            var ok = 0
            var failed = 0
            var missing = 0
            ids.forEachIndexed { index, id ->
                val entity = gameDao.byId(id.value) ?: return@forEachIndexed
                _progress.value = ScrapeProgress.Running(index, ids.size, entity.title)
                when (val outcome = scrapeOne(entity, prefs)) {
                    is Outcome.Success -> if (outcome.value) ok++ else missing++
                    is Outcome.Failure -> {
                        failed++
                        val err = outcome.error
                        if (err is VelaError.RateLimited || err is VelaError.Unauthorized) {
                            _progress.value = ScrapeProgress.Stopped(err.message)
                            return@withContext
                        }
                    }
                }
                delay(REQUEST_SPACING_MS)
            }
            _progress.value = ScrapeProgress.Finished(ok, failed, missing)
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
        val artworkProviders = prefs.artworkProviderIds.mapNotNull { providers[it] }.filter { it.isAvailable(prefs) }
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
                    is Outcome.Success -> r.value.maxByOrNull { it.score }?.takeIf { it.metadata.hasContent() }?.let { best ->
                        metadataDao.upsertMetadata(best.metadata.toEntity(game.id))
                        stored = true
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

        if (!stored) notFound += game.id
        return Outcome.success(stored)
    }

    private fun io.vela.core.model.GameMetadata.hasContent() =
        !description.isNullOrBlank() || !developer.isNullOrBlank() || !releaseDate.isNullOrBlank() || genres.isNotEmpty()

    private fun io.vela.core.model.GameMetadata.toEntity(gameId: Long) = GameMetadataEntity(
        gameId = gameId, title = title, description = description, developer = developer, publisher = publisher,
        releaseDate = releaseDate, genres = genres.joinToString("|").ifEmpty { null }, players = players, rating = rating,
        region = region, providerId = providerId, providerGameId = providerGameId, scrapedAt = scrapedAt ?: System.currentTimeMillis(),
    )

    private companion object {
        const val REQUEST_SPACING_MS = 350L
    }
}
