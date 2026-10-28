package com.mcfoster.inkar

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Theme choice: "dark", "light" or "system". Colours below follow it, so every screen restyles at once. */
object Theme {
    var mode by mutableStateOf("dark")
    var sysDark by mutableStateOf(true)
    val isLight: Boolean get() = mode == "light" || (mode == "system" && !sysDark)
}

val InkBlue = Color(0xFF2563EB)
val InkOk = Color(0xFF22C55E)
val InkBad = Color(0xFFEF4444)
val InkBg: Color get() = if (Theme.isLight) Color(0xFFF5F6F8) else Color(0xFF0B0D10)
val InkCard: Color get() = if (Theme.isLight) Color(0xFFFFFFFF) else Color(0xFF13161B)
val InkNav: Color get() = InkCard
val InkEl: Color get() = if (Theme.isLight) Color(0xFFECEEF2) else Color(0xFF1A1E25)
val InkBorder: Color get() = if (Theme.isLight) Color(0x17000000) else Color(0x14FFFFFF)
val InkText: Color get() = if (Theme.isLight) Color(0xF20A0C10) else Color(0xF2FFFFFF)
val InkMuted: Color get() = if (Theme.isLight) Color(0xA60A0C10) else Color(0xA6FFFFFF)
val InkDim: Color get() = if (Theme.isLight) Color(0x6B0A0C10) else Color(0x61FFFFFF)
val InkGlow: Color get() = if (Theme.isLight) Color(0x142563EB) else Color(0x2E2563EB)
val InkPop: Color get() = if (Theme.isLight) Color(0xEBFFFFFF) else Color(0xE61A1E25)

fun inkColorScheme() = if (Theme.isLight) {
    lightColorScheme(primary = InkBlue, background = InkBg, surface = InkBg, onBackground = InkText, onSurface = InkText)
} else {
    darkColorScheme(primary = InkBlue, background = InkBg, surface = InkBg, onBackground = InkText, onSurface = InkText)
}

fun onMain(block: () -> Unit) {
    Handler(Looper.getMainLooper()).post(block)
}

