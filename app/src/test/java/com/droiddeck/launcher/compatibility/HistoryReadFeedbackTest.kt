package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class HistoryReadFeedbackTest {
    @Test fun temporaryDenialIsDistinctFromReadFailure() {
        assertTrue(HistoryReadFeedback.failure(false, true).contains("보류"))
        assertFalse(HistoryReadFeedback.failure(false, false).contains("보류"))
    }

    @Test fun successfulSaveRemainsAcknowledgedIfRefreshFails() {
        for (deferred in listOf(false, true)) {
            assertTrue(HistoryReadFeedback.failure(true, deferred).startsWith("기록은 저장되었습니다."))
            assertFalse(HistoryReadFeedback.failure(false, deferred).contains("저장되었습니다"))
        }
    }

    @Test fun everyFailureAllowsManualRetry() {
        for (saved in listOf(false, true)) for (deferred in listOf(false, true)) {
            assertTrue(HistoryReadFeedback.failure(saved, deferred).contains("다시 조회"))
        }
    }
}
