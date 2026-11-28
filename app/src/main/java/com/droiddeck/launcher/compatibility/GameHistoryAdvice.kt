package com.droiddeck.launcher.compatibility

internal object GameHistoryAdvice {
    fun recommend(outcome: GameRunHistory.Outcome): List<String> = when (outcome) {
        GameRunHistory.Outcome.BLACK_SCREEN -> listOf(
            "게임 소리와 화면 출력 여부를 각각 확인하세요.",
            "게임 로그의 그래픽 초기화 오류를 확인하세요.",
            "게임 내부 렌더 해상도와 출력 해상도를 구분해 확인하세요.",
            "설정을 변경할 경우 백업 후 한 항목씩 비교하세요."
        )
        GameRunHistory.Outcome.CRASH -> listOf(
            "종료 직전 로그와 오류 코드를 확인하세요.",
            "최근 변경한 설정과 모드의 영향을 확인하세요.",
            "기존 백업과 현재 파일을 비교한 후 복원을 검토하세요."
        )
        GameRunHistory.Outcome.STARTED -> listOf("실제 화면 출력과 플레이 가능 여부를 확인하세요.")
        GameRunHistory.Outcome.PLAYABLE -> listOf("현재 설정과 성능 측정 결과를 비교 기준으로 기록하세요.")
        GameRunHistory.Outcome.UNKNOWN -> listOf("실행 결과와 로그가 부족하여 원인을 판단할 수 없습니다.")
    }
}
