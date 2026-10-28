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

    private fun applyBars(light: Boolean) {
        val style = if (light) SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT) else SystemBarStyle.dark(AColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun InkarApp(repo: Repo) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var openStory by rememberSaveable { mutableStateOf<String?>(null) }
    var storyTab by rememberSaveable { mutableStateOf("overview") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var newRequested by remember { mutableStateOf(false) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(enabled = openStory != null || tab != 0) {
        if (openStory != null) openStory = null else tab = 0
    }

    Column(Modifier.fillMaxSize().background(InkBg).statusBarsPadding().imePadding()) {
        AppTopBar(repo)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeScreen(
                    repo,
                    onOpenStory = { id ->
                        tab = 1
                        storyTab = "overview"
                        openStory = id
                    },
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
                        StoryPage(repo, id, storyTab, onTab = { storyTab = it }, onBack = { openStory = null })
                    }
                }
                2 -> ComingSoon("Write", "The writing screen arrives in Phase 2.")
                3 -> ComingSoon("Stats", "Charts and statistics arrive in Phase 3.")
                else -> MenuScreen(repo)
            }
        }
        if (!imeOpen) {
            BottomBar(tab) {
                if (it == 1) openStory = null
                tab = it
            }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Home", R.drawable.ic_home_outline, R.drawable.ic_home_solid),
        Triple("Stories", R.drawable.ic_stories_outline, R.drawable.ic_stories_solid),
        Triple("Write", R.drawable.ic_write_outline, R.drawable.ic_write_solid),
        Triple("Stats", R.drawable.ic_stats_outline, R.drawable.ic_stats_solid),
        Triple("Menu", R.drawable.ic_menu_outline, R.drawable.ic_menu_solid),
    )
    Column(Modifier.fillMaxWidth().background(InkCard)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(InkBorder))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items.forEachIndexed { i, (label, outline, solid) ->
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
                    VecIcon(if (on) solid else outline, color, 24.dp)
                    Txt(label, 11f, color, if (on) FontWeight.SemiBold else FontWeight.Normal, lh = 1.3f)
                }
            }
        }
    }
}
