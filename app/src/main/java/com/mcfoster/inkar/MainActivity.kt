package com.mcfoster.inkar

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        val repo = Repo.get(applicationContext)
        setContent {
            MaterialTheme(colorScheme = InkarColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = InkBg) {
                    InkarApp(repo)
                }
            }
        }
    }
}

private sealed interface Screen {
    data class Detail(val id: String) : Screen
    data class Form(val id: String?) : Screen
}

@Composable
private fun InkarApp(repo: Repo) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val stack = remember { mutableStateListOf<Screen>() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.isNotEmpty() || tab != 0) {
        if (stack.isNotEmpty()) pop() else tab = 0
    }

    Column(Modifier.fillMaxSize().background(InkBg).statusBarsPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val top = stack.lastOrNull()) {
                null -> when (tab) {
                    0 -> HomeScreen(
                        repo,
                        onStories = { tab = 1 },
                        onNewStory = { tab = 1; stack.add(Screen.Form(null)) },
                    )
                    1 -> StoriesScreen(
                        repo,
                        onOpen = { stack.add(Screen.Detail(it)) },
                        onNew = { stack.add(Screen.Form(null)) },
                    )
                    2 -> ComingSoon("Write", "The writing screen arrives in Phase 2.")
                    3 -> ComingSoon("Stats", "Charts and statistics arrive in Phase 3.")
                    else -> MenuScreen(repo)
                }
                is Screen.Detail -> StoryDetailScreen(
                    repo,
                    top.id,
                    onBack = { pop() },
                    onEdit = { stack.add(Screen.Form(top.id)) },
                )
                is Screen.Form -> StoryFormScreen(
                    repo,
                    top.id,
                    onBack = { pop() },
                    onSaved = { savedId ->
                        pop()
                        if (top.id == null) stack.add(Screen.Detail(savedId))
                    },
                )
            }
        }
        if (stack.isEmpty() && !imeOpen) {
            BottomBar(tab) { tab = it }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Home" to Ic.Home,
        "Stories" to Ic.Book,
        "Write" to Ic.Pen,
        "Stats" to Ic.Bars,
        "Menu" to Ic.Gear,
    )
    Row(Modifier.fillMaxWidth().background(InkNav).navigationBarsPadding().height(64.dp)) {
        items.forEachIndexed { i, (label, ic) ->
            val on = i == selected
            val color = if (on) InkBlue else InkDim
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSelect(i) },
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InkIcon(ic, color, 24.dp, filled = on)
                Spacer(Modifier.height(3.dp))
                Text(label, color = color, fontSize = 11.sp)
            }
        }
    }
}
