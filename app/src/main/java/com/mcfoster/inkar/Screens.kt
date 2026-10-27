package com.mcfoster.inkar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import kotlin.concurrent.thread

// ---------- Home ----------

@Composable
fun HomeScreen(repo: Repo, onStories: () -> Unit, onNewStory: () -> Unit) {
    val name = repo.name()
    val hour = LocalTime.now().hour
    val greet = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val goal = repo.goal()
    val today = repo.todayWords()
    val lv = repo.level()
    val stories = repo.storyViews()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Brand()
        Text("$greet, $name", color = InkText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Panel {
            Text("TODAY", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(6.dp))
            Text("${fmt(today)} / ${fmt(goal)} words", color = InkText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Bar(today.toFloat() / goal)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("STREAK", "${repo.streak()} days", Modifier.weight(1f))
            Tile("THIS WEEK", fmt(repo.weekWords()), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("TOTAL WORDS", fmt(stories.sumOf { it.words }), Modifier.weight(1f))
            Tile("STORIES", stories.size.toString(), Modifier.weight(1f))
        }

        Panel {
            Text("LEVEL ${lv.index + 1}", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(lv.title, color = InkText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Bar(lv.progress)
            Spacer(Modifier.height(8.dp))
            Text(
                if (lv.hasNext) "${fmt(lv.left)} words to level ${lv.index + 2}" else "Top level reached",
                color = InkMuted,
                fontSize = 13.sp,
            )
        }

        if (stories.isEmpty()) {
            PrimaryButton("Create your first story", onNewStory, Modifier.fillMaxWidth())
        } else {
            GhostButton("Open stories", onStories, Modifier.fillMaxWidth())
        }
    }
}

// ---------- Stories list ----------

@Composable
fun StoriesScreen(repo: Repo, onOpen: (String) -> Unit, onNew: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val all = repo.storyViews().sortedByDescending { it.updatedAt }
    val shown = all.filter {
        (filter == "All" || it.status == filter) &&
            (it.title + " " + it.description).contains(q.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Stories") { IconBtn(Ic.Plus, onNew, tint = InkBlue) }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InkField(q, { q = it }, "Search stories")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in listOf("All") + STATUSES) Chip(f, filter == f) { filter = f }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (all.isEmpty()) "No stories yet. Tap + to create one." else "No matching stories.",
                        color = InkMuted,
                    )
                }
            }
            items(shown, key = { it.id }) { s -> StoryCard(s) { onOpen(s.id) } }
        }
    }
}

@Composable
private fun StoryCard(s: StoryV, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverImage(s.cover, s.title, s.color, 72.dp, 96.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(s.title, color = InkText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.genre, color = InkMuted, fontSize = 12.sp)
                    Pill(s.status)
                }
                Text(
                    s.description.ifBlank { "No description" },
                    color = if (s.description.isBlank()) InkDim else InkMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${fmt(s.words)} words · ${s.chapters.size} ${if (s.chapters.size == 1) "chapter" else "chapters"} · ${ago(s.updatedAt)}",
                    color = InkDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------- Story detail ----------

private sealed interface Dlg {
    data object NewChapter : Dlg
    data class Rename(val cid: String, val title: String) : Dlg
    data class DelChapter(val cid: String, val title: String, val words: Int) : Dlg
    data object DelStory : Dlg
}

@Composable
fun StoryDetailScreen(repo: Repo, id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var reorder by remember { mutableStateOf(false) }
    val s = repo.storyView(id)
    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.title, onBack) {
            IconBtn(Ic.Pen, onEdit)
            IconBtn(Ic.Trash, { dlg = Dlg.DelStory }, tint = InkBad)
        }
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverImage(s.cover, s.title, s.color, 96.dp, 128.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.genre, color = InkMuted, fontSize = 13.sp)
                            Pill(s.status)
                        }
                        Text(
                            s.description.ifBlank { "No description" },
                            color = if (s.description.isBlank()) InkDim else InkMuted,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("WORDS", fmt(s.words), Modifier.weight(1f))
                    Tile("CHAPTERS", s.chapters.size.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Chapters", color = InkText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (s.chapters.size > 1) {
                        Chip(if (reorder) "Done" else "Reorder", reorder) { reorder = !reorder }
                        Spacer(Modifier.width(8.dp))
                    }
                    Chip("+ New", false) { dlg = Dlg.NewChapter }
                }
            }
            if (s.chapters.isEmpty()) {
                item { Text("No chapters yet.", color = InkMuted) }
            }
            itemsIndexed(s.chapters, key = { _, c -> c.id }) { i, c ->
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(InkCard).border(1.dp, InkBorder, shape)
                        .clickable { toast(ctx, "The writing screen arrives in Phase 2") }
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = InkDim, fontSize = 14.sp, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = InkText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmt(c.words)} words · ${ago(c.updatedAt)}", color = InkDim, fontSize = 12.sp)
                    }
                    if (reorder) {
                        IconBtn(Ic.Up, { repo.moveChapter(s.id, c.id, -1) }, enabled = i > 0)
                        IconBtn(Ic.Down, { repo.moveChapter(s.id, c.id, 1) }, enabled = i < s.chapters.lastIndex)
                    } else {
                        IconBtn(Ic.Pen, { dlg = Dlg.Rename(c.id, c.title) }, tint = InkMuted)
                        IconBtn(Ic.Trash, { dlg = Dlg.DelChapter(c.id, c.title, c.words) }, tint = InkBad)
                    }
                }
            }
        }
    }

    when (val d = dlg) {
        null -> {}
        Dlg.NewChapter -> TitleDialog(
            "New chapter", "Chapter title", "Chapter ${s.chapters.size + 1}", "Create",
            onDismiss = { dlg = null },
        ) { t ->
            repo.createChapter(s.id, t)
            dlg = null
        }
        is Dlg.Rename -> TitleDialog(
            "Rename chapter", "Chapter title", d.title, "Save",
            onDismiss = { dlg = null },
        ) { t ->
            repo.renameChapter(s.id, d.cid, t)
            dlg = null
        }
        is Dlg.DelChapter -> ConfirmDialog(
            "Delete this chapter?",
            "“${d.title}” (${fmt(d.words)} words) will be permanently deleted, and its words will be removed from your totals and level.",
            "Delete chapter",
            onDismiss = { dlg = null },
            onConfirm = {
                repo.deleteChapter(s.id, d.cid)
                dlg = null
            },
        )
        Dlg.DelStory -> ConfirmDialog(
            "Delete this story?",
            "“${s.title}” and its ${s.chapters.size} ${if (s.chapters.size == 1) "chapter" else "chapters"} (${fmt(s.words)} words) will be permanently deleted, along with its writing history.",
            "Delete story",
            onDismiss = { dlg = null },
            onConfirm = {
                dlg = null
                repo.deleteStory(s.id)
                onBack()
            },
        )
    }
}

