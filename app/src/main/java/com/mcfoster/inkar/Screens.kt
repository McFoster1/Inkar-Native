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

    val exportBackup = rememberBackupExporter(repo)

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
                    PrimaryButton("Export", exportBackup, Modifier.weight(1f))
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
