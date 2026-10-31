package com.mcfoster.inkar

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread
import kotlin.math.roundToInt

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

// ======================= android typefaces (families defined in res/font) =======================

private val typefaces = HashMap<String, Typeface>()

private fun androidTypeface(ctx: Context, key: String): Typeface {
    typefaces[key]?.let { return it }
    val tf: Typeface = try {
        when (key) {
            "merri" -> ctx.resources.getFont(R.font.font_merriweather)
            "opens" -> ctx.resources.getFont(R.font.font_opensans)
            "noticia" -> ctx.resources.getFont(R.font.font_noticia)
            "system" -> Typeface.DEFAULT
            else -> ctx.resources.getFont(R.font.font_lora)
        }
    } catch (e: Exception) {
        Typeface.DEFAULT
    }
    typefaces[key] = tf
    return tf
}

// ======================= layout: tight line height + small gap after each paragraph =======================

/** Comfortable but compact line height, as a multiple of the font size. */
private const val LINE_HEIGHT_EM = 1.4f

/** Extra space after every paragraph (after a line break), as a multiple of the font size. */
private const val PARAGRAPH_GAP_EM = 0.45f

/**
 * One span for the whole text. It sets the line height and adds a small gap under the last line of every
 * paragraph. It only changes how lines are drawn: the stored text still has a single line break.
 */
private class WriterLineSpan(var lineHeightPx: Int, var gapPx: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val current = fm.descent - fm.ascent
        val target = maxOf(lineHeightPx, current)
        val diff = target - current
        fm.ascent -= diff / 2
        fm.descent += diff - diff / 2
        if (end > start && end <= text.length && text[end - 1] == '\n') fm.descent += gapPx
        fm.top = fm.ascent
        fm.bottom = fm.descent
    }
}

