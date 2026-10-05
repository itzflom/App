package com.lastreel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lastreel.app.data.GuardRepository
import com.lastreel.app.service.isGuardEnabled
import com.lastreel.app.ui.HomeScreen
import com.lastreel.app.ui.LastReelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = GuardRepository.get(this)

        setContent {
            LastReelTheme {
                var guardEnabled by remember { mutableStateOf(isGuardEnabled(this)) }
                // The user flips the switch in system settings, then comes back here.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { guardEnabled = isGuardEnabled(this) }

                HomeScreen(
                    repo = repo,
                    guardEnabled = guardEnabled,
                    onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpenAppInfo = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                        )
                    },
                )
            }
        }
    }
}
