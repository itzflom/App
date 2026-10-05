package com.lastreel.app.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastreel.app.data.GuardRepository
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    repo: GuardRepository,
    guardEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    val state by repo.state.collectAsState()
    val settings by repo.settings.collectAsState()
    val now by rememberNow()
    val locked = state.isLocked(now)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(guardEnabled)

            if (!guardEnabled) SetupCard(onOpenAccessibility, onOpenAppInfo)

            Card {
                val used = state.usedAt(now, settings)
                val progress = if (locked) {
                    state.lockRemainingMs(now).toFloat() / settings.breakMs
                } else {
                    used.toFloat() / settings.limitMs
                }
                val ringColor = if (locked) {
                    RingColors.Rest
                } else {
                    lerp(RingColors.Calm, RingColors.Warm, ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f))
                }

                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(
                        progress = progress,
                        color = ringColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.width(240.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatClock(if (locked) state.lockRemainingMs(now) else used),
                                style = MaterialTheme.typography.displayLarge,
                            )
                            Text(
                                text = if (locked) "on break" else "of ${formatClock(settings.limitMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = heroCaption(locked, state.lockedUntil, settings.limitMs - used),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Card {
                Text("Your rules", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                SettingSlider(
                    label = "Kick me after",
                    valueLabel = "${settings.limitMinutes} min",
                    value = settings.limitMinutes,
                    range = 1..60,
                    enabled = !locked,
                    caption = "Total time in Instagram and TikTok.",
                ) { v -> repo.updateSettings { it.copy(limitMinutes = v) } }
                SettingSlider(
                    label = "Break length",
                    valueLabel = "${settings.breakMinutes} min",
                    value = settings.breakMinutes,
                    range = 1..30,
                    enabled = !locked,
                    caption = "Both apps stay blocked for this long.",
                ) { v -> repo.updateSettings { it.copy(breakMinutes = v) } }
                SettingSlider(
                    label = "Finish-the-video window",
                    valueLabel = "${settings.graceSeconds} s",
                    value = settings.graceSeconds,
                    range = 30..180,
                    step = 15,
                    enabled = !locked,
                    caption = "After your limit you can finish the video you're on. " +
                        "You're kicked at your next swipe, or after this long if a video just loops.",
                ) { v -> repo.updateSettings { it.copy(graceSeconds = v) } }
                if (locked) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Rules are locked during a break.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                "Watches Instagram and TikTok. Never reads your screen. No internet access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

private fun heroCaption(locked: Boolean, lockedUntil: Long, remainingMs: Long): String = when {
    locked -> "Instagram and TikTok are back at " +
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lockedUntil)) + "."
    remainingMs <= 0L -> "That's your limit. Your last video is the one you're on."
    else -> "${formatClock(remainingMs)} until your last reel."
}

@Composable
private fun Header(guardEnabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Last Reel", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Finish the one you're on. Then take a break.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(guardEnabled)
    }
}

@Composable
private fun StatusPill(on: Boolean) {
    val dot = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SetupCard(onOpenAccessibility: () -> Unit, onOpenAppInfo: () -> Unit) {
    Card {
        Text("Turn it on", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Last Reel needs the Accessibility permission to know when Instagram or TikTok is open, " +
                "and to send you home. It can't read what's on your screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "1. Open Accessibility settings\n2. Pick Last Reel (under Installed apps)\n3. Switch it on",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
            Text("Open Accessibility settings", style = MaterialTheme.typography.labelLarge)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Switch greyed out? Android blocks this for apps installed from a file. " +
                    "Open App info, tap the ⋮ menu, choose Allow restricted settings, then try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenAppInfo, contentPadding = PaddingValues(0.dp)) { Text("Open App info") }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    caption: String,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        // Continuous slider that snaps by rounding: avoids drawing a tick mark for every step.
        Slider(
            value = value.toFloat(),
            onValueChange = { raw -> onChange(((raw / step).roundToInt() * step).coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}
