package com.droiddeck.launcher.compatibility

import com.droiddeck.launcher.input.KeyValues
import com.droiddeck.launcher.stores.StoreGameSidecar
import java.io.DataInputStream
import java.io.File
import java.io.IOException

internal object InstalledGameVersion {
    fun read(gameId: String, folder: File?): String? {
        if (folder == null || !folder.isDirectory) return null
        return try {
            if (gameId.startsWith("steam:")) {
                val id = gameId.removePrefix("steam:").toIntOrNull() ?: return null
                val common = folder.parentFile ?: return null
                if (id <= 0 || common.name != "common") return null
                val text = metadata(File(common.parentFile, "appmanifest_$id.acf")) ?: return null
                val state = KeyValues.parse(text, maxDepth = 32).child("AppState") ?: return null
                if (state.string("appid") != id.toString() || state.string("installdir") != folder.name) return null
                val flags = state.string("StateFlags")?.toIntOrNull() ?: return null
                if (flags and 4 == 0) return null
                state.string("buildid")?.takeIf { it.toULongOrNull() != null && it.all { c -> c in '0'..'9' } && it.any { c -> c != '0' } && it.length <= 20 }
            } else {
                val text = metadata(StoreGameSidecar.file(folder)) ?: return null
                val record = StoreGameSidecar.parse(text) ?: return null
                if (!record.isInstalled || "${record.store.id}:${record.id}" != gameId) return null
                record.installVersion.trim().takeIf { it.length in 1..128 && it.none(Char::isISOControl) }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun metadata(file: File): String? {
        if (!file.isFile) return null
        return file.inputStream().use { stream ->
            val size = stream.channel.size()
            if (size !in 1L..1_048_576L) return null
            val bytes = ByteArray(size.toInt())
            DataInputStream(stream).readFully(bytes)
            if (stream.read() == -1) bytes.toString(Charsets.UTF_8) else null
        }
    }
}
