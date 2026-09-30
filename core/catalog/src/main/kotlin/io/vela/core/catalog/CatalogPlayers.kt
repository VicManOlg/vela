package io.vela.core.catalog

import io.vela.core.model.CatalogEmulator
import io.vela.core.model.ExtraType
import io.vela.core.model.IntentExtra
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import io.vela.core.model.RomDelivery

/**
 * Turns catalogue recipes (`emulators.json`, from Daijishō) into [PlayerDefinition]s so the
 * launcher can use an emulator Vela has no hand-written recipe for.
 *
 * Rules:
 * - `players.json` wins: any package it already names is skipped here, so a hand-checked recipe
 *   is never shadowed by a generated one.
 * - Only recipes that can be expressed with what Vela knows about a game are translated:
 *   `{file.uri}` → `{rom.uri}`, `{file.path}` → `{rom.path}`. Recipes needing Daijishō tags
 *   (`{tags.steamappid}`, `{tags.ps3folder}`…) or `{file.mime}` return null and are left out.
 * - Nothing is invented: package, activity, action, extras and flags are copied verbatim from
 *   the recipe; the Daijishō id stays in `notes`.
 */
class CatalogPlayers(
    private val emulators: EmulatorCatalog,
    private val platforms: PlatformCatalog,
    private val players: PlayerCatalog,
) {
    private val cache = HashMap<PlatformId, List<PlayerDefinition>>()

    /** Translated recipes for a Vela platform, in catalogue order; installed or not. */
    @Synchronized
    fun forPlatform(platformId: PlatformId): List<PlayerDefinition> = cache.getOrPut(platformId) {
        val platform = platforms[platformId] ?: return@getOrPut emptyList()
        val catalogIds = platform.catalogIds.ifEmpty { listOf(platformId.value) }
        val known = players.allPackages
        val out = ArrayList<PlayerDefinition>()
        for (catalogId in catalogIds) {
            for (recipe in emulators.platform(catalogId)?.emulators.orEmpty()) {
                val pkg = recipe.packageName ?: continue
                if (pkg in known) continue
                translate(recipe, platformId, catalogId)?.let(out::add)
            }
        }
        out.distinctBy { it.id }
    }

    /** Lookup by the ids this class hands out (`catalog.<daijishou id>`); null for anything else. */
    operator fun get(id: PlayerId): PlayerDefinition? {
        if (!id.value.startsWith(PREFIX)) return null
        return platforms.platforms.asSequence().flatMap { forPlatform(it.id).asSequence() }.firstOrNull { it.id == id }
    }

    companion object {
        const val PREFIX = "catalog."

        private val FILE_PLACEHOLDER = Regex("\\{file\\.[a-zA-Z]+\\}")

        private fun String.ported(): String = replace("{file.uri}", "{rom.uri}").replace("{file.path}", "{rom.path}")

        /** True when nothing Daijishō-specific is left after porting. */
        private fun String.translatable(): Boolean = !contains("{tags.") && !FILE_PLACEHOLDER.containsMatchIn(this)

        private fun extraType(type: String): ExtraType? = when (type) {
            "string" -> ExtraType.STRING
            "boolean" -> ExtraType.BOOLEAN
            "int" -> ExtraType.INT
            "long" -> ExtraType.LONG
            "float" -> ExtraType.FLOAT
            "uri" -> ExtraType.URI
            "string[]" -> ExtraType.STRING_ARRAY
            else -> null
        }

        /**
         * One recipe to one player, or null when it cannot be expressed without Daijishō-only
         * data. Pure function; tested in core/catalog.
         */
        fun translate(recipe: CatalogEmulator, platformId: PlatformId, catalogPlatformId: String = platformId.value): PlayerDefinition? {
            val pkg = recipe.packageName ?: return null
            val activity = recipe.activity ?: return null

            val extras = ArrayList<IntentExtra>()
            for (x in recipe.extras) {
                val type = extraType(x.type) ?: return null
                val value = (x.value ?: return null).ported()
                if (!value.translatable()) return null
                extras += IntentExtra(x.key, value, type)
            }

            val data = recipe.data?.ported()
            val delivery = when {
                data == null -> if (extras.isNotEmpty()) RomDelivery.EXTRAS_ONLY else RomDelivery.NONE
                data == "{rom.uri}" -> RomDelivery.CONTENT_URI_DATA
                data == "{rom.path}" -> RomDelivery.PATH_DATA
                else -> return null // a tag or something else Vela cannot fill in
            }
            val requiresPath = data == "{rom.path}" || extras.any { "{rom.path}" in it.value }

            return PlayerDefinition(
                id = PlayerId(PREFIX + recipe.id),
                name = recipe.name.removePrefix("$catalogPlatformId - ").ifBlank { recipe.name },
                packages = listOf(pkg),
                activity = activity,
                action = recipe.action,
                categories = recipe.categories,
                delivery = delivery,
                mimeType = recipe.mimeType,
                extras = extras,
                flags = (recipe.flags + "FLAG_ACTIVITY_NEW_TASK").distinct(),
                platforms = listOf(platformId),
                requiresFilePath = requiresPath,
                notes = listOfNotNull("Daijishō recipe ${recipe.id}", recipe.notes).joinToString(" · "),
            )
        }
    }
}
