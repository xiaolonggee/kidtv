package com.family.kidtv.data

/**
 * Rank only the selected language. Recorded completed plays take precedence;
 * the family's preferred shows break ties (including a new USB with no history).
 * Kotlin's stable sort preserves the scan order for all other equal scores.
 * Neither media keys nor stored play counts are rewritten.
 */
fun seriesByPopularity(
    series: List<Series>, language: String, counts: Map<String, Int>,
): List<Series> = series.filter { it.language == language }.sortedWith(
    compareByDescending<Series> { s ->
        s.episodes.sumOf { (counts[it.key] ?: 0).coerceAtLeast(0).toLong() }
    }.thenBy { familySeriesPriority(it) },
)

private fun familySeriesPriority(series: Series): Int {
    val names = listOf(series.folder, series.displayName, series.cnName)
        .joinToString(" ").lowercase(java.util.Locale.ROOT)
    return when {
        listOf("小鼠啵啵", "小鼠波波", "mouse boo", "maisy").any { it in names } -> 0
        "古力小超人" in names -> 1
        listOf("小驴托托", "trotro").any { it in names } -> 2
        else -> 3
    }
}
