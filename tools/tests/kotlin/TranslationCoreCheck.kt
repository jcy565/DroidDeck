package com.droiddeck.launcher.files

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import javax.imageio.ImageIO

/** Host regression checks using actual production helpers and disposable fixtures. */
fun main() {
    var checks = 0
    fun verify(condition: Boolean) { check(condition) { "Check ${checks + 1} failed" }; checks++ }
    fun reject(action: () -> Unit) = verify(runCatching(action).isFailure)
    fun flip(bytes: ByteArray, index: Int) = bytes.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
    val base = Files.createTempDirectory("dd-core-").toFile()
    try {
        val game = File(base, "game").apply { mkdir() }
        val drafts = File(base, "drafts")
        val queues = File(base, "queues")
        val backups = File(base, "backups")
        val sources = mapOf(
            "csv" to "id,text\r\na,Hello {name}\r\nb,Hello {name}\r\n",
            "tsv" to "id\ttext\na\tHello {name}\nb\tHello {name}",
            "json" to """{"a":"Hello {name}","b":"Hello {name}"}""")
        for ((ext, source) in sources) {
            val file = File(game, "strings.$ext").apply { writeText(source) }
            val original = file.readBytes()
            val partial = mapOf("a" to "안녕")
            TranslationDraftStore.save(drafts, file.canonicalPath, source, partial)
            verify(TranslationDraftStore.load(drafts, file.canonicalPath, source) == partial)
            reject { GamePatchAuthor.translation(source, partial) }
            val edited = mapOf("a" to "안녕 😀 {name}")
            val filled = TranslationReuse.fill(source, edited)
            verify(filled.added == 1 && filled.conflicts == 0)
            verify(TranslationReuse.review(source, filled.edits).count(TranslationReuse.Status.READY) == 2)
            TranslationDraftStore.save(drafts, file.canonicalPath, source, filled.edits)
            val preview = GamePatchAuthor.translation(source, filled.edits)
            val zip = GamePatchAuthor.zip(file.name, preview)
            val imported = LocalGameAdvisor.replacement(file.name, "patch.zip", zip.inputStream())
            verify(imported == preview.proposed)
            verify(GamePatchAuthor.extract(imported).associate { it.id to it.text } == filled.edits)
            verify(file.readBytes().contentEquals(original))
            reject { GamePatchStore.apply(game, file.name, original, imported.toByteArray(), game.resolve("bad-backup")) }
            verify(!game.resolve("bad-backup").exists())
            val backup = GamePatchStore.apply(game, file.name, original, imported.toByteArray(), backups)
            verify(GamePatchStore.findMatchingBackup(game, file.name, backups) == backup)
            val record = backup.readBytes()
            for (i in record.indices) {
                backup.writeBytes(flip(record, i))
                reject { GamePatchStore.restore(game, backup, file.name) }
                verify(file.readText() == imported)
            }
            backup.writeBytes(record)
            GamePatchStore.restore(game, backup, file.name)
            verify(file.readBytes().contentEquals(original))
        }
        val report = TranslationDiscovery.inspect(game)
        verify(report.candidates.size == 3 && !report.partial)
        val queue = TranslationWorklist.Queue(game, report.candidates)
        verify(runCatching { TranslationQueueStore.load(queues, game) { throw CancellationException() } }.exceptionOrNull() is CancellationException)
        TranslationQueueStore.save(queues, queue)
        verify(TranslationQueueStore.load(queues, game) == queue)
        verify(TranslationWorklist.review(queue, drafts).readyRows == 6)
        val queueRecord = queues.listFiles()!!.single()
        val queueBytes = queueRecord.readBytes()
        for (i in queueBytes.indices) {
            queueRecord.writeBytes(flip(queueBytes, i))
            reject { TranslationQueueStore.load(queues, game) }
        }
        queueRecord.writeBytes(queueBytes)
        val changed = File(game, "strings.csv")
        changed.appendText("\r\n")
        verify(TranslationWorklist.review(queue, drafts).entries.any { it.state == TranslationWorklist.State.SOURCE_CHANGED })
        reject { TranslationQueueStore.save(queues, queue) }
        verify(queueRecord.readBytes().contentEquals(queueBytes))
        val png = File(game, "menu.png")
        check(ImageIO.write(BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB), "png", png))
        val originalPng = png.readBytes()
        val images = ImageAssetDiscovery.inspect(game, probe = { bytes, _ ->
            val decoded = checkNotNull(ImageIO.read(bytes.inputStream()))
            ImageAssetDiscovery.Size(decoded.width, decoded.height)
        })
        verify(images.candidates.size == 1)
        val imageQueue = ImageAssetDiscovery.Queue(game, images.candidates)
        ImageQueueStore.save(queues, imageQueue)
        verify(ImageQueueStore.load(queues, game) == imageQueue)
        val identity = ImageDraftStore.identity(png)
        val box = ImageTextRegions.Box(0, 0, 10, 10)
        val regions = ImageTextRegions.build("Hello", 32, 16, sequenceOf(box to "Hello"))
        val draft = ImageDraftStore.Draft("Hello", "", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, regions, mapOf(1 to "안녕 😀"))
        verify(ImageDraftStore.saveIfAbsent(drafts, png, identity, draft))
        verify(!ImageDraftStore.saveIfAbsent(drafts, png, identity, draft))
        verify(ImageDraftStore.load(drafts, identity) == draft)
        for (code in 0xD800..0xDFFF) {
            val invalid = "bad" + code.toChar()
            verify(!GamePatchAuthor.validTranslation("Hello", invalid))
            reject { ImageTranslationInput.validateText(invalid) }
            val filtered = ImageTextRegions.build("Hello", 32, 16, sequenceOf(box to invalid, box to "Hello"))
            verify(filtered.partial && filtered.regions.single().text == "Hello")
            reject { ImageTextRegions.summary(regions, mapOf(1 to invalid)) }
        }
        reject { ImageDraftStore.save(drafts, png, identity, draft.copy(edits = mapOf(1 to "bad\uD800"))) }
        verify(ImageDraftStore.load(drafts, identity) == draft)
        verify(ImageDraftStore.deleteIfUnchanged(drafts, png, identity, draft))
        verify(png.readBytes().contentEquals(originalPng))
        println("PASS: $checks host assertions (not Android tests)")
    } finally { base.deleteRecursively() }
}
