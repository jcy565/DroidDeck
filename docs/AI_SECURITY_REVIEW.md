# 보안 검토

c9abc2f 소스를 기준으로 릴리스 applicationId가 공식 앱과 같다. provider authority와 일부 taskAffinity는 applicationId를 사용한다. 앱 전용 경로 외에도 Download/DroidDeck/Saves, 공개 로그 폴더, Pictures/DroidDeck을 사용한다. LogMigration은 기존 로그를 옮기고 원본을 삭제할 수 있다.

Gradle과 CI는 공개 AOSP 테스트 키로 서명한다. 개인 배포용 비공개 서명 키가 필요하다. 설치 차단 원인은 미확인이다. 시스템 경고·Play Protect를 유지한다.

경로 및 APK 인벤토리, 네이티브 입력 해시 기록이 존재한다. APK 해시 목록은 악성 동작 부재를 증명하지 않는다. 백업은 길이와 적용 파일 해시를 검사하지만 원본 백업 본문의 독립 체크섬, 외부 프로세스와의 원자적 비교·교체, 모든 파일 메타데이터의 보존은 인계 시점 미검증이다.

다음 검토 대상은 공식·개발 APK의 병합 Manifest, 권한, exported 구성요소, 인증서, 네이티브 변경, 실제 공유 쓰기 경로와 업데이트 경로이다.

신규 백업은 원본 바이트의 SHA-256을 보관하고, 같은 길이의 손상도 복원 전에 거부한다. 이전 형식은 기존 길이 검증 방식으로 읽는다. 일반 파일의 POSIX 권한은 호스트 JVM에서 적용·복원 왕복 검증했다. 서명 진위, ACL, 외부 프로세스와의 경쟁 조건은 검증 범위 밖이다. 개발판에서도 명시적으로 선택한 공유 게임 파일은 다른 앱과 공유될 수 있다. 별도로 복사한 게임 설정에서 먼저 검증한다.

## APK 실물 비교

공식 0.7.1 표준 APK의 SHA-256은 179ab8b4dc8f9223b70a1e4287c55a090b36ee376242186a60211100158462f1이며 공식 릴리스 메타데이터와 일치했다. apksigner로 확인한 공식 인증서 SHA-256은 b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0이다. 이전 개발 APK(app-release(1).apk, 해시 45d9d383d11640026b706bc536ecea7d4408c4abeee2e9c42f951b6629bb9b28)는 공개 테스트 인증서 a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc로 서명됐다. 두 파일의 aapt 권한 출력과 설치 ID는 동일했다. 네이티브 .so 파일은 양쪽 모두 35개이며 같은 경로의 14개는 해시가 달랐다. 재빌드·도구 체인 차이와 실제 실행 동작의 원인은 추가 비교가 필요하다.

기존 경로 감사 산출물은 5bd5d89에서 5,907개 소스 줄을 찾았다. APK 인벤토리는 ffe7cf4의 602개 항목이며 APK 해시는 709f02508586801f246fc1399643a7e91ae8cbf66a44a77c5a42054c07be8289이다. 위의 이전 개발 APK와도 별개의 빌드다.

## 독립 릴리스 서명

CI APK는 공개 테스트 키로 서명한 시험용이다. 소유자 전용 키는 본인이 관리하고 안전한 곳에 백업한다. Android 터미널에서 aapt, apksigner, keytool을 사용할 수 있다면 아래처럼 별도 키로 서명한다. 최초 키 생성은 한 번만 필요하다. 비밀번호는 도구의 비공개 입력창에 입력한다. 키 파일은 Git 작업 폴더 밖에 둔다.

```bash
umask 077
keytool -genkeypair -storetype PKCS12 -keystore ~/droiddeck-ai.p12 -alias droiddeck-ai -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=DroidDeck AI"
# Historical command superseded by the six-argument release guard below.
```

업데이트는 동일한 개인 키로 서명한다. 테스트 APK와 개인 서명 APK의 인증서는 다르므로 장기 사용할 기기에서는 처음부터 개인 서명 APK로 설치한다. Android가 차단하면 경고 문구를 확인하고 중단한다.

