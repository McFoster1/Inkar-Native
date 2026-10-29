package com.mcfoster.inkar

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/** Lets the activity save an open chapter when the app goes to the background. */
object EditorHooks {
    var flush: (() -> Unit)? = null
}

// ======================= fonts =======================

val EDITOR_FONTS = listOf("lora" to "Lora", "merri" to "Merriweather", "opens" to "Open Sans", "noticia" to "Noticia Text", "system" to "System")

private fun family(prefix: String) = FontFamily(
    Font(resFor(prefix, "regular"), FontWeight.Normal, FontStyle.Normal),
    Font(resFor(prefix, "bold"), FontWeight.Bold, FontStyle.Normal),
    Font(resFor(prefix, "italic"), FontWeight.Normal, FontStyle.Italic),
    Font(resFor(prefix, "bold_italic"), FontWeight.Bold, FontStyle.Italic),
)

private fun resFor(prefix: String, style: String): Int = when ("${prefix}_$style") {
    "lora_regular" -> R.font.lora_regular
    "lora_bold" -> R.font.lora_bold
    "lora_italic" -> R.font.lora_italic
    "lora_bold_italic" -> R.font.lora_bold_italic
    "merriweather_regular" -> R.font.merriweather_regular
    "merriweather_bold" -> R.font.merriweather_bold
    "merriweather_italic" -> R.font.merriweather_italic
    "merriweather_bold_italic" -> R.font.merriweather_bold_italic
    "opensans_regular" -> R.font.opensans_regular
    "opensans_bold" -> R.font.opensans_bold
    "opensans_italic" -> R.font.opensans_italic
    "opensans_bold_italic" -> R.font.opensans_bold_italic
    "noticia_regular" -> R.font.noticia_regular
    "noticia_bold" -> R.font.noticia_bold
    "noticia_italic" -> R.font.noticia_italic
    else -> R.font.noticia_bold_italic
}

private val LORA by lazy { family("lora") }
private val MERRI by lazy { family("merriweather") }
private val OPENS by lazy { family("opensans") }
private val NOTICIA by lazy { family("noticia") }

fun editorFamily(key: String): FontFamily = when (key) {
    "merri" -> MERRI
    "opens" -> OPENS
    "noticia" -> NOTICIA
    "system" -> FontFamily.Default
    else -> LORA
}

// ======================= HTML <-> text with bold/italic =======================

private const val BOLD = 1
private const val ITALIC = 2

/** Turns chapter HTML (as the HTML app stores it) into plain text plus a bold/italic flag per character. */
fun parseHtml(html: String): Pair<String, ByteArray> {
    val sb = StringBuilder()
    val st = ArrayList<Byte>()
    var bold = 0
    var ital = 0
    fun put(ch: Char) {
        sb.append(ch)
        st.add((if (ch == '\n') 0 else (if (bold > 0) BOLD else 0) or (if (ital > 0) ITALIC else 0)).toByte())
    }
    fun newlineIfNeeded() {
        if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') put('\n')
    }
    var i = 0
    while (i < html.length) {
        val c = html[i]
        if (c == '<') {
            if (html.startsWith("<!--", i)) {
                val e = html.indexOf("-->", i)
                i = if (e < 0) html.length else e + 3
                continue
            }
            val end = html.indexOf('>', i)
            if (end < 0) break
            val raw = html.substring(i + 1, end)
            i = end + 1
            val closing = raw.startsWith("/")
            val name = raw.trimStart('/').takeWhile { it.isLetterOrDigit() }.lowercase()
            when (name) {
                "b", "strong" -> bold = maxOf(0, bold + if (closing) -1 else 1)
                "i", "em" -> ital = maxOf(0, ital + if (closing) -1 else 1)
                "br" -> put('\n')
                "div", "p", "li", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote" -> if (!closing) newlineIfNeeded()
            }
        } else if (c == '&') {
            val semi = html.indexOf(';', i)
            if (semi in (i + 2)..(i + 9)) {
                val ent = html.substring(i + 1, semi)
                val ch: String? = when {
                    ent == "amp" -> "&"
                    ent == "lt" -> "<"
                    ent == "gt" -> ">"
                    ent == "quot" -> "\""
                    ent == "apos" || ent == "#39" -> "'"
                    ent == "nbsp" -> " "
                    ent.startsWith("#x") || ent.startsWith("#X") -> ent.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                    ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                    else -> null
                }
                if (ch != null) {
                    for (x in ch) put(x)
                    i = semi + 1
                    continue
                }
            }
            put('&')
            i++
        } else {
            if (c != '\r') put(c)
            i++
        }
    }
    return sb.toString() to ByteArray(st.size) { st[it] }
}

