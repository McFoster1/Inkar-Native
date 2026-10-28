package com.mcfoster.inkar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

private fun chapterWord(n: Int) = if (n == 1) "chapter" else "chapters"

// ======================= Stories list =======================

private sealed interface ListDlg {
    data object New : ListDlg
    data class Edit(val id: String) : ListDlg
    data class Delete(val id: String) : ListDlg
}

@Composable
fun StoriesPage(
    repo: Repo,
    query: String,
    onQuery: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    newRequested: Boolean,
    onNewHandled: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<ListDlg?>(null) }
    LaunchedEffect(newRequested) {
        if (newRequested) {
            dlg = ListDlg.New
            onNewHandled()
        }
    }
    val all = repo.storyViews()
    val shown = all.filter {
        (filter == "All" || it.status == filter) && (it.title + it.description).contains(query, ignoreCase = true)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp)) {
            item {
                Column {
                    Txt("My Stories", 26f, InkText, FontWeight.SemiBold, spacing = -0.26f)
                    Txt("Stories are stored locally on this device. Export a backup in Settings.", 15f, InkMuted, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    Btn("+ New Story", { dlg = ListDlg.New }, primary = true)
                    Spacer(Modifier.height(24.dp))
                    HField(query, onQuery, placeholder = "Search stories", maxLen = 200)
                    Spacer(Modifier.height(12.dp))
                    HSeg(listOf("All") + STATUSES, filter, onFilter, Modifier.horizontalScroll(rememberScrollState()))
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (shown.isEmpty()) {
                item {
                    HCard {
                        Column(Modifier.fillMaxWidth().padding(vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(if (all.isNotEmpty()) "No matching stories" else "No stories yet", 22f, InkText, FontWeight.SemiBold, align = TextAlign.Center)
                            if (all.isEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Btn("+ Create First Story", { dlg = ListDlg.New }, primary = true)
                            }
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { s ->
                Box(Modifier.padding(bottom = 12.dp)) {
                    StoryCard(
                        s,
                        onOpen = { onOpen(s.id) },
                        onEdit = { dlg = ListDlg.Edit(s.id) },
                        onDelete = { dlg = ListDlg.Delete(s.id) },
                    )
                }
            }
        }
        Fab({ dlg = ListDlg.New }, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp))
    }

    when (val d = dlg) {
        null -> {}
        ListDlg.New -> StoryFormDialog(repo, null, onDismiss = { dlg = null }, onSaved = { id ->
            dlg = null
            onOpen(id)
        })
        is ListDlg.Edit -> StoryFormDialog(repo, repo.storyView(d.id), onDismiss = { dlg = null }, onSaved = { dlg = null })
        is ListDlg.Delete -> {
            val s = repo.storyView(d.id)
            if (s == null) {
                dlg = null
            } else {
                ConfirmModal(
                    "Delete this story?",
                    deleteStoryMessage(s),
                    "Delete story",
                    onDismiss = { dlg = null },
                    onConfirm = {
                        dlg = null
                        repo.deleteStory(s.id)
                        toast(ctx, "Story deleted")
                    },
                )
            }
        }
    }
}

private fun deleteStoryMessage(s: StoryV): String {
    val notes = if (s.notes.isNotEmpty()) " and ${s.notes.size} note${if (s.notes.size == 1) "" else "s"}" else ""
    return "“${s.title}” and its ${s.chapters.size} ${chapterWord(s.chapters.size)} (${fmt(s.words)} words)$notes " +
        "will be permanently deleted, along with its writing history and statistics."
}

@Composable
private fun StoryCard(s: StoryV, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Panel(padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverImage(s.cover, s.title, s.color, 96.dp, 128.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(s.title, 16f, InkText, FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(s.genre, 13f, InkMuted)
                    Pill(s.status)
                }
                if (s.description.isBlank()) Txt("No description", 14f, InkDim)
                else Txt(s.description, 14f, InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Txt(metaLine("${fmt(s.words)} words", "${s.chapters.size} ${chapterWord(s.chapters.size)}", ago(s.updatedAt)), 13f, InkMuted)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn(if (s.chapters.isNotEmpty()) "Continue" else "Open Story", onOpen, small = true)
                    IconSq(R.drawable.ic_pen, onEdit)
                    IconSq(R.drawable.ic_trash, onDelete, bad = true)
                }
            }
        }
    }
}

// ======================= Story form (new / edit) =======================

@Composable
fun StoryFormDialog(repo: Repo, existing: StoryV?, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var genre by remember { mutableStateOf(existing?.genre ?: GENRES[0]) }
    var status by remember { mutableStateOf(existing?.status ?: STATUSES[0]) }
    var cover by remember { mutableStateOf(existing?.cover) }
    var busy by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            thread {
                val c = uriToCover(ctx, uri)
                onMain {
                    busy = false
                    if (c != null) cover = c else toast(ctx, "Could not read that image")
                }
            }
        }
    }

    HDialog(onDismiss) {
        Txt(if (existing == null) "New story" else "Edit story", 22f, InkText, FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverImage(cover, "?", "#818CF8", 96.dp, 128.dp)
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(if (busy) "Loading…" else "Upload cover", { if (!busy) pick.launch("image/*") }, small = true)
                    if (cover != null) Btn("Remove", { cover = null }, small = true)
                }
                Txt("Cropped to 600 × 800 px", 12f, InkDim, modifier = Modifier.padding(top = 8.dp))
            }
        }
        HField(title, { title = it; missing = false }, label = "Title *", maxLen = 120, error = missing)
        HField(desc, { desc = it }, label = "Description", minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HSelect("Genre", genre, GENRES, { genre = it }, Modifier.weight(1f))
            HSelect("Status", status, STATUSES, { status = it }, Modifier.weight(1f))
        }
        DialogActions(onCancel = onDismiss) {
            Btn(if (existing == null) "Create" else "Save changes", {
                val t = title.trim()
                if (t.isEmpty()) {
                    missing = true
                } else if (existing != null) {
                    repo.updateStory(existing.id, t, desc.trim(), genre, status, cover)
                    toast(ctx, "Story updated")
                    onSaved(existing.id)
                } else {
                    onSaved(repo.createStory(t, desc.trim(), genre, status, cover))
                }
            }, primary = true)
        }
    }
}

