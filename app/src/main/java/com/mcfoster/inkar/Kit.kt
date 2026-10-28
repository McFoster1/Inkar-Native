package com.mcfoster.inkar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val InkEl = Color(0xFF1A1E25)
val InkOk = Color(0xFF22C55E)
val InkGlow = Color(0x2E2563EB)

/** Text with the HTML app's 1.5 line height by default. */
@Composable
fun Txt(
    text: String,
    size: Float = 15f,
    color: Color = InkText,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
    lh: Float = 1.5f,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    align: TextAlign? = null,
    decoration: TextDecoration? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = spacing.sp,
        lineHeight = (size * lh).sp,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = align,
        textDecoration = decoration,
    )
}

@Composable
fun VecIcon(@DrawableRes id: Int, tint: Color, size: Dp = 24.dp) {
    Icon(painter = painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    small: Boolean = false,
    danger: Boolean = false,
    bad: Boolean = false,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        danger -> InkBad
        primary -> InkBlue
        bad -> InkBad.copy(alpha = 0.12f)
        else -> InkEl
    }
    val borderC = when {
        danger -> InkBad
        primary -> InkBlue
        bad -> InkBad.copy(alpha = 0.35f)
        else -> InkBorder
    }
    val fg = when {
        danger || primary -> Color.White
        bad -> InkBad
        else -> InkText
    }
    Row(
        modifier
            .heightIn(min = if (small) 32.dp else 40.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderC, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            VecIcon(icon, fg, 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Txt(text, if (small) 13f else 15f, fg, FontWeight.Medium, lh = 1.2f)
    }
}

/** Square 36 dp icon button (the HTML app's "btn sm ib"). */
@Composable
fun IconSq(@DrawableRes icon: Int, onClick: () -> Unit, bad: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(36.dp).clip(shape)
            .background(if (bad) InkBad.copy(alpha = 0.12f) else InkEl)
            .border(1.dp, if (bad) InkBad.copy(alpha = 0.35f) else InkBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VecIcon(icon, (if (bad) InkBad else InkText).copy(alpha = if (enabled) 1f else 0.4f), 18.dp)
    }
}

/** Joins meta parts with " · " and keeps each part on one line ("Oct 8" never splits). */
fun metaLine(vararg parts: String): String = parts.joinToString(" · ") { it.replace(' ', '\u00A0') }

@Composable
fun SquareIconBtn(@DrawableRes id: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(36.dp).clip(shape).background(InkEl).border(1.dp, InkBorder, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { VecIcon(id, InkText, 18.dp) }
}

enum class Glow { TopRight, TopLeft }

fun Modifier.glowBg(g: Glow): Modifier = drawBehind {
    drawRect(InkCard)
    val right = g == Glow.TopRight
    val rx = size.width * (if (right) 0.9f else 1.2f)
    val ry = size.height * (if (right) 0.7f else 1.4f)
    val stop = if (right) 0.65f else 0.6f
    val cx = if (right) size.width else 0f
    withTransform({
        translate(cx, 0f)
        scale(1f, ry / rx, Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(0f to InkGlow, stop to Color.Transparent, center = Offset.Zero, radius = rx),
            radius = rx,
            center = Offset.Zero,
        )
    }
}

@Composable
fun HCard(
    modifier: Modifier = Modifier,
    glow: Glow? = null,
    borderColor: Color = InkBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier.fillMaxWidth().clip(shape)
    Column(
        (if (glow == null) base.background(InkCard) else base.glowBg(glow)).border(1.dp, borderColor, shape).padding(18.dp),
        content = content,
    )
}
