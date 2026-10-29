package com.family.kidtv.ui

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.GatePurpose
import com.family.kidtv.Screen

/** 今日上限已到：温柔的“明天再见” + 家长入口 / 再看一集（经家长门，当日一次性加额）。 */
@Composable
fun DoneScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val daily by vm.dailyStats.collectAsState()
    val today by vm.todayDate.collectAsState()
    val eps = daily[today.toString()]?.eps ?: 0
    val limit = vm.effectiveDailyLimit()
    val bonus = vm.bonusForToday()

    val float by rememberInfiniteTransition(label = "moon").animateFloat(
        0f, -14f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "moon-y",
    )
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🌙", fontSize = 90.sp, modifier = Modifier.offset(y = float.dp))
        Spacer(Modifier.height(10.dp))
        Text("今天的动画看完啦，明天再见！", color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("Bye-bye! See you tomorrow · À demain ! 👋", color = C.Muted, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "今天看了 $eps/$limit 集" + (if (bonus > 0) "（含家长加看 $bonus）" else "") +
                " · 🔥 连续 ${vm.streak()} 天",
            color = C.Muted, fontSize = 15.sp,
        )
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                "📅 看看贴纸", primary = true, requestInitialFocus = true,
                onClick = { vm.push(Screen.Calendar) },
            )
            TvButton("🏠 回到首页", onClick = { vm.goHome() })
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton("🔒 家长设置", onClick = { vm.openGate(GatePurpose.ParentSettings) })
            TvButton("➕ 再看一集", onClick = {
                Toast.makeText(context, "需要家长确认后才能再看一集", Toast.LENGTH_SHORT).show()
                vm.openGate(GatePurpose.OneMoreEpisode)
            })
        }
        Spacer(Modifier.height(8.dp))
        Text("「再看一集」只加今天一次，不会永久改每日上限", color = C.Muted, fontSize = 12.sp)
    }
}
