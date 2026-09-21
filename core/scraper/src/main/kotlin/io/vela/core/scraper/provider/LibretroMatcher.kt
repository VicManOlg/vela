package io.vela.core.scraper.provider

import io.vela.core.common.TitleCleaner

/**
 * The file names of one libretro-thumbnails system folder, indexed by [LibretroMatcher.key] so a
 * game can be matched without its file being named exactly like the No-Intro/Redump entry.
 */
class LibretroNames(names: Collection<String>) {
    val all: Set<String> = names.toHashSet()
    val byKey: Map<String, List<String>> = names.groupBy(LibretroMatcher::key)
    val size: Int get() = all.size
}

/**
 * Chooses libretro-thumbnails names for a game whose file carries extra tags, lacks a region, or
 * differs in punctuation/articles from the database entry. Names compare on a normalised key
 * (tags in brackets, articles, punctuation and case ignored); ties are broken by the game's own
 * region tag, then the user's preferred regions, then by penalising demos/betas/prototypes.
 */
object LibretroMatcher {

    private val tagRegex = Regex("""[(\[][^)\]]*[)\]]""")

    private val regionWords: Map<String, List<String>> = mapOf(
        "us" to listOf("usa"),
        "eu" to listOf("europe", "spain", "germany", "france", "italy", "united kingdom", "netherlands", "sweden", "australia"),
        "wor" to listOf("world"),
        "jp" to listOf("japan"),
        "es" to listOf("spain"),
        "fr" to listOf("france"),
        "de" to listOf("germany"),
        "it" to listOf("italy"),
        "asia" to listOf("asia", "korea", "china", "taiwan"),
    )

    private val regionTags: Map<String, String> = mapOf(
        "us" to "USA", "eu" to "Europe", "wor" to "World", "jp" to "Japan",
        "es" to "Spain", "fr" to "France", "de" to "Germany", "it" to "Italy",
    )

    private val lowPriority = listOf("demo", "beta", "proto", "sample", "kiosk", "unl", "pirate", "aftermarket", "promo", "virtual console")

    /** `Legend of Zelda, The - A Link to the Past (USA) (Rev 1)` -> `legend of zelda a link to the past`. */
    fun key(name: String): String = TitleCleaner.sortKey(TitleCleaner.clean("$name.png"))

    /** libretro tag for a Vela region code, e.g. `us` -> `USA`. */
    fun regionTag(region: String): String? = regionTags[region]

    /**
     * Candidate names, best first, for a game called [title] stored as [fileName].
     * An exact (sanitised) file-name match always wins; [fileName]'s own region tag, if any,
     * takes precedence over [preferredRegions].
     */
    fun rank(title: String, fileName: String, names: LibretroNames, preferredRegions: List<String>, limit: Int = 5): List<String> {
        val stem = LibretroThumbnailsProvider.sanitize(TitleCleaner.stem(fileName))
        val regions = (listOfNotNull(TitleCleaner.region(fileName)) + preferredRegions).distinct()
        val candidates = LinkedHashSet<String>()
        if (stem in names.all) candidates += stem
        candidates += names.byKey[key(title)].orEmpty()
        return candidates.sortedWith(
            compareBy<String>(
                { if (it == stem) 0 else 1 },
                { if (lowPriority.any { w -> w in tags(it) }) 1 else 0 },
                { regionRank(it, regions) },
                { it.length },
                { it },
            ),
        ).take(limit)
    }

    internal fun tags(name: String): String = tagRegex.findAll(name).joinToString(" ") { it.value.lowercase() }

    private fun regionRank(name: String, regions: List<String>): Int {
        val t = tags(name)
        regions.forEachIndexed { i, r -> if (regionWords[r].orEmpty().any { it in t }) return i }
        return regions.size
    }
}
