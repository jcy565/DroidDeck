package com.droiddeck.launcher.files

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exported Android share destination. Treat every sender and response as untrusted. */
class AiResponseShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = try {
            if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.takeIf {
                    it.length in 1..GameAgent.MAX_OUTPUT_CHARS + 32
                }?.toString() else null
        } catch (_: RuntimeException) { null }
        setContent {
            var review by remember { mutableStateOf<SharedAgentReply.Replacement?>(null) }
            var replacing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            fun complete(message: String) {
                Toast.makeText(this@AiResponseShareActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
            Text("DroidDeck AI 응답 확인 중")
            LaunchedEffect(Unit) {
                try {
                    requireNotNull(incoming) { "응답의 JSON 텍스트를 공유하세요. 대화 링크는 지원하지 않습니다." }
                    review = withContext(Dispatchers.IO) { SharedAgentReply.receive(applicationContext, incoming) }
                    if (review == null) complete("응답을 받았습니다. DroidDeck의 기존 AI 작업에서 검토하세요.")
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) {
                    complete("응답을 받지 못했습니다. 저장된 요청의 JSON 텍스트를 공유하세요. 링크는 지원하지 않습니다.")
                }
            }
            review?.let { candidate ->
                AlertDialog(
                    onDismissRequest = { if (!replacing) finish() },
                    title = { Text("보관된 AI 응답 교체") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("같은 요청의 다른 응답이 이미 있습니다. 교체해도 게임에는 적용되지 않습니다.")
                            Text("기존 응답")
                            Text(candidate.previous)
                            Text("새 응답")
                            Text(candidate.incoming)
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = !replacing, onClick = {
                            replacing = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { SharedAgentReply.replace(applicationContext, candidate) }
                                    complete("응답을 교체했습니다. 기존 AI 작업에서 검토하세요.")
                                } catch (e: CancellationException) { throw e }
                                  catch (_: Exception) { complete("보관된 요청 또는 응답이 변경되어 교체하지 못했습니다.") }
                            }
                        }) { Text("새 응답으로 교체") }
                    },
                    dismissButton = {
                        TextButton(enabled = !replacing, onClick = { finish() }) { Text("기존 응답 유지") }
                    },
                )
            }
        }
    }
}
