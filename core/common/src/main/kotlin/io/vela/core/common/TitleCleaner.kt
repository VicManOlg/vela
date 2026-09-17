package io.vela.core.common

/**
 * Turns a ROM file name into a human title and a sort key.
 *
 * `Legend of Zelda, The - A Link to the Past (USA) [!].sfc` ->
 *   title = "The Legend of Zelda - A Link to the Past", sortTitle = "legend of zelda - a link to the past"
 */
object TitleCleaner {

    private val parenthesised = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")
    private val discMarker = Regex("""\s*[-_ ]*(disc|disk|cd|side)\s*[0-9a-z]+\b""", RegexOption.IGNORE_CASE)
    private val multiSpace = Regex("""\s{2,}""")
    private val trailingArticle = Regex("""^([^,]+),\s*(the|a|an|la|el|los|las|le|les|der|die|das)(\s*[-:–].*)?$""", RegexOption.IGNORE_CASE)
    private val leadingArticle = Regex("""^(the|a|an)\s+""", RegexOption.IGNORE_CASE)
    private val versionTag = Regex("""\s+v\d+(\.\d+)*$""", RegexOption.IGNORE_CASE)

    fun stem(fileName: String): String {
        val slash = fileName.lastIndexOfAny(charArrayOf('/', '\\'))
        val name = if (slash >= 0) fileName.substring(slash + 1) else fileName
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    fun extension(fileName: String): String {
        val name = stemAndExt(fileName)
        return name.second
    }

    private fun stemAndExt(fileName: String): Pair<String, String> {
        val slash = fileName.lastIndexOfAny(charArrayOf('/', '\\'))
        val name = if (slash >= 0) fileName.substring(slash + 1) else fileName
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot + 1).lowercase() else name to ""
    }

    /** Human readable title. */
    fun clean(fileName: String): String {
        var s = stem(fileName)
        s = s.replace(parenthesised, "")
        s = s.replace(discMarker, "")
        s = s.replace(versionTag, "")
        s = s.replace('_', ' ')
        s = s.replace(multiSpace, " ").trim().trimEnd('-', ',', ' ')
        trailingArticle.matchEntire(s)?.let { m ->
            s = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
        }
        return s.ifBlank { stem(fileName) }
    }

    /** Lower-case key without a leading article, used for ORDER BY and duplicate detection. */
    fun sortKey(title: String): String {
        val t = title.trim().lowercase().replace(leadingArticle, "")
        return t.replace(Regex("""[^\p{L}\p{N} ]"""), "").replace(multiSpace, " ").trim()
    }

    /** Region hints found in tags, e.g. `(Europe)` -> `eu`. */
    fun region(fileName: String): String? {
        val tags = parenthesised.findAll(stem(fileName)).map { it.value.lowercase() }.joinToString(" ")
        return when {
            "europe" in tags || "(e)" in tags || "spain" in tags || "germany" in tags || "france" in tags || "italy" in tags -> "eu"
            "usa" in tags || "(u)" in tags -> "us"
            "japan" in tags || "(j)" in tags -> "jp"
            "world" in tags || "(w)" in tags -> "wor"
            else -> null
        }
    }

    /** Disc number if the file name carries one (Disc 2, CD2...). */
    fun discNumber(fileName: String): Int? =
        Regex("""(?:disc|disk|cd)\s*(\d+)""", RegexOption.IGNORE_CASE).find(stem(fileName))?.groupValues?.get(1)?.toIntOrNull()
}
