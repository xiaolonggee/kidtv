package com.family.kidtv.data

/** Adds a parent-approved batch for one date without modifying permanent settings. */
fun Settings.withExtraEpisodesToday(today: String, amount: Int): Settings {
    require(amount in 1..20) { "Extra episode batch must be between 1 and 20" }
    val current = if (extraEpisodesDate == today) extraEpisodes.coerceAtLeast(0) else 0
    require(current <= Int.MAX_VALUE - dailyLimit.coerceAtLeast(0) - amount) { "Daily allowance overflow" }
    return copy(extraEpisodesDate = today, extraEpisodes = current + amount)
}
