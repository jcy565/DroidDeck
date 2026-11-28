package com.droiddeck.launcher.session

import android.os.Environment
import com.droiddeck.launcher.frontend.AddedGames
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameStorageTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writableProbePreservesExistingFileAndCleansOnlyItsOwnProbe() {
        val root = tmp.newFolder("library")
        val steamapps = File(root, "steamapps").apply { mkdirs() }
        val existing = File(steamapps, ".writable").apply { writeText("owned by another app") }
        assertNull(GameStorage.prepare(app, root.path))
        assertEquals("owned by another app", existing.readText())
        assertEquals(listOf(".writable"), steamapps.list()!!.sorted())
    }

    @Test fun knownOfficialPackagePathsAreNotPreparedOrSelected() {
        val official = tmp.newFolder("com.droiddeck.launcher")
        val library = File(official, "steam").apply { mkdir() }
        assertTrue(GameStorage.touchesOfficialPackage(library))
        assertNotNull(GameStorage.prepare(app, library.path))
        assertFalse(File(library, "steamapps").exists())
        SessionPrefs.setGameStorage(app, library.path, "Official")
        assertNull(GameStorage.effective(app))
        val result = SteamLibraries.add(app, library.path)
        assertTrue(result is SteamLibraries.AddResult.Refused)
        assertFalse(File(library, "steamapps").exists())
        val ai = tmp.newFolder("com.droiddeck.launcher.ai")
        assertFalse(GameStorage.touchesOfficialPackage(ai))
        assertNull(GameStorage.prepare(app, ai.path))
    }

    @Test fun officialAliasAndLinkedSteamappsAreRefused() {
        val official = tmp.newFolder("com.droiddeck.shortcuts")
        val alias = File(tmp.root, "alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), official.toPath())
        assertTrue(GameStorage.touchesOfficialPackage(alias))
        assertNotNull(GameStorage.prepare(app, alias.path))
        assertFalse(File(official, "steamapps").exists())
        val root = tmp.newFolder("separate")
        val other = tmp.newFolder("other")
        java.nio.file.Files.createSymbolicLink(File(root, "steamapps").toPath(), other.toPath())
        assertNotNull(GameStorage.prepare(app, root.path))
        assertTrue(other.list()!!.isEmpty())
    }

    @Test fun onlyAGamesFolderSteamInstalledIntoStaysALibrary() {
        val pc = tmp.newFolder("PC")
        File(pc, "steamapps").mkdirs()
        File(pc, "steamapps/appmanifest_220.acf").writeText("\"AppState\" { \"appid\" \"220\" }")
        val plain = tmp.newFolder("Games")
        File(plain, "steamapps/common").mkdirs()
        val missing = File(tmp.root, "Unplugged")
        SessionPrefs.setAddedGamesDirs(app, listOf(pc.path, plain.path, missing.path))
        val libraries = GameStorage.gamesFolderLibraries(app)
        assertEquals(listOf(pc), libraries.map { it.host })
        assertEquals(listOf(AddedGames.GUEST_DIR + "/PC"), libraries.map { it.guest })
        assertEquals(listOf("PC"), libraries.map { it.label })
    }

    @Test fun aChosenLibraryOrInternalKeepsGamesFoldersPlain() {
        val pc = tmp.newFolder("PC")
        File(pc, "steamapps").mkdirs()
        File(pc, "steamapps/appmanifest_220.acf").writeText("x")
        SessionPrefs.setAddedGamesDirs(app, listOf(pc.path))
        SessionPrefs.setGameStorage(app, SessionPrefs.GAME_STORAGE_OFF, "")
        assertTrue(GameStorage.gamesFolderLibraries(app).isEmpty())
        SessionPrefs.setGameStorage(app, tmp.newFolder("chosen").path, "Chosen")
        assertTrue(GameStorage.gamesFolderLibraries(app).isEmpty())
    }

    @Test fun namesTheClientCannotStoreAreLeftOut() {
        val odd = listOf(tmp.newFolder("Quote\"d"), tmp.newFolder("Back\\slash"))
        odd.forEach { File(it, "steamapps").mkdirs(); File(it, "steamapps/appmanifest_220.acf").writeText("x") }
        SessionPrefs.setAddedGamesDirs(app, odd.map { it.path })
        assertTrue(GameStorage.gamesFolderLibraries(app).isEmpty())
    }

    @Test fun anUnpluggedDriveFallsBackToInternalWithoutForgettingIt() {
        val gone = "/storage/0000-0000/Android/data/com.droiddeck.launcher/files/steam"
        SessionPrefs.setGameStorage(app, gone, "USB drive")
        assertNull(GameStorage.effective(app))
        assertNull(GameStorage.x64CardLibrary(app))
        assertEquals(gone, SessionPrefs.gameStorage(app))
    }

    @Test fun aPluggedInDriveStaysTheLibrary() {
        val files = tmp.newFolder("files")
        val steam = File(files, "steam")
        SessionPrefs.setGameStorage(app, steam.path, "USB drive")
        assertEquals(steam.path, GameStorage.effective(app)?.path)
        steam.mkdirs()
        assertEquals(steam.path, GameStorage.effective(app)?.path)
        assertEquals("USB drive", GameStorage.effective(app)?.label)
    }

    @Test fun missingSubfolderIsAttachedWhenItsVolumeIsMounted() {
        val path = File(tmp.newFolder("mounted-volume"), "missing/steam")
        assertTrue(GameStorage.attached(path, Environment.MEDIA_MOUNTED, externalStoragePath = true))
    }

    @Test fun removedVolumeIsNotAttachedEvenWhenItsParentExists() {
        val parent = tmp.newFolder("storage")
        val volume = File(parent, "0000-0000")
        assertTrue(parent.isDirectory)
        assertFalse(GameStorage.attached(volume, Environment.MEDIA_REMOVED, externalStoragePath = true))
        assertFalse(GameStorage.attached(volume, Environment.MEDIA_UNKNOWN, externalStoragePath = true))
    }

    @Test fun aRawVolumePathIsCheckedAsItsStorageVolume() {
        val asked = mutableListOf<String>()
        val mounted = { file: File -> asked += file.path; if (file.path.startsWith("/storage/B2BD-BCFC")) Environment.MEDIA_MOUNTED else Environment.MEDIA_UNKNOWN }
        assertTrue(GameStorage.attached(File("/mnt/media_rw/B2BD-BCFC/STEAM GAMES"), mounted))
        assertEquals("/storage/B2BD-BCFC/STEAM GAMES", asked.single())
        assertTrue(GameStorage.attached(File("/storage/B2BD-BCFC/STEAM GAMES"), mounted))
    }

    @Test fun aRawVolumePathOnARemovedCardIsNotAttached() {
        assertFalse(GameStorage.attached(File("/mnt/media_rw/B2BD-BCFC/STEAM GAMES")) { Environment.MEDIA_REMOVED })
        assertFalse(GameStorage.attached(File("/mnt/media_rw/B2BD-BCFC/STEAM GAMES")) { Environment.MEDIA_UNKNOWN })
        assertFalse(GameStorage.attached(File("/mnt/media_rw/B2BD-BCFC/STEAM GAMES")) { null })
    }

    @Test fun aSavedFolderThatIsThereIsAttachedWhateverItsVolumeReports() {
        val folder = tmp.newFolder("card", "STEAM GAMES")
        listOf(Environment.MEDIA_UNKNOWN, Environment.MEDIA_UNMOUNTED, Environment.MEDIA_REMOVED, null).forEach {
            assertTrue(GameStorage.attached(folder, it, externalStoragePath = true))
        }
        assertTrue(GameStorage.attached(folder) { Environment.MEDIA_UNKNOWN })
    }

    @Test fun onlyASavedDriveThatIsMissingCountsAsDisconnected() {
        SessionPrefs.setGameStorage(app, "", "")
        assertFalse(GameStorage.disconnected(app))
        SessionPrefs.setGameStorage(app, SessionPrefs.GAME_STORAGE_OFF, "")
        assertFalse(GameStorage.disconnected(app))
        SessionPrefs.setGameStorage(app, "/storage/0000-0000/Android/data/com.droiddeck.launcher/files/steam", "USB drive")
        assertTrue(GameStorage.disconnected(app))
        SessionPrefs.setGameStorage(app, tmp.newFolder("plugged").path, "USB drive")
        assertFalse(GameStorage.disconnected(app))
    }

    @Test fun onlyAVfatMountCountsAsFat32() {
        val mounts = """
            /dev/fuse /storage/emulated fuse rw 0 0
            /dev/block/vold/public:179,65 /mnt/media_rw/8C5A-1F23 vfat rw 0 0
            /dev/block/vold/public:8,1 /mnt/media_rw/1111-2222 exfat rw 0 0
            /dev/fuse /storage/8C5A-1F23 fuse rw 0 0
        """.trimIndent()
        assertTrue(GameStorage.isFat32("/storage/8C5A-1F23/Android/data/com.droiddeck.launcher/files/steam", mounts))
        assertTrue(GameStorage.isFat32("/mnt/media_rw/8C5A-1F23/Games", mounts))
        assertFalse(GameStorage.isFat32("/storage/1111-2222/Android/data/com.droiddeck.launcher/files/steam", mounts))
        assertFalse(GameStorage.isFat32("/storage/emulated/0/Android/data/com.droiddeck.launcher/files/steam", mounts))
        assertFalse(GameStorage.isFat32("/storage/8C5A-1F230/Games", mounts))
    }
}
