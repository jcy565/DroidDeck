# 보안 검토

c9abc2f 소스를 기준으로 릴리스 applicationId가 공식 앱과 같다. provider authority와 일부 taskAffinity는 applicationId를 사용한다. 앱 전용 경로 외에도 Download/DroidDeck/Saves, 공개 로그 폴더, Pictures/DroidDeck을 사용한다. LogMigration은 기존 로그를 옮기고 원본을 삭제할 수 있다.

Gradle과 CI는 공개 AOSP 테스트 키로 서명한다. 개인 배포용 비공개 서명 키가 필요하다. 설치 차단 원인은 미확인이다. 시스템 경고·Play Protect를 유지한다.

경로 및 APK 인벤토리, 네이티브 입력 해시 기록이 존재한다. APK 해시 목록은 악성 동작 부재를 증명하지 않는다. 백업은 길이와 적용 파일 해시를 검사하지만 원본 백업 본문의 독립 체크섬, 외부 프로세스와의 원자적 비교·교체, 모든 파일 메타데이터의 보존은 인계 시점 미검증이다.

다음 검토 대상은 공식·개발 APK의 병합 Manifest, 권한, exported 구성요소, 인증서, 네이티브 변경, 실제 공유 쓰기 경로와 업데이트 경로이다.
