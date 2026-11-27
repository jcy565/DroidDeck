package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageAssetDiscoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private val probe: (ByteArray, String) -> ImageAssetDiscovery.Size = { bytes, _ ->
        require(bytes.contentEquals(byteArrayOf(1, 2, 3)))
        ImageAssetDiscovery.Size(32, 16)
    }

    @Test fun discoversSupportedCandidatesAndRechecksRevision() {
        val root = temp.newFolder()
        val image = File(root, "menu.dds").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        File(root, "broken.tga").writeBytes(byteArrayOf(4))
        File(root, "dd-translated-result.png").writeBytes(byteArrayOf(1, 2, 3))
        File(root, "other.txt").writeBytes(byteArrayOf(1, 2, 3))
        val report = ImageAssetDiscovery.inspect(root, probe = probe)
        assertEquals(2, report.examined)
        assertEquals(1, report.rejected)
        val candidate = report.candidates.single()
        assertEquals("menu.dds", candidate.relative)
        assertEquals(image.canonicalFile, ImageAssetDiscovery.open(root, candidate))
        image.writeBytes(byteArrayOf(3, 2, 1))
        assertTrue(runCatching { ImageAssetDiscovery.open(root, candidate) }.isFailure)
        assertTrue(runCatching { ImageAssetDiscovery.open(root, candidate.copy(relative = "../menu.dds")) }.isFailure)
    }

    @Test fun respectsBudgetAndDoesNotFollowLinks() {
        val root = temp.newFolder()
        repeat(12) { File(root, "$it.png").writeBytes(byteArrayOf(1, 2, 3)) }
        val outside = temp.newFile("outside.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        Files.createSymbolicLink(File(root, "link.png").toPath(), outside.toPath())
        val report = ImageAssetDiscovery.inspect(root, probe = probe)
        assertTrue(report.partial)
        assertEquals(8, report.candidates.size)
        assertFalse(report.candidates.any { it.relative == "link.png" })
        assertTrue(runCatching { ImageAssetDiscovery.inspect(root, probe = probe) { throw java.util.concurrent.CancellationException() } }.isFailure)
    }
}
