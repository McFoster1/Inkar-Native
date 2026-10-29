package com.mcfoster.inkar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

val GENRES = listOf("Fantasy", "Sci-Fi", "Romance", "Mystery", "Thriller", "Horror", "Literary", "Historical", "Other")
val STATUSES = listOf("Draft", "Ongoing", "Completed", "On Hold")
val PALETTE = listOf("#22D3EE", "#A78BFA", "#F472B6", "#FBBF24", "#34D399", "#FB7185", "#F97316", "#818CF8")
val LEVELS = listOf(
    "Aspiring Writer" to 0, "Novice Scribe" to 5000, "Budding Author" to 15000,
    "Apprentice Wordsmith" to 30000, "Emerging Author" to 60000, "Dedicated Scribe" to 100000,
    "Story Weaver" to 150000, "Skilled Wordsmith" to 220000, "Accomplished Author" to 300000,
    "Master Storyteller" to 400000, "Expert Scribe" to 500000, "Grand Wordsmith" to 650000,
    "Story Architect" to 800000, "Veteran Author" to 950000, "Master Wordsmith" to 1100000,
)

/** Read-only snapshots used by the UI (data classes, so Compose can compare them). */
data class ChapterV(val id: String, val title: String, val words: Int, val updatedAt: Long)

data class NoteV(val id: String, val title: String, val cat: String, val text: String, val updatedAt: Long)

val NCATS = listOf("Character", "Place", "Plot", "Idea", "Other")

data class StoryV(
    val id: String,
    val title: String,
    val description: String,
    val genre: String,
    val status: String,
    val color: String,
    val cover: String?,
    val updatedAt: Long,
    val chapters: List<ChapterV>,
    val notes: List<NoteV>,
) {
    val words: Int get() = chapters.sumOf { it.words }
}

