package io.vela.core.scraper

import io.vela.core.common.Outcome
import io.vela.core.model.MetadataMatch
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.MetadataQuery
import io.vela.core.model.ScrapingSettings

/**
 * A metadata/artwork source. Implementations are stateless adapters over one remote API (or a
 * local database); credentials arrive with each call from [ScrapingSettings] so the user can
 * switch providers at any time.
 */
interface MetadataProvider {
    val info: MetadataProviderInfo

    /** True when the provider can be used with the current settings (credentials present, etc). */
    fun isAvailable(settings: ScrapingSettings): Boolean

    /** Returns candidate matches ordered by score; an empty list is a valid "not found". */
    suspend fun search(query: MetadataQuery, settings: ScrapingSettings): Outcome<List<MetadataMatch>>
}

/** All registered providers; new ones are added through Hilt multibinding (`@IntoSet`). */
class ProviderRegistry(providers: Set<@JvmSuppressWildcards MetadataProvider>) {
    val all: List<MetadataProvider> = providers.sortedBy { it.info.name }

    operator fun get(id: String): MetadataProvider? = all.firstOrNull { it.info.id == id }
}
