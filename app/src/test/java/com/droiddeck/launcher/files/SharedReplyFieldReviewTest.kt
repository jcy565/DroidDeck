package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class SharedReplyFieldReviewTest {
    @Test fun emptyOrIdenticalInputNeedsNoReplacementReview() {
        assertNull(SharedReplyFieldReview.create("request", "", "new"))
        assertNull(SharedReplyFieldReview.create("request", "  ", "new"))
        assertNull(SharedReplyFieldReview.create("request", "same", "same"))
    }

    @Test fun reviewedReplacementRequiresExactRequestFieldAndInbox() {
        val review = requireNotNull(SharedReplyFieldReview.create("request", "typed", "shared"))
        assertEquals("typed", review.previous)
        assertEquals("shared", review.incoming)
        assertTrue(review.matches("request", "typed", "shared"))
        assertFalse(review.matches("other", "typed", "shared"))
        assertFalse(review.matches(null, "typed", "shared"))
        assertFalse(review.matches("request", "edited", "shared"))
        assertFalse(review.matches("request", "typed", "new reply"))
        assertFalse(review.matches("request", "typed", null))
    }
}
