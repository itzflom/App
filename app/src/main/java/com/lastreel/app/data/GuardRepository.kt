package com.lastreel.app.data

import android.content.Context
import android.content.SharedPreferences
import com.lastreel.app.engine.Action
import com.lastreel.app.engine.Event
import com.lastreel.app.engine.SessionState
import com.lastreel.app.engine.Settings
import com.lastreel.app.engine.nextWakeupMs
import com.lastreel.app.engine.reduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth shared by the accessibility service and the UI (they live in one process).
 * Wraps the pure engine with observable state and persistence, so a killed service resumes with the
 * same usage and lockout it had.
 */
class GuardRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("last_reel", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    private val _state = MutableStateFlow(loadState())

    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Synchronized
    fun dispatch(event: Event, now: Long = System.currentTimeMillis()): Action {
        val (next, action) = reduce(_state.value, event, now, _settings.value)
        if (next != _state.value) {
            _state.value = next
            saveState(next)
        }
        return action
    }

    @Synchronized
    fun nextWakeupMs(now: Long = System.currentTimeMillis()): Long? =
        nextWakeupMs(_state.value, now, _settings.value)

    /**
     * Settings are frozen during a break, otherwise the obvious cheat is to open this app and set
     * the break to 1 minute. Returns false if the change was refused.
     */
    @Synchronized
    fun updateSettings(now: Long = System.currentTimeMillis(), change: (Settings) -> Settings): Boolean {
        if (_state.value.isLocked(now)) return false
        val next = change(_settings.value)
        if (next != _settings.value) {
            _settings.value = next
            prefs.edit()
                .putInt(KEY_LIMIT, next.limitMinutes)
                .putInt(KEY_BREAK, next.breakMinutes)
                .putInt(KEY_GRACE, next.graceSeconds)
                .apply()
        }
        return true
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            limitMinutes = prefs.getInt(KEY_LIMIT, d.limitMinutes),
            breakMinutes = prefs.getInt(KEY_BREAK, d.breakMinutes),
            graceSeconds = prefs.getInt(KEY_GRACE, d.graceSeconds),
        )
    }

    private fun loadState() = SessionState(
        usedMs = prefs.getLong(KEY_USED, 0L),
        onScreenSince = prefs.getLong(KEY_ON_SCREEN, NONE).takeIf { it != NONE },
        leftAt = prefs.getLong(KEY_LEFT_AT, NONE).takeIf { it != NONE },
        finishingSince = prefs.getLong(KEY_FINISHING, NONE).takeIf { it != NONE },
        lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L),
    )

    private fun saveState(s: SessionState) {
        prefs.edit()
            .putLong(KEY_USED, s.usedMs)
            .putLong(KEY_ON_SCREEN, s.onScreenSince ?: NONE)
            .putLong(KEY_LEFT_AT, s.leftAt ?: NONE)
            .putLong(KEY_FINISHING, s.finishingSince ?: NONE)
            .putLong(KEY_LOCKED_UNTIL, s.lockedUntil)
            .apply()
    }

    companion object {
        private const val NONE = -1L
        private const val KEY_LIMIT = "limit_minutes"
        private const val KEY_BREAK = "break_minutes"
        private const val KEY_GRACE = "grace_seconds"
        private const val KEY_USED = "used_ms"
        private const val KEY_ON_SCREEN = "on_screen_since"
        private const val KEY_LEFT_AT = "left_at"
        private const val KEY_FINISHING = "finishing_since"
        private const val KEY_LOCKED_UNTIL = "locked_until"

        @Volatile
        private var instance: GuardRepository? = null

        fun get(context: Context): GuardRepository =
            instance ?: synchronized(this) {
                instance ?: GuardRepository(context).also { instance = it }
            }
    }
}
