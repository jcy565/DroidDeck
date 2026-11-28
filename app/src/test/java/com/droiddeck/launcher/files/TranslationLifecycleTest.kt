package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercise the production draft, parser, ZIP importer and patch store together. */
class TranslationLifecycleTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun reviewedTranslationsCanRoundTripWithoutChangingUnselectedFiles() {
        val sources = mapOf(
            "csv" to "id,text\r\na,Hello {name}\r\nb,Bye\r\n",
            "tsv" to "id\ttext\r\na\tHello {name}\r\nb\tBye\r\n",
            "json" to "{\n  \"a\": \"Hello {name}\",\n  \"b\": \"Bye\"\n}\n")
        for ((ext, source) in sources) {
            val root = temp.newFolder("game-$ext")
            val drafts = File(temp.root, "draft-$ext")
            val backups = File(temp.root, "backup-$ext")
            val file = File(root, "strings.$ext").apply { writeText(source) }
            val peer = File(root, "peer.$ext").apply { writeText(source) }
            val target = file.canonicalPath
            val original = file.readBytes()
            val partial = mapOf("a" to "안녕")
            val edits = mapOf("a" to "안녕 😀 {name}")

            TranslationDraftStore.save(drafts, target, source, partial)
            assertEquals(partial, TranslationDraftStore.load(drafts, target, source))
            assertTrue(runCatching { GamePatchAuthor.translation(source, partial) }.isFailure)
            assertArrayEquals(original, file.readBytes())
            assertNull(TranslationDraftStore.load(drafts, peer.canonicalPath, source))
            assertNull(TranslationDraftStore.load(drafts, target, source + " "))
            TranslationDraftStore.save(drafts, target, source, edits)

            val preview = GamePatchAuthor.translation(source,
                requireNotNull(TranslationDraftStore.load(drafts, target, source)))
            val zip = GamePatchAuthor.zip(file.name, preview)
            val imported = LocalGameAdvisor.replacement(file.name, "translation.zip", zip.inputStream())
            assertEquals(preview.proposed, imported)
            assertEquals(mapOf("a" to edits.getValue("a"), "b" to "Bye"),
                GamePatchAuthor.extract(imported).associate { it.id to it.text })
            assertTrue(runCatching {
                LocalGameAdvisor.replacement(peer.name, "translation.zip", zip.inputStream())
            }.isFailure)
            assertArrayEquals(original, file.readBytes())

            val proposed = imported.toByteArray()
            file.writeText(source + " ")
            assertTrue(runCatching { GamePatchStore.apply(root, file.name, original, proposed, backups) }.isFailure)
            assertEquals(source + " ", file.readText())
            assertFalse(backups.exists())
            file.writeBytes(original)

            val backup = GamePatchStore.apply(root, file.name, original, proposed, backups)
            assertArrayEquals(proposed, file.readBytes())
            assertArrayEquals(original, peer.readBytes())
            assertEquals(backup, GamePatchStore.findMatchingBackup(root, file.name, backups))
            assertTrue(runCatching { GamePatchStore.restore(root, backup, peer.name) }.isFailure)
            assertArrayEquals(original, peer.readBytes())

            val record = backup.readBytes()
            backup.writeBytes(record.copyOf().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            })
            assertTrue(runCatching { GamePatchStore.restore(root, backup, file.name) }.isFailure)
            assertArrayEquals(proposed, file.readBytes())
            backup.writeBytes(record)

            file.writeText(imported + " ")
            assertTrue(runCatching { GamePatchStore.restore(root, backup, file.name) }.isFailure)
            assertEquals(imported + " ", file.readText())
            file.writeBytes(proposed)

            GamePatchStore.restore(root, backup, file.name)
            assertArrayEquals(original, file.readBytes())
            assertArrayEquals(original, peer.readBytes())
            assertEquals(edits, TranslationDraftStore.load(drafts, target, source))
            assertEquals(2, root.listFiles()!!.size)
            assertEquals(1, backups.listFiles()!!.size)
        }
    }
}
