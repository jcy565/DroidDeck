package com.droiddeck.launcher.files

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Optional bundled multilingual OCR. Call from an IO dispatcher. */
internal object ImageOcrRuntime {
    const val enabled = true
    private val occupied = AtomicBoolean(false)

    suspend fun recognize(file: File, script: ImageTranslationInput.Script, rotation: ImageTranslationInput.Rotation, allowed: () -> Boolean): ImageTextRegions.Result {
        check(allowed()) { "게임 실행 또는 메모리 상태로 이미지 인식을 보류했습니다." }
        check(occupied.compareAndSet(false, true)) { "이전 이미지 인식 작업이 아직 종료되지 않았습니다." }
        try {
            require(file.extension.lowercase() in ImageTranslationInput.extensions)
            require(!Files.isSymbolicLink(requireNotNull(file.parentFile).toPath()))
            require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
            require(file.length() in 1L..ImageTranslationInput.MAX_BYTES.toLong())
            val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use(ImageTranslationInput::read)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/webp"))
            val sample = ImageTranslationInput.sampleSize(bounds.outWidth, bounds.outHeight)
            currentCoroutineContext().ensureActive()
            check(allowed())
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })) { "이미지를 읽을 수 없습니다." }
            try {
                require(bitmap.width.toLong() * bitmap.height <= 4_194_304L)
                check(allowed())
                val client = when (script) {
                    ImageTranslationInput.Script.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    ImageTranslationInput.Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                    ImageTranslationInput.Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                }
                val recognized = try {
                    // Keep bitmap/client alive until the native task finishes, even if the UI closes.
                    suspendCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                        client.process(InputImage.fromBitmap(bitmap, rotation.degrees)).addOnCompleteListener { task ->
                            if (task.isSuccessful) continuation.resume(task.result)
                            else continuation.resumeWithException(task.exception ?: IllegalStateException("이미지 인식 실패"))
                        }
                    }
                } finally { client.close() }
                currentCoroutineContext().ensureActive()
                check(allowed()) { "게임 또는 메모리 상태가 바뀌어 결과를 반영하지 않았습니다." }
                val text = ImageTranslationInput.validateText(recognized.text)
                // Avoid assuming how the SDK maps rotated rectangles. Text-only OCR still works.
                if (rotation != ImageTranslationInput.Rotation.NONE) {
                    return ImageTextRegions.Result(text, bitmap.width, bitmap.height, emptyList(), true)
                }
                val lines = recognized.textBlocks.asSequence().flatMap { it.lines.asSequence() }.map { line ->
                    val box = line.boundingBox?.let { ImageTextRegions.Box(it.left, it.top, it.right, it.bottom) }
                    box to line.text
                }
                return ImageTextRegions.build(text, bitmap.width, bitmap.height, lines)
            } finally { bitmap.recycle() }
        } finally { occupied.set(false) }
    }
}
