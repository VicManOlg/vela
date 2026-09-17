package io.vela.core.catalog

import io.vela.core.common.VelaJson
import io.vela.core.model.Platform
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformKind
import kotlinx.serialization.builtins.ListSerializer

/**
 * Built-in platform definitions loaded from `catalog/platforms.json`, optionally merged with
 * user-provided JSON (same schema) so new systems can be added without touching code.
 */
class PlatformCatalog(userJson: String? = null) {

    val platforms: List<Platform>
    private val byId: Map<PlatformId, Platform>
    private val byAlias: Map<String, Platform>

    init {
        val builtIn = CatalogResources.readList("catalog/platforms.json", Platform.serializer())
        val user = userJson?.let { VelaJson.decodeFromString(ListSerializer(Platform.serializer()), it) }.orEmpty()
        // User entries override built-ins with the same id.
        platforms = (builtIn.associateBy { it.id } + user.associateBy { it.id }).values.sortedBy { it.sortOrder }
        byId = platforms.associateBy { it.id }
        byAlias = buildMap {
            platforms.forEach { p ->
                put(p.id.value.lowercase(), p)
                p.folderAliases.forEach { alias -> putIfAbsent(alias.lowercase(), p) }
            }
        }
    }

    operator fun get(id: PlatformId): Platform? = byId[id]

    fun require(id: PlatformId): Platform = byId[id] ?: error("Unknown platform $id")

    /** Only systems that run ROM files through emulators. */
    val emulated: List<Platform> get() = platforms.filter { it.kind == PlatformKind.EMULATED }

    /** Resolves a folder name such as `SNES`, `Super Nintendo` or `psx` to a platform. */
    fun byFolderName(folderName: String): Platform? {
        val key = folderName.trim().lowercase()
        byAlias[key]?.let { return it }
        // Tolerate decorations like "01 - SNES" or "snes (japan)".
        val simplified = key.replace(Regex("""^[\d\s._-]+"""), "").replace(Regex("""\s*[(\[].*$"""), "").trim()
        return byAlias[simplified]
    }

    /** Platforms that accept the given extension, most specific first (fewest extensions). */
    fun byExtension(extension: String): List<Platform> {
        val ext = extension.lowercase().removePrefix(".")
        return emulated.filter { ext in it.extensions }.sortedBy { it.extensions.size }
    }

    /** True when the extension is unambiguous across the catalog (e.g. `sfc`, `gba`, `nds`). */
    fun isUnambiguousExtension(extension: String): Boolean = byExtension(extension).size == 1
}
