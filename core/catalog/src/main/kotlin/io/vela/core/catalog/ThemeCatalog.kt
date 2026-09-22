package io.vela.core.catalog

import io.vela.core.common.VelaJson
import io.vela.core.model.ThemeSpec

/** Bundled themes (`catalog/themes/<name>.json`) plus user themes decoded from JSON strings. */
class ThemeCatalog(userThemes: List<String> = emptyList()) {

    val themes: List<ThemeSpec>

    init {
        val builtIn = BUILT_IN.map { CatalogResources.read("catalog/themes/$it.json", ThemeSpec.serializer()) }
        val user = userThemes.mapNotNull { runCatching { VelaJson.decodeFromString(ThemeSpec.serializer(), it) }.getOrNull() }
        themes = (builtIn.associateBy { it.id } + user.associateBy { it.id }).values.toList()
    }

    fun byId(id: String): ThemeSpec = themes.firstOrNull { it.id == id } ?: themes.first()

    val default: ThemeSpec get() = byId(DEFAULT_ID)

    companion object {
        const val DEFAULT_ID = "vela-night"
        private val BUILT_IN = listOf("vela-night", "vela-day", "vela-ember", "vela-mono", "vela-joy", "vela-azure", "vela-emerald")
    }
}
