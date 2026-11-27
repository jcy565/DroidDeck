package com.droiddeck.launcher.compatibility

import android.content.Context
import com.droiddeck.launcher.stores.Store
import com.droiddeck.launcher.stores.StoreGameSidecar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class InstalledGameVersionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun recordsEachInstalledSteamBuildAndMissingMetadataAcrossReopen() = runBlocking {
        val game = temp.newFolder("steamapps", "common", "Example")
        val manifest = File(temp.root, "steamapps/appmanifest_42.acf")
        val app: Context = RuntimeEnvironment.getApplication()
        app.deleteDatabase("ai_game_run_history.db")
        val service = GameCompatibilityService(app)
        val id = "steam:42"
        service.recordInstalledOutcome(id, game, GameRunHistory.Outcome.UNKNOWN, 1L)
        for ((build, outcome) in listOf("82" to GameRunHistory.Outcome.CRASH, "83" to GameRunHistory.Outcome.PLAYABLE)) {
            manifest.writeText(""" "AppState" { "appid" "42" "installdir" "Example" "StateFlags" "4"
                "InstalledDepots" { "1" { "buildid" "999" } } "buildid" "$build" } """)
            service.recordInstalledOutcome(id, game, outcome, build.toLong())
        }
        GameRunHistory(app).use {
            val rows = it.recent(id)
            assertEquals(listOf("83", "82", null), rows.map { r -> r.gameVersion })
            assertEquals(listOf(GameRunHistory.Outcome.PLAYABLE, GameRunHistory.Outcome.CRASH,
                GameRunHistory.Outcome.UNKNOWN), rows.map { r -> r.outcome })
        }
    }

    @Test fun steamRejectsDifferentGameDirectoryIncompleteInstallAndTargetBuild() {
        val game = temp.newFolder("steamapps", "common", "Example")
        val manifest = File(temp.root, "steamapps/appmanifest_42.acf")
        for (fields in listOf(
            """"appid" "7" "installdir" "Example" "StateFlags" "4" "buildid" "88"""",
            """"appid" "42" "installdir" "Other" "StateFlags" "4" "buildid" "88"""",
            """"appid" "42" "installdir" "Example" "StateFlags" "2" "buildid" "88"""",
            """"appid" "42" "installdir" "Example" "StateFlags" "4" "TargetBuildID" "88"""",
        )) {
            manifest.writeText(""" "AppState" { $fields } """)
            assertNull(fields, InstalledGameVersion.read("steam:42", game))
        }
    }

    @Test fun storeVersionBelongsToTheInstalledRecordAndSelectedStore() {
        val game = temp.newFolder("store-game")
        val record = StoreGameSidecar(Store.GOG, "123", "Example", "game.exe", installVersion = "1.2")
        record.write(game)
        assertEquals("1.2", InstalledGameVersion.read("gog:123", game))
        assertNull(InstalledGameVersion.read("epic:123", game))
        assertNull(InstalledGameVersion.read("gog:124", game))
        record.copy(state = StoreGameSidecar.STATE_UPDATING).write(game)
        assertNull(InstalledGameVersion.read("gog:123", game))
        for (invalid in listOf("", " ", "v1\nv2", "a".repeat(129))) {
            record.copy(installVersion = invalid).write(game)
            assertNull(InstalledGameVersion.read("gog:123", game))
        }
    }
}