// ======================= Story page =======================

private sealed interface SDlg {
    data object EditStory : SDlg
    data object DelStory : SDlg
    data object NewCh : SDlg
    data class RenCh(val cid: String, val title: String) : SDlg
    data class DelCh(val cid: String, val title: String, val words: Int) : SDlg
    data object NewNote : SDlg
    data class EditNote(val nid: String) : SDlg
    data class DelNote(val nid: String, val title: String) : SDlg
}

private val TABS = listOf("overview", "chapters", "notes", "analytics")

private fun safeName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]+"), "").trim().take(80).ifEmpty { "chapter" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryPage(repo: Repo, id: String, tab: String, onTab: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<SDlg?>(null) }
    var reorder by remember { mutableStateOf(false) }
    var viewCover by remember { mutableStateOf(false) }
    var noteFilter by rememberSaveable { mutableStateOf("All") }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    val s = repo.storyView(id)

    val exportPick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            val (title, body) = pending.first to pending.second
            val bytes = ("\uFEFF" + title + "\r\n\r\n" + body.replace(Regex("\r?\n"), "\r\n")).toByteArray(Charsets.UTF_8)
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

    if (s == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    fun export(fileBase: String, title: String, body: String) {
        pendingExport = title to body
        exportPick.launch(safeName(fileBase) + ".txt")
    }

    val totalAll = maxOf(1, repo.totalWords())
    val cols = if (LocalConfiguration.current.screenWidthDp >= 514) 3 else 2

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        ) {
            Row(Modifier.clickable(onClick = onBack).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                VecIcon(R.drawable.ic_back, InkMuted, 16.dp)
                Spacer(Modifier.width(6.dp))
                Txt("Back to stories", 14f, InkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = s.cover != null) { viewCover = true }) {
                    CoverImage(s.cover, s.title, s.color, 140.dp, (140f * 4f / 3f).dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(s.title, 26f, InkText, FontWeight.SemiBold, spacing = -0.26f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(s.genre, 13f, InkMuted)
                        Pill(s.status)
                    }
                    if (s.description.isNotBlank()) Txt(s.description, 15f, InkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("+ New Chapter", { dlg = SDlg.NewCh }, primary = true)
                        Btn("Edit story", { dlg = SDlg.EditStory }, small = true, icon = R.drawable.ic_pen)
                        Btn("Export story", {
                            val body = repo.storyExportBody(s.id)
                            if (body == null) toast(ctx, "No chapters to export yet") else export(s.title, s.title, body)
                        }, small = true, icon = R.drawable.ic_dl)
                        Btn("Delete", { dlg = SDlg.DelStory }, small = true, bad = true, icon = R.drawable.ic_trash)
                    }
                }
            }

            // tabs
            Column(Modifier.padding(vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    for (t in TABS) {
                        val on = t == tab
                        val label = t.replaceFirstChar { it.uppercase() } + if (t == "notes" && s.notes.isNotEmpty()) " (${s.notes.size})" else ""
                        Column(Modifier.clickable { onTab(t) }) {
                            Txt(label, 15f, if (on) InkText else InkMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) InkBlue else InkBorder.copy(alpha = 0f)))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
            }

            when (tab) {
                "chapters" -> HCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Txt("Chapters", 15f, InkText, FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s.chapters.size > 1) {
                                Btn(if (reorder) "Done" else "Reorder", { reorder = !reorder }, small = true, primary = reorder, icon = if (reorder) null else R.drawable.ic_updown)
                            }
                            Btn("+ New Chapter", { dlg = SDlg.NewCh }, small = true)
                        }
                    }
                    if (s.chapters.isEmpty()) {
                        Txt("No chapters yet. Add your first one.", 15f, InkMuted, modifier = Modifier.padding(top = 8.dp))
                    }
                    for ((i, c) in s.chapters.withIndex()) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt("${i + 1}", 15f, InkDim, modifier = Modifier.width(28.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Txt(c.title, 16f, InkText, FontWeight.SemiBold)
                                    Txt(metaLine("${fmt(c.words)} words", "Updated " + ago(c.updatedAt)), 13f, InkMuted)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (reorder) {
                                    IconSq(R.drawable.ic_up, { repo.moveChapter(s.id, c.id, -1) }, enabled = i > 0)
                                    IconSq(R.drawable.ic_down, { repo.moveChapter(s.id, c.id, 1) }, enabled = i < s.chapters.lastIndex)
                                } else {
                                    Btn("Write", { toast(ctx, "The writing screen arrives in Phase 2") }, small = true)
                                    IconSq(R.drawable.ic_pen, { dlg = SDlg.RenCh(c.id, c.title) })
                                    IconSq(R.drawable.ic_dl, { export(s.title + " - " + c.title, c.title, repo.chapterContent(s.id, c.id)) })
                                    IconSq(R.drawable.ic_trash, { dlg = SDlg.DelCh(c.id, c.title, c.words) }, bad = true)
                                }
                            }
                        }
                    }
                }

                "notes" -> HCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Txt("Notes", 15f, InkText, FontWeight.SemiBold)
                        Btn("+ New Note", { dlg = SDlg.NewNote }, small = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    HSeg(listOf("All") + NCATS, noteFilter, { noteFilter = it }, Modifier.horizontalScroll(rememberScrollState()))
                    Spacer(Modifier.height(12.dp))
                    val list = s.notes.filter { noteFilter == "All" || it.cat == noteFilter }
                    if (list.isEmpty()) {
                        Txt(
                            if (s.notes.isNotEmpty()) "No notes in this category." else "No notes yet. Use notes for characters, places, plot points and ideas.",
                            15f, InkMuted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    for ((i, n) in list.withIndex()) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                        Row(
                            Modifier.fillMaxWidth().clickable { dlg = SDlg.EditNote(n.id) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Txt(n.title, 16f, InkText, FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Pill(n.cat)
                                    Txt("· Updated " + ago(n.updatedAt).replace(' ', '\u00A0'), 13f, InkMuted)
                                }
                                if (n.text.isNotBlank()) Txt(n.text, 13f, InkMuted, modifier = Modifier.padding(top = 4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            IconSq(R.drawable.ic_trash, { dlg = SDlg.DelNote(n.id, n.title) }, bad = true)
                        }
                    }
                }

                "analytics" -> {
                    val avg = if (s.chapters.isEmpty()) 0 else Math.round(s.words.toDouble() / s.chapters.size).toInt()
                    val longest = s.chapters.maxOfOrNull { it.words } ?: 0
                    val stats = listOf(
                        "AVG WORDS / CHAPTER" to fmt(avg),
                        "MOST WORDS / CHAPTER" to fmt(longest),
                        "LAST 7 DAYS" to fmt(repo.last7(s.id)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (row in stats.chunked(cols)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for ((label, value) in row) StatCard(label, value, Modifier.weight(1f))
                                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        HCard {
                            Txt("Chapter statistics", 15f, InkText, FontWeight.SemiBold)
                            if (s.chapters.isEmpty()) Txt("Nothing to show yet.", 15f, InkMuted, modifier = Modifier.padding(top = 8.dp))
                            val maxW = maxOf(1, s.chapters.maxOfOrNull { it.words } ?: 1)
                            for ((i, c) in s.chapters.withIndex()) {
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                                val typed = repo.chTyped(c.id)
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Txt("${i + 1}", 15f, InkDim, modifier = Modifier.width(28.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Txt(c.title, 13f, InkMuted, modifier = Modifier.weight(1f, fill = false))
                                            Txt(fmt(c.words), 13f, InkMuted)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(InkEl)) {
                                            Box(Modifier.fillMaxWidth(Math.round(c.words.toDouble() / maxW * 100).toInt() / 100f).height(8.dp).clip(CircleShape).background(InkBlue))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        val pct = Math.round(c.words.toDouble() / maxOf(s.words, 1) * 100).toInt()
                                        Txt(
                                            (if (typed > 0) fmt(typed) + " typed · " else "") + "${fmt(repo.chWeek(c.id))} this week · $pct% of story",
                                            12f, InkDim, lh = 1.4f,
                                        )
                                    }
                                }
                            }
                            Txt("${Math.round(s.words.toDouble() / totalAll * 100)}% of all your words.", 15f, InkDim, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val stats = listOf("WORDS" to fmt(s.words), "CHAPTERS" to s.chapters.size.toString())
                        for (row in stats.chunked(cols)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for ((label, value) in row) StatCard(label, value, Modifier.weight(1f))
                                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        HCard {
                            Txt("Recent chapters", 15f, InkText, FontWeight.SemiBold)
                            val recent = s.chapters.sortedByDescending { it.updatedAt }.take(4)
                            if (recent.isEmpty()) Txt("No chapters yet.", 15f, InkMuted, modifier = Modifier.padding(top = 8.dp))
                            for ((i, c) in recent.withIndex()) {
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Txt(c.title, 16f, InkText, FontWeight.SemiBold)
                                        Txt(metaLine("${fmt(c.words)} words", ago(c.updatedAt)), 13f, InkMuted)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Btn("Write", { toast(ctx, "The writing screen arrives in Phase 2") }, small = true)
                                }
                            }
                        }
                    }
                }
            }
        }
        Fab({ dlg = SDlg.NewCh }, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp))
    }

    val coverData = s.cover
    if (viewCover && coverData != null) CoverViewer(coverData) { viewCover = false }

    when (val d = dlg) {
        null -> {}
        SDlg.EditStory -> StoryFormDialog(repo, s, onDismiss = { dlg = null }, onSaved = { dlg = null })
        SDlg.DelStory -> ConfirmModal("Delete this story?", deleteStoryMessage(s), "Delete story", { dlg = null }) {
            dlg = null
            repo.deleteStory(s.id)
            toast(ctx, "Story deleted")
            onBack()
        }
        SDlg.NewCh -> NameDialog("New chapter", "Chapter title", "Chapter ${s.chapters.size + 1}", "Create", { dlg = null }) { t ->
            dlg = null
            repo.createChapter(s.id, t)
            onTab("chapters")
            toast(ctx, "Chapter created")
        }
        is SDlg.RenCh -> NameDialog("Rename chapter", "Chapter title", d.title, "Save", { dlg = null }) { t ->
            dlg = null
            repo.renameChapter(s.id, d.cid, t)
        }
        is SDlg.DelCh -> ConfirmModal(
            "Delete this chapter?",
            "“${d.title}” (${fmt(d.words)} words) will be permanently deleted, and its words will be removed from your totals, charts and level. Download it first if you want a copy.",
            "Delete chapter",
            { dlg = null },
        ) {
            dlg = null
            repo.deleteChapter(s.id, d.cid)
            toast(ctx, "Chapter deleted")
        }
        SDlg.NewNote -> NoteDialog(null, { dlg = null }) { t, c, x ->
            dlg = null
            repo.createNote(s.id, t, c, x)
        }
        is SDlg.EditNote -> NoteDialog(s.notes.firstOrNull { it.id == d.nid }, { dlg = null }) { t, c, x ->
            dlg = null
            repo.updateNote(s.id, d.nid, t, c, x)
        }
        is SDlg.DelNote -> ConfirmModal(
            "Delete this note?",
            "“${d.title}” will be permanently deleted.",
            "Delete note",
            { dlg = null },
        ) {
            dlg = null
            repo.deleteNote(s.id, d.nid)
            toast(ctx, "Note deleted")
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, small: Boolean = false) {
    HCard(modifier) {
        Txt(label, 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
        Spacer(Modifier.height(4.dp))
        Txt(value, if (small) 20f else 26f, InkText, FontWeight.Bold)
    }
}

@Composable
private fun NoteDialog(existing: NoteV?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var cat by remember { mutableStateOf(existing?.cat ?: NCATS[0]) }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var missing by remember { mutableStateOf(false) }
    HDialog(onDismiss) {
        Txt(if (existing == null) "New note" else "Edit note", 22f, InkText, FontWeight.SemiBold)
        HField(title, { title = it; missing = false }, label = "Title *", maxLen = 120, error = missing)
        HSelect("Category", cat, NCATS, { cat = it })
        HField(text, { text = it }, label = "Note", placeholder = "Details, ideas, reminders…", minLines = 9)
        DialogActions(onCancel = onDismiss) {
            Btn(if (existing == null) "Add note" else "Save", {
                val t = title.trim()
                if (t.isEmpty()) missing = true else onSave(t, cat, text)
            }, primary = true)
        }
    }
}
