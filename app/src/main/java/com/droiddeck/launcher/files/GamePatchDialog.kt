package com.droiddeck.launcher.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun GamePatchDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember(file) { mutableStateOf("") }
    var key by remember(file) { mutableStateOf("") }
    var value by remember(file) { mutableStateOf("") }
    var preview by remember(file) { mutableStateOf<GameConfigPreview.Preview?>(null) }
    var message by remember(file) { mutableStateOf("") }
    var busy by remember(file) { mutableStateOf(false) }
    var backup by remember(file) { mutableStateOf<File?>(null) }
    LaunchedEffect(file) {
        busy = true
        try {
            backup = withContext(Dispatchers.IO) {
                GamePatchStore.findMatchingBackup(file.parentFile!!, file.name, File(context.filesDir, "game-patch-backups"))
            }
            if (backup != null) message = "Saved backup available."
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "Backup lookup failed"
        } finally { busy = false }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Game configuration: ${file.name}") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(section, { section = it; preview = null }, label = { Text("INI section (optional)") }, enabled = !busy, singleLine = true)
            OutlinedTextField(key, { key = it; preview = null }, label = { Text("Existing key") }, enabled = !busy, singleLine = true)
            OutlinedTextField(value, { value = it; preview = null }, label = { Text("New value") }, enabled = !busy, singleLine = true)
            TextButton(enabled = !busy && key.isNotBlank(), onClick = {
                busy = true; preview = null
                val s = section.trim().ifEmpty { null }; val k = key.trim(); val v = value
                scope.launch {
                    try {
                        preview = withContext(Dispatchers.IO) {
                            require(file.extension.lowercase() in setOf("ini", "cfg"))
                            require(file.length() <= 262144)
                            val bytes = file.readBytes(); require(bytes.size <= 262144)
                            val original = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                            GameConfigPreview.preview(original, s, mapOf(k to v))
                        }
                        message = "Preview ready. Close the game before applying."
                    } catch (e: Exception) { message = e.message ?: "Preview failed" } finally { busy = false }
                }
            }) { Text("Preview") }
            preview?.changes?.forEach { Text("${it.key}: ${it.before} → ${it.after}") }
            Text(message)
            if (backup != null) TextButton(enabled = !busy, onClick = {
                val saved = backup ?: return@TextButton
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { GamePatchStore.restore(file.parentFile!!, saved) }; backup = null; preview = null; message = "Restored" }
                    catch (e: Exception) { message = e.message ?: "Restore failed" } finally { busy = false }
                }
            }) { Text("Restore last change") }
        }
    }, confirmButton = {
        TextButton(enabled = !busy && preview?.changes?.isNotEmpty() == true, onClick = {
            val p = preview ?: return@TextButton
            busy = true
            scope.launch {
                try {
                    backup = withContext(Dispatchers.IO) { GamePatchStore.apply(file.parentFile!!, file.name, p.original.toByteArray(Charsets.UTF_8), p.proposed.toByteArray(Charsets.UTF_8), File(context.filesDir, "game-patch-backups")) }
                    preview = null; message = "Applied; backup saved."
                } catch (e: Exception) { message = e.message ?: "Apply failed" } finally { busy = false }
            }
        }) { Text("Apply with backup") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } })
}
