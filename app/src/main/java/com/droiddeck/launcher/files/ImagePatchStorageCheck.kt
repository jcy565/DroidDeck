package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.nio.file.attribute.PosixFileAttributeView
import java.util.concurrent.CancellationException

/** Read-only provider check; never creates a file in the selected game root. */
internal object ImagePatchStorageCheck {
    enum class Result { READ_CAPABLE, UNSAFE_ROOT, NO_SECURE_STREAM, NO_POSIX_IDENTITY, READ_FAILED }

    fun inspect(root: File, checkpoint: () -> Unit = {}): Result {
        return try {
            checkpoint()
            val path = root.toPath().toAbsolutePath().normalize()
            if (path != root.canonicalFile.toPath() || !Files.isDirectory(path, NOFOLLOW_LINKS))
                return Result.UNSAFE_ROOT
            val parts = path.toList()
            fun descend(handle: SecureDirectoryStream<Path>, index: Int): Result {
                checkpoint()
                if (index < parts.size) {
                    return handle.newDirectoryStream(parts[index], NOFOLLOW_LINKS).use {
                        descend(it, index + 1)
                    }
                }
                val view = handle.getFileAttributeView(PosixFileAttributeView::class.java)
                    ?: return Result.NO_POSIX_IDENTITY
                val attrs = view.readAttributes()
                return if (attrs.isDirectory && attrs.fileKey() != null) Result.READ_CAPABLE
                    else Result.NO_POSIX_IDENTITY
            }
            Files.newDirectoryStream(requireNotNull(path.root)).use { stream ->
                if (stream !is SecureDirectoryStream<Path>) Result.NO_SECURE_STREAM
                else descend(stream, 0)
            }
        } catch (e: CancellationException) { throw e }
          catch (_: UnsupportedOperationException) { Result.NO_POSIX_IDENTITY }
          catch (_: Exception) { Result.READ_FAILED }
    }
}
