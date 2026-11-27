package com.droiddeck.launcher.compatibility

import android.content.Context
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class GameRunHistoryTest {
    private lateinit var context: Context
    private lateinit var history: GameRunHistory

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_game_run_history.db")
        history = GameRunHistory(context)
    }

    @After fun cleanup() {
        history.close()
        context.deleteDatabase("ai_game_run_history.db")
    }

    private fun observation(id: String, time: Long) =
        GameRunHistory.Observation(id, null, GameRunHistory.Outcome.BLACK_SCREEN, time)

    @Test fun isolatesGamesAndReturnsNewestFirst() {
        history.record(observation("steam:1", 1))
        history.record(observation("steam:2", 2))
        history.record(observation("steam:1", 3))
        assertEquals(listOf(3L, 1L), history.recent("steam:1").map { it.recordedAt })
        assertEquals(1, history.recent("steam:2").size)
        assertTrue(history.recent("missing").isEmpty())
    }

    @Test fun persistsVersionAndOutcomeAcrossReopen() {
        val entry = observation("steam:1", 1).copy(gameVersion = "1.2", outcome = GameRunHistory.Outcome.PLAYABLE)
        history.record(entry)
        history.close()
        history = GameRunHistory(context)
        assertEquals(listOf(entry), history.recent("steam:1"))
    }

    @Test fun versionFilterRunsBeforeLimitAndExcludesUnknown() {
        val old = observation("steam:1", 1).copy(gameVersion = "old")
        history.record(old)
        history.record(observation("steam:1", 2))
        repeat(25) { history.record(observation("steam:1", it.toLong() + 3).copy(gameVersion = "new")) }
        history.record(observation("steam:2", 99).copy(gameVersion = "old"))
        assertEquals(listOf(old), history.recent("steam:1", 1, "old"))
        assertTrue(history.recent("steam:1", gameVersion = "missing").isEmpty())
        assertEquals("new", history.recent("steam:1", 1).single().gameVersion)
    }

    @Test fun versionFilterTreatsSqlCharactersLiterally() {
        val version = "' OR 1=1 --"
        val entry = observation("steam:1", 1).copy(gameVersion = version)
        history.record(entry)
        history.record(observation("steam:1", 2).copy(gameVersion = "other"))
        assertEquals(listOf(entry), history.recent("steam:1", gameVersion = version))
    }

    @Test fun boundsStorageAndQuerySize() {
        history.record(observation("old", 0))
        repeat(500) { history.record(observation("new", it.toLong())) }
        assertTrue(history.recent("old").isEmpty())
        assertEquals(100, history.recent("new", 1000).size)
        assertEquals(499L, history.recent("new", 1).single().recordedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankGameId() {
        history.record(observation(" ", 1))
    }

    @Test fun treatsSqlCharactersAsLiteralId() {
        val id = "game' OR 1=1 --"
        history.record(observation(id, 1))
        history.record(observation("other", 2))
        assertEquals(listOf(observation(id, 1)), history.recent(id))
    }
}
