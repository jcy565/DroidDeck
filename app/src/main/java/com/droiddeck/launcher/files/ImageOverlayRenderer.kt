package com.droiddeck.launcher.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest

internal object ImageOverlayRenderer {
    fun render(file: File, expected: ImageDraftStore.Identity, result: ImageTextRegions.Result,
        edits: Map<Int, String>, checkpoint: () -> Unit): Bitmap {
        checkpoint()
        val plan = ImageOverlayPlan.prepare(result, edits)
        require(!Files.isSymbolicLink(requireNotNull(file.parentFile).toPath()))
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.canonicalPath == expected.path)
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use(ImageTranslationInput::read)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == expected.digest) { "원본 이미지가 변경됐습니다." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/webp"))
        val sample = ImageTranslationInput.sampleSize(bounds.outWidth, bounds.outHeight)
        checkpoint()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
        try {
            require(bitmap.width == result.width && bitmap.height == result.height &&
                bitmap.width.toLong() * bitmap.height <= 4_194_304L) { "인식 이미지 크기가 달라졌습니다." }
            val canvas = Canvas(bitmap)
            val background = Paint().apply { color = Color.BLACK }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
            }
            for (item in plan) {
                checkpoint()
                val b = item.box
                val w = (b.right - b.left - 4).toFloat()
                val h = (b.bottom - b.top - 4).toFloat()
                var size = minOf(h, 128f)
                while (size >= 8f) {
                    ink.textSize = size
                    val metrics = ink.fontMetrics
                    if (ink.measureText(item.text) <= w && metrics.bottom - metrics.top <= h) break
                    size -= 1f
                }
                require(size >= 8f) { "번역문이 너무 깁니다. 글자를 줄이거나 더 큰 이미지를 선택하세요." }
                val metrics = ink.fontMetrics
                canvas.save()
                canvas.clipRect(b.left, b.top, b.right, b.bottom)
                canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), background)
                canvas.drawText(item.text, (b.left + b.right) / 2f,
                    (b.top + b.bottom) / 2f - (metrics.top + metrics.bottom) / 2f, ink)
                canvas.restore()
            }
            checkpoint()
            require(ImageDraftStore.identity(file) == expected) { "원본 이미지가 변경됐습니다." }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    /** Allocate a fresh unique file in the original folder; never open the original for writing. */
    fun saveCopy(file: File, expected: ImageDraftStore.Identity, preview: Bitmap,
        checkpoint: () -> Unit): File {
        checkpoint()
        require(ImageDraftStore.identity(file) == expected)
        require(!preview.isRecycled && preview.width.toLong() * preview.height <= 4_194_304L)
        val parent = requireNotNull(file.canonicalFile.parentFile)
        require(Files.isDirectory(parent.toPath(), NOFOLLOW_LINKS))
        val output = Files.createTempFile(parent.toPath(), "dd-translated-", ".png").toFile()
        try {
            FileOutputStream(output).use {
                require(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG 생성 실패" }
                it.fd.sync()
            }
            checkpoint()
            require(ImageDraftStore.identity(file) == expected) { "원본 이미지가 변경됐습니다." }
            return output
        } catch (failure: Throwable) {
            Files.deleteIfExists(output.toPath())
            throw failure
        }
    }
}