// ---------- Story form ----------

@Composable
fun StoryFormScreen(repo: Repo, id: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    val existing = remember(id) { id?.let { repo.storyView(it) } }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var desc by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var genre by rememberSaveable { mutableStateOf(existing?.genre ?: GENRES[0]) }
    var status by rememberSaveable { mutableStateOf(existing?.status ?: STATUSES[0]) }
    var cover by remember { mutableStateOf(existing?.cover) }
    var busy by remember { mutableStateOf(false) }
    var missingTitle by remember { mutableStateOf(false) }

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

    val save: () -> Unit = {
        val t = title.trim()
        if (t.isEmpty()) {
            missingTitle = true
        } else if (existing != null) {
            repo.updateStory(existing.id, t, desc.trim(), genre, status, cover)
            toast(ctx, "Story updated")
            onSaved(existing.id)
        } else {
            onSaved(repo.createStory(t, desc.trim(), genre, status, cover))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (existing == null) "New story" else "Edit story", onBack) {
            TextButton(onClick = save) { Text("Save", color = InkBlue, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverImage(cover, title, existing?.color ?: "#818CF8", 96.dp, 128.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(if (busy) "Loading…" else "Upload cover", { if (!busy) pick.launch("image/*") })
                    if (cover != null) GhostButton("Remove", { cover = null })
                    Text("Cropped to 600 × 800 px", color = InkDim, fontSize = 12.sp)
                }
            }
            InkField(title, { title = it; missingTitle = false }, "Title *", isError = missingTitle)
            InkField(desc, { desc = it }, "Description", minLines = 3)
            Text("Genre", color = InkMuted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in GENRES) Chip(g, genre == g) { genre = g }
            }
            Text("Status", color = InkMuted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (st in STATUSES) Chip(st, status == st) { status = st }
            }
            PrimaryButton(if (existing == null) "Create story" else "Save changes", save, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------- Menu ----------

@Composable
fun MenuScreen(repo: Repo) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(repo.name()) }
    var goal by remember { mutableStateOf(repo.goal().toString()) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    val lastExport = repo.lastExport()

    @Suppress("DEPRECATION")
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "unknown"
    }

    val importPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            thread {
                val text = try {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
                onMain {
                    val n = text?.let { repo.peekBackup(it) }
                    if (text == null || n == null) {
                        toast(ctx, "That file isn't an Inkar backup")
                    } else {
                        pendingImport = text
                        pendingCount = n
                    }
                }
            }
        }
    }

    val exportPick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            val bytes = repo.exportJson().toByteArray()
            thread {
                val ok = try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
                } catch (e: Exception) {
                    false
                }
                onMain {
                    if (ok) {
                        repo.markExported()
                        toast(ctx, "Backup saved")
                    } else {
                        toast(ctx, "Could not save the backup")
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Menu")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel {
                Text("Profile", color = InkText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                InkField(name, { name = it }, "Your name")
                Spacer(Modifier.height(12.dp))
                InkField(goal, { goal = it.filter { ch -> ch.isDigit() }.take(6) }, "Daily word goal", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Save", {
                    repo.setProfile(name, goal.toIntOrNull() ?: repo.goal())
                    name = repo.name()
                    goal = repo.goal().toString()
                    toast(ctx, "Saved")
                }, Modifier.fillMaxWidth())
            }

            Panel {
                Text("Backup", color = InkText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last backup: " + if (lastExport == 0L) "never" else ago(lastExport),
                    color = InkMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("Uses the same file as the HTML app, so backups work both ways.", color = InkDim, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Import", { importPick.launch(arrayOf("*/*")) }, Modifier.weight(1f))
                    PrimaryButton("Export", { exportPick.launch("inkar-backup-${LocalDate.now()}.json") }, Modifier.weight(1f))
                }
            }

            Panel {
                Text("About", color = InkText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Inkar-Native · version $version", color = InkMuted, fontSize = 13.sp)
                Text("Phase 1: stories, chapters and backup", color = InkDim, fontSize = 12.sp)
            }
        }
    }

    val text = pendingImport
    if (text != null) {
        ConfirmDialog(
            "Replace current data?",
            "This backup has $pendingCount ${if (pendingCount == 1) "story" else "stories"}. Everything currently in this app will be replaced.",
            "Replace",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                if (repo.importBackup(text)) {
                    name = repo.name()
                    goal = repo.goal().toString()
                    toast(ctx, "Backup restored")
                } else {
                    toast(ctx, "Could not restore that backup")
                }
            },
            danger = false,
        )
    }
}

// ---------- Placeholder ----------

@Composable
fun ComingSoon(title: String, text: String) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title)
        Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(text, color = InkMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}
