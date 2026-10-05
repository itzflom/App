package com.lastreel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastreel.app.data.GuardRepository
import java.text.DateFormat
import java.util.Date

private val Ink = Color(0xFFEDEEF5)
private val InkSoft = Color(0xFFA3A7B8)

private val suggestions = listOf(
    "Drink some water.",
    "Look out of a window for a minute.",
    "Stretch your neck and shoulders.",
    "Take five slow breaths.",
    "Stand up and walk around.",
    "Text a friend something real.",
    "Tidy one small thing.",
    "Step outside for some fresh air.",
)

/** Shown when you're kicked, or when you try to open a blocked app during the break. Always dark. */
@Composable
fun LockScreen(repo: GuardRepository, onGoHome: () -> Unit, onDone: () -> Unit) {
    val state by repo.state.collectAsState()
    val settings by repo.settings.collectAsState()
    val now by rememberNow(500L)
    val locked = state.isLocked(now)

    // The break is over: nothing left to show.
    LaunchedEffect(locked) { if (!locked) onDone() }

    val remaining = state.lockRemainingMs(now)
    val suggestion = suggestions[((state.lockedUntil / 60_000L) % suggestions.size).toInt()]
    val backAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lockedUntil))

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0E13), Color(0xFF0F1E1F)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Take a breather", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Spacer(Modifier.height(32.dp))

            ProgressRing(
                progress = remaining.toFloat() / settings.breakMs,
                color = RingColors.Rest,
                trackColor = Color(0xFF1E2A2B),
                modifier = Modifier.width(260.dp),
                strokeWidth = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatClock(remaining),
                        style = MaterialTheme.typography.displayLarge,
                        color = Ink,
                    )
                    Text("until you're back", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                suggestion,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Instagram and TikTok open again at $backAt.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RingColors.Rest, contentColor = Color(0xFF05201A)),
            ) {
                Text("Go home", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
