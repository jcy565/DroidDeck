package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameHistoryAdviceTest {
    @Test fun allOutcomesHaveAdvice() {
        GameRunHistory.Outcome.values().forEach {
            assertTrue(GameHistoryAdvice.recommend(it).all { advice -> advice.isNotBlank() })
            assertTrue(GameHistoryAdvice.recommend(it).isNotEmpty())
        }
    }

    @Test fun unknownDoesNotInventCause() {
        assertTrue(GameHistoryAdvice.recommend(GameRunHistory.Outcome.UNKNOWN).single().contains("판단할 수 없습니다"))
    }

    @Test fun blackScreenDistinguishesResolutions() {
        assertTrue(GameHistoryAdvice.recommend(GameRunHistory.Outcome.BLACK_SCREEN).any {
            it.contains("렌더 해상도") && it.contains("출력 해상도")
        })
    }
}