/** EditText that pastes as plain text and reports selection changes. */
class WriterEditText(ctx: Context) : EditText(ctx) {
    var onSel: ((Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSel?.invoke(selStart, selEnd)
    }

    override fun onTextContextMenuItem(id: Int): Boolean =
        if (id == android.R.id.paste) super.onTextContextMenuItem(android.R.id.pasteAsPlainText) else super.onTextContextMenuItem(id)
}

// ======================= bold / italic as spans =======================

private fun bitsOf(style: Int): Int = when (style) {
    Typeface.BOLD -> BOLD
    Typeface.ITALIC -> ITALIC
    Typeface.BOLD_ITALIC -> BOLD or ITALIC
    else -> 0
}

/** One flag byte (bold = 1, italic = 2) per character, read from the text's style spans. */
private fun flagsOf(e: Spanned): ByteArray {
    val f = ByteArray(e.length)
    for (sp in e.getSpans(0, e.length, StyleSpan::class.java)) {
        val a = e.getSpanStart(sp)
        val b = e.getSpanEnd(sp)
        if (a < 0 || b <= a) continue
        val bits = bitsOf(sp.style)
        for (k in a until b) f[k] = (f[k].toInt() or bits).toByte()
    }
    return f
}

private fun flagAtChar(e: Spanned, idx: Int): Int {
    if (idx < 0 || idx >= e.length) return 0
    var f = 0
    for (sp in e.getSpans(idx, idx + 1, StyleSpan::class.java)) f = f or bitsOf(sp.style)
    return f
}

private fun addRuns(e: Editable, base: Int, fl: IntArray) {
    for (bit in intArrayOf(BOLD, ITALIC)) {
        var i = 0
        while (i < fl.size) {
            if ((fl[i] and bit) == 0) {
                i++
                continue
            }
            var j = i + 1
            while (j < fl.size && (fl[j] and bit) != 0) j++
            e.setSpan(StyleSpan(if (bit == BOLD) Typeface.BOLD else Typeface.ITALIC), base + i, base + j, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            i = j
        }
    }
}

/** Changes the flags of every character in [a, b) and rebuilds the style spans that touch that range. */
private fun modifyFlags(e: Editable, a: Int, b: Int, f: (Int) -> Int) {
    if (b <= a) return
    val spans = e.getSpans(a, b, StyleSpan::class.java)
    var lo = a
    var hi = b
    for (sp in spans) {
        lo = minOf(lo, e.getSpanStart(sp))
        hi = maxOf(hi, e.getSpanEnd(sp))
    }
    val fl = IntArray(hi - lo)
    for (sp in spans) {
        val s = e.getSpanStart(sp)
        val en = e.getSpanEnd(sp)
        val bits = bitsOf(sp.style)
        for (k in s until en) fl[k - lo] = fl[k - lo] or bits
    }
    for (sp in spans) e.removeSpan(sp)
    for (k in a until b) fl[k - lo] = f(fl[k - lo])
    addRuns(e, lo, fl)
}

private fun buildSpannable(text: String, flags: ByteArray): SpannableStringBuilder {
    val sb = SpannableStringBuilder(text)
    val n = minOf(text.length, flags.size)
    addRuns(sb, 0, IntArray(n) { flags[it].toInt() })
    return sb
}

private class Snap(val text: String, val flags: ByteArray, val a: Int, val b: Int)

// ======================= controller: owns the EditText, undo/redo and style state =======================

class WriterController(private val initialText: String, private val initialFlags: ByteArray) {
    var view: WriterEditText? = null
        private set
    var dirty by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var activeFlags by mutableIntStateOf(0)
        private set

    private var pending: Int? = null
    private var suppress = false
    private var changing = false
    private var lastStart = 0
    private var lastCount = 0
    private var lastEdit = 0L
    private var lookKey = ""
    private var viewingApplied: Boolean? = null
    private val lineSpan = WriterLineSpan(1, 0)
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (suppress) return
            changing = true
            val now = SystemClock.uptimeMillis()
            if (undoStack.isEmpty() || now - lastEdit > 900L) pushSnap()
            lastEdit = now
            if (redoStack.isNotEmpty()) redoStack.clear()
            syncUndoState()
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (suppress) return
            lastStart = start
            lastCount = count
        }

        override fun afterTextChanged(e: Editable) {
            if (suppress) return
            val p = pending
            if (p != null && lastCount > 0) {
                val a = lastStart.coerceIn(0, e.length)
                val b = (lastStart + lastCount).coerceIn(a, e.length)
                modifyFlags(e, a, b) { p }
            }
            pending = null
            ensureLineSpan(e)
            changing = false
            dirty++
            refreshActive()
        }
    }

    fun attach(v: WriterEditText) {
        view = v
        v.onSel = { _, _ ->
            if (!changing) pending = null
            refreshActive()
        }
        suppress = true
        v.setText(buildSpannable(initialText, initialFlags), TextView.BufferType.EDITABLE)
        suppress = false
        v.addTextChangedListener(watcher)
        ensureLineSpan(v.text)
    }

