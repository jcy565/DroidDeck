package com.droiddeck.launcher.files

import java.util.Locale

/** Small original UI glossary, not a neural model. Unknown text is left untouched. */
internal class MenuTranslationProvider(private val updated: Map<String, String> = emptyMap()) : FileTranslationProvider {
    override suspend fun translate(text: String): String =
        updated[text.lowercase(Locale.ROOT)] ?: glossary[text.lowercase(Locale.ROOT)] ?: text

    override fun close() = Unit

    private companion object {
        val glossary = mapOf(
            "start game" to "게임 시작", "new game" to "새 게임", "continue" to "계속하기",
            "load game" to "게임 불러오기", "save game" to "게임 저장", "quick save" to "빠른 저장",
            "main menu" to "메인 메뉴", "settings" to "설정", "options" to "옵션",
            "exit game" to "게임 종료", "quit game" to "게임 종료", "resume game" to "게임 재개",
            "pause" to "일시 정지", "back" to "뒤로", "cancel" to "취소", "confirm" to "확인",
            "apply" to "적용", "reset" to "초기화", "restore defaults" to "기본값 복원",
            "yes" to "예", "no" to "아니요", "on" to "켜기", "off" to "끄기",
            "language" to "언어", "subtitles" to "자막", "audio" to "오디오",
            "master volume" to "전체 음량", "music volume" to "음악 음량", "sound effects" to "효과음",
            "graphics" to "그래픽", "display" to "화면", "resolution" to "해상도",
            "fullscreen" to "전체 화면", "windowed" to "창 모드", "brightness" to "밝기",
            "texture quality" to "텍스처 품질", "shadow quality" to "그림자 품질",
            "low" to "낮음", "medium" to "보통", "high" to "높음", "ultra" to "최고",
            "controls" to "조작", "keyboard" to "키보드", "controller" to "컨트롤러",
            "mouse sensitivity" to "마우스 감도", "loading..." to "불러오는 중...",
            "saving..." to "저장 중...", "try again" to "다시 시도", "connection failed" to "연결 실패",
            "inventory" to "인벤토리", "map" to "지도", "journal" to "일지", "help" to "도움말"
        )
    }
}
