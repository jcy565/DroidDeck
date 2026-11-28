package com.droiddeck.launcher.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Shared decoder for OCR, original comparison and reviewed image rendering. */
internal object ImageAssetDecoder {
    data class Bounds(val width: Int, val height: Int)

    fun bounds(bytes: ByteArray, extension: String): Bounds {
        if (extension == "tga") {
            val info = TgaImage.inspect(bytes)
            return Bounds(info.width, info.height)
        }
        if (extension == "dds") {
            val info = DdsImage.inspect(bytes)
            return Bounds(info.width, info.height)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val expected = when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> error("지원하지 않는 이미지 형식입니다.")
        }
        require(options.outMimeType == expected)
        ImageTranslationInput.sampleSize(options.outWidth, options.outHeight)
        return Bounds(options.outWidth, options.outHeight)
    }

    fun decode(bytes: ByteArray, extension: String, sample: Int, mutable: Boolean,
        checkpoint: () -> Unit): Bitmap {
        checkpoint()
        if (extension == "tga") {
            val image = TgaImage.decode(bytes, sample, checkpoint)
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.setPixels(image.argb, 0, image.width, 0, 0, image.width, image.height)
                checkpoint()
                return bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
        if (extension == "dds") {
            val image = DdsImage.decode(bytes, sample, checkpoint)
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.setPixels(image.argb, 0, image.width, 0, 0, image.width, image.height)
                checkpoint()
                return bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inMutable = mutable
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })) { "이미지를 읽을 수 없습니다." }
        try {
            checkpoint()
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }
}
