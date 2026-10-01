package io.vela.core.data.repository

import io.vela.core.common.Outcome
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.ProviderRegistry
import io.vela.core.model.ScrapeProgress
import io.vela.core.scraper.ScrapeScheduler
import io.vela.core.scraper.ScrapeService
import io.vela.core.scraper.store.ArtworkStore
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Facade over the scraping service and artwork store for the feature modules. */
@Singleton
class ScrapeRepository @Inject constructor(
    private val service: ScrapeService,
    private val scheduler: ScrapeScheduler,
    private val registry: ProviderRegistry,
    private val artwork: ArtworkStore,
) {
    val progress: StateFlow<ScrapeProgress> get() = service.progress
    val isRunning: Boolean get() = service.isRunning

    fun providers(): List<MetadataProviderInfo> = registry.all.map { it.info }

    fun availableProviders(settings: ScrapingSettings): List<MetadataProviderInfo> =
        registry.all.filter { it.isAvailable(settings) }.map { it.info }

    fun scrapeMissingInBackground() = scheduler.scrapeMissing()

    fun scrapeInBackground(ids: List<GameId>) = service.scrapeInBackground(ids)

    suspend fun scrapeGame(id: GameId, overwrite: Boolean = true): Outcome<Boolean> = service.scrapeGame(id, overwrite)

    fun cancel() = scheduler.cancel()

    suspend fun importArtwork(id: GameId, type: ArtworkType, stream: InputStream, extension: String) =
        artwork.importLocal(id, type, stream, extension)

    suspend fun deleteArtwork(id: GameId, type: ArtworkType) = artwork.delete(id, type)

    suspend fun artworkBytes(): Long = artwork.totalBytes()
}
