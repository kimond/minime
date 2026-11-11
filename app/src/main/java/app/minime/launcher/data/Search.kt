package app.minime.launcher.data

import java.text.Normalizer

object Search {
    private val diacritics = Regex("\\p{Mn}+")
    private val separators = Regex("[\\s\\-_.:]+")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    /**
     * Ranks matches: label prefix, then word prefix ("maps" -> "Google Maps"), then initials
     * ("gm" -> "Google Maps"), then anywhere ("tube" -> "YouTube"). Input order (alphabetical) is kept within a rank.
     */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return apps
        return apps.mapNotNull { app ->
            val label = normalize(app.label)
            val words = label.split(separators).filter { it.isNotEmpty() }
            val rank = when {
                label.startsWith(q) -> 0
                words.any { it.startsWith(q) } -> 1
                q.length > 1 && words.joinToString("") { it.take(1) }.startsWith(q) -> 2
                label.contains(q) -> 3
                else -> return@mapNotNull null
            }
            rank to app
        }.sortedBy { it.first }.map { it.second }
    }
}
