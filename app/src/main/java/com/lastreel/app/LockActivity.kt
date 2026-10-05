package com.lastreel.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lastreel.app.data.GuardRepository
import com.lastreel.app.ui.LastReelTheme
import com.lastreel.app.ui.LockScreen

/** The break screen. Launched by [com.lastreel.app.service.GuardService] when it kicks or blocks you. */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The break screen is always dark, so force light system-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val repo = GuardRepository.get(this)

        setContent {
            LastReelTheme {
                LockScreen(repo = repo, onGoHome = ::goHome, onDone = ::finish)
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
