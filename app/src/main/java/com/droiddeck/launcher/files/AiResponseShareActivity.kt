package com.droiddeck.launcher.files

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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
            Text("DroidDeck AI 응답 확인 중")
            LaunchedEffect(Unit) {
                val message = try {
                    requireNotNull(incoming) { "응답의 JSON 텍스트를 공유하세요. 대화 링크는 지원하지 않습니다." }
                    withContext(Dispatchers.IO) { SharedAgentReply.receive(applicationContext, incoming) }
                    "응답을 받았습니다. DroidDeck의 기존 AI 작업에서 검토하세요."
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) { "응답을 받지 못했습니다. 저장된 요청의 JSON 텍스트를 공유하세요. 링크는 지원하지 않습니다." }
                Toast.makeText(this@AiResponseShareActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
