package com.family.kidtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect

/**
 * 可聚焦卡片：黄色焦点环 + 放大，等价于原型的 .focused 样式。
 * Compose 自带 D-pad 空间导航；clickable 响应遥控器 OK 键。
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    background: Color = C.Panel,
    backgroundBrush: Brush? = null,
    focusScale: Float = 1.06f,
    requestInitialFocus: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focus-scale")
    val fr = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            kotlinx.coroutines.delay(60)
            runCatching { fr.requestFocus() }
        }
    }
    Box(
        modifier = modifier
            .scale(scale)
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 4.dp else 0.dp,
                color = if (focused) C.Focus else Color.Transparent,
                shape = shape,
            )
            .let { m ->
                if (backgroundBrush != null) m.background(backgroundBrush, shape)
                else m.background(background, shape)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) { content(focused) }
}

/** 封面：优先真实图片（抽帧 / cover.jpg）；仅无图时用渐变 + emoji 占位。 */
@Composable
fun Art(
    name: String,
    cn: String,
    coverUri: String?,
    modifier: Modifier = Modifier,
    emojiSize: androidx.compose.ui.unit.TextUnit = 40.sp,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
) {
    val (c1, c2) = C.seriesColors(name)
    Box(
        modifier.background(Brush.linearGradient(listOf(c1, c2)), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUri != null) {
            coil.compose.AsyncImage(
                model = coverUri,
                contentDescription = name,
                modifier = Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Text(seriesEmoji(name, cn), fontSize = emojiSize)
        }
    }
}

/** 圆角按钮（原型 .btn）。 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    requestInitialFocus: Boolean = false,
) {
    val bg = when { primary -> C.Focus; danger -> C.Danger; else -> C.Panel }
    val fg = if (primary) Color(0xFF1B1B1B) else C.Text
    Focusable(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        background = bg,
        focusScale = 1.05f,
        requestInitialFocus = requestInitialFocus,
    ) {
        Box(Modifier.padding(horizontal = 18.dp).height(44.dp), contentAlignment = Alignment.Center) {
            Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 星星行：★ 已看次数 / 灰色未满（原型 starsHTML）。 */
@Composable
fun Stars(n: Int, max: Int, fontSize: androidx.compose.ui.unit.TextUnit = 15.sp) {
    val filled = n.coerceAtMost(max)
    Row {
        Text("★".repeat(filled), color = C.Star, fontSize = fontSize, letterSpacing = 1.sp)
        Text("★".repeat(max - filled), color = C.StarOff, fontSize = fontSize, letterSpacing = 1.sp)
    }
}

/** 顶栏信息胶囊（今天 n/limit 集 · 连续 N 天）。 */
@Composable
fun Pill(text: String, highlight: String, done: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(
                if (done) C.Good.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val parts = text.split("{}")
        Text(parts[0], color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(highlight, color = if (done) C.Good else C.Focus, fontSize = 18.sp, fontWeight = FontWeight.Black)
        if (parts.size > 1) Text(parts[1], color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 迷你进度条（原型 .bar-mini）。 */
@Composable
fun MiniBar(pct: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier
            .height(height)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(height / 2)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .height(height)
                .background(C.BarBrush, RoundedCornerShape(height / 2)),
        )
    }
}
