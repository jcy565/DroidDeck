package com.droiddeck.launcher.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest

internal object ImageOverlayRenderer {
    fun render(file: File, expected: ImageDraftStore.Identity, result: ImageTextRegions.Result,
        edits: Map<Int, String>, style: ImageOverlayPlan.Appearance, checkpoint: () -> Unit): Bitmap {
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
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width.toLong() * height <= ImageOverlayPlan.MAX_EXPORT_PIXELS) {
            "원본 해상도는 최대 8Mi 픽셀까지 출력할 수 있습니다."
        }
        fun matchesFrame(original: Int, decoded: Int): Boolean =
            decoded in (original / sample).coerceAtLeast(1)..((original + sample - 1) / sample)
        require(matchesFrame(width, result.width) && matchesFrame(height, result.height)) {
            "인식 이미지와 원본 해상도가 맞지 않습니다."
        }
        val mapped = ImageOverlayPlan.originalSize(plan, result.width, result.height, width, height)
        val runtime = Runtime.getRuntime()
        val heapFree = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        require(heapFree >= width.toLong() * height * 4 + 32L * 1024 * 1024) {
            "이미지를 출력할 메모리가 부족합니다."
        }
        checkpoint()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = 1
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
        try {
            require(bitmap.width == width && bitmap.height == height) { "원본 이미지 크기가 달라졌습니다." }
            val canvas = Canvas(bitmap)
            val background = Paint().apply { color = Color.BLACK }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = if (style.weight == ImageOverlayPlan.Weight.BOLD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
            }
            for (item in mapped) {
                checkpoint()
                val b = item.box
                var gradient: ImageGradientBackground.Gradient? = null
                val palette = when (style.background) {
                    ImageOverlayPlan.Background.BLACK -> ImageOverlayPlan.Palette(Color.BLACK, Color.WHITE)
                    ImageOverlayPlan.Background.MATCH_GRADIENT -> {
                        val top = mutableListOf<Int>()
                        val bottom = mutableListOf<Int>()
                        val left = mutableListOf<Int>()
                        val right = mutableListOf<Int>()
                        for (i in 0..4) {
                            val x = b.left + (b.right - b.left - 1) * i / 4
                            val y = b.top + (b.bottom - b.top - 1) * i / 4
                            top.add(bitmap.getPixel(x, b.top))
                            bottom.add(bitmap.getPixel(x, b.bottom - 1))
                            left.add(bitmap.getPixel(b.left, y))
                            right.add(bitmap.getPixel(b.right - 1, y))
                        }
                        val estimated = ImageGradientBackground.estimate(top, bottom, left, right)
                            ?: error("배경의 색상 변화가 불규칙합니다. 다른 배경 방식으로 다시 검토하세요.")
                        gradient = estimated
                        val midpoint = ImageGradientBackground.mix(estimated.start, estimated.end, 2)
                        requireNotNull(ImageOverlayPlan.flatPalette(List(8) { midpoint }))
                    }
                    ImageOverlayPlan.Background.MATCH_FLAT -> {
                        val samples = mutableListOf<Int>()
                        for (i in 0..4) {
                            val x = b.left + (b.right - b.left - 1) * i / 4
                            val y = b.top + (b.bottom - b.top - 1) * i / 4
                            samples.add(bitmap.getPixel(x, b.top))
                            samples.add(bitmap.getPixel(x, b.bottom - 1))
                            samples.add(bitmap.getPixel(b.left, y))
                            samples.add(bitmap.getPixel(b.right - 1, y))
                        }
                        ImageOverlayPlan.flatPalette(samples)
                            ?: error("단색 배경을 확인할 수 없습니다. 검은 배경 방식으로 다시 검토하세요.")
                    }
                }
                background.color = palette.background
                background.shader = gradient?.let { g ->
                    val x = b.left + 0.5f
                    val y = b.top + 0.5f
                    val endX = if (g.axis == ImageGradientBackground.Axis.HORIZONTAL) b.right - 0.5f else x
                    val endY = if (g.axis == ImageGradientBackground.Axis.VERTICAL) b.bottom - 0.5f else y
                    LinearGradient(x, y, endX, endY, g.start, g.end, Shader.TileMode.CLAMP)
                }
                ink.color = ImageOverlayPlan.foreground(palette, style.foreground)
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

    /** Bounded original preview, bound to the same source revision as the translation. */
    fun originalPreview(file: File, expected: ImageDraftStore.Identity, checkpoint: () -> Unit): Bitmap {
        checkpoint()
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
        val sample = ImageOverlayPlan.comparisonSample(bounds.outWidth, bounds.outHeight)
        checkpoint()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
        try {
            require(bitmap.width.toLong() * bitmap.height <= 1_048_576L)
            checkpoint()
            require(ImageDraftStore.identity(file) == expected)
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
        require(!preview.isRecycled && preview.width.toLong() * preview.height <= ImageOverlayPlan.MAX_EXPORT_PIXELS)
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
