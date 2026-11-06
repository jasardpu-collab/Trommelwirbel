package de.jasminfreiner.trommelwirbel

/**
 * Sucht im erkannten Text vom Waschmaschinen-Display nach der Restzeit.
 * Beispiele: "1:24", "1.24", "1h24", "0:45", "45 min", "45'", "2h".
 * Rückgabe: Minuten oder null.
 */
object TimeParser {

    private val hm = Regex("""(?<!\d)(\d{1,2})\s*[:.hH]\s*(\d{2})(?!\d)""")
    private val minutes = Regex("""(?<!\d)(\d{1,3})\s*(min|MIN|Min|m|M|'|´)""")
    private val hoursOnly = Regex("""(?<!\d)(\d)\s*(h|H|std|Std)(?![a-zA-Z0-9])""")
    private val bare = Regex("""(?<![\d:.])(\d{1,3})(?![\d:.°]|\s*°)""")

    fun parse(raw: String): Int? {
        // Häufige OCR-Verwechslungen auf 7-Segment-Displays ausbügeln
        val text = raw
            .replace('O', '0').replace('o', '0')
            .replace('l', '1').replace('I', '1')
            .replace('S', '5').replace('B', '8')
            .replace('D', '0')

        val candidates = mutableListOf<Int>()

        hm.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (b < 60) {
                // Waschmaschinen zeigen fast immer Stunden:Minuten (max. ~5 Stunden).
                val total = if (a <= 9) a * 60 + b else a // "45:00" -> 45 Minuten
                if (total in 1..600) candidates += total
            }
        }
        if (candidates.isNotEmpty()) return candidates.max()

        // Bei "min"-Suche das Original verwenden, sonst wird aus "min" nichts Gutes
        minutes.findAll(raw).forEach { m ->
            val v = m.groupValues[1].toIntOrNull()
            if (v != null && v in 1..600) candidates += v
        }
        if (candidates.isNotEmpty()) return candidates.max()

        hoursOnly.findAll(raw).forEach { m ->
            val v = m.groupValues[1].toInt()
            if (v in 1..9) candidates += v * 60
        }
        if (candidates.isNotEmpty()) return candidates.max()

        // Letzter Versuch: eine einzelne Zahl zwischen 5 und 300 (z. B. "45")
        bare.findAll(text).forEach { m ->
            val v = m.groupValues[1].toInt()
            if (v in 5..300) candidates += v
        }
        return candidates.maxOrNull()
    }
}
