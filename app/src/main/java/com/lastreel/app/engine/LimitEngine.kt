package com.lastreel.app.engine

/**
 * The whole "when do I kick you out" brain. Pure Kotlin, no Android types, so it is unit-testable
 * with a fake clock. The accessibility service only translates system events into [Event]s and
 * performs the returned [Action]s.
 *
 * Lifecycle of one cycle:
 *   WATCHING  -> usage reaches the limit ->
 *   FINISHING -> the current video is allowed to play out; the first swipe (or the grace timeout,
 *                whichever comes first) ->
 *   LOCKED    -> kicked out; monitored apps are blocked until [SessionState.lockedUntil].
 */

/** User-tunable knobs. Everything is stored in whole minutes/seconds to keep the UI simple. */
data class Settings(
    val limitMinutes: Int = 10,
    val breakMinutes: Int = 5,
    val graceSeconds: Int = 90,
) {
    val limitMs: Long get() = limitMinutes * 60_000L
    val breakMs: Long get() = breakMinutes * 60_000L
    val graceMs: Long get() = graceSeconds * 1_000L
}

/**
 * Everything the engine needs to remember. All timestamps are wall-clock epoch millis.
 *
 * @property usedMs time spent in monitored apps this session, *excluding* the currently open
 *   interval (see [onScreenSince]).
 * @property onScreenSince when the current uninterrupted stay in a monitored app began; null if the
 *   user is not in one.
 * @property leftAt when the user last left the monitored apps; used to decide whether a long enough
 *   break has passed to start a fresh session.
 * @property finishingSince set once the limit is hit: the user is now "on their last video".
 * @property lockedUntil end of the current lockout (0 or in the past = not locked).
 */
data class SessionState(
    val usedMs: Long = 0L,
    val onScreenSince: Long? = null,
    val leftAt: Long? = null,
    val finishingSince: Long? = null,
    val lockedUntil: Long = 0L,
) {
    fun isLocked(now: Long): Boolean = now < lockedUntil

    fun lockRemainingMs(now: Long): Long = (lockedUntil - now).coerceAtLeast(0L)

    /**
     * Usage as of [now]. If the user has been away from the apps for at least a full break, the
     * session counts as fresh (0) even though we haven't been told yet.
     */
    fun usedAt(now: Long, settings: Settings): Long {
        val away = leftAt
        if (onScreenSince == null && away != null && now - away >= settings.breakMs) return 0L
        return usedMs + (onScreenSince?.let { now - it } ?: 0L)
    }
}

sealed interface Event {
    /** A monitored app is in front. Safe to send repeatedly. */
    data object AppVisible : Event

    /** The user left the monitored apps, or the screen turned off. */
    data object AppGone : Event

    /** The user scrolled / swiped inside a monitored app: our best signal for "next video". */
    data object Swipe : Event

    /** Timer fired; re-evaluate limit and grace deadlines. */
    data object Tick : Event

    /** The service (re)started. Time we weren't watching can't be attributed to anything. */
    data object ServiceStarted : Event
}

enum class Action {
    NONE,

    /** Limit just hit: tell the user this is their last video. */
    LAST_REEL_NOTICE,

    /** Throw the user out of the app now, then show the break screen. */
    KICK,

    /** The user tried to open a monitored app during a lockout: bounce them again. */
    BLOCK,
}

/** Swipes this soon after the limit is hit are ignored so the video you just landed on can play. */
const val SETTLE_MS = 1_000L

fun reduce(state: SessionState, event: Event, now: Long, settings: Settings): Pair<SessionState, Action> =
    when (event) {
        Event.AppVisible -> onAppVisible(state, now, settings)
        Event.AppGone -> onAppGone(state, now, settings)
        Event.Swipe -> onSwipe(state, now, settings)
        Event.Tick -> if (state.isLocked(now)) state to Action.NONE else evaluateLimit(state, now, settings)
        Event.ServiceStarted -> onServiceStarted(state, now, settings)
    }

/** Milliseconds until the engine next needs a [Event.Tick], or null if no timer is needed. */
fun nextWakeupMs(state: SessionState, now: Long, settings: Settings): Long? {
    if (state.onScreenSince == null || state.isLocked(now)) return null
    val finishing = state.finishingSince
    val remaining = if (finishing == null) {
        settings.limitMs - state.usedAt(now, settings)
    } else {
        finishing + settings.graceMs - now
    }
    return remaining.coerceAtLeast(0L)
}

private fun onAppVisible(state: SessionState, now: Long, settings: Settings): Pair<SessionState, Action> {
    if (state.isLocked(now)) return state to Action.BLOCK
    var s = state
    if (s.onScreenSince == null) {
        val away = s.leftAt
        // A full break since the last visit means the clock starts fresh.
        if (away != null && now - away >= settings.breakMs) {
            s = s.copy(usedMs = 0L, finishingSince = null)
        }
        s = s.copy(onScreenSince = now, leftAt = null)
    }
    return evaluateLimit(s, now, settings)
}

private fun onAppGone(state: SessionState, now: Long, settings: Settings): Pair<SessionState, Action> {
    val since = state.onScreenSince ?: return state to Action.NONE
    val used = state.usedMs + (now - since)
    val s = state.copy(usedMs = used, onScreenSince = null, leftAt = now)
    // Leaving after the limit counts as being kicked: you don't get to dodge the break by bailing.
    return if (state.finishingSince != null || used >= settings.limitMs) {
        lock(now, settings) to Action.NONE
    } else {
        s to Action.NONE
    }
}

private fun onSwipe(state: SessionState, now: Long, settings: Settings): Pair<SessionState, Action> {
    if (state.isLocked(now)) return state to Action.BLOCK
    val (s, action) = evaluateLimit(state, now, settings)
    if (action != Action.NONE) return s to action
    val finishing = s.finishingSince ?: return s to Action.NONE
    if (now - finishing >= SETTLE_MS) return lock(now, settings) to Action.KICK
    return s to Action.NONE
}

private fun onServiceStarted(state: SessionState, now: Long, settings: Settings): Pair<SessionState, Action> {
    if (state.onScreenSince == null) return state to Action.NONE
    // We can't know how much of the gap was real use, so drop the open interval without counting it.
    return if (state.finishingSince != null) {
        lock(now, settings) to Action.NONE
    } else {
        state.copy(onScreenSince = null, leftAt = now) to Action.NONE
    }
}

/** Checks the two deadlines: the usage limit, then (once finishing) the grace timeout. */
private fun evaluateLimit(state: SessionState, now: Long, settings: Settings): Pair<SessionState, Action> {
    if (state.onScreenSince == null) return state to Action.NONE
    val finishing = state.finishingSince
    if (finishing == null) {
        return if (state.usedAt(now, settings) >= settings.limitMs) {
            state.copy(finishingSince = now) to Action.LAST_REEL_NOTICE
        } else {
            state to Action.NONE
        }
    }
    return if (now - finishing >= settings.graceMs) lock(now, settings) to Action.KICK else state to Action.NONE
}

private fun lock(now: Long, settings: Settings): SessionState =
    SessionState(usedMs = 0L, onScreenSince = null, leftAt = now, finishingSince = null, lockedUntil = now + settings.breakMs)