fun toast(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

// ---------- icons (drawn on a 24 x 24 grid) ----------

enum class Ic { Home, Book, Pen, Bars, Gear, Plus, Back, Trash, Search, Up, Down, Swap, Download, Upload }

private fun poly(vararg p: Float, shut: Boolean = false): Path = Path().apply {
    moveTo(p[0], p[1])
    var i = 2
    while (i + 1 < p.size) {
        lineTo(p[i], p[i + 1])
        i += 2
    }
    if (shut) close()
}

@Composable
fun InkIcon(ic: Ic, tint: Color, size: Dp = 24.dp, filled: Boolean = false, cut: Color = InkNav) {
    Canvas(Modifier.size(size)) {
        val sc = this.size.minDimension / 24f
        val line = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body: DrawStyle = if (filled) Fill else line
        val inner = if (filled) cut else tint
        scale(sc, Offset.Zero) {
            when (ic) {
                Ic.Home -> {
                    drawPath(path = poly(12f, 3.5f, 21f, 11f, 18.5f, 11f, 18.5f, 20f, 5.5f, 20f, 5.5f, 11f, 3f, 11f, shut = true), color = tint, style = body)
                    drawPath(path = poly(10f, 20f, 10f, 14.5f, 14f, 14.5f, 14f, 20f), color = inner, style = line)
                }
                Ic.Book -> {
                    drawRoundRect(color = tint, topLeft = Offset(5f, 3f), size = Size(14f, 18f), cornerRadius = CornerRadius(2.5f, 2.5f), style = body)
                    drawPath(path = poly(9.5f, 3f, 9.5f, 21f), color = inner, style = line)
                    drawPath(path = poly(12.5f, 8f, 16f, 8f), color = inner, style = line)
                    drawPath(path = poly(12.5f, 12f, 16f, 12f), color = inner, style = line)
                }
                Ic.Pen -> {
                    drawPath(path = poly(4f, 20f, 5f, 15f, 16f, 4f, 20f, 8f, 9f, 19f, shut = true), color = tint, style = body)
                    drawPath(path = poly(13.5f, 6.5f, 17.5f, 10.5f), color = inner, style = line)
                }
                Ic.Bars -> {
                    drawRoundRect(color = tint, topLeft = Offset(4f, 12f), size = Size(4f, 8f), cornerRadius = CornerRadius(1.2f, 1.2f), style = body)
                    drawRoundRect(color = tint, topLeft = Offset(10f, 5f), size = Size(4f, 15f), cornerRadius = CornerRadius(1.2f, 1.2f), style = body)
                    drawRoundRect(color = tint, topLeft = Offset(16f, 9f), size = Size(4f, 11f), cornerRadius = CornerRadius(1.2f, 1.2f), style = body)
                }
                Ic.Gear -> {
                    drawPath(path = poly(4f, 6f, 20f, 6f), color = tint, style = line)
                    drawPath(path = poly(4f, 12f, 20f, 12f), color = tint, style = line)
                    drawPath(path = poly(4f, 18f, 20f, 18f), color = tint, style = line)
                    for ((cx, cy) in listOf(9f to 6f, 15f to 12f, 8f to 18f)) {
                        drawCircle(color = cut, radius = 2.4f, center = Offset(cx, cy))
                        drawCircle(color = tint, radius = 2.4f, center = Offset(cx, cy), style = body)
                    }
                }
                Ic.Plus -> {
                    drawPath(path = poly(12f, 5f, 12f, 19f), color = tint, style = line)
                    drawPath(path = poly(5f, 12f, 19f, 12f), color = tint, style = line)
                }
                Ic.Back -> drawPath(path = poly(15f, 5f, 8f, 12f, 15f, 19f), color = tint, style = line)
                Ic.Trash -> {
                    drawPath(path = poly(5f, 7f, 19f, 7f), color = tint, style = line)
                    drawPath(path = poly(10f, 7f, 10f, 4f, 14f, 4f, 14f, 7f), color = tint, style = line)
                    drawPath(path = poly(7f, 7f, 8f, 20f, 16f, 20f, 17f, 7f), color = tint, style = line)
                }
                Ic.Search -> {
                    drawCircle(color = tint, radius = 6.5f, center = Offset(10.5f, 10.5f), style = line)
                    drawPath(path = poly(15.5f, 15.5f, 20f, 20f), color = tint, style = line)
                }
                Ic.Up -> drawPath(path = poly(6f, 15f, 12f, 9f, 18f, 15f), color = tint, style = line)
                Ic.Down -> drawPath(path = poly(6f, 9f, 12f, 15f, 18f, 9f), color = tint, style = line)
                Ic.Swap -> {
                    drawPath(path = poly(8f, 20f, 8f, 5f), color = tint, style = line)
                    drawPath(path = poly(5f, 8f, 8f, 5f, 11f, 8f), color = tint, style = line)
                    drawPath(path = poly(16f, 4f, 16f, 19f), color = tint, style = line)
                    drawPath(path = poly(13f, 16f, 16f, 19f, 19f, 16f), color = tint, style = line)
                }
                Ic.Download -> {
                    drawPath(path = poly(12f, 4f, 12f, 15f), color = tint, style = line)
                    drawPath(path = poly(8f, 11f, 12f, 15f, 16f, 11f), color = tint, style = line)
                    drawPath(path = poly(5f, 20f, 19f, 20f), color = tint, style = line)
                }
                Ic.Upload -> {
                    drawPath(path = poly(12f, 15f, 12f, 4f), color = tint, style = line)
                    drawPath(path = poly(8f, 8f, 12f, 4f, 16f, 8f), color = tint, style = line)
                    drawPath(path = poly(5f, 20f, 19f, 20f), color = tint, style = line)
                }
            }
        }
    }
}

// ---------- building blocks ----------

@Composable
fun Brand(sizeSp: Int = 18) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = InkText)) { append("INK") }
            withStyle(SpanStyle(color = InkBlue)) { append("AR") }
        },
        fontSize = sizeSp.sp,
        letterSpacing = 0.02.em,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun IconBtn(ic: Ic, onClick: () -> Unit, tint: Color = InkText, enabled: Boolean = true) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkIcon(ic, if (enabled) tint else InkDim.copy(alpha = 0.4f), 22.dp)
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(Ic.Back, onBack) else Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = InkText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var m = modifier.fillMaxWidth().clip(shape).background(InkCard).border(1.dp, InkBorder, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier, padding = 14.dp) {
        Text(label, color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = InkText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Bar(fraction: Float, color: Color = InkBlue) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(InkBorder)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun Pill(text: String) {
    Text(
        text,
        color = InkMuted,
        fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(InkEl).padding(horizontal = 9.dp, vertical = 1.dp),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) InkBlue else InkCard)
            .border(1.dp, if (selected) InkBlue else InkBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) Color.White else InkMuted, fontSize = 13.sp)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = InkBlue,
            contentColor = Color.White,
            disabledContainerColor = InkBorder,
            disabledContentColor = InkDim,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, InkBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) InkBad else InkText),
    ) { Text(text) }
}

@Composable
fun InkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = minLines == 1,
        minLines = minLines,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = InkText,
            unfocusedTextColor = InkText,
            errorTextColor = InkText,
            focusedBorderColor = InkBlue,
            unfocusedBorderColor = InkBorder,
            errorBorderColor = InkBad,
            focusedLabelColor = InkBlue,
            unfocusedLabelColor = InkMuted,
            errorLabelColor = InkBad,
            cursorColor = InkBlue,
        ),
    )
}

@Composable
fun TitleDialog(heading: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkCard,
        title = { Text(heading, color = InkText) },
        text = { InkField(t, { t = it }, label) },
        confirmButton = { TextButton(onClick = { onConfirm(t.trim()) }) { Text(confirm, color = InkBlue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) } },
    )
}

@Composable
fun ConfirmDialog(heading: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkCard,
        title = { Text(heading, color = InkText) },
        text = { Text(message, color = InkMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) InkBad else InkBlue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) } },
    )
}

// ---------- covers ----------

private val coverCache = LruCache<Int, ImageBitmap>(24)

private fun decodeCover(data: String): ImageBitmap? {
    val key = data.hashCode()
    coverCache.get(key)?.let { return it }
    return try {
        val bytes = Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        if (bmp != null) coverCache.put(key, bmp)
        bmp
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CoverImage(cover: String?, letter: String, colorHex: String, width: Dp, height: Dp) {
    val bmp = remember(cover) { cover?.let { decodeCover(it) } }
    val shape = RoundedCornerShape(8.dp)
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width, height).clip(shape),
        )
    } else {
        Box(
            Modifier.size(width, height).clip(shape).background(InkEl).border(1.dp, InkBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter.trim().take(1).uppercase().ifEmpty { "?" },
                color = InkDim,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun coverBitmap(data: String): ImageBitmap? = decodeCover(data)
