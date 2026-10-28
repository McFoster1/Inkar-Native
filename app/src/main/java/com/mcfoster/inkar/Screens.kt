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

// ---------- Backup export (shared by Home and Menu) ----------

@Composable
fun rememberBackupExporter(repo: Repo): () -> Unit {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
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
    return { pick.launch("inkar-backup-${LocalDate.now()}.json") }
}

// ---------- Menu ----------

private val THEME_OPTS = listOf("Dark", "Light", "System")
private val BK_OPTS = listOf(0 to "Off", 7 to "Every week", 14 to "Every 2 weeks", 30 to "Every month")

@Composable
fun MenuScreen(repo: Repo) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(repo.name()) }
    var goal by remember { mutableStateOf(repo.goal().toString()) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    val lastExport = repo.lastExport()
    val theme = repo.theme()
    val bkEvery = repo.bkEvery()
    val exportBackup = rememberBackupExporter(repo)

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Txt("Settings", 26f, InkText, FontWeight.SemiBold, spacing = -0.26f)

        HCard {
            Txt("Profile", 18f, InkText, FontWeight.Bold)
            HField(name, { name = it }, label = "Your name", maxLen = 60)
            HField(goal, { goal = it.filter { ch -> ch.isDigit() }.take(6) }, label = "Daily word goal", keyboardType = KeyboardType.Number)
            HSelect("Theme", theme.replaceFirstChar { it.uppercase() }, THEME_OPTS, { repo.setTheme(it.lowercase()) })
            Spacer(Modifier.height(16.dp))
            Btn("Save", {
                repo.setProfile(name, goal.toIntOrNull() ?: repo.goal())
                name = repo.name()
                goal = repo.goal().toString()
                toast(ctx, "Saved")
            }, Modifier.fillMaxWidth(), primary = true)
        }

        HCard {
            Txt("Backup", 18f, InkText, FontWeight.Bold)
            Txt(
                "Last backup: " + if (lastExport == 0L) "never" else ago(lastExport),
                15f, InkMuted, modifier = Modifier.padding(top = 4.dp),
            )
            Txt("Uses the same file as the HTML app, so backups work both ways.", 13f, InkDim, modifier = Modifier.padding(top = 2.dp))
            HSelect(
                "Backup reminder",
                BK_OPTS.firstOrNull { it.first == bkEvery }?.second ?: "Every 2 weeks",
                BK_OPTS.map { it.second },
                { label -> repo.setBkEvery(BK_OPTS.first { it.second == label }.first) },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Import", { importPick.launch(arrayOf("*/*")) }, Modifier.weight(1f))
                Btn("Export", exportBackup, Modifier.weight(1f), primary = true)
            }
        }

        HCard {
            Txt("About", 18f, InkText, FontWeight.Bold)
            Txt("Inkar-Native · version $version", 15f, InkMuted, modifier = Modifier.padding(top = 4.dp))
            Txt("Phase 1: stories, chapters and backup", 13f, InkDim)
        }
    }

    val text = pendingImport
    if (text != null) {
        ConfirmModal(
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
