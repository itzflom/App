package com.lastreel.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val SEC = 1_000L

/** Tiny harness: a fake clock plus the engine's current state. */
private class Sim(var settings: Settings = Settings(limitMinutes = 10, breakMinutes = 5, graceSeconds = 90)) {
    var now = 1_000_000L
    var state = SessionState()

    fun send(event: Event): Action {
        val (next, action) = reduce(state, event, now, settings)
        state = next
        return action
    }

    fun advance(ms: Long) {
        now += ms
    }

    /** Enter the app and use it for [ms], with a Tick at the end like the real timer would send. */
    fun watch(ms: Long): Action {
        send(Event.AppVisible)
        advance(ms)
        return send(Event.Tick)
    }
}

class LimitEngineTest {

    @Test
    fun `no action while under the limit`() {
        val sim = Sim()
        assertEquals(Action.NONE, sim.watch(9 * MIN))
        assertNull(sim.state.finishingSince)
        assertFalse(sim.state.isLocked(sim.now))
    }

    @Test
    fun `hitting the limit starts the last video but does not kick`() {
        val sim = Sim()
        assertEquals(Action.LAST_REEL_NOTICE, sim.watch(10 * MIN))
        assertEquals(sim.now, sim.state.finishingSince)
        assertFalse(sim.state.isLocked(sim.now))
    }