    private fun ensureLineSpan(e: Editable) {
        val st = e.getSpanStart(lineSpan)
        val en = e.getSpanEnd(lineSpan)
        if (st != 0 || en != e.length) {
            e.removeSpan(lineSpan)
            e.setSpan(lineSpan, 0, e.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    /** Applies font, size, colours and the line-height span. Cheap when nothing changed. */
    fun applyLook(v: WriterEditText, fontKey: String, sizeSp: Int, textColor: Int, hintColor: Int, accent: Int) {
        val key = "$fontKey|$sizeSp|$textColor|$hintColor|$accent"
        if (key == lookKey) return
        lookKey = key
        val density = v.resources.displayMetrics.density
        v.typeface = androidTypeface(v.context, fontKey)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        v.setTextColor(textColor)
        v.setHintTextColor(hintColor)
        v.highlightColor = (accent and 0x00FFFFFF) or (0x5A shl 24)
        if (Build.VERSION.SDK_INT >= 29) {
            val d = GradientDrawable()
            d.setColor(accent)
            d.setSize(maxOf(2, (2 * density).roundToInt()), -1)
            v.textCursorDrawable = d
        }
        val px = v.textSize
        lineSpan.lineHeightPx = (px * LINE_HEIGHT_EM).roundToInt()
        lineSpan.gapPx = (px * PARAGRAPH_GAP_EM).roundToInt()
        val e = v.text
        e.removeSpan(lineSpan)
        e.setSpan(lineSpan, 0, e.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        v.setPadding(0, (12 * density).roundToInt(), 0, (24 * density).roundToInt())
    }

    /** Edit mode lets you type; view mode only lets you read and scroll. */
    fun setViewing(v: WriterEditText, viewing: Boolean) {
        if (viewingApplied == viewing) return
        viewingApplied = viewing
        if (viewing) {
            v.clearFocus()
            (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
            v.isFocusable = false
            v.isFocusableInTouchMode = false
            v.isCursorVisible = false
        } else {
            v.isFocusable = true
            v.isFocusableInTouchMode = true
            v.isCursorVisible = true
        }
    }

    fun focusAndShowKeyboard() {
        val v = view ?: return
        v.requestFocus()
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    fun text(): String = view?.text?.toString() ?: ""

    fun flagsNow(): ByteArray = view?.text?.let { flagsOf(it) } ?: ByteArray(0)

    fun isBlank(): Boolean = text().isBlank()

    // ----- style toggles -----

    fun toggle(flag: Int) {
        val v = view ?: return
        val e = v.text
        val a = minOf(v.selectionStart, v.selectionEnd)
        val b = maxOf(v.selectionStart, v.selectionEnd)
        if (a < 0) return
        if (a == b) {
            val before = if (a > 0) flagAtChar(e, a - 1) else flagAtChar(e, 0)
            pending = (pending ?: before) xor flag
            refreshActive()
            return
        }
        pushSnap()
        lastEdit = SystemClock.uptimeMillis()
        redoStack.clear()
        syncUndoState()
        val fl = flagsOf(e)
        var all = true
        for (k in a until b) {
            if (e[k] != '\n' && (fl[k].toInt() and flag) == 0) {
                all = false
                break
            }
        }
        modifyFlags(e, a, b) { old -> if (all) old and flag.inv() else old or flag }
        dirty++
        refreshActive()
    }

    private fun refreshActive() {
        val v = view ?: return
        val e = v.text
        val a = minOf(v.selectionStart, v.selectionEnd)
        activeFlags = pending ?: when {
            a < 0 -> 0
            v.selectionStart != v.selectionEnd -> flagAtChar(e, a)
            a > 0 -> flagAtChar(e, a - 1)
            else -> flagAtChar(e, 0)
        }
    }

    // ----- undo / redo -----

    private fun snapshot(): Snap? {
        val v = view ?: return null
        val e = v.text
        return Snap(e.toString(), flagsOf(e), maxOf(0, v.selectionStart), maxOf(0, v.selectionEnd))
    }

    private fun pushSnap() {
        val s = snapshot() ?: return
        undoStack.addLast(s)
        if (undoStack.size > 200) undoStack.removeFirst()
    }

    private fun syncUndoState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun restore(s: Snap) {
        val v = view ?: return
        val e = v.text
        suppress = true
        for (sp in e.getSpans(0, e.length, StyleSpan::class.java)) e.removeSpan(sp)
        e.removeSpan(lineSpan)
        e.replace(0, e.length, buildSpannable(s.text, s.flags))
        ensureLineSpan(e)
        val n = e.length
        v.setSelection(s.a.coerceIn(0, n), s.b.coerceIn(0, n))
        suppress = false
        changing = false
        pending = null
        dirty++
        refreshActive()
    }

    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        snapshot()?.let { redoStack.addLast(it) }
        restore(s)
        lastEdit = 0L
        syncUndoState()
    }

    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        snapshot()?.let { undoStack.addLast(it) }
        restore(s)
        lastEdit = 0L
        syncUndoState()
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
    LaunchedEffect(sid, cid) { repo.setLast(sid, cid) }

    val ctl = remember(sid, cid) {
        val (t, st) = if (chapter.html.isNotBlank()) parseHtml(chapter.html) else plainText(chapter.content)
        WriterController(t, st)
    }
    var title by remember(sid, cid) { mutableStateOf(chapter.title) }
    var font by remember { mutableStateOf(repo.editorFontPeek()) }
    var size by remember { mutableStateOf(repo.editorSizePeek()) }
    var status by remember { mutableStateOf("Saved locally") }
    var saved by remember { mutableStateOf(true) }
    var showFp by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val commit: () -> Unit = {
        if (!saved && ctl.view != null) {
            val text = ctl.text()
            repo.saveChapter(sid, cid, text, toHtml(text, ctl.flagsNow()), title)
            saved = true
            status = "Saved locally · " + LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
        }
    }
    val commitNow = rememberUpdatedState(commit)

    LaunchedEffect(ctl.dirty, title) {
        if (ctl.dirty == 0 && title == chapter.title) return@LaunchedEffect
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
    LaunchedEffect(sid, cid) {
        delay(350)
        if (ctl.view != null && ctl.isBlank() && !viewing) ctl.focusAndShowKeyboard()
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
    val textColor = InkText.toArgb()
    val hintColor = InkDim.toArgb()
    val accent = InkBlue.toArgb()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // header: back, status, view, export
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
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
                        pendingExport = Triple("", t, ctl.text())
                        exportPick.launch(safeName(chapter.storyTitle + " - " + t) + ".txt")
                    }, small = true, icon = R.drawable.ic_dl)
                }
            }
        }

        // title
        BasicTextField(
            value = title,
            onValueChange = { if (it.length <= 120) title = it.replace("\n", "") },
            readOnly = viewing,
            singleLine = true,
            textStyle = TextStyle(fontFamily = fam, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.26).sp, color = InkText),
            cursorBrush = SolidColor(InkBlue),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )

        // toolbar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!viewing) {
                TbText("B", FontWeight.Bold, FontStyle.Normal, (ctl.activeFlags and BOLD) != 0) { ctl.toggle(BOLD) }
                TbText("I", FontWeight.Normal, FontStyle.Italic, (ctl.activeFlags and ITALIC) != 0) { ctl.toggle(ITALIC) }
                TbIcon(R.drawable.ic_undo, ctl.canUndo) { ctl.undo() }
                TbIcon(R.drawable.ic_redo, ctl.canRedo) { ctl.redo() }
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

        // editor: takes all remaining space and scrolls on its own
        AndroidView(
            factory = { c ->
                WriterEditText(c).also { v ->
                    v.background = null
                    v.gravity = Gravity.TOP or Gravity.START
                    v.hint = "Start writing…"
                    v.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                    v.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                    v.setIncludeFontPadding(false)
                    v.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    v.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                    v.isVerticalScrollBarEnabled = true
                    v.overScrollMode = View.OVER_SCROLL_NEVER
                    ctl.attach(v)
                }
            },
            update = { v ->
                ctl.applyLook(v, font, size, textColor, hintColor, accent)
                ctl.setViewing(v, viewing)
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        // footer: counts, only in view mode
        if (viewing) {
            val counts = remember(viewing) {
                val t = ctl.text()
                wc(t) to t.replace("\n", "").length
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            ) {
                Txt("${fmt(counts.first)} words", 13f, InkMuted, lh = 1.3f)
                Txt("${fmt(counts.second)} characters", 13f, InkMuted, lh = 1.3f)
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt("FONT", 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
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
        Spacer(Modifier.height(6.dp))
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
