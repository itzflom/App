package com.lastreel.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.lastreel.app.LockActivity
import com.lastreel.app.data.GuardRepository
import com.lastreel.app.data.MonitoredApps
import com.lastreel.app.engine.Action
import com.lastreel.app.engine.Event
import com.lastreel.app.ui.formatClock

/**
 * Thin adapter between Android and the engine. It only learns two things from the system: which app
 * window is in front, and that something scrolled. It cannot read what is on screen
 * (`canRetrieveWindowContent` is false in the service config).
 *
 * Enforcement is [performGlobalAction] HOME, which always works. The break screen and the toast are
 * explanation on top: Android does not guarantee that an accessibility service may start an activity
 * from the background, so nothing important depends on them.
 */
class GuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: GuardRepository
    private var lastBlockAt = 0L

    private val timer = Runnable {
        act(repo.dispatch(Event.Tick))
        scheduleTimer()
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            repo.dispatch(Event.AppGone)
            handler.removeCallbacks(timer)
        }
    }

    override fun onServiceConnected() {
        repo = GuardRepository.get(this)
        ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        repo.dispatch(Event.ServiceStarted)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val monitored = MonitoredApps.isMonitored(pkg)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> when {
                monitored -> act(repo.dispatch(Event.AppVisible))
                isTransientWindow(pkg) -> Unit
                else -> repo.dispatch(Event.AppGone)
            }

            // Scrolls also re-assert "this app is in front", which self-heals the case where a
            // window event was missed (e.g. returning from the keyboard or the lock screen).
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (monitored) {
                val visible = repo.dispatch(Event.AppVisible)
                act(if (visible == Action.NONE) repo.dispatch(Event.Swipe) else visible)
            }
        }
        scheduleTimer()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    private fun scheduleTimer() {
        handler.removeCallbacks(timer)
        repo.nextWakeupMs()?.let { handler.postDelayed(timer, it.coerceAtLeast(MIN_TIMER_MS)) }
    }

    private fun act(action: Action) {
        when (action) {
            Action.NONE -> Unit
            Action.LAST_REEL_NOTICE -> toast("That's your limit. Finish this video, then you're out.")
            Action.KICK -> kick("Time's up. Back in ${remaining()}.")
            Action.BLOCK -> {
                val now = System.currentTimeMillis()
                if (now - lastBlockAt >= BLOCK_COOLDOWN_MS) {
                    lastBlockAt = now
                    kick("On a break. Back in ${remaining()}.")
                }
            }
        }
    }

    private fun kick(message: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        toast(message)
        // Give the home transition a moment, otherwise it can bury the break screen.
        handler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }, LOCK_SCREEN_DELAY_MS)
    }

    private fun remaining(): String =
        formatClock(repo.state.value.lockRemainingMs(System.currentTimeMillis()))

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
    }

    /** Windows that appear on top of an app without meaning the user left it. */
    private fun isTransientWindow(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private companion object {
        const val MIN_TIMER_MS = 50L
        const val BLOCK_COOLDOWN_MS = 1_500L
        const val LOCK_SCREEN_DELAY_MS = 250L
    }
}
