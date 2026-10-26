package com.mcfoster.inkar

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val InkarBlue = Color(0xFF2563EB)
private val InkarBg = Color(0xFF0B0D10)
private val InkarCard = Color(0xFF14181F)

private val InkarColors = darkColorScheme(
    primary = InkarBlue,
    background = InkarBg,
    surface = InkarBg,
    onBackground = Color(0xFFEDEFF3),
    onSurface = Color(0xFFEDEFF3),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = InkarColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = InkarBg) {
                    Phase0Screen()
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@androidx.compose.runtime.Composable
private fun Phase0Screen() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }
    var taps by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("INK") }
                withStyle(SpanStyle(color = InkarBlue)) { append("AR") }
            },
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Native build, phase 0",
            color = Color(0xFF9AA4B2),
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = InkarCard),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("It works", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This is the first native Kotlin build of Inkar. " +
                        "If you can read this and the app icon looks sharp, " +
                        "the build and install pipeline is working.",
                    color = Color(0xFFB4BCC8),
                )
                Spacer(Modifier.height(16.dp))
                Text("Version: $version", color = Color(0xFF9AA4B2))
                Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = Color(0xFF9AA4B2))
                Text("Device: ${Build.MANUFACTURER} ${Build.MODEL}", color = Color(0xFF9AA4B2))
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { taps++ },
            colors = ButtonDefaults.buttonColors(containerColor = InkarBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (taps == 0) "Tap test" else "Taps: $taps")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the button to check the press feel. There should be no blue flash.",
            color = Color(0xFF6B7380),
            fontSize = 13.sp,
        )
    }
}