    @Test
    fun `the video you are on is allowed to finish - no kick without a swipe`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(30 * SEC)
        assertEquals(Action.NONE, sim.send(Event.Tick))
        assertFalse(sim.state.isLocked(sim.now))
    }

    @Test
    fun `first swipe after the limit kicks and starts the break`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(20 * SEC)
        assertEquals(Action.KICK, sim.send(Event.Swipe))
        assertTrue(sim.state.isLocked(sim.now))
        assertEquals(5 * MIN, sim.state.lockRemainingMs(sim.now))
    }

    @Test
    fun `a swipe right as the limit is hit does not kick - the new video gets to play`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(300)
        assertEquals(Action.NONE, sim.send(Event.Swipe))
        assertFalse(sim.state.isLocked(sim.now))
        sim.advance(SETTLE_MS)
        assertEquals(Action.KICK, sim.send(Event.Swipe))
    }

    @Test
    fun `swiping before the limit never kicks`() {
        val sim = Sim()
        sim.send(Event.AppVisible)
        repeat(30) {
            sim.advance(10 * SEC)
            assertEquals(Action.NONE, sim.send(Event.Swipe))
        }
        assertFalse(sim.state.isLocked(sim.now))
    }

    @Test
    fun `grace timeout kicks you even if you never swipe, e g on a looping video`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(89 * SEC)
        assertEquals(Action.NONE, sim.send(Event.Tick))
        sim.advance(1 * SEC)
        assertEquals(Action.KICK, sim.send(Event.Tick))
        assertTrue(sim.state.isLocked(sim.now))
    }

    @Test
    fun `leaving the app after the limit still starts the break`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(5 * SEC)
        assertEquals(Action.NONE, sim.send(Event.AppGone))
        assertTrue(sim.state.isLocked(sim.now))
        assertEquals(Action.BLOCK, sim.send(Event.AppVisible))
    }

    @Test
    fun `leaving at the exact moment the limit is crossed also starts the break`() {
        val sim = Sim()
        sim.send(Event.AppVisible)
        sim.advance(10 * MIN) // no Tick delivered yet
        assertEquals(Action.NONE, sim.send(Event.AppGone))
        assertTrue(sim.state.isLocked(sim.now))
    }

    @Test
    fun `monitored apps are blocked for the whole break and free after it`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(2 * SEC)
        sim.send(Event.Swipe)
        assertTrue(sim.state.isLocked(sim.now))

        sim.advance(4 * MIN + 59 * SEC)
        assertEquals(Action.BLOCK, sim.send(Event.AppVisible))
        assertEquals(Action.BLOCK, sim.send(Event.Swipe))

        sim.advance(2 * SEC)
        assertFalse(sim.state.isLocked(sim.now))
        assertEquals(Action.NONE, sim.send(Event.AppVisible))
    }

    @Test
    fun `blocked attempts do not touch usage`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(2 * SEC)
        sim.send(Event.Swipe)
        val before = sim.state
        sim.advance(MIN)
        sim.send(Event.AppVisible)
        assertEquals(before, sim.state)
    }

    @Test
    fun `after a break you get a fresh full allowance`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(2 * SEC)
        sim.send(Event.Swipe)
        sim.advance(5 * MIN + SEC)

        assertEquals(Action.NONE, sim.watch(9 * MIN))
        assertEquals(Action.LAST_REEL_NOTICE, sim.watch(1 * MIN))
    }

    @Test
    fun `hopping out for a few seconds does not reset the clock`() {
        val sim = Sim()
        sim.watch(6 * MIN)
        sim.send(Event.AppGone)
        sim.advance(30 * SEC)
        assertEquals(6 * MIN, sim.state.usedAt(sim.now, sim.settings))

        sim.send(Event.AppVisible)
        sim.advance(4 * MIN - 30 * SEC)
        // 6:00 before + 3:30 now = 9:30, still under the limit
        assertEquals(Action.NONE, sim.send(Event.Tick))
        sim.advance(30 * SEC)
        assertEquals(Action.LAST_REEL_NOTICE, sim.send(Event.Tick))
    }

    @Test
    fun `being away for a full break resets usage without ever hitting the limit`() {
        val sim = Sim()
        sim.watch(8 * MIN)
        sim.send(Event.AppGone)
        sim.advance(5 * MIN)
        assertEquals(0L, sim.state.usedAt(sim.now, sim.settings))

        assertEquals(Action.NONE, sim.watch(9 * MIN))
    }

    @Test
    fun `usage accumulates across separate visits`() {
        val sim = Sim()
        sim.watch(4 * MIN)
        sim.send(Event.AppGone)
        sim.advance(MIN)
        sim.watch(4 * MIN)
        sim.send(Event.AppGone)
        sim.advance(MIN)
        // 4:00 + 4:00 so far; this visit brings it to 9:59, one second short of the limit
        assertEquals(Action.NONE, sim.watch(2 * MIN - SEC))
        sim.advance(SEC)
        assertEquals(Action.LAST_REEL_NOTICE, sim.send(Event.Tick))
    }

    @Test
    fun `repeated AppVisible events are idempotent`() {
        val sim = Sim()
        sim.send(Event.AppVisible)
        val first = sim.state
        sim.advance(5 * SEC)
        sim.send(Event.AppVisible)
        assertEquals(first, sim.state)
    }

    @Test
    fun `AppGone without being in an app changes nothing`() {
        val sim = Sim()
        assertEquals(Action.NONE, sim.send(Event.AppGone))
        assertEquals(SessionState(), sim.state)
    }

    @Test
    fun `service restart drops the open interval instead of counting dead time`() {
        val sim = Sim()
        sim.send(Event.AppVisible)
        sim.advance(30 * MIN) // service was dead for this stretch
        assertEquals(Action.NONE, sim.send(Event.ServiceStarted))
        assertNull(sim.state.onScreenSince)
        assertEquals(0L, sim.state.usedMs)
        assertFalse(sim.state.isLocked(sim.now))
    }

    @Test
    fun `service restart while on the last video counts as being kicked`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(5 * SEC)
        sim.send(Event.ServiceStarted)
        assertTrue(sim.state.isLocked(sim.now))
    }

    @Test
    fun `settings changes apply to the running session`() {
        val sim = Sim()
        sim.send(Event.AppVisible)
        sim.advance(3 * MIN)
        sim.settings = sim.settings.copy(limitMinutes = 3)
        assertEquals(Action.LAST_REEL_NOTICE, sim.send(Event.Tick))
    }

    @Test
    fun `break length comes from settings`() {
        val sim = Sim(Settings(limitMinutes = 1, breakMinutes = 2, graceSeconds = 30))
        sim.watch(1 * MIN)
        sim.advance(2 * SEC)
        sim.send(Event.Swipe)
        assertEquals(2 * MIN, sim.state.lockRemainingMs(sim.now))
    }

    @Test
    fun `wakeup is the time to the limit, then the time to the grace deadline`() {
        val sim = Sim()
        assertNull(nextWakeupMs(sim.state, sim.now, sim.settings))

        sim.send(Event.AppVisible)
        sim.advance(4 * MIN)
        assertEquals(6 * MIN, nextWakeupMs(sim.state, sim.now, sim.settings))

        sim.advance(6 * MIN)
        sim.send(Event.Tick)
        assertEquals(90 * SEC, nextWakeupMs(sim.state, sim.now, sim.settings))

        sim.advance(40 * SEC)
        assertEquals(50 * SEC, nextWakeupMs(sim.state, sim.now, sim.settings))
    }

    @Test
    fun `no wakeup needed while locked or away`() {
        val sim = Sim()
        sim.watch(10 * MIN)
        sim.advance(2 * SEC)
        sim.send(Event.Swipe)
        assertNull(nextWakeupMs(sim.state, sim.now, sim.settings))
    }
}
