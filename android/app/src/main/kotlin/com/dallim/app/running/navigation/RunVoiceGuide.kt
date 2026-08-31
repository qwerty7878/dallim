package com.dallim.app.running.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * S-21 "음성 안내" — 1km 통과/코스 이탈 시 짧은 한국어 음성 안내를 재생하는 얇은 래퍼.
 * 화면(Composable)이 [DisposableEffect]로 생성/해제를 관리한다 — [RunNavigationScreen] 참고.
 */
class RunVoiceGuide(context: Context) {
    private var isReady = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.KOREAN }

    fun speak(message: String) {
        if (!isReady) return
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, message.hashCode().toString())
    }

    fun announceKilometer(km: Int) {
        speak("${km}킬로미터를 통과했습니다")
    }

    fun announceOffRoute() {
        speak("코스에서 벗어났어요. 계획된 경로로 돌아가주세요")
    }

    fun announceStart() {
        speak("달리기를 시작합니다")
    }

    fun announcePaused() {
        speak("일시정지되었습니다")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
