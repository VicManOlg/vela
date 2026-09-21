package io.vela.core.scraper.provider

import java.text.Normalizer

/**
 * Token overlap between a game title and a candidate name from a web source (Wikipedia page,
 * SteamGridDB entry). Diacritics, case, punctuation and filler words are ignored so
 * "Pokemon - HeartGold Version" matches "Pokémon HeartGold and SoulSilver".
 */
object TitleSimilarity {

    private val filler = setOf("the", "a", "an", "of", "and", "version", "edition", "game", "video", "vs", "in", "on", "to", "for")
    private val marks = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    fun tokens(text: String): Set<String> {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()
        return plain.split(nonAlnum).filter { it.isNotBlank() && it !in filler }.toSet()
    }

    /** Share of [title]'s tokens present in [candidate], 0..1; 0 when the candidate is far longer. */
    fun score(title: String, candidate: String): Float {
        val want = tokens(title)
        val have = tokens(candidate)
        if (want.isEmpty() || have.isEmpty()) return 0f
        if (have.size > want.size + 4) return 0f
        return want.count { it in have }.toFloat() / want.size
    }

    /** Best candidate at or above [threshold], or null. Ties go to the shorter name. */
    fun <T> best(title: String, candidates: List<T>, threshold: Float = 0.7f, name: (T) -> String): T? =
        candidates
            .map { it to score(title, name(it)) }
            .filter { it.second >= threshold }
            .sortedWith(compareByDescending<Pair<T, Float>> { it.second }.thenBy { name(it.first).length })
            .firstOrNull()?.first
}
