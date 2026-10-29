package com.family.kidtv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.Screen
import kotlin.random.Random

/** 家长门：答加法题，或长按 OK 3 秒。默认焦点在「返回」上，防止宝宝误入。 */
@Composable
fun GateScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var seed by remember { mutableStateOf(0) }
    val (a, b, options) = remember(seed) {
        val a = 2 + Random.nextInt(7)
        val b = 2 + Random.nextInt(7)
        val ans = a + b
        val opts = mutableSetOf(ans)
        while (opts.size < 4) opts += (ans + Random.nextInt(-4, 5)).coerceAtLeast(2)
        Triple(a, b, opts.shuffled())
    }
    val answer = a + b

    var holding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) { holdProgress = 0f; return@LaunchedEffect }
        val start = System.currentTimeMillis()
        while (holding) {
            holdProgress = ((System.currentTimeMillis() - start) / 3000f).coerceAtMost(1f)
            if (holdProgress >= 1f) { vm.onGateUnlocked(); return@LaunchedEffect }
            kotlinx.coroutines.delay(50)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            TvButton("⬅ 返回", onClick = { vm.pop() }, requestInitialFocus = true)
        }
        Spacer(Modifier.height(8.dp))
        Text("🔒", fontSize = 44.sp)
        val purpose = vm.gatePurpose
        Text(
            if (purpose == com.family.kidtv.GatePurpose.OneMoreEpisode)
                "再看一集需要家长确认" else "家长专区 · Parents only",
            color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Black,
        )
        Text("请回答问题，或长按 OK 键 3 秒", color = C.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Text("$a + $b = ?", color = C.Text, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            options.forEach { v ->
                Focusable(
                    onClick = {
                        if (v == answer) vm.onGateUnlocked()
                        else { Toast.makeText(context, "答错啦，再试一次", Toast.LENGTH_SHORT).show(); seed++ }
                    },
                    modifier = Modifier.size(110.dp, 84.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("$v", color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Focusable(
            onClick = { /* 长按触发，单击无效 */ },
            modifier = Modifier
                .width(340.dp).height(54.dp)
                .onKeyEvent { e ->
                    val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!isOk) return@onKeyEvent false
                    when (e.type) {
                        KeyEventType.KeyDown -> { holding = true; true }
                        KeyEventType.KeyUp -> { holding = false; true }
                        else -> false
                    }
                },
            shape = RoundedCornerShape(27.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth(holdProgress).fillMaxHeight()
                        .background(C.Good.copy(alpha = 0.4f), RoundedCornerShape(27.dp)),
                )
                Text(
                    "✋ 长按 OK 3 秒进入", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