fun plainText(text: String): Pair<String, ByteArray> {
    val t = text.replace("\r\n", "\n").replace('\r', '\n')
    return t to ByteArray(t.length)
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun lineHtml(text: String, styles: ByteArray, from: Int, to: Int): String {
    if (from >= to) return ""
    val out = StringBuilder()
    var i = from
    while (i < to) {
        val v = styles[i].toInt()
        var j = i + 1
        while (j < to && styles[j].toInt() == v) j++
        val seg = esc(text.substring(i, j))
        val isBold = (v and BOLD) != 0
        val isItal = (v and ITALIC) != 0
        if (isBold) out.append("<b>")
        if (isItal) out.append("<i>")
        out.append(seg)
        if (isItal) out.append("</i>")
        if (isBold) out.append("</b>")
        i = j
    }
    return out.toString()
}

/** Writes the HTML structure the HTML app produces itself: first line bare, later lines as divs. */
fun toHtml(text: String, styles: ByteArray): String {
    val out = StringBuilder()
    var start = 0
    var first = true
    val firstEmptyWithMore = text.startsWith("\n")
    while (true) {
        val nl = text.indexOf('\n', start)
        val end = if (nl < 0) text.length else nl
        val line = lineHtml(text, styles, start, end)
        if (first && !firstEmptyWithMore) {
            out.append(line)
        } else {
            out.append("<div>").append(if (line.isEmpty()) "<br>" else line).append("</div>")
        }
        first = false
        if (nl < 0) break
        start = nl + 1
    }
    return out.toString()
}

private fun styledTransform(styles: ByteArray) = VisualTransformation { text ->
    if (styles.size != text.length) {
        TransformedText(text, OffsetMapping.Identity)
    } else {
        val b = AnnotatedString.Builder(text)
        var i = 0
        while (i < styles.size) {
            val v = styles[i].toInt()
            var j = i + 1
            while (j < styles.size && styles[j].toInt() == v) j++
            if (v != 0) {
                b.addStyle(
                    SpanStyle(
                        fontWeight = if ((v and BOLD) != 0) FontWeight.Bold else null,
                        fontStyle = if ((v and ITALIC) != 0) FontStyle.Italic else null,
                    ),
                    i, j,
                )
            }
            i = j
        }
        TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

// ======================= editor state (text, styles, undo/redo) =======================

private class Snap(val text: String, val styles: ByteArray, val sel: TextRange)

private class RichState(text: String, initialStyles: ByteArray) {
    var value by mutableStateOf(TextFieldValue(text))
    var styles: ByteArray = initialStyles
    var styleVer by mutableIntStateOf(0)
    var dirty by mutableIntStateOf(0)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    var pending: Int? = null
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var lastEdit = 0L

    private fun flags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun push(old: TextFieldValue, force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (force || undoStack.isEmpty() || now - lastEdit > 900) {
            undoStack.addLast(Snap(old.text, styles.copyOf(), old.selection))
            if (undoStack.size > 200) undoStack.removeFirst()
        }
        lastEdit = now
        redoStack.clear()
        flags()
    }

    fun onChange(nv: TextFieldValue) {
        val old = value
        if (nv.text == old.text) {
            if (nv.selection != old.selection) pending = null
            value = nv
            return
        }
        push(old, false)
        val o = old.text
        val n = nv.text
        var p = 0
        val maxP = minOf(o.length, n.length)
        while (p < maxP && o[p] == n[p]) p++
        var s = 0
        val maxS = minOf(o.length, n.length) - p
        while (s < maxS && o[o.length - 1 - s] == n[n.length - 1 - s]) s++
        val removed = o.length - p - s
        val inserted = n.length - p - s
        val base = if (removed > 0 && p < styles.size) styles[p].toInt() else if (p > 0) styles[p - 1].toInt() else 0
        val ins = (pending ?: base).toByte()
        val ns = ByteArray(n.length)
        System.arraycopy(styles, 0, ns, 0, p)
        for (k in 0 until inserted) ns[p + k] = ins
        System.arraycopy(styles, o.length - s, ns, p + inserted, s)
        styles = ns
        pending = null
        value = nv
        styleVer++
        dirty++
    }

    fun toggle(bit: Int) {
        val sel = value.selection
        if (sel.collapsed) {
            val cur = pending ?: (if (sel.start > 0 && sel.start <= styles.size) styles[sel.start - 1].toInt() else 0)
            pending = cur xor bit
            styleVer++
            return
        }
        push(value, true)
        val a = sel.min
        val b = sel.max
        var all = true
        for (k in a until b) {
            if (value.text[k] != '\n' && (styles[k].toInt() and bit) == 0) {
                all = false
                break
            }
        }
        val ns = styles.copyOf()
        for (k in a until b) {
            ns[k] = (if (all) (ns[k].toInt() and bit.inv()) else (ns[k].toInt() or bit)).toByte()
        }
        styles = ns
        styleVer++
        dirty++
    }

    /** Style flags at the cursor (or at the start of the selection). */
    fun activeStyle(): Int {
        pending?.let { return it }
        val sel = value.selection
        return if (sel.collapsed) {
            if (sel.start > 0 && sel.start <= styles.size) styles[sel.start - 1].toInt() else 0
        } else {
            if (sel.min < styles.size) styles[sel.min].toInt() else 0
        }
    }

    private fun restore(s: Snap) {
        styles = s.styles
        value = TextFieldValue(s.text, s.sel)
        pending = null
        styleVer++
        dirty++
    }

    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snap(value.text, styles.copyOf(), value.selection))
        restore(s)
        lastEdit = 0L
        flags()
    }

    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snap(value.text, styles.copyOf(), value.selection))
        restore(s)
        lastEdit = 0L
        flags()
    }
}

