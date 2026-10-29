package com.mcfoster.inkar

import android.graphics.Color as AColor
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo.get(applicationContext)
        Theme.mode = repo.theme()
        Theme.sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        applyBars(Theme.isLight)
        setContent {
            val saved = repo.theme()
            val sysDark = isSystemInDarkTheme()
            SideEffect {
                Theme.mode = saved
                Theme.sysDark = sysDark
            }
            val light = Theme.isLight
            SideEffect { applyBars(light) }
            MaterialTheme(colorScheme = inkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = InkBg) {
                    InkarApp(repo)
                }
            }
        }
    }

    override fun onPause() {
        EditorHooks.flush?.invoke()
        super.onPause()
    }

    private fun applyBars(light: Boolean) {
        val style = if (light) SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT) else SystemBarStyle.dark(AColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun InkarApp(repo: Repo) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var openStory by rememberSaveable { mutableStateOf<String?>(null) }
    var storyTab by rememberSaveable { mutableStateOf("overview") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var newRequested by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf(false) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val levelUp = repo.levelUp
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    fun selectTab(i: Int) {
        if (i == 1) openStory = null
        if (i != 2) editing = null
        tab = i
    }

    fun openEditor(sid: String, cid: String) {
        viewing = false
        editing = "$sid|$cid"
        tab = 2
    }

    fun leaveEditor(sid: String) {
        viewing = false
        editing = null
        storyTab = "chapters"
        openStory = sid
        tab = 1
    }

    fun continueStory(s: StoryV) {
        val c = s.chapters.maxByOrNull { it.updatedAt }
        if (c != null) {
            openEditor(s.id, c.id)
        } else {
            storyTab = "overview"
            openStory = s.id
            tab = 1
        }
    }

    BackHandler(enabled = editing != null || openStory != null || tab != 0) {
        val e = editing
        when {
            e != null && viewing -> viewing = false
            e != null -> leaveEditor(e.substringBefore('|'))
            openStory != null -> openStory = null
            else -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(InkBg).statusBarsPadding().imePadding()) {
            if (!viewing) AppTopBar(repo, showNav = wide && editing == null, selected = tab, onSelect = { selectTab(it) })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(
                        repo,
                        onContinue = { s -> continueStory(s) },
                        onNewStory = {
                            openStory = null
                            newRequested = true
                            tab = 1
                        },
                    )
                    1 -> {
                        val id = openStory
                        if (id == null) {
                            StoriesPage(
                                repo,
                                query = query,
                                onQuery = { query = it },
                                filter = filter,
                                onFilter = { filter = it },
                                newRequested = newRequested,
                                onNewHandled = { newRequested = false },
                                onOpen = { sid ->
                                    storyTab = "overview"
                                    openStory = sid
                                },
                            )
                        } else {
                            StoryPage(
                                repo, id, storyTab,
                                onTab = { storyTab = it },
                                onWrite = { cid -> openEditor(id, cid) },
                                onBack = { openStory = null },
                            )
                        }
                    }
                    2 -> {
                        val e = editing
                        if (e != null) {
                            val sid = e.substringBefore('|')
                            val cid = e.substringAfter('|')
                            EditorScreen(repo, sid, cid, viewing, { viewing = it }, onBack = { leaveEditor(sid) })
                        } else {
                            LaunchedEffect(Unit) {
                                val l = repo.lastChapter()
                                if (l != null) {
                                    openEditor(l.first, l.second)
                                } else {
                                    toast(ctx, "Create a story and chapter to start writing")
                                    tab = 1
                                }
                            }
                        }
                    }
                    3 -> ComingSoon("Stats", "Charts and statistics arrive in Phase 3.")
                    else -> MenuScreen(repo)
                }
            }
            if (!wide && !imeOpen && !viewing && editing == null) {
                BottomBar(tab) { selectTab(it) }
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        if (levelUp != null) {
            LaunchedEffect(levelUp) {
                delay(6000)
                repo.dismissLevelUp()
            }
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 360.dp).fillMaxWidth()
                    .shadow(12.dp, shape, ambientColor = InkBlue, spotColor = InkBlue)
                    .clip(shape).background(InkCard).border(1.dp, InkBlue, shape)
                    .clickable { repo.dismissLevelUp() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Txt("LEVEL UP!", 12f, InkBlue, FontWeight.Bold, spacing = 0.96f)
                Txt("You've reached Level ${levelUp.index + 1}", 15f, InkText)
                Txt(levelUp.title, 18f, InkText, FontWeight.Bold, modifier = Modifier.padding(vertical = 2.dp))
                Txt("Your dedication to writing continues to grow.", 13f, InkDim)
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(InkCard)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            NAV_DEFS.forEachIndexed { i, d ->
                val on = i == selected
                val color = if (on) InkBlue else InkDim
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                Column(
                    Modifier.weight(1f)
                        .heightIn(min = 54.dp)
                        .graphicsLayer {
                            alpha = if (pressed) 0.55f else 1f
                            scaleX = if (pressed) 0.94f else 1f
                            scaleY = if (pressed) 0.94f else 1f
                        }
                        .clickable(interactionSource = src, indication = null) { onSelect(i) }
                        .padding(top = 8.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    VecIcon(if (on) d.solid else d.outline, color, 24.dp)
                    Txt(d.short, 11f, color, if (on) FontWeight.SemiBold else FontWeight.Normal, lh = 1.3f)
                }
            }
        }
    }
}