이전 개발 APK에서 변경된 네이티브 14개의 ELF DT_NEEDED 목록도 대조했다. 런타임 라이브러리 이름의 추가·삭제는 없었다. 이는 기계어 수준 동작 검증과 구분한다. 공식 최신 main과 시험 병합한 결과와 검사 대상은 AI_DEVELOPMENT_STATUS.md에 기록했다.

## Hybrid AI branch security follow-up

Current applicationId is com.droiddeck.launcher.ai, independent from the official app; Gradle still signs CI artifacts with public testkey. OfficialUpdatePolicy isolates the AI package and dot-suffixed variants; AppUpdates blocks upstream APK offers and SelfInstaller refuses official download and install before disk/network/session work. Reading upstream release metadata remains possible. This is collision protection, not a bypass of Android security or proof of safe APK contents. Separate signing key, verified release provenance, clean install/update tests, and review of shared storage and native libraries remain mandatory. Android installation warning text has not been confirmed. Do not delete official app or disable Play Protect. Previous APK test-key detection is evidence, not proof of the cause.

## Independent APK signing guard

The release helper now requires an independently reviewed input APK SHA-256 and the expected independent release certificate SHA-256, in addition to the existing owner-managed key and alias. The older four-argument invocation is no longer supported. It snapshots the input, checks the package identity and exactly one expected test certificate, refuses the official or public test certificate as the release identity, verifies the output package and exact expected release certificate, compares all non-signature ZIP file contents, and publishes atomically without overwriting. No secret or password argument. Requires Bash, GNU coreutils (including hard-link support), Python and real Android build-tools. The approved hash must come from a reviewed, trusted build, not just an arbitrary downloaded APK. This is a release integrity guard, not malware detection or a way to bypass Android/Play Protect. Real key, real APK signing and device installation remain unverified. Host tests use simulated Android tools and synthetic ZIPs; four unittest methods passed (including 5 invalid digest/certificate cases, 9 tool/payload failure cases, existing and dangling-symlink output checks). CI still uses a public test key; do not distribute it as production.


## Device-block follow-up: permission purpose and privacy review (2026-11-28 KST)

Reviewed source at fc57f72eca36b3930177bc521b752dfeca230044 (build 46139567549), not the merged APK manifest or runtime behavior. Findings:

- `update/OfficialUpdatePolicy.kt` and `update/SelfInstaller.kt`: the independent app rejects official APK download/installation before performing the operation. The manifest still declares package install/delete requests; the broader call graph and actual package-delete purpose are not yet verified. Review least privilege and whether these capabilities have a valid independent-app requirement.
- `input/HomeButtonService.kt`, `res/xml/home_button_service.xml`, `input/BottomScreenGuests.kt`: accessibility supports controller/home shortcuts and external-display window state. Window-content access is declared and window metadata is read. Review consent, scope, active session boundaries, absence of sensitive content collection, and lifecycle cleanup. No conclusion about classifier rules or malware status.
- `files/StorageRoots.kt`, `storage/StorageScan.kt`: the file manager enumerates storage roots and measures game/library data. Review explicit user-selected scope, writes, traversal and symlink boundaries, and protection of unrelated personal files and official saves.
- `handtracking/HandCamera.kt`, `handtracking/TrackingCameras.kt`, `handtracking/HandCameraRecorder.kt`: camera supports hand tracking; diagnostic recording is gated by a local flag and writes frame metadata and optional JPEGs into app cache. Review opt-in, visibility, retention, access and cleanup of recorded images. Microphone purpose and complete runtime consent flow are not yet verified.
- These are source-level observations, not verified device behavior. No proof of malware or false positive. No executable code, manifest, signing identity or APK changed to evade security detection. Quarantine the rejected artifact and seek official security-provider review; do not retry or bypass the block. Next: inventory actual dependencies, compare versions against published advisories and audit build provenance, then remediate substantiated defects with regression tests. Release remains blocked pending safety review and normal platform acceptance.
