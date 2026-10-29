package com.family.kidtv.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 原型配色 1:1。 */
object C {
    val Bg = Color(0xFF0B1433)
    val Bg2 = Color(0xFF13214F)
    val Panel = Color(0xFF1A2B63)
    val Panel2 = Color(0xFF223570)
    val Text = Color.White
    val Muted = Color(0xFFB9C4E8)
    val Focus = Color(0xFFFFD400)
    val Good = Color(0xFF4ADE80)
    val Warn = Color(0xFFFF8A3D)
    val Star = Color(0xFFFFC531)
    val StarOff = Color(0xFF4A5A8C)
    val Danger = Color(0xFFE2435B)
    val Pink = Color(0xFFFF7AA2)
    val PathText = Color(0xFF9FB0E0)
    val MasteredBg = Color(0xFF1F5B3A)

    val BgBrush = Brush.verticalGradient(listOf(Bg2, Bg))
    val BarBrush = Brush.horizontalGradient(listOf(Focus, Warn))

    /** 系列卡片渐变色板（无 cover.jpg 时按系列名稳定取色）。 */
    val SeriesPalette = listOf(
        Color(0xFFFF8FB1) to Color(0xFFFFD1DC),
        Color(0xFFFF6F91) to Color(0xFFFFC2D1),
        Color(0xFF4FB3FF) to Color(0xFFB8E2FF),
        Color(0xFFFFC93C) to Color(0xFFFFF0B3),
        Color(0xFFB48CFF) to Color(0xFFE3D4FF),
        Color(0xFF2FD3A6) to Color(0xFFB5F5E3),
    )
    fun seriesColors(name: String): Pair<Color, Color> =
        SeriesPalette[(name.hashCode().and(Int.MAX_VALUE)) % SeriesPalette.size]
}

/** 系列 emoji（无封面时的占位图），按名字关键词猜测。 */
fun seriesEmoji(name: String, cn: String): String {
    val s = (name + cn).lowercase()
    return when {
        "mouse" in s || "鼠" in s -> "🐭"
        "peppa" in s || "猪" in s -> "🐷"
        "song" in s || "儿歌" in s || "comptine" in s -> "🎵"
        "choupi" in s || "企鹅" in s -> "🐧"
        "dog" in s || "狗" in s -> "🐶"
        "cat" in s || "猫" in s -> "🐱"
        "bear" in s || "熊" in s -> "🐻"
        else -> "🎬"
    }
}

fun languageEmoji(lang: String): String = when (lang) {
    "英语" -> "🔤"; "法语" -> "🥐"; else -> "🌍"
}

fun languageTag(lang: String): String = when (lang) {
    "英语" -> "EN"; "法语" -> "FR"; else -> lang.take(2).uppercase()
}

fun languageSub(lang: String): String = when (lang) {
    "英语" -> "English"; "法语" -> "Français"; else -> ""
}