// ======================= screen =======================

@Composable
fun EditorScreen(repo: Repo, sid: String, cid: String, viewing: Boolean, onViewing: (Boolean) -> Unit, onBack: () -> Unit) {
    val chapter = remember(sid, cid) { repo.chapterForEdit(sid, cid) }
    if (chapter == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    EditorBody(repo, sid, cid, chapter, viewing, onViewing, onBack)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorBody(
    repo: Repo,
    sid: String,
    cid: String,
    chapter: EditChapter,
    viewing: Boolean,
    onViewing: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val screenH = LocalConfiguration.current.screenHeightDp
    LaunchedEffect(sid, cid) { repo.setLast(sid, cid) }

    val rs = remember(sid, cid) {
        val (t, st) = if (chapter.html.isNotBlank()) parseHtml(chapter.html) else plainText(chapter.content)
        RichState(t, st)
    }
    var title by remember(sid, cid) { mutableStateOf(chapter.title) }
    var font by remember { mutableStateOf(repo.editorFontPeek()) }
    var size by remember { mutableStateOf(repo.editorSizePeek()) }
    var status by remember { mutableStateOf("Saved locally") }
    var saved by remember { mutableStateOf(true) }
    var showFp by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val commit: () -> Unit = {
        if (!saved) {
            repo.saveChapter(sid, cid, rs.value.text, toHtml(rs.value.text, rs.styles), title)
            saved = true
            status = "Saved locally · " + LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
        }
    }
    val commitNow = rememberUpdatedState(commit)

    LaunchedEffect(rs.dirty, title) {
        if (rs.dirty == 0 && title == chapter.title) return@LaunchedEffect
        saved = false
        status = "Saving…"
        delay(700)
        commitNow.value()
    }
    DisposableEffect(Unit) {
        EditorHooks.flush = { commitNow.value() }
        onDispose {
            commitNow.value()
            EditorHooks.flush = null
        }
    }

    val exportPick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        val p = pendingExport
        pendingExport = null
        if (uri != null && p != null) {
            val bytes = ("\uFEFF" + p.second + "\r\n\r\n" + p.third.replace(Regex("\r?\n"), "\r\n")).toByteArray(Charsets.UTF_8)
            thread {
                val ok = try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
                } catch (e: Exception) {
                    false
                }
                onMain { toast(ctx, if (ok) "Saved" else "Could not save the file") }
            }
        }
    }

    val fam = remember(font) { editorFamily(font) }
    val bodyStyle = TextStyle(fontFamily = fam, fontSize = size.sp, lineHeight = (size * 1.75f).sp, color = InkText)
    val vt = remember(rs.styleVer) { styledTransform(rs.styles) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(sid, cid) {
        if (rs.value.text.isBlank() && !viewing) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!viewing) {
                        Row(Modifier.weight(1f, fill = false).clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                            VecIcon(R.drawable.ic_back, InkMuted, 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Txt("Back to story", 14f, InkMuted, lh = 1.3f)
                        }
                    }
                    Txt(status, 13f, if (saved) InkMuted else InkDim, modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp), lh = 1.3f)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Btn(if (viewing) "Unview" else "View", {
                            showFp = false
                            onViewing(!viewing)
                        }, small = true)
                        if (!viewing) {
                            Btn(".txt", {
                                val t = title.trim().ifEmpty { "Untitled" }
                                pendingExport = Triple("", t, rs.value.text)
                                exportPick.launch(safeName(chapter.storyTitle + " - " + t) + ".txt")
                            }, small = true, icon = R.drawable.ic_dl)
                        }
                    }
                }
            }
            item {
                BasicTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it.replace("\n", "") },
                    readOnly = viewing,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = fam, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.26).sp, color = InkText),
                    cursorBrush = SolidColor(InkBlue),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
            stickyHeader {
                Column(Modifier.fillMaxWidth().background(InkBg)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!viewing) {
                            val active = rs.activeStyle()
                            TbText("B", FontWeight.Bold, FontStyle.Normal, (active and BOLD) != 0) { rs.toggle(BOLD) }
                            TbText("I", FontWeight.Normal, FontStyle.Italic, (active and ITALIC) != 0) { rs.toggle(ITALIC) }
                            TbIcon(R.drawable.ic_undo, rs.canUndo) { rs.undo() }
                            TbIcon(R.drawable.ic_redo, rs.canRedo) { rs.redo() }
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            TbText("Aa", FontWeight.SemiBold, FontStyle.Normal, showFp) { showFp = !showFp }
                            if (showFp) {
                                val off = with(LocalDensity.current) { 46.dp.roundToPx() }
                                Popup(
                                    alignment = Alignment.TopEnd,
                                    offset = IntOffset(0, off),
                                    onDismissRequest = { showFp = false },
                                    properties = PopupProperties(focusable = true),
                                ) {
                                    FontPanel(font, size, onFont = {
                                        font = it
                                        repo.setEditorFont(it)
                                    }, onSize = { size = it }, onSizeDone = { repo.setEditorSize(size) }, onReset = {
                                        size = 16
                                        repo.setEditorSize(16)
                                    })
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                }
            }
            item {
                BasicTextField(
                    value = rs.value,
                    onValueChange = { rs.onChange(it) },
                    readOnly = viewing,
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(if (viewing) Color.Transparent else InkBlue),
                    visualTransformation = vt,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(min = (screenH * 0.6f).dp).focusRequester(focus)
                        .padding(top = 24.dp, bottom = (screenH * 0.4f).dp),
                    decorationBox = { inner ->
                        Box {
                            if (rs.value.text.isEmpty()) Text("Start writing…", style = bodyStyle.copy(color = InkDim))
                            inner()
                        }
                    },
                )
            }
        }

        if (viewing) {
            val text = rs.value.text
            val words = remember(text) { wc(text) }
            val chars = remember(text) { text.replace("\n", "").length }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(InkBg)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                ) {
                    Txt("${fmt(words)} words", 13f, InkMuted, lh = 1.3f)
                    Txt("${fmt(chars)} characters", 13f, InkMuted, lh = 1.3f)
                }
            }
        }
    }
}