/** Same word rule as the HTML app: runs of non-space characters. */
private val WORD = Regex("[^\\s\\u00A0\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

fun wc(t: String): Int = WORD.findAll(t).count()

class EditChapter(val title: String, val html: String, val content: String, val storyTitle: String)

data class ChartDay(val date: LocalDate, val words: Map<String, Int>)

class LevelInfo(
    val index: Int,
    val title: String,
    val words: Int,
    val left: Int,
    val progress: Float,
    val hasNext: Boolean,
)

fun fmt(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000.0
    return when {
        m < 1 -> "Just now"
        m < 60 -> "${m.toInt()} min ago"
        m < 1440 -> "${(m / 60).toInt()} h ago"
        else -> Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun newId(): String {
    val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
    val r = (1..8).map { chars.random() }.joinToString("")
    return r + java.lang.Long.toString(System.currentTimeMillis(), 36).takeLast(4)
}

private fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

private fun JSONObject.objs(key: String): List<JSONObject> {
    val a = optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

/**
 * Holds the whole app state as the same JSON the HTML app uses, so backups
 * move between both apps untouched. Unknown keys are kept as they are.
 */
class Repo private constructor(context: Context) {
    var levelUp by mutableStateOf<LevelInfo?>(null)
        private set

    fun dismissLevelUp() {
        levelUp = null
    }

    private val file = File(context.filesDir, "inkar-state.json")
    private val io = Executors.newSingleThreadExecutor()
    private var root: JSONObject = JSONObject()

    private var rev by mutableIntStateOf(0)

    init {
        root = load()
        fix()
        persist()
    }

    companion object {
        @Volatile
        private var inst: Repo? = null

        fun get(context: Context): Repo =
            inst ?: synchronized(this) { inst ?: Repo(context.applicationContext).also { inst = it } }
    }

    @Suppress("UNUSED_EXPRESSION")
    private fun track() {
        rev
    }

    // ---------- storage ----------

    private fun defaultState(): JSONObject = JSONObject().apply {
        put("profile", JSONObject().put("name", "Writer"))
        put("goal", 2000)
        put("theme", "dark")
        put("lastExport", JSONObject.NULL)
        put("bkEvery", 14)
        put("bkSnooze", 0)
        put("last", JSONObject.NULL)
        put("stories", JSONArray())
        put("activity", JSONObject())
        put("log", JSONObject())
        put("gone", JSONObject())
        put("ed", JSONObject().put("font", "lora").put("size", 16))
    }

    private fun load(): JSONObject {
        if (!file.exists()) return defaultState()
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            try {
                file.copyTo(File(file.parentFile, "inkar-state.broken.json"), overwrite = true)
            } catch (_: Exception) {
            }
            defaultState()
        }
    }

    private fun persist() {
        val text = root.toString()
        io.execute {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }

    private fun commit() {
        rev++
        persist()
    }

    private fun fix() {
        if (root.optJSONObject("profile") == null) root.put("profile", JSONObject().put("name", "Writer"))
        for (k in listOf("activity", "log")) if (root.optJSONObject(k) == null) root.put(k, JSONObject())
        if (root.optJSONArray("stories") == null) root.put("stories", JSONArray())
        root.put("gone", JSONObject())
        val ed = root.optJSONObject("ed") ?: JSONObject().also { root.put("ed", it) }
        if (ed.isNull("font")) ed.put("font", "lora")
        if (ed.isNull("size")) ed.put("size", 16)
        if (root.isNull("goal")) root.put("goal", 2000)
        if (root.isNull("theme")) root.put("theme", "dark")
        if (root.isNull("bkEvery")) root.put("bkEvery", 14)
        if (root.isNull("bkSnooze")) root.put("bkSnooze", 0)

        val stories = root.objs("stories")
        val used = stories.map { it.optString("color", "") }.toMutableList()
        val ids = HashSet<String>()
        for ((idx, s) in stories.withIndex()) {
            if (s.optJSONArray("notes") == null) s.put("notes", JSONArray())
            if (s.optJSONArray("chapters") == null) s.put("chapters", JSONArray())
            if (s.optString("color", "").isEmpty()) {
                val c = PALETTE.firstOrNull { it !in used } ?: PALETTE[idx % PALETTE.size]
                s.put("color", c)
                used.add(c)
            }
            ids.add(s.optString("id"))
            for (c in s.objs("chapters")) ids.add(c.optString("id"))
        }
        val log = root.getJSONObject("log")
        for (d in log.keyList()) {
            val l = log.optJSONObject(d) ?: continue
            for (id in l.keyList()) if (id !in ids) purge(id)
        }
    }

    // ---------- reading ----------

    private fun toView(s: JSONObject): StoryV = StoryV(
        id = s.optString("id"),
        title = s.optString("title", ""),
        description = s.optString("description", ""),
        genre = s.optString("genre", "Other"),
        status = s.optString("status", "Draft"),
        color = s.optString("color", "#818CF8"),
        cover = if (s.isNull("cover")) null else s.optString("cover", "").takeIf { it.startsWith("data:") },
        updatedAt = s.optLong("updatedAt", 0),
        chapters = s.objs("chapters").map {
            ChapterV(it.optString("id"), it.optString("title", "Untitled"), it.optInt("words", 0), it.optLong("updatedAt", 0))
        },
        notes = s.objs("notes").map {
            NoteV(it.optString("id"), it.optString("title", ""), it.optString("cat", "Other"), it.optString("text", ""), it.optLong("updatedAt", 0))
        },
    )

    fun storyViews(): List<StoryV> {
        track()
        return root.objs("stories").map { toView(it) }
    }

    fun storyView(id: String): StoryV? {
        track()
        return root.objs("stories").firstOrNull { it.optString("id") == id }?.let { toView(it) }
    }

    private fun storyJson(id: String): JSONObject? =
        root.objs("stories").firstOrNull { it.optString("id") == id }

    fun name(): String {
        track()
        return root.optJSONObject("profile")?.optString("name", "Writer")?.ifBlank { "Writer" } ?: "Writer"
    }

    fun goal(): Int {
        track()
        return root.optInt("goal", 2000)
    }

    fun lastExport(): Long {
        track()
        return root.optLong("lastExport", 0)
    }

    fun totalWords(): Int = storyViews().sumOf { it.words }

    fun todayWords(): Int {
        track()
        return maxOf(0, root.getJSONObject("activity").optInt(LocalDate.now().toString(), 0))
    }

    fun weekWords(): Int {
        track()
        val act = root.getJSONObject("activity")
        var t = 0
        for (i in 0..6) t += maxOf(0, act.optInt(LocalDate.now().minusDays(i.toLong()).toString(), 0))
        return t
    }

    fun streak(): Int {
        track()
        val act = root.getJSONObject("activity")
        var d = LocalDate.now()
        if (act.optInt(d.toString(), 0) <= 0) d = d.minusDays(1)
        var c = 0
        while (act.optInt(d.toString(), 0) > 0) {
            c++
            d = d.minusDays(1)
        }
        return c
    }

    private fun lifeWords(): Int {
        val act = root.getJSONObject("activity")
        var t = 0
        for (k in act.keyList()) {
            val v = act.optInt(k, 0)
            if (v > 0) t += v
        }
        return t
    }

    fun level(): LevelInfo {
        track()
        return levelFor(lifeWords())
    }

    private fun levelFor(words: Int): LevelInfo {
        var i = 0
        LEVELS.forEachIndexed { j, p -> if (words >= p.second) i = j }
        val cur = LEVELS[i]
        val nx = LEVELS.getOrNull(i + 1)
        val p = if (nx != null) ((words - cur.second).toFloat() / (nx.second - cur.second)).coerceIn(0f, 1f) else 1f
        return LevelInfo(i, cur.first, words, if (nx != null) nx.second - words else 0, p, nx != null)
    }

    // ---------- stats helpers (same rules as the HTML app) ----------

    private fun purge(id: String): Boolean {
        var changed = false
        val log = root.getJSONObject("log")
        val act = root.getJSONObject("activity")
        for (d in log.keyList()) {
            val l = log.optJSONObject(d) ?: continue
            if (l.has(id)) {
                act.put(d, act.optInt(d, 0) - l.optInt(id, 0))
                l.remove(id)
                changed = true
                if (l.length() == 0) log.remove(d)
                if (act.optInt(d, 0) <= 0) act.remove(d)
            }
        }
        return changed
    }

    private fun chapterTyped(cid: String): Int {
        val log = root.getJSONObject("log")
        var t = 0
        for (d in log.keyList()) t += log.optJSONObject(d)?.optInt(cid, 0) ?: 0
        return t
    }

    private fun dropChapterStats(storyId: String, cid: String, words: Int) {
        val act = root.getJSONObject("activity")
        val log = root.getJSONObject("log")
        val logged = chapterTyped(cid)
        purge(cid)
        var need = maxOf(0, words - logged)
        val days = act.keyList().sortedDescending()
        for (d in days) {
            if (need <= 0) break
            val l = log.optJSONObject(d) ?: continue
            val have = l.optInt(storyId, 0)
            val t = minOf(need, have)
            if (t > 0) {
                if (have - t <= 0) l.remove(storyId) else l.put(storyId, have - t)
                act.put(d, act.optInt(d, 0) - t)
                need -= t
            }
        }
        for (d in days) {
            if (need <= 0) break
            val l = log.optJSONObject(d)
            var sum = 0
            if (l != null) for (k in l.keyList()) sum += l.optInt(k, 0)
            val t = minOf(need, maxOf(0, act.optInt(d, 0) - sum))
            if (t > 0) {
                act.put(d, act.optInt(d, 0) - t)
                need -= t
            }
        }
        for (d in act.keyList()) if (act.optInt(d, 0) <= 0) act.remove(d)
        for (d in log.keyList()) {
            val l = log.optJSONObject(d)
            if (l != null && l.length() == 0) log.remove(d)
        }
        root.put("lvl", levelFor(lifeWords()).index)
    }

    // ---------- stories ----------

    fun createStory(title: String, description: String, genre: String, status: String, cover: String?): String {
        val now = System.currentTimeMillis()
        val used = root.objs("stories").map { it.optString("color", "") }
        val color = PALETTE.firstOrNull { it !in used } ?: PALETTE[used.size % PALETTE.size]
        val s = JSONObject()
        val id = newId()
        s.put("id", id)
        s.put("title", title)
        s.put("description", description)
        s.put("genre", genre)
        s.put("status", status)
        s.put("cover", cover ?: JSONObject.NULL)
        s.put("color", color)
        s.put("createdAt", now)
        s.put("updatedAt", now)
        s.put("chapters", JSONArray())
        s.put("notes", JSONArray())
        root.getJSONArray("stories").put(s)
        commit()
        return id
    }

    fun updateStory(id: String, title: String, description: String, genre: String, status: String, cover: String?) {
        val s = storyJson(id) ?: return
        s.put("title", title)
        s.put("description", description)
        s.put("genre", genre)
        s.put("status", status)
        s.put("cover", cover ?: JSONObject.NULL)
        commit()
    }

    fun deleteStory(id: String) {
        val arr = root.getJSONArray("stories")
        var idx = -1
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("id") == id) idx = i
        if (idx < 0) return
        val s = arr.getJSONObject(idx)
        arr.remove(idx)
        purge(id)
        for (c in s.objs("chapters")) purge(c.optString("id"))
        root.put("lvl", levelFor(lifeWords()).index)
        val last = root.optJSONObject("last")
        if (last != null && last.optString("sid") == id) root.put("last", JSONObject.NULL)
        commit()
    }

    // ---------- chapters ----------

    fun createChapter(sid: String, title: String): String? {
        val s = storyJson(sid) ?: return null
        val now = System.currentTimeMillis()
        val c = JSONObject()
        val id = newId()
        c.put("id", id)
        c.put("title", title.ifBlank { "Untitled" })
        c.put("content", "")
        c.put("html", "")
        c.put("words", 0)
        c.put("createdAt", now)
        c.put("updatedAt", now)
        s.getJSONArray("chapters").put(c)
        s.put("updatedAt", now)
        commit()
        return id
    }

    fun renameChapter(sid: String, cid: String, title: String) {
        val c = storyJson(sid)?.objs("chapters")?.firstOrNull { it.optString("id") == cid } ?: return
        c.put("title", title.ifBlank { "Untitled" })
        commit()
    }

    fun deleteChapter(sid: String, cid: String) {
        val s = storyJson(sid) ?: return
        val arr = s.getJSONArray("chapters")
        var idx = -1
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("id") == cid) idx = i
        if (idx < 0) return
        val c = arr.getJSONObject(idx)
        arr.remove(idx)
        s.put("updatedAt", System.currentTimeMillis())
        dropChapterStats(sid, cid, c.optInt("words", 0))
        val last = root.optJSONObject("last")
        if (last != null && last.optString("cid") == cid) root.put("last", JSONObject.NULL)
        commit()
    }

    fun moveChapter(sid: String, cid: String, delta: Int) {
        val s = storyJson(sid) ?: return
        val items = s.objs("chapters").toMutableList()
        val i = items.indexOfFirst { it.optString("id") == cid }
        val j = i + delta
        if (i < 0 || j < 0 || j >= items.size) return
        val t = items[i]
        items[i] = items[j]
        items[j] = t
        val arr = JSONArray()
        for (c in items) arr.put(c)
        s.put("chapters", arr)
        commit()
    }

    // ---------- home helpers ----------

    fun chapterCount(): Int {
        track()
        return root.objs("stories").sumOf { it.objs("chapters").size }
    }

    fun bkDue(): Boolean {
        track()
        val every = root.optInt("bkEvery", 0)
        val now = System.currentTimeMillis()
        val last = root.optLong("lastExport", 0)
        return every > 0 && now >= root.optLong("bkSnooze", 0) && lifeWords() >= 300 &&
            (last == 0L || now - last > every * 86_400_000L)
    }

    fun bkLater() {
        root.put("bkSnooze", System.currentTimeMillis() + 3 * 86_400_000L)
        commit()
    }

    fun setGoal(g: Int) {
        root.put("goal", maxOf(50, g))
        commit()
    }

    /** Words per day for the last k days, split by story ("_old" = earlier writing). */
    fun chartDays(k: Int): List<ChartDay> {
        track()
        val act = root.getJSONObject("activity")
        val log = root.getJSONObject("log")
        val owner = HashMap<String, String>()
        for (s in root.objs("stories")) {
            val sid = s.optString("id")
            owner[sid] = sid
            for (c in s.objs("chapters")) owner[c.optString("id")] = sid
        }
        val out = ArrayList<ChartDay>()
        for (i in k - 1 downTo 0) {
            val d = LocalDate.now().minusDays(i.toLong())
            val key = d.toString()
            val r = LinkedHashMap<String, Int>()
            var t = 0
            val l = log.optJSONObject(key)
            if (l != null) {
                for (id in l.keyList()) {
                    val o = owner[id] ?: continue
                    val w = l.optInt(id, 0)
                    r[o] = (r[o] ?: 0) + w
                    t += w
                }
            }
            val extra = act.optInt(key, 0) - t
            if (extra > 0) r["_old"] = extra
            out.add(ChartDay(d, r))
        }
        return out
    }

    /** Title and colour for a chart series id. */
    fun metaOf(id: String): Pair<String, String> {
        track()
        if (id == "_old") return "Earlier writing" to "#94A3B8"
        val s = storyJson(id)
        if (s != null) return s.optString("title", "") to s.optString("color", "#818CF8")
        return "Deleted story" to "#94A3B8"
    }

    // ---------- notes, statistics and export ----------

    fun createNote(sid: String, title: String, cat: String, text: String) {
        val s = storyJson(sid) ?: return
        val now = System.currentTimeMillis()
        val n = JSONObject()
        n.put("id", newId())
        n.put("title", title)
        n.put("cat", cat)
        n.put("text", text)
        n.put("createdAt", now)
        n.put("updatedAt", now)
        val arr = JSONArray()
        arr.put(n)
        for (x in s.objs("notes")) arr.put(x)
        s.put("notes", arr)
        commit()
    }

    fun updateNote(sid: String, nid: String, title: String, cat: String, text: String) {
        val n = storyJson(sid)?.objs("notes")?.firstOrNull { it.optString("id") == nid } ?: return
        n.put("title", title)
        n.put("cat", cat)
        n.put("text", text)
        n.put("updatedAt", System.currentTimeMillis())
        commit()
    }

    fun deleteNote(sid: String, nid: String) {
        val s = storyJson(sid) ?: return
        val arr = JSONArray()
        for (x in s.objs("notes")) if (x.optString("id") != nid) arr.put(x)
        s.put("notes", arr)
        commit()
    }

    fun chTyped(cid: String): Int {
        track()
        return chapterTyped(cid)
    }

    fun chWeek(cid: String): Int {
        track()
        val log = root.getJSONObject("log")
        var t = 0
        for (i in 0..6) t += log.optJSONObject(LocalDate.now().minusDays(i.toLong()).toString())?.optInt(cid, 0) ?: 0
        return t
    }

    fun last7(storyId: String): Int {
        track()
        val log = root.getJSONObject("log")
        val owner = HashMap<String, String>()
        for (s in root.objs("stories")) {
            val sid = s.optString("id")
            for (c in s.objs("chapters")) owner[c.optString("id")] = sid
            owner[sid] = sid
        }
        var t = 0
        for (i in 0..6) {
            val l = log.optJSONObject(LocalDate.now().minusDays(i.toLong()).toString()) ?: continue
            for (k in l.keyList()) if (owner[k] == storyId) t += l.optInt(k, 0)
        }
        return t
    }

    fun chapterContent(sid: String, cid: String): String =
        storyJson(sid)?.objs("chapters")?.firstOrNull { it.optString("id") == cid }?.optString("content", "") ?: ""

    /** Whole-story text for export, or null if the story has no chapters. */
    fun storyExportBody(sid: String): String? {
        val chs = storyJson(sid)?.objs("chapters") ?: return null
        if (chs.isEmpty()) return null
        return chs.joinToString("\n\n\n") { it.optString("title", "") + "\n\n" + it.optString("content", "").trim() }
    }

    // ---------- writing ----------

    /** Reads a chapter for the editor without subscribing to changes. */
    fun chapterForEdit(sid: String, cid: String): EditChapter? {
        val s = storyJson(sid) ?: return null
        val c = s.objs("chapters").firstOrNull { it.optString("id") == cid } ?: return null
        return EditChapter(c.optString("title", "Untitled"), c.optString("html", ""), c.optString("content", ""), s.optString("title", ""))
    }

    fun setLast(sid: String, cid: String) {
        val o = JSONObject()
        o.put("sid", sid)
        o.put("cid", cid)
        root.put("last", o)
        persist()
    }

    /** The chapter the Write tab reopens, if it still exists. */
    fun lastChapter(): Pair<String, String>? {
        val l = root.optJSONObject("last") ?: return null
        val sid = l.optString("sid")
        val cid = l.optString("cid")
        val ok = storyJson(sid)?.objs("chapters")?.any { it.optString("id") == cid } == true
        return if (ok) sid to cid else null
    }

    fun editorFontPeek(): String = root.optJSONObject("ed")?.optString("font", "lora") ?: "lora"

    fun editorSizePeek(): Int = root.optJSONObject("ed")?.optInt("size", 16) ?: 16

    fun setEditorFont(f: String) {
        root.getJSONObject("ed").put("font", f)
        persist()
    }

    fun setEditorSize(n: Int) {
        root.getJSONObject("ed").put("size", n)
        persist()
    }

    /** Saves a chapter and logs newly written words (same rules as the HTML app). */
    fun saveChapter(sid: String, cid: String, text: String, html: String, title: String) {
        val s = storyJson(sid) ?: return
        val c = s.objs("chapters").firstOrNull { it.optString("id") == cid } ?: return
        val w = wc(text)
        val d = w - c.optInt("words", 0)
        if (d > 0) {
            val day = LocalDate.now().toString()
            val act = root.getJSONObject("activity")
            act.put(day, act.optInt(day, 0) + d)
            val log = root.getJSONObject("log")
            val l = log.optJSONObject(day) ?: JSONObject().also { log.put(day, it) }
            l.put(cid, l.optInt(cid, 0) + d)
        }
        val now = System.currentTimeMillis()
        c.put("words", w)
        c.put("content", text)
        c.put("html", html)
        c.put("title", title.trim().ifEmpty { "Untitled" })
        c.put("updatedAt", now)
        s.put("updatedAt", now)
        levelCheck()
        commit()
    }

    private fun levelCheck() {
        val l = levelFor(lifeWords())
        val cur = if (root.isNull("lvl")) -1 else root.optInt("lvl", -1)
        if (cur < 0 || l.index < cur) {
            root.put("lvl", l.index)
            return
        }
        if (l.index > cur) {
            root.put("lvl", l.index)
            levelUp = l
        }
    }

    // ---------- settings ----------

    fun theme(): String {
        track()
        val t = root.optString("theme", "dark")
        return if (t == "light" || t == "system") t else "dark"
    }

    fun setTheme(t: String) {
        root.put("theme", t)
        commit()
    }

    fun bkEvery(): Int {
        track()
        return root.optInt("bkEvery", 14)
    }

    fun setBkEvery(days: Int) {
        root.put("bkEvery", days)
        commit()
    }

    fun setProfile(name: String, goal: Int) {
        root.getJSONObject("profile").put("name", name.trim().ifEmpty { "Writer" })
        root.put("goal", goal.coerceIn(1, 100000))
        commit()
    }

    // ---------- backup ----------

    /** Returns the number of stories if the text is an Inkar backup, otherwise null. */
    fun peekBackup(text: String): Int? {
        val d = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        if (d.has("app") && d.optString("app") != "inkar") return null
        return d.optJSONArray("stories")?.length()
    }

    fun importBackup(text: String): Boolean {
        val d = try {
            JSONObject(text)
        } catch (e: Exception) {
            return false
        }
        if (d.optJSONArray("stories") == null) return false
        val merged = defaultState()
        for (k in d.keyList()) if (k != "app" && k != "version" && k != "exportedAt") merged.put(k, d.get(k))
        root = merged
        fix()
        commit()
        return true
    }

    fun exportJson(): String {
        val out = JSONObject()
        out.put("app", "inkar")
        out.put("version", 1)
        out.put("exportedAt", Instant.now().toString())
        for (k in root.keyList()) if (k != "app" && k != "version" && k != "exportedAt") out.put(k, root.get(k))
        return out.toString(2)
    }

    fun markExported() {
        root.put("lastExport", System.currentTimeMillis())
        commit()
    }
}
