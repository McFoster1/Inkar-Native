package com.mcfoster.inkar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalTime

@Composable
fun HomeScreen(repo: Repo, onOpenStory: (String) -> Unit, onNewStory: () -> Unit) {
    val ctx = LocalContext.current
    val exportBackup = rememberBackupExporter(repo)
    val name = repo.name()
    val goal = repo.goal()
    val today = repo.todayWords()
    val week = repo.weekWords()
    val streak = repo.streak()
    val chapters = repo.chapterCount()
    val stories = repo.storyViews()
    val totalWords = stories.sumOf { it.words }
    val bkDue = repo.bkDue()
    var showGoal by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Int?>(null) }

    val hour = LocalTime.now().hour
    val greet = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val cols = if (LocalConfiguration.current.screenWidthDp >= 514) 3 else 2

    Column(Modifier.fillMaxSize()) {

        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) { detectTapGestures { sel = null } }
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (stories.isEmpty()) {
                HCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("Your writing starts here.", 22f, InkText, FontWeight.SemiBold, align = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            "Create your first story and begin building your local writing workspace.",
                            15f, InkMuted, align = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Btn("+ Create First Story", onNewStory, primary = true)
                    }
                }
            } else {
                Column {
                    Txt("$greet, $name.", 26f, InkText, FontWeight.SemiBold, spacing = -0.26f)
                    Txt("Here's how your writing is going.", 15f, InkMuted)
                    Spacer(Modifier.height(16.dp))
                    Btn("+ New Story", onNewStory, primary = true)
                    Spacer(Modifier.height(8.dp))
                }

                if (bkDue) {
                    val lastExport = repo.lastExport()
                    HCard(glow = Glow.TopLeft, borderColor = InkBlue) {
                        Txt("Back up your writing", 15f, InkText, FontWeight.Bold)
                        Txt(
                            if (lastExport == 0L) "You have not made a backup yet. A backup protects your stories if this phone is lost or reset."
                            else "Your last backup was ${ago(lastExport)}. A backup protects your stories if this phone is lost or reset.",
                            13f, InkDim, modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Export backup", exportBackup, primary = true, small = true)
                            Btn("Later", { repo.bkLater() }, small = true)
                        }
                    }
                }

                HCard {
                    Txt("TOTAL WORDS", 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
                    Spacer(Modifier.height(6.dp))
                    Txt(fmt(totalWords), 44f, InkText, FontWeight.Bold, spacing = -1.32f, lh = 1.05f)
                    Spacer(Modifier.height(6.dp))
                    if (week > 0) Txt("+" + fmt(week) + " this week", 15f, InkOk, FontWeight.Medium)
                    else Txt("No words this week yet", 15f, InkDim)
                }

                val stats = listOf(
                    "WORDS TODAY" to fmt(today),
                    "CHAPTERS" to fmt(chapters),
                    "WRITING STREAK" to "$streak ${if (streak == 1) "day" else "days"}",
                )
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (row in stats.chunked(cols)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for ((label, value) in row) {
                                HCard(Modifier.weight(1f)) {
                                    Txt(label, 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
                                    Spacer(Modifier.height(4.dp))
                                    Txt(value, 26f, InkText, FontWeight.Bold)
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                WritingChartCard(repo, goal, sel) { sel = it }

                HCard {
                    Txt("DAILY GOAL", 11f, InkDim, FontWeight.SemiBold, spacing = 0.88f)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(fmt(today), 26f, InkText, FontWeight.Bold)
                        Txt(" / " + fmt(goal), 16f, InkDim, FontWeight.Medium, Modifier.padding(bottom = 2.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    val pct = minOf(100, today * 100 / goal)
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(InkEl)) {
                        Box(
                            Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(CircleShape)
                                .background(if (pct >= 100) InkOk else InkBlue),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Txt("$pct%", 15f, InkMuted)
                        Btn("Change goal", { showGoal = true }, small = true)
                    }
                }

                HCard {
                    Txt("Recent Stories", 15f, InkText, FontWeight.SemiBold)
                    val recent = stories.sortedByDescending { it.updatedAt }.take(4)
                    for ((i, s) in recent.withIndex()) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(s.cover, s.title, s.color, 44.dp, (44f * 4f / 3f).dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Txt(s.title, 16f, InkText, FontWeight.SemiBold)
                                Txt(metaLine(s.genre, "${fmt(s.words)} words", "${s.chapters.size} ch", ago(s.updatedAt)), 13f, InkMuted)
                            }
                            Spacer(Modifier.width(8.dp))
                            Btn("Continue", { onOpenStory(s.id) }, small = true)
                        }
                    }
                }
            }
        }
    }

    if (showGoal) {
        var value by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { showGoal = false },
            containerColor = InkCard,
            title = { Txt("Daily goal", 18f, InkText, FontWeight.SemiBold) },
            text = {
                InkField(value, { value = it.filter { c -> c.isDigit() }.take(6) }, "Words per day", keyboardType = KeyboardType.Number)
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.setGoal(value.toIntOrNull() ?: 2000)
                    showGoal = false
                }) { Txt("Save goal", 15f, InkBlue, FontWeight.Medium) }
            },
            dismissButton = { TextButton(onClick = { showGoal = false }) { Txt("Cancel", 15f, InkMuted) } },
        )
    }
}