@Composable
private fun TbText(label: String, weight: FontWeight, style: FontStyle, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) InkEl else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) InkText else InkMuted, fontSize = 16.sp, fontWeight = weight, fontStyle = style)
    }
}

@Composable
private fun TbIcon(icon: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { VecIcon(icon, if (enabled) InkMuted else InkDim.copy(alpha = 0.5f), 20.dp) }
}

@Composable
private fun FontPanel(font: String, size: Int, onFont: (String) -> Unit, onSize: (Int) -> Unit, onSizeDone: () -> Unit, onReset: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(300.dp).shadow(12.dp, shape).clip(shape).background(InkCard).border(1.dp, InkBorder, shape).padding(14.dp),
    ) {
        Txt("FONT", 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
        Spacer(Modifier.height(6.dp))
        for ((key, label) in EDITOR_FONTS) {
            val on = key == font
            Box(
                Modifier.fillMaxWidth().heightIn(min = 38.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) InkBlue else Color.Transparent)
                    .clickable { onFont(key) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(label, color = if (on) Color.White else InkText, fontSize = 16.sp, fontFamily = editorFamily(key))
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("SIZE · $size px", 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
        Slider(
            value = size.toFloat(),
            onValueChange = { onSize(it.toInt()) },
            onValueChangeFinished = onSizeDone,
            valueRange = 12f..32f,
            steps = 19,
            colors = SliderDefaults.colors(thumbColor = InkBlue, activeTrackColor = InkBlue, inactiveTrackColor = InkEl),
        )
        Btn("Reset size", onReset, small = true)
    }
}
