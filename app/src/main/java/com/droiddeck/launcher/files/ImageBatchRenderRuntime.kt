package com.droiddeck.launcher.files

import android.graphics.Bitmap
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** Android-specific rendering for unapproved private review copies. */
internal object ImageBatchRenderRuntime {
    fun encode(file: File, id: ImageDraftStore.Identity, draft: ImageDraftStore.Draft,
        extension: String, output: OutputStream, style: ImageOverlayPlan.Appearance, checkpoint: () -> Unit) {
        val regions = requireNotNull(draft.regions)
        val image = ImageOverlayRenderer.render(file, id, regions, draft.edits, style, checkpoint)
        try {
            when (extension) {
                "png" -> require(image.compress(Bitmap.CompressFormat.PNG, 100, output))
                "tga", "dds" -> {
                    val source = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use(ImageTranslationInput::read)
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(source)
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    require(hash == id.digest)
                    if (extension == "tga") {
                        TgaImage.writeCopy(source, image.width, image.height, output, { y, row ->
                            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
                        }, checkpoint)
                    } else {
                        val boxes = ImageOverlayPlan.originalSize(ImageOverlayPlan.prepare(regions, draft.edits),
                            regions.width, regions.height, image.width, image.height).map {
                            DdsCopyEncoder.Rect(it.box.left, it.box.top, it.box.right, it.box.bottom)
                        }
                        val bytes = java.io.ByteArrayOutputStream(source.size)
                        DdsCopyEncoder.writeCopy(source, image.width, image.height, boxes, bytes, { x, y, block ->
                            for (i in 0..15) block[i] = image.getPixel(
                                minOf(image.width - 1, x + i % 4), minOf(image.height - 1, y + i / 4))
                        }, checkpoint)
                        val candidate = bytes.toByteArray()
                        DdsCopyReview.validate(source, candidate, boxes, checkpoint)
                        output.write(candidate)
                    }
                }
                else -> error("지원하지 않는 출력 형식")
            }
            checkpoint()
        } finally { image.recycle() }
    }

    fun verify(bytes: ByteArray, extension: String, candidate: ImageAssetDiscovery.Candidate) {
        val bounds = ImageAssetDecoder.bounds(bytes, extension)
        require(bounds.width == candidate.width && bounds.height == candidate.height)
    }

    fun preview(artifact: ImageBatchRender.Artifact, checkpoint: () -> Unit): Bitmap {
        checkpoint()
        return previewBytes(ImageBatchRender.readVerified(artifact), artifact.file.extension, artifact.candidate, checkpoint)
    }

    fun previewBytes(bytes: ByteArray, ext: String, candidate: ImageAssetDiscovery.Candidate,
        checkpoint: () -> Unit): Bitmap {
        checkpoint()
        verify(bytes, ext, candidate)
        val bitmap = ImageAssetDecoder.decode(bytes, ext,
            ImageOverlayPlan.comparisonSample(candidate.width, candidate.height), false, checkpoint)
        try {
            require(bitmap.width.toLong() * bitmap.height <= 1_048_576L)
            checkpoint()
            return bitmap
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }
}
