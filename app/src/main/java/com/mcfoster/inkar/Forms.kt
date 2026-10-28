package com.mcfoster.inkar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Top bar shown on every tab: logo, search and theme buttons (both inactive for now). */
@Composable
fun AppTopBar(repo: Repo) {
    val ctx = LocalContext.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Brand(18)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SquareIconBtn(R.drawable.ic_search) { toast(ctx, "Search arrives in a later phase") }
                Btn("◐ Theme", { repo.setTheme(if (Theme.isLight) "dark" else "light") }, small = true)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
    }
}

@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp)
            .shadow(10.dp, CircleShape, ambientColor = InkBlue, spotColor = InkBlue)
            .clip(CircleShape)
            .background(InkBlue)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { VecIcon(R.drawable.ic_plus, Color.White, 24.dp) }
}

/** Segmented control. accent = blue active segment (chart), otherwise card-coloured (filters). */
@Composable
fun HSeg(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    Row(modifier.clip(RoundedCornerShape(8.dp)).background(InkEl).padding(2.dp)) {
        for (o in options) {
            val on = o == selected
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (on) (if (accent) InkBlue else InkCard) else Color.Transparent)
                    .clickable { onSelect(o) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Txt(o, 13f, if (on) (if (accent) Color.White else InkText) else InkMuted, lh = 1.3f) }
        }
    }
}

@Composable
fun HField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    minLines: Int = 1,
    maxLen: Int = 5000,
    error: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    if (label != null) Txt(label, 12f, InkMuted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp), lh = 1.2f)
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= maxLen) onChange(it) },
        textStyle = TextStyle(color = InkText, fontSize = 15.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(InkBlue),
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(shape).background(InkEl)
                    .border(
                        if (focused || error) 2.dp else 1.dp,
                        if (error) InkBad else if (focused) InkBlue else InkBorder,
                        shape,
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Txt(placeholder, 15f, InkDim)
                inner()
            }
        },
    )
}

@Composable
fun HSelect(label: String, value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Txt(label, 12f, InkMuted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp), lh = 1.2f)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(shape).background(InkEl)
                    .border(1.dp, InkBorder, shape)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(value, 15f, InkText, modifier = Modifier.weight(1f))
                VecIcon(R.drawable.ic_down, InkMuted, 18.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(InkEl)) {
                for (o in options) {
                    DropdownMenuItem(text = { Txt(o, 15f, InkText) }, onClick = {
                        onSelect(o)
                        open = false
                    })
                }
            }
        }
    }
}

@Composable
fun HDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 460.dp).fillMaxWidth().heightIn(max = maxH)
                .clip(RoundedCornerShape(14.dp)).background(InkCard)
                .border(1.dp, InkBorder, RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            content = content,
        )
    }
}

@Composable
fun DialogActions(cancel: String = "Cancel", onCancel: () -> Unit, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Btn(cancel, onCancel)
        actions()
    }
}

@Composable
fun ConfirmModal(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    HDialog(onDismiss) {
        Txt(title, 20f, InkText, FontWeight.SemiBold)
        Txt(message, 15f, InkMuted, modifier = Modifier.padding(top = 8.dp))
        DialogActions(onCancel = onDismiss) { Btn(confirm, onConfirm, danger = true) }
    }
}

@Composable
fun NameDialog(heading: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    HDialog(onDismiss) {
        Txt(heading, 20f, InkText, FontWeight.SemiBold)
        HField(t, { t = it }, label = label, maxLen = 120)
        DialogActions(onCancel = onDismiss) { Btn(confirm, { onConfirm(t.trim().ifEmpty { "Untitled" }) }, primary = true) }
    }
}

/** Full-screen cover viewer: tap to close, pinch to zoom, double-tap to reset. */
@Composable
fun CoverViewer(cover: String, onDismiss: () -> Unit) {
    val bmp = remember(cover) { coverBitmap(cover) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF2000000))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                onDismiss()
                            }
                        },
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(24.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                )
            }
        }
    }
}
