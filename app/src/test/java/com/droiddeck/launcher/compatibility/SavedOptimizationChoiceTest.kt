package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SavedOptimizationChoiceTest {
    @Test fun bookmarkIsGameScopedAndCanBeReplacedOrRemoved() {
        val context = RuntimeEnvironment.getApplication()
        val a = SavedOptimizationChoice.Choice("steam", "1280x720")
        val b = SavedOptimizationChoice.Choice("global", "false")
        SavedOptimizationChoice.save(context, "steam:1", a)
        assertEquals(a, SavedOptimizationChoice.load(context, "steam:1"))
        assertNull(SavedOptimizationChoice.load(context, "steam:2"))
        SavedOptimizationChoice.save(context, "steam:2", b)
        SavedOptimizationChoice.save(context, "steam:1", b)
        SavedOptimizationChoice.remove(context, "steam:1")
        assertNull(SavedOptimizationChoice.load(context, "steam:1"))
        assertEquals(b, SavedOptimizationChoice.load(context, "steam:2"))
    }
    @Test fun rejectsUnsupportedChoices() {
        assertTrue(runCatching { SavedOptimizationChoice.validate(SavedOptimizationChoice.Choice("global", "1280x720")) }.isFailure)
        assertTrue(runCatching { SavedOptimizationChoice.validate(SavedOptimizationChoice.Choice("steam", "1920x1080")) }.isFailure)
    }
}
