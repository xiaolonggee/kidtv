package com.family.kidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import java.time.LocalDate
import java.time.YearMonth

private val STICKERS = listOf("🌟", "🐰", "🍓", "🎈", "🦄", "🌈", "🐳", "🌻")
private val WEEK_CN = listOf("一", "二", "三", "四", "五", "六", "日")
private val DayOn = Brush.linearGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFB347)))

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val daily by vm.dailyStats.collectAsState()
    val today by vm.todayDate.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today.toString()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton("⬅ 返回", onClick = { vm.pop() })
            Spacer(Modifier.width(14.dp))
            Text("📅 打卡日历", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text("${ym.year}年${ym.monthValue}月 · Check-in", color = C.Muted, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            TvButton("◀ 上个月", onClick = { ym = ym.minusMonths(1) })
            Spacer(Modifier.width(10.dp))
            TvButton("下个月 ▶", onClick = { ym = ym.plusMonths(1) })
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxSize()) {
            // ------------ 日历 ------------
            Column(Modifier.weight(1.8f)) {
                Row(Modifier.fillMaxWidth()) {
                    WEEK_CN.forEach {
                        Text(
                            it, color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val lead = (ym.atDay(1).dayOfWeek.value + 6) % 7
                val days = ym.lengthOfMonth()
                val cells = lead + days
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c
                            val d = idx - lead + 1
                            if (d < 1 || d > days) {
                                Spacer(Modifier.weight(1f).aspectRatio(1.35f))
                            } else {
                                val ds = ym.atDay(d).toString()
                                val h = daily[ds]
                                val on = (h?.eps ?: 0) > 0
                                val isToday = ds == today.toString()
                                val future = ym.atDay(d).isAfter(today)
                                Focusable(
                                    onClick = { selected = ds },
                                    modifier = Modifier.weight(1f).aspectRatio(1.35f)
                                        .androidxFocusSelect { selected = ds },
                                    shape = RoundedCornerShape(14.dp),
                                    background = if (on) Color.Transparent else Color.White.copy(alpha = if (future) 0.03f else 0.07f),
                                    backgroundBrush = if (on) DayOn else null,
                                    focusScale = 1.08f,
                                    requestInitialFocus = isToday && ym == YearMonth.from(today),
                                ) {
                                    Box(
                                        Modifier.fillMaxSize().let {
                                            if (isToday) it.border(2.5.dp, Color.White, RoundedCornerShape(14.dp)) else it
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "$d", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                                color = if (on) Color(0xFF4A2B00) else if (future) C.Muted.copy(alpha = .45f) else C.Text,
                                            )
                                            if (on) Text(STICKERS[d % STICKERS.size], fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.width(18.dp))

            // ------------ 侧栏统计 ------------
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val monthStats = daily.filterKeys { it.startsWith("%04d-%02d".format(ym.year, ym.monthValue)) }
                StatCard("🔥 连续打卡 Streak", "${vm.streak()}", "天")
                StatCard(
                    "⏱ 本周观看（周一起）", "${vm.weekSeconds() / 60}",
                    "分钟 · 近7天 ${vm.last7Seconds() / 60} 分钟",
                )
                StatCard("🗓 本月打卡", "${monthStats.count { it.value.eps > 0 }}", "天 · ${monthStats.values.sumOf { it.eps }} 集")
                val sel = LocalDate.parse(selected)
                val h = daily[selected]
                Column(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(
                        "${sel.monthValue}月${sel.dayOfMonth}日 星期${WEEK_CN[sel.dayOfWeek.value - 1]}" +
                            if (selected == today.toString()) "（今天）" else "",
                        color = C.Muted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (h != null && h.eps > 0)
                            "${STICKERS[sel.dayOfMonth % STICKERS.size]} 看了 ${h.eps} 集 · ${(h.sec + 30) / 60} 分钟"
                        else "还没有贴纸",
                        color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

/** 焦点落上即选中该天（原型 data-day 行为）。 */
private fun Modifier.androidxFocusSelect(onFocus: () -> Unit): Modifier =
    this.then(Modifier.onFocusEvent { if (it.isFocused) onFocus() })

@Composable
private fun StatCard(k: String, v: String, unit: String) {
    Column(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(k, color = C.Muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(6.dp))
            Text(unit, color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}
