package com.kissa.app.scene

import com.kissa.app.model.SceneEvent
import org.junit.Assert.*
import org.junit.Test

class ShotDirectorTest {
    private fun run(d: ShotDirector, from: Float, to: Float, speaker: String?, thinking: Boolean = false): Float {
        var t = from
        while (t < to) { d.update(t, speaker, thinking); t += 1f / 30f }
        return t
    }

    @Test fun introPlaysEstablishingThenPlayerMediumThenCharacterCloseUp() {
        val d = ShotDirector("riya")
        d.intro(0f)
        assertEquals(ShotType.ESTABLISHING, d.current.type)
        run(d, 0f, 2.5f, null)
        assertEquals(ShotType.MEDIUM, d.current.type); assertEquals("player", d.current.subject)
        run(d, 2.5f, 4.8f, null)
        assertEquals(ShotType.CLOSE_UP, d.current.type); assertEquals("riya", d.current.subject)
    }

    @Test fun cutsToWhoeverSpeaksWithShotReverseShot() {
        val d = ShotDirector("riya"); d.reset(0f)
        run(d, 0f, 1f, "riya")
        assertEquals("riya", d.current.subject)
        assertTrue(d.current.type == ShotType.CLOSE_UP || d.current.type == ShotType.OTS)
        run(d, 1f, 2f, null)
        run(d, 2f, 3f, "player")
        assertEquals("the player must be on screen when they talk", "player", d.current.subject)
        assertEquals("riya", d.current.other)
        // over several lines both clean close-ups and over-the-shoulder angles are used
        val types = mutableSetOf<ShotType>()
        var t = 3f
        repeat(6) { i -> t = run(d, t, t + 1.5f, if (i % 2 == 0) "riya" else "player"); types += d.current.type; t = run(d, t, t + 0.5f, null) }
        assertTrue(types.containsAll(listOf(ShotType.CLOSE_UP, ShotType.OTS)))
    }

    @Test fun longLinesGetOneReactionInsertThenReturnToTheSpeaker() {
        val d = ShotDirector("riya"); d.reset(0f)
        var t = run(d, 0f, ShotDirector.INSERT_AFTER + 0.3f, "riya")
        assertEquals(ShotType.CLOSE_UP, d.current.type)
        assertEquals("reaction shot of the listener", "player", d.current.subject)
        t = run(d, t, t + ShotDirector.INSERT_LEN + 0.2f, "riya")
        assertEquals("riya", d.current.subject)
        val cuts = d.cutCount
        run(d, t, t + 10f, "riya")
        assertEquals("only one insert per line", cuts, d.cutCount)
    }

    @Test fun thinkingHoldsOnTheCharacter() {
        val d = ShotDirector("riya"); d.reset(0f)
        run(d, 0f, 1f, "player")
        run(d, 1f, 3f, null, thinking = true)
        assertEquals("riya", d.current.subject); assertEquals(ShotType.CLOSE_UP, d.current.type)
    }

    @Test fun exitShowsTheWalkAwayThenThePlayerAlone() {
        val d = ShotDirector("riya"); d.reset(0f)
        d.onEvent(SceneEvent.CHARACTER_EXITS, 1f)
        assertEquals(ShotType.EXIT_WALK, d.current.type)
        run(d, 1f, 3f, "player")
        assertEquals("walk-away is held", ShotType.EXIT_WALK, d.current.type)
        run(d, 5f, 6f, null)
        assertEquals("player", d.current.subject)
        d.onEvent(SceneEvent.CHARACTER_ENTERS, 7f)
        assertEquals(ShotType.MEDIUM, d.current.type); assertEquals("riya", d.current.subject)
    }

    @Test fun idleWaitingWidensOccasionallyButNotJittery() {
        val d = ShotDirector("riya"); d.reset(0f)
        run(d, 0f, ShotDirector.IDLE_HOLD - 1f, null)
        assertEquals(1, d.cutCount)
        run(d, ShotDirector.IDLE_HOLD - 1f, ShotDirector.IDLE_HOLD + 1f, null)
        assertEquals(ShotType.TWO_SHOT, d.current.type)
        assertTrue(d.cutCount <= 3)
    }

    @Test fun lockedShotIgnoresUpdates() {
        val d = ShotDirector("riya")
        d.locked = Shot(ShotType.OTS, "player", "riya", 0f)
        run(d, 0f, 3f, "riya")
        assertEquals(ShotType.OTS, d.current.type); assertEquals("player", d.current.subject)
    }
}
