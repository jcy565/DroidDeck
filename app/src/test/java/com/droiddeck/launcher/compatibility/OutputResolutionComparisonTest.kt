package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class OutputResolutionComparisonTest {
    @Test fun widePanel720pDiffersFromFixed720() {
        val result = requireNotNull(OutputResolutionComparison.calculate(2400 to 1080, "720p", "1280x720"))
        assertEquals(1600 to 720, result.current)
        assertEquals(1280 to 720, result.proposed)
        assertFalse(result.sameDimensions)
        assertFalse(result.currentFollowsWindow)
    }

    @Test fun standardPanelCanHaveSameSizeWithDifferentChoices() {
        val result = requireNotNull(OutputResolutionComparison.calculate(1920 to 1080, "720p", "1280x720"))
        assertTrue(result.sameDimensions)
    }

    @Test fun followUsesPanelReferenceAndRemainsDynamic() {
        val result = requireNotNull(OutputResolutionComparison.calculate(2400 to 1080, "follow/2", "1600x900"))
        assertEquals(1200 to 540, result.current)
        assertTrue(result.currentFollowsWindow)
        assertEquals(1600 to 900, result.proposed)
    }

    @Test fun malformedAndUnsupportedChoicesAreNotSilentlyResolved() {
        assertNull(OutputResolutionComparison.calculate(2400 to 1080, "unknown", "1280x720"))
        assertNull(OutputResolutionComparison.calculate(2400 to 1080, "999999999999x720", "1280x720"))
        assertNull(OutputResolutionComparison.calculate(0 to 1080, "720p", "1280x720"))
        assertNull(OutputResolutionComparison.calculate(2400 to 1080, "720p", "999x999"))
    }
}
