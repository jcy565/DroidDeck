# DroidDeck AI — Work 종합 개발 인계서

기준일: 2026-11-28. 새 Work 세션의 첫 문서. 아래 최신 상태는 과거 문서의 오래된 상태보다 우선한다. 원본 문서와 Git 이력은 세부 설계 및 검증 증거로 보존한다.

## 0. 사용자 지정 최우선 규칙: 할당량 5% 인계

**어떤 기능 개발, 빌드, 테스트보다 인계서 보존이 우선이다.**
- Work 시작 및 주요 단계 전후에 사용 가능한 공식 사용량 정보를 확인한다. 5시간/주간 등 적용되는 제한 중 하나라도 잔여량이 5% 이하이면 즉시 신규 작업을 중단하고 이 문서의 최신 체크포인트를 갱신한다.
- 사용량 경고, 예상 소진, 중단 위험이 있거나 잔여량을 조회할 수 없다면 5%까지 기다리지 말고 선제적으로 갱신한다. 접근 불가능한 수치는 추측하지 않는다. 사용자에게 필요한 경우 사용량 확인을 요청한다.
- 기능 단위 변경/커밋, 테스트 완료/실패, CI 시작/종료 때마다 체크포인트를 저장한다. 한도 직전까지 기록을 미루지 않는다.
- 5% 이하에서는 새 코드, 긴 조사, 추가 빌드/테스트를 시작하지 않는다. 이미 수행한 변경, 정확한 브랜치/HEAD, 미커밋 파일, CI 실행 ID/상태, 테스트 근거, 오류, 다음 작업/명령, 안전 제약을 기록한다. 실행 중인 CI를 임의 취소하지 않는다.
- 작업 브랜치에 인계서를 커밋하고 실제 원격 저장을 확인한다. 저장 실패 시 현재 대화와 로컬 문서로 최소 인계 정보를 즉시 제공한다. 할당량이 회복되면 이 문서에서 재개한다.
- 이 규칙은 프로젝트 작업 지시이며 제품 한도를 제어하거나 에이전트의 실시간 조회를 보장하는 설정이 아니다. 사용자는 설정 → 사용량에서 확인할 수 있다. 유료 추가 사용량 구매나 업그레이드를 자동 수행하지 않는다.

## 1. 재개 기준

- 저장소: https://github.com/jcy565/DroidDeck
- 공식 기준: 0.7.1 / cdff94e72c45da50af43bbceff28effaf0179caf
- 최신 작업 브랜치: ai-independent-apk-verification-next
- 인계서 작성 직전 HEAD: 86dbed8baba892dfd9e8b3c6f2234b3aab776f36 (인계서 커밋 이후 HEAD는 다시 확인).
- 최신 성공 APK 소스: ai-device-validation-next / 3f3eaab6920c8f149dd5bd9ad34a77962b6baeb8.
- 후속 staging: ai-release-signature-next / 946b4e3ddbfe6df42f9449f330068b5ba7696bd9. 독립 검증 브랜치는 이를 계승. APK 성공 소스 대비 인계 전 13 commits ahead, 0 behind; 변경 7개 파일은 검증/키 생성 도구 및 문서, 별도 검증 workflow. 최신 브랜치 전체 APK를 다시 빌드했다는 의미가 아니다.
- 공식 main/공식 릴리스 보존. 강제 push 금지. 실행 중인 빌드 브랜치 변경 금지. 진행 중인 CI 확인 및 ancestry/expected HEAD 확인 후 비강제 통합. 성공판 아티팩트 보존.
- 대상 Galaxy S23+ 8 GB, Android ARM64. RE7: 검은 화면, 무음, 약 24초 후 LOW_MEMORY, 설정 720p/DX12/저텍스처에도 1080p 복귀 관측. DD-Turnip v0.9.1 환경에서의 과거 관측이며 최신판 원인/해결 여부는 미검증.
- 기능 구현률 85~90%는 과거 주관적 추정일 뿐 정량 지표 아님. 실제 기기 D01~D08 0/8. 배포 미승인.

## 2. 최신 검증 사실 (우선 참조)

| 항목 | 결과 |
|---|---|
| OCR 활성 통합 빌드 | [46111306535](https://github.com/jcy565/DroidDeck/actions/runs/46111306535) 성공, 필수 9/9, 공식 배포 4개 생략 |
| Android | JUnit 590 XML, 4271/4271 통과, 실패·오류·건너뜀·중복·이전 성공판 대비 누락 0 |
| Python 및 네이티브 | Python 2199 보고, 2149 통과, 50 skip, 실패 0; 네이티브 작업 성공 |
| 서명 보호 | 별도 모의 환경 5/5 통과 (실제 키 서명 아님) |
| OCR 패키징 | 1012개 일반 파일 인벤토리 대조, OCR 39개, Hani/Jpan/Latn 모델 포함 |
| Android 독립 검증 | [46113827368](https://github.com/jcy565/DroidDeck/actions/runs/46113827368) 성공; job 140883055366 실제 로그 확인. APK ZIP 및 APK 해시 OK, v2=true, v3=true, signer 1명, aapt 패키지 확인. v1=false는 해당 apksigner 보고값; 별도 Java JAR 검증 기록과 구분. v3.1/v3.2/v4=false. |
| 독립 배포용 개인 서명 | 미완료. 현재는 공개 테스트 인증서 APK |
| 기기 설치, 실행, 성능 | 미검증. 배포·실기 승인 아님 |

고정 아티팩트 (실제 ZIP을 내려받아 SHA-256, CRC, 내부 목록 대조한 이전 기록):
- APK ZIP 15250811269: 9bcb719ddcad35148d90ffed0da6494d4fc9bb2e0339c70d3448a6223b630616
- APK app-release.apk: a2b72cf82e4bdad9dd264c4bcc7774658ce7e8fdbf1c2b8dc2111f9da4fb1eaf (105537979 bytes)
- 인벤토리 ZIP 15250111273: 86895699426e54052f6732577639794c9dcc0011dedff444d37f8253bd17f1ae
- JUnit ZIP 15250811254: a46aaf76d64378c02945f69cc2e03223b49ba570202938c5fd35cb0c550ea126
- 패키지 com.droiddeck.launcher.ai; 공개 테스트 인증서 a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc; 공식 인증서 b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0. 공식판 대비 추가 권한 0, provider/taskAffinity 공유 없음 (비교 보고서 기준). 해시/서명 검사와 악성 동작 부재는 다른 주장이다.
- 이전 OCR 활성 성공 46100476853 / 680e197d4553003f5ab92fef45619175806c86dd: 4271 동일 테스트 ID, APK 1012개. 이전 OCR 비활성 성공 46108743293 / 0a87e1b1935d72dad4718d0e6a9a25e9097b8de3: 4271 테스트, APK 602개. 후자는 OCR 시험 후보가 아니다.
- 실제 로그나 파일을 확인하지 않은 결과를 확정하지 않는다. 과거 문서의 로컬 시험 주장은 필요한 경우 재현한다.

## 3. 사용자 요구 및 기능 현황

| 분야 | 구현 및 제한 |
|---|---|
| 한국어 AI/게임 진단 | 규칙 기반 로그 증거·가설 구분, 선택 파일 기반 자연어 제안 및 검증 도구. 자동 원본 변경 금지 |
| ChatGPT | 기존 앱/구독 이용, Android 공유 시트로 사용자가 검토한 요청만 전송, 구조화 JSON 수동 붙여넣기, 요청 UUID·원본·초안 binding, 재개 checkpoint. 로그인/토큰/API/자동 전송 없음. 실기 공유 미검증 |
| 선택형 로컬 AI | MediaPipe tasks-genai 0.10.35 실험 adapter, experimentalLocalAi=true만 활성. 기본 noLocalAi, 모델 미포함. 2B급 양자화 후보, 모델 라이선스·한국어 품질·메모리·SDK 호환·실제 추론 미검증. 2 GiB 여유 메모리 바닥은 휴리스틱. 실제 앱에 유료 API/PC 서버 의존 추가 금지 |
| 설정/모드 | INI/CFG 기존 키 제한, 720p/900p/지원 Unreal 30FPS 제안, 사용자 승인/백업/복원, 단일 파일 ZIP, 원본 해시 검증. 위험한 자동 드라이버/옵션 변경, 범용 바이너리·다중 파일 자동 적용은 미완료 |
| 텍스트 번역 | CSV/TSV/flat JSON, 변수 보존, 제한된 배치/초안/목록/ZIP. 로컬 메뉴 사전, ChatGPT 교환. 신경망 번역 미검증, 사전과 모델 혼동 금지 |
| 이미지 번역 | 정적 파일 Latin/일본어/중국어 OCR, 영역 검토, PNG/TGA/제한 DDS, 목록 및 ZIP. 실제 게임 파일 적용/복구 UI 비활성 유지; 실기 OCR 미검증. 실시간 화면 번역/오버레이 제외 |
| 성능 | Gamescope 10초 실제 프레임, FPS/P95, 같은 기기·게임·장면 비교, 표본 검증 및 취소. SQLite 최대 500건. 측정 인과/향상 보장 금지; 실측 미검증 |
| 호환성 | 게임·런타임·버전별 이력 및 비교. Steam/Epic/GOG 소스 존재, 실제 플랫폼/게임별 실행 검증 별도. Ubisoft/EA 통합 미확인 |
| 저장공간 | 읽기 전용 폴더 분석, 임시/동일 이름·크기 중복 후보, 최대 10000항목 깊이32. 자동 삭제 금지 |
| 독립 설치/업데이트 | AI 패키지와 공식 앱 분리, 공식 APK 자체 설치 차단, 별도 공개 경로/세이브 보호 목표. :ai-core Java17 라이브러리 계약, host API1/module0.1.0/schema1. 번들 코드만 정상 서명 APK로 업데이트, DDAG/DDAM 서명 데이터 업데이트 기반, 실제 생산 키/모델 및 실기 미검증 |
| 실행 중 부하 | 게임 우선, 백그라운드 추론/스캔/폴링 없음, 선택 분석 취소 및 쓰기 전 재확인. 목표 증분 메모리 <20MiB, 중앙 FPS 저하 <2%, P95 악화 <5%는 미측정 목표 |

## 4. 다음 작업 (순서 준수)

1. **서명 검증 결과 인수:** 46113827368 성공 및 실제 로그 재확인. 기존 성공 APK로 계속 진행; 동일 빌드를 이유 없이 반복하지 않는다. 후속 검증 도구 변경은 별도 CI로 검증한다.
2. **소유자 관리 독립 배포 키:** 기존 tools/release/make-release-key.sh는 공식 repo Secrets용이므로 사용 금지. tools/release/make-ai-release-key.sh는 로컬 대화형, 저장소 밖 신규 비공개 디렉터리, PKCS12/RSA3072/alias droiddeck-ai. 기존 키 있으면 재사용. 개인 키/암호를 ChatGPT, GitHub, 클라우드 러너에 보내지 않는다. 사용자의 신뢰 가능한 로컬 환경에서만 실행. 키를 임의 생성/업로드하지 않는다.
3. **실제 재서명 및 검사:** 사용자 관리 환경에 JDK, aapt, apksigner, Python, GNU 도구 필요. tools/release/sign-ai-apk.sh INPUT.apk OUTPUT.apk KEYSTORE KEY_ALIAS APPROVED_INPUT_SHA256 EXPECTED_RELEASE_CERT_SHA256 (6인자). 해시·패키지·입출력 인증서·v2·payload 동일·덮어쓰기 금지 검사. 이전 공개 테스트 및 공식 인증서 재사용 금지. 출력 APK 해시 및 공개 인증서 기록, v2/v3 및 패키지 재확인. 실기/배포 승인과 구분. 현재 로컬 환경의 SDK 제한을 확인하고 사용자가 할 수 있는 단계만 요청한다.
4. **실기 D01~D08** (모두 미검증): D01 기존 공식 앱/세이브 보호·독립 설치; D02 실행/재시작 및 공유 경로 보존; D03 복제 INI/CFG 미리보기/취소/적용/복구 해시; D04 텍스트/ChatGPT 공유 및 초안 재개/잘못된 응답 거부; D05 복제 이미지 OCR/검토/ZIP; D06 소유 게임 실행/버전 이력; D07 동일 장면 FPS/P95 비교; D08 게임 중 AI/스캔 제한 및 세이브 보존. 사용자 실기 결과 없이 성공 표기 금지. 설치 오류 발생 시 메시지 기록하고 중단; 공식 앱 삭제/보호 해제 금지.
5. **RE7:** D01~D03 이후 최초 설정 그대로 실행 관측. 게임 버전, Proton/호환 레이어, 드라이버, 실제 출력과 내부 해상도 별도 기록. 검은 화면/무음/LOW_MEMORY 재현 여부, 로그와 메모리/온도 관측. 원인 단정 금지. 한 설정 그룹씩 승인 후 검증·복원; 실행 실패 상태에서 FPS 비교 금지. 같은 장면 반복 측정.
6. **실험 로컬 AI:** Gradle 실험 빌드 및 SDK 의존/ABI/개인정보·라이선스, 모델 해시와 신뢰 출처, 실제 추론/취소/native close/메모리 확인. 기본 비활성 유지. 게임 실행 우선. 모델/AI 데이터 공개 키는 별도 검토; 신뢰되지 않은 dex/native code 다운로드 금지.
7. **남은 기능 및 출시:** 실기 결과 기반 결함 수정, 번역/이미지 실제 적용·복원 안전성, 엔진/AI update matrix, 멀티파일/플랫폼 확장 검토. 공식 upstream 병합은 별도 브랜치에서 full regression; 이전 공식 1534cf1 시험 병합 1260파일 변경/4충돌 기록 확인. 사용자 승인 및 모든 출시 기준 전 정식 배포 금지.

## 5. 코드와 검증 참고 위치

- .github/workflows/build.yml: 필수 CI/branch allowlist/OCR 플래그/테스트 키, [build apk] 조건. 기본 로컬 AI OFF.
- .github/workflows/ai-independent-apk-verification.yml: 읽기 전용, 기존 성공 artifact/source/hash pin, 실제 SDK v2/v3/aapt 검증. 별도 브랜치에서 성공.
- tools/ci/verify-development-apk.py, apk_inventory.py, check_ocr_inventory.py, apk_signer_report.py, compare_official_apk.py: 실제 아티팩트/권한/서명 보고서 검증.
- tools/ci/android_test_summary.py, compare_unit_test_results.py: JUnit 오류/중복/집계 검사. 후속 strict --require-identical-passes 옵션은 비교용이며 새 테스트 추가 자체를 금지하지 않음. 정당한 ID 변화는 diff 검토.
- tools/release/sign-ai-apk.sh, test_sign_ai_apk.py, make-ai-release-key.sh, publish_ai_data.py: 독립 서명/데이터 도구.
- :ai-core, AiHostBridge, GameAgent, GlossaryStore, ModelRelease, MenuTranslationProvider, GameConfigPreview, GamePatchDialog, FileManagerScreen: 기능 경계. 실제 경로는 Git 검색으로 확인.

## 6. 절대 준수 사항

공식판/세이브/사용자 데이터 보호. 공식 main, release, 태그, 배포 비밀 및 기존 키 임의 변경 금지. 사용자 승인 없는 원본 쓰기/삭제 금지. 유료 API·자동 구매·PC 서버 의존·자동 외부 데이터 전송 금지. Android/Play Protect/DRM/열 보호 우회 금지. 모델 출력은 신뢰하지 않으며 파일 접근·검증·쓰기 권한을 호스트에서 제어한다. 민감 로그/토큰/개인 키/암호 공유 금지. 기기 검증과 모의/단위 테스트 및 패키징을 구분한다. 실패와 skip을 숨기지 않는다. 실제 작업보다 문서 반복이나 불필요한 빌드를 늘리지 않는다.

## 7. 세부 원본 문서 (삭제하지 않음)

- [AI_HANDOVER.md](AI_HANDOVER.md): 장기 개발 이력/검증 근거.
- [AI_DEVELOPMENT_STATUS.md](AI_DEVELOPMENT_STATUS.md): 단계별 구현 및 상태 이력.
- [AI_PROJECT_SPEC.md](AI_PROJECT_SPEC.md): 사용자 요구 및 우선순위 변경.
- [AI_NEXT_TASKS.md](AI_NEXT_TASKS.md): 초기 작업 계획; 최신 순서는 본문 4절.
- [AI_DEVICE_ACCEPTANCE.md](AI_DEVICE_ACCEPTANCE.md): 실기/서명 절차 및 해시.
- [AI_SECURITY_REVIEW.md](AI_SECURITY_REVIEW.md): 보안 경계.
- [AI_MODULE_ARCHITECTURE.md](AI_MODULE_ARCHITECTURE.md): AI 계약, DDAG/DDAM, 모델 lifecycle/rollback/retire.
- [AI_AGENT_RUNTIME.md](AI_AGENT_RUNTIME.md): 로컬 추론 실험 및 한계.
- [AI_RUNTIME_BUDGET.md](AI_RUNTIME_BUDGET.md): 성능 예산.
- [AI_TRANSLATION_PROVIDER.md](AI_TRANSLATION_PROVIDER.md): 번역 adapter/사전 및 모델 후보.

## 8. 최신 체크포인트 — 이후 Work가 갱신

- 2026-11-28: 독립 APK 검증 46113827368 완료(success). 실제 job 로그에서 v2/v3 true 및 1 signer, package 확인. 기존 성공 APK와 동일 해시. 개인 배포 서명 및 기기 미검증.
- 인계 직전 브랜치 HEAD 86dbed8baba892dfd9e8b3c6f2234b3aab776f36. 인계 커밋 후 실제 HEAD 재조회.
- 다음 행동: 기존 성공 후보 보존; 소유자 로컬 키/서명 환경 확인. 사용자의 물리적 기기 또는 키가 필요한 부분은 명확히 요청하고, 없는 상태에서 결과를 꾸미지 않는다.
- Work의 5시간/주간 잔여량 조회 가능 여부는 세션마다 확인. 5% 이하/경고/조회 불가 시 0절 적용. 앞으로 매 체크포인트에 실제 변경/결과/근거/다음 단계/미완료를 기록한다.

### 2026-11-28 Work 재개 — 4절 1단계 인수 완료

- 재개 HEAD `52a30eae1184e9fa2a7a739deb0b94f36dc66bf9`. 이 브랜치의 Actions 실행 목록 1건, 실행 중 0건. 실행 46113827368 / job 140883055366 성공. 원본 로그에서 reviewed.zip 및 reviewed.apk SHA-256 검사 OK, v2/v3 true, signer 1명, 공개 테스트 인증서 및 AI 패키지 일치를 확인했다.
- 빌드 46111306535의 APK artifact 15250811269는 조회 시 미만료, GitHub가 보고한 ZIP digest는 2절과 일치. 새 빌드/서명/기기 시험은 시작하지 않았다.
- 다음 순서인 4절 2~3단계는 소유자 PC에서 진행한다. 이 Work 환경에는 java, keytool, python3, sha256sum이 있으나 PATH에서 aapt, apksigner, adb를 찾지 못했다. 소유자 PC의 OS·Android Build Tools 준비 여부와 기존 독립 키 보유 여부만 확인할 것. 키 파일·암호는 전송하지 않는다. 재서명 이후 공개 인증서 및 APK 해시와 v2/v3 결과를 기록하고 D01로 진행한다.
- 공식 잔여량 수치 조회 불가. 단계별 기록 방식 적용. 이 단계의 변경은 본 문서 한 파일이며 앱 소스 변경 및 미커밋 개발 파일 없음.

### 독립 배포 서명 도구 보완
- 기준 HEAD 39cc0b4ec3fcfeb0c2221dfa2da612ba354d0031. 출력 APK가 v2만 통과해도 게시되던 경로를 수정했다. v2/v3를 명시적으로 요청하며 출력의 v3 확인 전에 최종 파일을 만들지 않는다.
- bash 구문 검사 및 로컬 모의 Android 도구 테스트 5/5 통과. v3 누락·false에서 출력 및 임시 파일 미잔존 확인. 개인 키는 사용하지 않았다. 실제 서명과 D01~D08은 미검증.
- 할당량 조회 불가로 단계별 저장. 다음은 D01 설치 전후 패키지 상태를 읽기 전용으로 기록하는 도구 및 별도 검증 CI.

### D01 패키지 상태 조회 추가
- 기준 HEAD e20be95b5f21d64875be40de8f52e8fa582bcba2. 현재 Android 사용자에 대해 공식판과 독립판의 정확한 패키지 이름 및 versionCode를 출력한다. 저장소의 기존 ADB 기기 선택 로직을 사용하며 설치·실행·쓰기 명령을 호출하지 않는다.
- 로컬 모의 조회 3/3 통과. 부재, 이름이 비슷한 패키지, 잘못된 응답 및 중복 응답을 검사했다. 실제 Galaxy S23+ 및 세이브 데이터는 미검증. 설치 전후 실행 방법은 AI_DEVICE_ACCEPTANCE.md에 기록했다.
- 다음은 두 변경을 독립 검증 workflow에 연결해 호스트 회귀 및 기존 APK의 SDK 검증 결과를 확인하는 단계다.

### 서명·기기 호스트 테스트 CI 연결
- 기준 HEAD 11cf94158cac3e4cf13204992d961339a233a989. 전용 경로 필터를 가진 읽기 전용 workflow를 추가했다. 모의 서명 테스트, 서명 보고서 파서, D01 조회 테스트만 실행한다. 전체 APK 빌드 또는 소유자 키가 필요하지 않다.
- 다음은 이 커밋으로 시작되는 AI release tool tests의 실행 번호·로그·종료 상태 기록. 실제 서명과 설치 전후 조회는 여전히 미검증.
- CI 시작 확인: a6d5ac05bf2769d72de4a43244fe8dbe7109a829 / 실행 46114365598 in_progress. 실행 중인 브랜치 보호를 위해 로컬에 기록하고 종료 후 원격에 함께 저장한다.
- CI 종료 확인: 실행 46114365598 성공. job 140884736950 원본 로그에서 서명 도구 5/5, 보고서 파서 3/3, 기기 패키지 조회 3/3, 총 11/11 통과. 실패·skip 없음. 공개 테스트 APK의 기존 독립 검증 결과는 2절 참조. 전체 APK 재빌드 없음.
- 다음 재개점: 소유자 환경에서 기존 성공 APK 재서명 후 출력 해시·공개 인증서 지문을 기록한다. D01은 설치 전후 동일한 기기와 사용자에서 조회하고 실제 세이브를 별도 확인한다. 키·기기 없이 D01~D08 판정은 보류한다.

### 앱 설정 미리보기와 복구 경로
- 기준 HEAD cdefc507ca2e56a85d1ffec249717dfbe0d9eb4f. 기존 섹션 인식은 줄 끝이 닫는 괄호여야 했다. 주석이 달린 다음 섹션의 키를 이전 섹션 키로 간주하는 경우를 수정했다. 해석이 모호한 헤더에서는 제안을 중단한다.
- Unreal 자동 제안과 편집기의 섹션 인식을 일치시켰다. 첫 줄의 UTF-8 BOM을 보존하며 값 양옆의 공백, 탭, 혼합 줄바꿈을 유지한다. 주석 경계, BOM이 있는 전역 키, 미리보기→적용→복구 바이트 왕복 테스트를 추가했다.
- 실제 앱 소스와 기존 JUnit 테스트를 컴파일하는 별도 JVM workflow를 추가했다. 아직 이 변경의 테스트 통과 및 실기 동작은 확인되지 않았다. 할당량 조회 불가로 단계별 저장, APK 재빌드 없음.
- 설정 회귀 CI 시작 확인: 46114614765 / 085a58e9e036676dbb7c20be6654d6a613ba2737. 실행 중 원격 브랜치를 유지하고 로컬 체크포인트에 기록.
- 설정 CI 46114614765 성공. job 140885523379 원본 JUnit 로그 OK (34 tests). 디스크에 저장한 복제 파일의 적용·복구까지 JVM에서 확인했다.

### 번역 파일 UTF-8 BOM 지원
- 기준 HEAD 085a58e9e036676dbb7c20be6654d6a613ba2737. CSV/TSV/flat JSON 번역 파일에서 시작 BOM을 형식 검사·행 추출 때만 제외하고 원본과 출력에 보존한다. 네트워크 응답/메타데이터에 쓰는 공통 JSON 파서의 허용 범위는 그대로다.
- 파일 탐색과 제작 UI가 쓰는 형식 검사, 변수 검증, 선택 행 편집, ZIP 출력 및 다시 불러오기에 3개 형식을 모두 포함하는 JUnit 테스트를 추가했다. 새 CI 로그 대기. 실제 기기 D04 미검증.
- 번역 회귀 CI 시작 확인: 46114711476 / 508f98ee183bcbbf59851fbedd8e9ce30a0d42d9. 실행 중 변경은 로컬에서 준비한다.
- 번역 CI 46114711476 성공, job 140885812759에서 JUnit 35/35 통과 확인.

### 게임 시작 시 패치 쓰기 및 백업 조회 중단
- 기준 HEAD 508f98ee183bcbbf59851fbedd8e9ce30a0d42d9. GamePatchDialog의 적용·복구가 백업/검증을 마친 직후, 파일 교체 직전에 코루틴 취소와 세션 상태를 다시 검사한다. 최종 검사에서 중단된 적용은 이번에 생성한 백업만 정리한다. 복구 중단은 기존 백업을 보존한다. 세션 상태 검사와 파일 이동 자체는 단일 트랜잭션이 아니다.
- 대화상자를 열 때와 복구 후의 백업 이력 검색도 여유 메모리 및 게임 상태에 따라 보류하고, 디렉터리 순회와 레코드마다 중단을 허용한다. 세션 전환을 모사한 적용·복구·조회 회귀 테스트를 추가했다.
- 다음은 해당 커밋에 대한 Kotlin JVM 회귀 결과 확인. Android UI 컴파일·실제 기기 및 성능 수치는 별도 검증 대상이다.
- 게임 상태 재확인 CI 시작: 46114862256 / 934a13a3fc96e868291b396cd48929a693e627d2. 실행 중 원격 브랜치를 유지한다.
- 세션 전환 및 백업 CI 46114862256 성공, job 140886281698에서 37/37 통과 확인.

### Android 소스 통합 컴파일
- 기준 HEAD 934a13a3fc96e868291b396cd48929a693e627d2. 검증한 성공판의 packs ZIP을 SHA-256으로 고정한 읽기 전용 workflow를 추가했다. OCR 활성/기본 로컬 AI 비활성 앱과 실험형 AI 활성 앱 양쪽에서 compileReleaseKotlin만 실행한다. Rust 및 Vulkan 네이티브 빌드와 APK 패키징은 이 소스 검사 범위 밖이다. 개인 키·모델·기기를 사용하지 않는다.
- 다음은 Gradle 실행과 실제 SDK 의존성 컴파일 결과 확인. 실험형 빌드가 통과해도 실제 추론·ABI 실행·메모리 검증은 별도다.
- Android 소스 CI 시작: 46114950805 / a4b7ce2fd4c00e85ebc39504e20a37901f7b1602. 원격 브랜치를 유지하며 결과를 기다린다.
- 로컬에서 다음 변경 준비 중: AI 취소/제한 시간 범위에 모델 검증과 생성 단계를 포함한다. 모델별 SHA-256 확인 및 네이티브 엔진 초기화 사이의 상태 확인을 연결했다. 추론용 여유 메모리 하한과 시스템 저메모리 판단은 같은 조회 결과를 사용한다. 외부 모듈 호출 자체를 강제 종료하지 않는다. 회귀는 취소된 검증이 모델을 열지 않는지 확인한다.
- Android CI 46114950805 성공. job 140886569488에서 기본 AI/OCR 및 실험 로컬 AI/OCR 양쪽의 Gradle 컴파일 단계 성공 확인.
- 위에 준비한 취소/메모리 변경을 반영하며 모델 생성 중 예산 변경 시 close 및 추론 미시작 테스트도 추가했다. JUnit에 GameAgentTest와 실제 ai-core 계약을 포함한다. 새 Android CI에서는 컴파일에 사용한 AAR의 ARM64 ELF와 POM 라이선스 메타데이터를 확인한다. 실제 모델/세이브/키는 사용하지 않는다.
- CI 시작 확인: 21b404888a7a35b956e9e9178a4fa4d3b01a9c53 / 46115320721, 46115320752. 실행 중 HEAD를 유지하고 종료 시 로그·결과를 함께 저장한다.
- Kotlin 호스트 회귀 46115320752 성공. Android 컴파일이 같은 HEAD에서 진행 중이므로 원격 저장은 종료 후 반영한다.

### 통합 검증 완료 — 다음 세션 재개점
- 검증한 소스 HEAD 21b404888a7a35b956e9e9178a4fa4d3b01a9c53. Kotlin 회귀 실행 46115320752 / job 140887728319의 JUnit 원본 로그에서 45/45 통과. 설정·백업·번역·로컬 AI 작업 취소를 포함한다.
- Android 실행 46115320721 / job 140887728155 성공. OCR을 켠 기본판 및 실험 로컬 AI판 모두 실제 :app:compileReleaseKotlin 성공. tasks-genai-0.10.35.aar SHA-256 08faec83df518356c2ededcd3763ba361a8ad708fff6686b19d110f8a11924d8, jni/arm64-v8a/libllm_inference_engine_jni.so의 ELF AArch64 확인. 해당 SDK POM의 라이선스 표기는 Apache 2.0이다. 모델 라이선스와 실제 네이티브 로딩·추론·메모리 사용은 미검증.
- 새 APK/서명/릴리스 없음. 2절에 보존한 이전 APK에는 이번 소스 변경이 들어 있지 않다. 기존 후보 D01~D08과 새 소스의 실기 결과를 섞지 않는다. 다음 실기 단계는 소유자 키/기기, 새 소스의 APK는 사용자 빌드 지시가 필요하다. 작업 종료 시 남은 미커밋 개발 변경 없음. 할당량 조회 불가로 각 단계 결과를 기록했다.

### 모델 검증 취소 및 누적 번역 초안 검증 보완
- 기준 HEAD f5b6f9f15728403249edeb6b59162c59da22ad9f. 이전 CI 8건은 모두 종료 상태(success); 기존 성공 APK와 공식 브랜치를 유지한다. 공식 할당량 조회 기능 없음.
- 모델 파일의 읽기 완료 직후(EOF 포함)와 검증 반환 직전에 게임/메모리/취소 조건을 다시 확인한다. 중단된 신규 import는 임시 파일을 정리하고 기존 모델은 보존한다.
- GameAgent에서 기존 번역 초안의 원본 ID와 변수/이스케이프를 모델 호출 전 검증하고, 새 번역과 합친 전체 초안을 기존 패치 검증기에 전달해 누적 크기 한도를 적용한다. 결과 검증 뒤에도 코루틴 취소 상태를 확인한다. 기존 초안과 EOF 취소 회귀를 추가하고 LocalModelStoreTest를 전용 JVM CI에 연결했다.
- 이 체크포인트는 코드와 같은 커밋에 저장한다. 새 JVM/Android CI 결과 미확인; 다음 세션은 이 커밋의 Actions 실행 ID와 로그를 먼저 확인한다. 실제 키/모델/기기 미사용, APK 재빌드 및 배포 없음. D01~D08 미검증. CI 실행 중 브랜치 변경 금지.

### 이미지 패치 취소 경계와 복구 회귀
- 기준 HEAD 57706bd204204afa6a9321237f0cae608a6a7e11. JVM 실행 46116016857 / job 140889927670 실제 로그 OK (50 tests). Android 46116016835 상태 in_progress/미정. 실행 중 브랜치를 변경하지 않기 위해 별도 staging 브랜치 ai-image-cancellation-staging-next에 저장; 원래 브랜치에서 Android CI 종료/성공 및 ancestry 확인 후에만 비강제 fast-forward 통합.
- 이미지 패치 ZIP 읽기를 최대 64KiB 단위로 나눠 취소 확인, 검증 콜백 전/후 및 해시 반환 직전 확인, 복사 EOF 및 백업/복구 저널 읽기 취소 확인을 추가. 취소 시 새 복사본 정리 및 기존 저널 유지/재개를 회귀로 검사. 실제 적용 UI 활성화 없음. 차단되는 I/O 자체를 강제 중단하지 않으며 파일 교체와 세션 확인은 단일 트랜잭션이 아니다.
- 이미지 아카이브/트랜잭션 테스트를 기존 JVM CI에 연결. 신규 테스트 실행 결과 미확인. 새 APK/개인 키/실기 없음; D01~D08 미검증. 할당량 조회 불가로 코드와 함께 인계 저장. 다음: 신규 CI 확인 및 필요 시 통합, 이후 이미지 원본 검증/실기 준비. 공식 main과 성공 APK 유지.

### 이미지 취소 회귀 통합 및 PNG 원본 검사 강화
- 이전 Android 46116016835 / job 140889927284 실제 로그: 기본/실험형 Kotlin 컴파일 모두 성공, ARM64 ELF 확인. 이미지 staging 46116294167 / job 140890771803 실제 로그 OK (63 tests). 비교 ahead 1 / behind 0 확인 후 기존 작업 브랜치를 538c344d1ebc97502b35b5fc7c9e574062f5f60d로 비강제 fast-forward 완료.
- 통합 브랜치에서 재실행 46116423039 진행 중이어서 동일 HEAD의 기존 staging 브랜치에서 후속 개발. PNG tRNS 중복·순서·색상형식·팔레트/샘플 범위를 검증하고 CRC 계산 중 64KiB마다 취소를 확인한다. PNG 및 저장소 검사 JVM 테스트 연결. Android 기본 BitmapFactory 디코딩 완료 후 취소를 재확인하며 취소된 bitmap을 recycle한다. 네이티브 디코딩 자체 강제 중단은 아님.
- 새 회귀 및 Android 컴파일 결과 미확인. staging CI 종료 및 원래 브랜치 CI 성공, ancestry 확인 후 통합. Android workflow는 통합 뒤 새 이미지 소스를 컴파일한다. 실제 게임 파일 적용 UI는 비활성 유지. 실기 0/8, 신규 APK/개인 키 없음. 할당량 조회 불가로 단계별 저장.

### DDS/TGA 승인 영역 고정 및 출력 취소
- 통합 46116423039 / job 140891182698 실제 JUnit OK (63 tests). 기준 67c4fbc6eebc5149358fa3ee91842e14e28cd774의 PNG 회귀 46116467883 상태 completed/success; 이전 staging CI 종료 성공 후 동일 staging에서 개발. 원래 작업 브랜치와 모든 staging CI 확인/로그 및 ancestry 확인 후 통합할 것.
- DDS 인코더/검토기가 승인 영역을 독립 스냅샷으로 유지하고 오버플로 없는 좌표 검증을 사용한다. DDS 블록과 TGA 행을 읽은 후 출력 전 취소를 재확인한다. 중단 시 출력 스트림 자체에 이미 쓰인 바이트는 되돌리지 않으므로 기존 호출자의 임시 파일 정리/원본 보호 정책은 유지해야 한다. TGA 구조 검사 시작/완료 시 취소 확인.
- DDS/TGA 기존 및 신규 회귀를 전용 JVM CI에 연결. 신규 회귀 결과 및 Android 컴파일 미확인. 실제 적용 UI 비활성, 기기 0/8, 신규 APK/개인 키 없음. 할당량 조회 불가로 코드와 인계 동시 저장.

### 텍스트 패치 조회 및 초안 무결성
- DDS/TGA staging 46116548417 / job 140891567466 실제 로그 OK (84 tests). 원래 작업 브랜치 대비 ahead 2/behind 0 확인 후 28dc72c9ee01a2be1fa282ffcb7dc9322fb6afce로 비강제 fast-forward 완료. 원래 브랜치 CI를 보호하기 위해 후속 코드는 기존 staging에 저장.
- GamePatchStore의 파일/백업 읽기 중 및 EOF 후 checkpoint를 확인한다. 이력 조회에서 checkpoint 예외(취소/게임 상태 변경)를 손상 백업으로 오인해 삼키지 않는다. 복원 후보 반환 직전 현재 파일 해시를 재확인한다. 실제 복원 전 검사도 기존대로 유지. 조회와 외부 파일 변경은 원자적이지 않다.
- TranslationDraftStore는 잘못된 UTF-16 surrogate가 해시/저장 과정에서 다른 문자로 바뀌지 않도록 대상/원본/초안 값을 검사한다. 불완전하지만 유효한 Unicode 번역 초안은 계속 저장 가능하다. 저장소 테스트를 JVM CI에 추가.
- 신규 테스트 및 최신 Android 컴파일 미확인, 실제 기기 0/8, APK/키/배포 없음. 할당량 조회 불가, 코드와 인계 동시 저장. 다음: 양쪽 CI 확인 후 비강제 통합, 최신 소스 Android 검증.

### ChatGPT 교환 체크포인트 취소 및 UUID/Unicode 검사
- 기준 632f8a1028028193e1d4bd99cff80003af2f7e09, staging 46116709235 / job 140892067915 실제 JUnit 로그 OK (93 tests). 통합 Android 46116662577 in_progress/미정; 원래 브랜치는 변경하지 않고 staging에서 후속 작업.
- ExchangeCheckpoint 저장의 최종 이동 전 및 읽기 도중 취소 확인, GamePatchDialog의 코루틴/게임 예산과 연결. 저장 취소 시 기존 재개 정보 보존, 신규 임시 파일만 정리. 자동 공유/전송 없음. GameAgent 외부 교환의 UUID 형식 정규 검증, 잘못된 UTF-16 거부, 로그 꼬리 경계의 surrogate 분리 방지. 경계가 달라진 이전 요청은 fingerprint 불일치 시 새 요청 필요.
- ChatGPT 교환 및 체크포인트 기존/신규 JVM 테스트 연결, Android workflow 경로 보완. 신규 테스트 및 Android 소스 컴파일 미확인. 기기 0/8, APK/키/배포 없음. 공식 할당량 조회 불가로 코드와 인계 동시 저장. 다음: CI 결과 및 ancestry 확인 후 통합, 최신 Android 컴파일.

### 서명 모델 검증 및 정리 안정성
- staging 46116918317 / job 140892706515 실제 JUnit OK (106 tests). Android 46116662577 / job 140891924333 기본/실험형 컴파일 성공 및 ARM64 확인. ahead 2/behind 0 및 종료 상태 확인 후 원래 브랜치를 8d9510df8c90b23121e79e31564c335f1db7780d로 비강제 통합. 통합 CI 보호를 위해 다음 변경은 staging에 저장.
- ModelRelease는 읽기 완료 직후 및 검증 반환 전 예산 확인, 잘못된 저장소 경로 거부, 정리 후보의 파일 크기/수정·생성 시각/가능한 파일 식별자를 검토 상태에 바인딩하고 삭제 직전 재검사한다. 이는 파일 시스템 메타데이터 기반 검사이며 외부 변경과 삭제를 원자적으로 묶거나 콘텐츠를 인증하지 않는다. 정리 취소 후 기존 정책대로 orphan이 남을 수 있다.
- AiModelUpdates의 검토 명세 바이트를 방어 복사하고 GamePatchDialog의 모델 가져오기/rollback/정리 콜백에 코루틴 취소 검사를 연결. 실제 개인 키/모델은 사용하지 않으며 신규 JVM 테스트는 생성한 시험용 키와 가짜 바이트만 사용한다.
- 신규 ModelRelease 회귀와 기존 테스트를 JVM CI에 연결하고 Android 경로 필터를 보완. 신규 테스트 및 Android 컴파일 미확인. 실기 0/8, APK/키/배포 없음. 할당량 조회 불가로 코드와 함께 저장. 다음: CI 결과/ancestry 확인 후 통합, 실기 준비.

### 서명 메뉴 사전 상태 무결성 및 취소
- 기준 26c702dfe1120129472df7a7a42efef0c836a9d5, 모델 CI 46117306975 completed/success. 이전 staging CI 종료 성공 확인 후 동일 브랜치 사용. 통합 Android 46117252262 및 모델/사전 CI 결과를 확인한 후 ancestry에 따라 비강제 통합.
- GlossaryStore 상태 파일에 SHA-256 체크섬과 신규 magic을 추가. 기존 상태는 읽을 수 있고 정상 설치/rollback 때 새 형식으로 저장한다. 손상된 최고 버전/메타데이터 검출, 파일 크기 제한 읽기 및 잘못된 디렉터리 거부. 체크섬은 우발적 손상 검출용이며 공격자에 의한 상태 재작성·롤백 방지의 암호학적 보증이 아니다. 번들 RSA 서명 검증은 유지한다.
- 사전 가져오기 EOF 이후 및 저장 전 예산 확인, UI 코루틴 취소 연결. 테스트는 시험용 키/가짜 데이터로 기존 상태 마이그레이션, 손상 및 취소 시 원본 보존을 검사한다. 신규 JVM 및 Android 결과 미확인. 실기 0/8, 신규 APK/개인 키/배포 없음. 할당량 조회 불가로 코드와 함께 인계 저장.

### 게임 성능 측정 관측 일관성
- 기준 7fffd4873945ff53279d1aebadcaadf04a08c7d5. 사전 CI 46117403906 completed/success, Android 46117252262 in_progress/미정. 사전 CI 종료 성공 이후 기존 staging에서 진행. 이전 모델 CI 46117306975 / job 140893891540 실제 OK (113 tests).
- 명시적 10초 측정의 게임 ID/라이브 프레임/Steam 포커스/PID/해상도 동일성 검사를 호스트 테스트 가능 함수로 분리하고 각 프레임 스냅샷 전후 및 요약 전후에 재검사한다. 관측 필드는 원자적 스냅샷이 아니므로 짧은 전환을 모두 감지한다는 보장은 없다. 장면 이름은 80자 및 제어문자 검증, 비교 조건이 달라지면 비교 불가 처리. 성능 인과관계 주장 없음.
- 기존 및 신규 성능 회귀를 JVM CI에 연결. 새 Android 소스 검증/실기 수치 없음. 기기 0/8, 신규 APK/개인 키/배포 없음. 할당량 조회 불가로 코드와 인계 동시 저장. 다음은 CI 검증 및 ancestry 확인 후 통합, 호환성 기록 검증 보완.

### 호환성 실행 기록 범위 검증
- 성능 staging 46117523145 / job 140894564022 실제 로그 OK (130 tests), 사전 46117403906 / job 140894193843 OK (118 tests), 통합 Android 46117252262 / job 140893735015 기본/실험판 컴파일 성공. 기존 작업 브랜치 ahead 3 / behind 0 확인 후 b104c32e47d59cb24586fbadf886fcd254cf8267로 비강제 통합. 후속 변경은 CI 보호를 위해 staging에서 저장.
- GameHistoryQuery 게임 ID/버전의 제어문자·잘못된 Unicode를 거부, 조회된 행의 게임과 버전 범위를 재검증. GameRunHistory는 커서 이동 전후/종료 시 checkpoint로 조회를 취소하고 잘못된 시간/초과 행을 거부한다. 서비스는 코루틴과 게임/메모리 상태를 조회 및 로그 분석 전후 재확인하고, 요약 입력의 100개 상한과 기록을 검증한다. 기존 기록 자동 삭제/수정 없음. 잘못된 기존 기록이 발견되면 조회 오류로 처리한다. 게임 ID 버전 검사는 실행 자체의 성공 증명이 아니다.
- 순수 JVM 쿼리 회귀를 CI에 추가, Android 서비스 회귀는 기존 앱 테스트 경로에 저장했으나 전용 JVM CI에는 Android 의존성으로 포함하지 않았다. 최신 테스트·Android 컴파일·실기 미확인. D01~D08 0/8, 새 APK/키/배포 없음. 할당량 조회 불가로 코드와 인계 동시 저장. 다음: 양쪽 CI 결과 확인 후 통합 및 실기 검증 준비 정리.

### D01 실기 증거 비교 도구 보완
- staging 46117644902 / job 140894941321 실제 JUnit OK (136 tests); 통합 JVM 46117610261 / job 140894831009 OK (130 tests). 통합 Android 46117610244는 조회 시 진행 중이므로 기존 작업 브랜치 b104c32e47d59cb24586fbadf886fcd254cf8267를 변경하지 않는다.
- 읽기 전용 패키지 조회에서 Android 사용자 전환 여부를 최종 재확인. 오프라인 JSON 비교에 엄격한 스키마/크기/중복 키 검사, 공식판 버전 유지, 독립판 최초 설치 전 부재/설치 후 기대 versionCode 검증을 추가. 도구는 ADB 쓰기/설치, 기기 동일성/서명/세이브 확인을 하지 않으며 D01 전체 성공을 반환하지 않는다. 기존 APK와 새 소스의 차이를 실기 문서에 명시.
- 새 Python 모의 테스트 및 기존 release tool CI를 staging에도 연결. 실제 키/기기/게임 사용 없음, 신규 APK 없음. 공식 잔여량 조회 불가로 코드/인계 동시 저장. 다음: Android와 staging CI 종료 및 실제 로그 확인, ancestry에 따른 비강제 통합. 사용자 APK 빌드 지시 및 실제 소유자 서명/기기 검증 필요.

### 최신 소스 수동 APK 빌드 경로 준비
- staging 46117776849 / job 140895333745 실제 Python 결과 5+3+6=14 테스트 통과. Android 46117610244 / job 140894831154 기본/실험판 컴파일 성공 및 ARM64 확인. staging 대비 기존 작업 브랜치 ahead 2/behind 0 확인 후 80f64eb40bd69e9d96f65bcfc3b4c6fb45881724로 비강제 통합. 이전 최신 JVM 136/136(46117644902) 유지. 통합 CI 보호 위해 다음 변경 staging에 저장.
- Build APK의 기존 workflow_dispatch 경로에서 독립 검증 브랜치의 OCR 활성 및 실패 APK 진단 보존을 준비. 자동 push 브랜치 목록, 작성자 검사, 개인 키 검사, 정식 main 서명/게시 가드 변경 없음. 별도 APK 빌드나 workflow dispatch 호출 없음. 실험 로컬 AI 기본 false. 수동 실행 및 검증 절차와 정적 회귀를 추가. 정적 테스트가 실제 전체 빌드를 대신하지 않는다.
- 새 CI 결과 미확인, 실제 소유자 서명 및 D01~D08 0/8. 할당량 조회 불가로 코드/인계 동시 저장. 다음: 신규 정적 CI 및 기존 통합 Android/JVM/도구 CI 종료 확인, ancestry 비강제 통합, 사용자 빌드 명시 지시 이후 수동 실행 가능 여부 확인. 기존 성공 APK/공식판 유지.

### 후속 APK 독립 검증 보완
- staging 46118381680 / job 140897198841 17/17, 통합 JVM 46118322222 / job 140897011508 136/136, release 46118322161 14/14. Android 46118322187 조회 시 진행 중이므로 기존 브랜치 80f64eb 유지.
- verify-development-apk.py의 v3 및 선택형 aapt 실제 패키지/versionCode 검증 추가, 동일 검증 임시 APK 사용. 기존 인벤토리 정보와 실제 SDK 결과 구분, 모의 테스트 전용 CI 연결. 신규 테스트 미확인. 실제 새 APK/SDK 검증/키/기기/배포 없음. D01~D08 0/8. 할당량 조회 불가로 코드와 인계 저장. 다음: CI 종료와 ancestry 확인 후 통합. 명시 APK 빌드 지시 필요.

### APK 검증 CI 실패 수정
- staging 46118561356 / job 140897748482 실패: 이전 17개 테스트 통과 뒤 신규 APK 검증 모듈 import SyntaxError line 98. 문자열 치환에서 정규식 끝의 달러와 따옴표가 치환 토큰으로 해석되어 코드 일부가 중복된 것이 원인. 실패한 커밋을 통합하지 않았다. 중복 제거 및 v3 정규식 복구, 주요 함수 각 1개 확인. 회귀 테스트 그대로 유지. 수정판 CI 결과 미확인.
- 통합 Android 46118322187 / job 140897011482 기본/실험형 compileReleaseKotlin 모두 성공, ARM64 확인. 통합 JVM 136/136 및 release 14/14 성공. 수정 staging CI 종료 성공과 ancestry 확인 후 비강제 통합. APK 빌드 승인 없음, 실기 0/8, 개인 키/배포 없음. 공식 할당량 조회 불가로 코드와 함께 체크포인트 저장.

### 최종 통합 및 APK 승인 대기
- 수정 staging 46119587942 / job 140900921715 실제 로그 5+3+6+3+13=30/30 Python 통과. 구문 오류 해결 확인. 기존 작업 브랜치 80f64eb 대비 ahead 3/behind 0, 양쪽 CI 종료 확인 후 405e90afe5c6854d5fca3bc96cde92c61547e961로 비강제 통합 완료. 변경은 빌드/검증 도구 및 문서 7파일이며 앱 소스는 Android 46118322187 기본/실험판 성공 기준과 동일하다. JVM 46118322222 136/136. 통합 release CI 46120105990 in_progress 시점 기록.
- 이전 APK 15250811269 메타데이터: {"expired":false,"digest":"sha256:9bcb719ddcad35148d90ffed0da6494d4fc9bb2e0339c70d3448a6223b630616"}. 현재 조회는 메타데이터 확인이며 새 다운로드/서명/기기 시험 아님.
- 현재 연결 도구에서 새 workflow_dispatch 실행 기능을 찾지 못했다. 명시 사용자 빌드 승인 및 통합 CI 성공 이후 GitHub Actions Build APK 수동 실행(브랜치 ai-independent-apk-verification-next, rebuild_preloads=false) 경로를 사용한다. 승인 없이 자동 빌드 트리거를 추가하지 않는다. 최신 source SHA와 실제 run ID를 기록한 뒤 산출물 및 전체 JUnit 결과 검증. 개인 키/실기/배포 없음, D01~D08 0/8.
- 할당량 조회 불가로 통합 사실 및 다음 행동을 staging 문서에 저장. 통합 CI 실행 중인 원래 브랜치는 변경하지 않음. 다음: 46120105990 결과 확인, 문서 커밋의 비강제 통합, 사용자 명시 빌드 지시/수동 실행. 불필요한 소스 변경 및 반복 빌드 없음.

### 통합 최종 호스트 검증 완료 — 빌드 승인 대기
- 기존 작업 브랜치 통합 release CI 46120105990 / job 140902531174 실제 로그 5+3+6+3+13=30/30 성공. JVM 136/136 및 Android 기본/실험형 컴파일 성공 기록 유지(앱 소스 변경 없음). 문서만 추가한 staging c2c9616d187bfbe24a39b84eb2be98bbb34d5da9와 통합 기준 405e90afe5c6854d5fca3bc96cde92c61547e961 ancestry 확인, ahead 1/behind 0.
- 코드 추가 및 재검증 반복 없이 신규 APK 명시 승인 대기. 현재 연결 도구에는 workflow_dispatch 실행 기능 없음. 승인 후 Actions > Build APK > Run workflow에서 ai-independent-apk-verification-next 선택, rebuild_preloads=false. 만약 UI에서 브랜치/실행 버튼이 보이지 않으면 승인 이후 별도 정식 경로를 확인한다. 실제 APK 빌드/실기/개인 키/배포 없음. 이전 APK와 새 소스 구분 유지. D01~D08 0/8. 할당량 조회 불가로 이 단계도 저장.

### 사용자 APK 빌드 명시 승인
- 사용자 지시: “빌드 시행해줘”. 기존 수동 dispatch 호출 도구 없음. 승인된 기존 Build APK push 경로인 ai-device-validation-next의 [build apk] 조건을 사용한다. 기존 빌드 브랜치 3f3eaab6920c8f149dd5bd9ad34a77962b6baeb8는 최신 통합 1c42b91f23832ddb8727387bd66b7b63b5933d50의 조상(ahead 41/behind 0); 실행 중 CI 없음 확인. 소스/보호 설정을 변경하지 않고 인계 문서만 추가한 커밋을 비강제 fast-forward한다. 공식 main/릴리스/키/기존 아티팩트는 유지한다. OCR true, 실험 로컬 AI 기본 false, 독립 패키지 com.droiddeck.launcher.ai. 작성자/키 검사 등 기존 CI 보호 유지. 빌드 실행 및 결과는 원격 ref 변경 후 실제 Actions 조회로 확인해야 한다.
- 기존 성공 APK는 계속 이전 3f3eaab6920c8f149dd5bd9ad34a77962b6baeb8에 해당한다. 새 APK, 서명, 전체 테스트, 실기는 아직 미검증. D01~D08 0/8. 공식 할당량 조회 불가로 승인 및 소스 체크포인트를 선저장.

### 승인된 신규 APK 빌드 실행 확인
- ai-device-validation-next를 c9742460981a42411334c3d1db7a57140dacd17f로 비강제 fast-forward 완료. 실제 Actions [46121382760](https://github.com/jcy565/DroidDeck/actions/runs/46121382760) / head c9742460981a42411334c3d1db7a57140dacd17f / 조회 상태 in_progress/미정 확인. 빌드 브랜치는 실행 종료까지 변경하지 않는다. 이 인계 갱신은 비빌드 브랜치에만 저장. 기존 APK 및 공식 main 보존. 결과/새 APK SHA-256/전체 Android·Python 테스트/서명은 미확인. 다음: 실제 실행 로그 및 아티팩트 확인, 성공 시 새 APK 독립 검증, 소유자 서명 및 D01~D08. 실패 시 실제 원인에 한해 수정하고 보호 정책 우회 금지. 공식 할당량 조회 불가로 실행 ID 선저장.

### 신규 APK 빌드 중간 검증
- 실행 46121382760, 소스 c9742460981a42411334c3d1db7a57140dacd17f, 현재 in_progress/미정. 소스 고정 job 140906473667 실제 checkout 로그에서 c9742460981a42411334c3d1db7a57140dacd17f 일치 확인. Xbox ARM64 job 140906518448 성공, backend/source 아티팩트 메타데이터 조회: 15253208328 (xbox-arm64), 15253123681 (xbox-corresponding-source). 실제 다운로드/내용 검증은 아직 하지 않았다.
- 조회된 작업: Pin source tree=completed/success; Python and native regression tests=in_progress/미정; Xbox ARM64 backend=completed/success; Store engines host tests=in_progress/미정; components-runner / Pick runner=completed/success; Prepare verified components=in_progress/미정. 아직 새 APK 및 전체 테스트 결과를 성공으로 판정하지 않는다. 빌드 브랜치 변경 금지 유지. 공식 할당량 조회 불가로 비빌드 브랜치에 단계 기록. 다음: 동일 run의 종료/로그/아티팩트 확인 후 성공하면 독립 검증, 실패하면 실제 원인 분석. 소유자 서명/실기 0/8 유지.

### 최신 사용자 지시 — 개발용 APK 빌드 사전 승인 상시 부여
- 2026-11-28 사용자: “앞으로는 승인없어도 알아서 빌드시작해”. 이 규칙은 이전 인계서의 개발용 APK 빌드 개별 승인 요구 및 승인 대기 지시보다 우선한다. 후속 작업 세션에서 필요한 경우 별도 질문 없이 개발 전용 APK 빌드를 시작한다. 단순 문서 변경이나 동일 소스의 중복 빌드는 피하고, 소스 통합·회귀 확인·CI 상태 확인 이후 기존 개발용 빌드 브랜치의 [build apk] 경로를 사용한다. 실행 중인 빌드 브랜치는 변경하지 않고 종료 후 로그 및 결과를 확인한다. 테스트 실패, 키 검사, 배포 보호 등 기존 가드를 우회하지 않는다.
- 허용 범위는 개발용 APK 생성 및 해당 CI 검증에 한정된다. 공식 main/릴리스 변경, 정식 배포, 개인 서명 키 생성·접근·전송, 실제 기기 설치·원본 파일 및 세이브 변경, 유료 서비스는 기존 승인 및 보호 규칙 유지. 할당량 조회 불가 시 선제 인계 유지.
- 현재 승인된 빌드 46121382760 / c9742460981a42411334c3d1db7a57140dacd17f 는 조회 시 in_progress. 중복 빌드나 빌드 브랜치 변경 없음. 이 규칙은 비빌드 브랜치 양쪽에만 저장. 다음: 동일 실행 결과 검증, 필요할 때 자동으로 후속 개발 빌드 진행.

### 신규 APK 빌드 — 스토어 엔진 검증 완료
- 실행 46121382760 / c9742460981a42411334c3d1db7a57140dacd17f / in_progress/미정. Store engines host tests job 140906518449 성공, 실제 Rust 로그: 171 passed, 0 failed, 0 ignored. Xbox/소스 고정/러너 선정 성공 유지. 전체 Python·네이티브 및 컴포넌트 준비는 아직 최종 미확인.
- 조회된 작업: Pin source tree=completed/success; Python and native regression tests=in_progress/미정; Xbox ARM64 backend=completed/success; Store engines host tests=completed/success; components-runner / Pick runner=completed/success; Prepare verified components=in_progress/미정. 새 APK, 전체 Android 테스트 및 실제 서명은 미확인. 기존 성공 APK 보존. 현재 실행을 중복 시작하거나 실행 중 빌드 브랜치를 변경하지 않는다. 할당량 조회 불가로 비빌드 브랜치에 기록. 다음: 동일 실행의 종료/로그/아티팩트 확인; 실패 시 원인 수정, 필요시 별도 승인 없이 후속 개발용 빌드. 실기 D01~D08 0/8.

### 전체 APK 빌드 Python 회귀 실패 수정
- 46121382760 / Python job 140906518436 실패. 실제 로그 test_ci_checks.CiChecksTest.test_ocr_variant_is_shared_by_build_tests_and_isolated_diagnostics 의 OCR 브랜치 고정 목록 불일치 1건. 838/723/651개 시험 보고, 723개 그룹 실패 1 skip 6. 나머지 그룹 세부 skip/집계는 로그 확인 후 기록. 신규 OCR 허용 브랜치를 기대값에 반영하되 기존 공통 플래그/정식 배포/실패 진단 가드를 유지. 기존 test_ci_checks 12개 전체를 release 도구 CI에 연결하여 다음 전체 빌드 전 선행 검증. 추가 테스트는 독립 브랜치 자동 push 미허용 및 실패 진단 포함을 확인한다.
- 기존 빌드 Android job 140907126730 진행 중, 컴포넌트 준비 성공. 실행 중 빌드 브랜치 변경 금지. 수정은 staging에만 저장; 신규 CI 및 기존 APK/Android 종료 확인 전 재빌드하지 않는다. 사용자 상시 승인에 따라 성공한 수정 통합 후 필요시 자동 재빌드. 기존 실패 실행은 성공판으로 간주하지 않는다. 공식 main/키/데이터 변경 없음. 할당량 조회 불가로 코드와 인계 동시 저장.

### OCR 회귀 수정 사전 검증 성공
- staging 46121727140 / job 140907508557 실제 로그 5+3+6+3+13+12=42/42 통과, 기존 test_ci_checks 전체 통과. 작업 브랜치 14c38d6 대비 ahead 1/behind 0 확인 후 37dba4bab658a74ad28e5eb0b9652a0143c41969 비강제 통합. 원래 빌드 46121382760은 조회 시 in_progress/미정; Android job 140907126730 in_progress, 기존 Python 실패 유지. 빌드 종료 전 브랜치를 변경하지 않는다.
- 현재 작업 브랜치 통합 release CI 상태는 별도로 확인한다. 할당량 조회 불가로 staging에 검증 기록을 저장. 다음: 기존 전체 빌드 종료 및 Android 로그 확인, 수정판 통합 CI 성공 및 ancestry 확인 후 [build apk] 경로를 자동 재실행. 원래 실패 실행 APK는 설치 후보가 아니다. 기기/키/배포 없음.

### 수정판 통합 CI 성공 — 기존 Android 빌드 종료 대기
- 통합 CI 46121778821 / job 140907664109 실제 로그 42/42 성공. 기존 실패 Python 실행 838+723+651=2212개 회귀 시험 중 2161 통과, 1 실패, 50 skip; 별도 서명 5/5 통과. 전체 실패 원인은 앞서 수정한 OCR 기대값 불일치. 컴포넌트 artifact 15253643907 (prepared-components) 존재 확인. 신규 APK artifact 미확인.
- 기존 APK 실행 46121382760 조회 시 in_progress/미정. Android job 140907126730 Build 진행 중 관측. 기존 실행 종료 및 로그 확인 후 자동 재빌드; 새 코드 변경 없이 이미 통과한 수정판 사용. 할당량 조회 불가로 비빌드 브랜치에 체크포인트 기록. D01~D08 미검증.

### 전체 APK 실행 종료 및 오류 수정판 자동 재빌드
- 46121382760 전체 failure: Python 기존 OCR 기대값 오류 1건. Android job 140907126730 성공: APK build 7m21s, Android unit tests 12m8s, 실제 XML summary passed=4303 skipped=0, known/other failures 없음. 공개 테스트 인증서 a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc, v2/v3 true, OCR inventory matches requested profile. APK artifact 15255210432 ZIP sha256 711f68fba15bc9cc952b642a6486e6a9c916c5d111b160476112fb774ecb88d6; inventory 15253443924 ZIP sha256 37dcec2a21252f96e6708c57c5271d4bade40a1a39162bf2bf94222e336019a8; unit XML 15255120612. 실패한 실행은 최종 후보가 아니다. 공식 sign/packs/announce/catalog skipped.
- 수정판 42/42 staging 및 통합 CI 성공 확인, 기존 빌드 브랜치와 ahead 7/behind 0. 사용자 상시 승인에 따라 최신 수정판으로 [build apk] 비강제 fast-forward 실행. 변경은 Python 테스트 기대값/사전 CI/문서뿐이며 앱 소스 및 보호 가드 동일. 새 실행 ID 확인 전 성공 판정 금지. D01~D08 0/8, 소유자 서명/배포 없음. 할당량 조회 불가로 인계 동시 저장.

### 자동 재빌드 실행 확인
- 실제 신규 실행 46124010382 / d8c84a5faec188c4c3d2cbf61df8b234495a1668 / in_progress/미정. https://github.com/jcy565/DroidDeck/actions/runs/46124010382 . 기존 실패 run 46121382760 결과 및 아티팩트 보존. 빌드 브랜치 변경 금지. 신규 전체 테스트/새 APK/실기 아직 미확인. 다음: 46124010382 종료 및 전체 로그 확인, 성공 시 신규 해시와 인증서 독립 검증, 기기 D01~D08. 할당량 조회 불가로 비빌드 브랜치에 실행 ID 저장.

### 자동 재빌드 중간 상태 확인
- 46124010382: 소스 고정 140914437824, Xbox ARM64 140914507676, 러너 선정 140914507714 성공. 스토어 엔진 140914507592, Python/네이티브 140914507622 (Steam import and game environment tests), 컴포넌트 140914527791 (Mali bridge Linux side) 진행 중. 신규 APK 및 전체 결과 미확인. 빌드 브랜치 변경 및 중복 실행 없음. 할당량 조회 불가로 비빌드 브랜치에 기록. 다음: 동일 실행 결과 및 실제 로그/아티팩트 검증. 실기 0/8, 개인 서명 미완료.

- 후속 조회: 46124010382 스토어 엔진 job 140914507592 completed/success. Python 140914507622 Steam import and game environment tests 진행 중, 컴포넌트 140914527791 x86 preloads FEX guests (glibc 2.31) 진행 중. 현재 6개 조회 작업 중 4개 성공. 전체 APK 결과 미확인. 빌드 브랜치 변경 없음, 할당량 조회 불가로 단계 기록.

- 46124010382 후속 조회: Prepare verified components 140914527791 및 build-runner 140915078226 성공. Compile APK and Android tests 140915109409 queued, Python/native 140914507622 in_progress. 조회 작업 8개 중 6개 성공. 전체 결과 및 신규 APK 미확인. 빌드 브랜치 변경 없음. 할당량 조회 불가로 주요 단계 기록.

- 46124010382 Python/native job 140914507622 completed/success (job 상태 확인, 상세 테스트 집계 미확인). Android 140915109409 in_progress / Build the add-on packs. 조회 8개 작업 중 7개 성공. 신규 APK/전체 실행 결과 미확인. 빌드 브랜치 변경 없음, 할당량 조회 불가로 주요 단계 저장.

- 46124010382 Python/native job 140914507622 원본 로그 확인: 841+720+651=2212개, skip 34+9+7=50, 통과 2162, 실패 0. 별도 서명 도구 5/5 OK. Android 140915109409는 Build the add-on packs 진행 중. 신규 APK 및 전체 실행 미확인. 빌드 브랜치 변경 없음, 할당량 조회 불가로 기록.

- 46124010382 스토어 엔진 140914507592 원본 로그 171 passed, 0 failed, 0 ignored. Android 140915109409 현재 Build 단계 진행 중. 신규 APK/Android 결과 미확인. 빌드 브랜치 변경 없음. 할당량 조회 불가로 주요 단계 저장.

- Run 46124010382 job 140915109409: Build, store library check, testkey v1/v2/v3 signing, signature check, inventory generation, pinned official 0.7.1 comparison and inventory artifact upload all success. Unit tests step 42 in progress; final APK upload and actual device verification pending. No build source changes; quota unavailable.

- Run 46124010382 completed successfully, Android 4303 passed, prior host 2338 passed, total 6641 passed, 50 host skipped. APK artifact 15255820290 (105546315 bytes), inventory 15254459803, unit results 15255448428, packs 15255309103. Development testkey only. Independent post-build verification and D01–D08 actual device checks pending. No build source changes.

### 2026-11-28 실제 기기 설치 차단 — D01 중단 / 보안 검토 우선
- 사용자 Galaxy에서 '악성 앱으로 의심' 설치 차단 화면 제공. 설치 성공으로 기록하지 않는다. 보안 해제, 우회 설치, 탐지 회피 목적의 서명/패키지/권한 변경 금지. 공식 앱 및 세이브 보존.
- 사용자 첨부 app-release.apk 실제 로컬 ZIP CRC 전 항목 정상, 1012 entries, SHA-256 e8cb6c70216be904f7f597c4ab5963a3cefe93c3aa8f8ec40701d6556c77ff70. 내장 VCS revision d8c84a5faec188c4c3d2cbf61df8b234495a1668. binary manifest 직접 파싱: com.droiddeck.launcher.ai, versionCode 19, versionName 0.7.1. keytool 인증서 SHA-256 a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc, 공개 테스트 키, 인증서 SHA1withRSA. 이는 악성 판정 원인의 증명이나 안전성 증명 아님.
- Actions 46124010382의 9개 개발 job 성공, 4개 배포 job skip. job 140915109409 원본 로그에서 CI APK v2/v3 true 및 동일 인증서 확인. artifact 15255820290 ZIP digest fb527aa8335280c83486b6242cb7830f1910b3d3c1a0451002462f94f1f195e3. ZIP digest와 첨부 APK digest는 다른 대상; 원본 artifact 내부 APK와 바이트 동일 여부 아직 미확인. 로컬 apksigner 미설치로 첨부 APK v2/v3 독립 검증 미완료.
- manifest 실제 uses-permission 26개(병합 포함), 전체 저장공간, 패키지 설치/삭제 요청, 위치, 카메라, 마이크, WRITE_SETTINGS 등 존재. DUMP 및 BIND_ACCESSIBILITY_SERVICE는 uses-permission이 아니라 호출자 제한 컴포넌트 권한. WirelessAdbPairingService exported=false. 소스 manifest d8c84a5에서 동일 선언 확인. 존재만으로 실제 악성 행위를 추정하지 않는다.
- 다음: 기존 artifact 내부 APK 해시 대조, 공식 기준과 코드/권한 및 의존성 출처 검토, 최소 권한/외부 컴포넌트의 필요성 검토, 공식 보안 검토 경로로 차단 판정 확인. 실제 위험 요소를 제거하는 경우 기능 회귀 검증. 현재 원인 불명, 앱 코드/서명/빌드 변경 없음. D01 차단, D02–D08 미검증. 할당량 조회 불가로 단계별 기록.

### 설치 차단 후속 — 공식 소스 대조 및 접근성 검토
- 첨부 APK 재검사: CRC 정상, 1012개 ZIP 항목, 36개 .so, SHA-256 e8cb6c70216be904f7f597c4ab5963a3cefe93c3aa8f8ec40701d6556c77ff70. minSdk 26, targetSdk 28, compileSdk 34. 공개 테스트 인증서 및 인증서 자체 SHA1withRSA 확인. 로컬 Android SDK 서명 도구 부재, artifact 내부 APK와 동일성은 미확인.
- 공식 기준 cdff94e72c45da50af43bbceff28effaf0179caf와 빌드 소스 d8c84a5faec188c4c3d2cbf61df8b234495a1668의 app/src/main/AndroidManifest.xml 실제 대조: 소스 uses-permission 선언 23개 동일, DUMP/접근성 서비스의 호출자 제한 및 WirelessAdbPairingService 선언 동일. 병합 APK 26개와 구분. home_button_service.xml 및 network_security_config.xml 내용 동일. HomeButtonService.kt 내용 동일. 접근성 설정은 키 이벤트 필터링 및 창 내용 조회 가능; 서비스 코드상 컨트롤러 홈 단축키, 활성 세션 키보드 단축키, 지원 듀얼 화면 관찰 기능이 존재. 추가 화면 관찰 경로 및 권한 필요성을 검토해야 하며, 이 비교는 동작 안전성 증명이 아니다.
- 기존 공식 기능의 권한을 AI가 새로 요청했다는 근거 없음. 탐지 회피 목적의 권한/서명/패키지 변경 금지. D01 설치 차단 유지, D02–D08 미검증. 앱 코드 변경/재서명/재빌드 없음. 다음: 신뢰 가능한 기존 아티팩트 내부 해시 대조, 네이티브 및 의존성 출처와 실제 동작 검토, 불필요한 접근 범위가 입증되면 기능 보존 회귀와 함께 축소, 공식 보안 검토 요청. 할당량 조회 불가로 이 문서만 갱신.

### 설치 차단 후속 — ELF 정적 검사 및 Gradle 의존성 비교
- 첨부 APK 재조회 SHA-256 e8cb6c70216be904f7f597c4ab5963a3cefe93c3aa8f8ec40701d6556c77ff70, ZIP 1012 entries, CRC 정상. 읽기 전용 로컬 readelf -W -l -d 검사, APK를 실행하지 않음. 전체 ELF 84개: Android lib ARM64 36개, assets ARM64 40개/x86_64 5개/i386 3개. assets .so 24개, 기타 ELF 24개. readelf 오류 0, 실행 가능한 GNU_STACK 0, RWX LOAD 0, TEXTREL 태그 0. DT_NEEDED 고유 이름 95개. 모든 .so가 ELF 형식. 정적 헤더/동적 태그 검사이며 동작 분석, CVE 검증, 안전성 보증이 아님.
- 공식 cdff94e72c45da50af43bbceff28effaf0179caf와 첨부 소스 d8c84a5faec188c4c3d2cbf61df8b234495a1668의 app/build.gradle 비교: 기존 직접 의존성 선언 26줄 공통, 삭제 0, 추가 5줄 (:ai-core, ML Kit text-recognition/Chinese/Japanese 16.0.1, opt-in MediaPipe tasks-genai 0.10.35). OCR APK에 libmlkit_google_ocr_pipeline.so 존재. Gradle 선언과 실제 전이 의존성의 출처/해시 검증은 구분. 실험 로컬 AI 기본 비활성이라는 기존 설정 유지. CI job 140915109409 로그에는 ZIP artifact 15255820290 업로드 크기 105546315 확인, 첨부 APK 내부 해시와 같은지 증명할 원본 파일 해시 로그는 찾지 못함.
- 다음: 신뢰 원본 inventory 및 APK 내부 해시 대조, SBOM/전이 의존성 및 네이티브 provenance 검토, 실제 위험 발견 시 안전성 개선과 기능 회귀, 공식 보안 검토. D01 차단/D02-D08 미검증. 앱 소스/서명/빌드 변경 없음. 할당량 조회 불가로 인계 갱신.

### 설치 차단 후속 — 원본 빌드 산출물 동일성 검증 완료
- Actions 46124010382에서 APK artifact 15255820290 및 inventory artifact 15254459803을 실제 다운로드하여 읽기 전용 검사. ZIP SHA-256 각각 fb527aa8335280c83486b6242cb7830f1910b3d3c1a0451002462f94f1f195e3 / 5a4c7ecbd112143b56d6516061b72891f657e049aade68b054f28478a8278749, GitHub digest와 일치; 양쪽 ZIP CRC 정상. 내부 APK 105546171 bytes, SHA-256 e8cb6c70216be904f7f597c4ab5963a3cefe93c3aa8f8ec40701d6556c77ff70, 이전 사용자 첨부 해시 및 inventory apk-sha256.txt와 일치. 내부 APK CRC 정상, 1012개 파일 전부 inventory SHA-256 일치, 누락/추가/불일치 0. source-commit.txt d8c84a5faec188c4c3d2cbf61df8b234495a1668 일치. CI signature.txt v1/v2/v3 true, signer 1명 공개 테스트 인증서; 로컬 독립 apksigner 재검증은 도구 부재로 미수행.
- official-comparison.json 추가 권한 0 확인. 별도 inventory baseline 비교 added 410, changed 32, removed 0, review_required 51, native added libmlkit_google_ocr_pipeline.so 1개, changed lib/ 0; 비교 기준이 다른 공식 비교 보고서와 혼동 금지. AndroidX 및 Kotlin 버전 메타데이터 확인, 완전한 SBOM 및 CVE 검사 아님. 로컬 osv-scanner/trivy/grype 부재로 취약점 검증 미완료. 다음: 의존성 해석 및 SBOM/공개 취약점 검증, 네이티브 출처 검토, 공식 보안 검토. 설치 차단 원인 미확정. D01 차단/D02–D08 미검증. 보호 우회/앱 코드/서명/빌드 변경 없음. 잔여 할당량 조회 불가로 이 체크포인트 저장.

### 설치 차단 후속 — 빌드 공급망 무결성 강화
- 원본 빌드 d8c84a5faec188c4c3d2cbf61df8b234495a1668의 settings.gradle, app/build.gradle, ai-core/build.gradle, build.gradle, wrapper 및 Cargo.lock 확인. Rust lock 189 packages 중 registry 188개 모두 checksum 존재, git 의존성 0. Gradle google/Maven Central/JitPack 사용, 프로젝트별 저장소 금지, 조건부 로컬 Robolectric 저장소는 단일 버전 제한. gradle/verification-metadata.xml 조회 404. 직접 선언만으로 전이 의존성의 취약점 부재를 단정하지 않는다.
- 로컬 원본 ZIP 및 APK SHA-256/CRC를 재검사하여 직전 기록 재확인. Gradle/OSV scanner/cargo-audit 실행 파일은 PATH에서 확인되지 않았다.
- 작업 브랜치 커밋 b089f3dc8824ff5f5d9e8daa736f20af80be888a: gradle/wrapper/gradle-wrapper.properties에 기존 8.10.2-all.zip의 공식 SHA-256 2ab88d6de2c23e6adae7363ae6e29cbdd2a709e992929b48b6530fd0c7133bd6 추가. 출처 https://gradle.org/release-checksums/ (8.10.2 Complete ZIP). 기존 URL, 버전 및 CRLF 유지. 원격 내용 재조회 일치. 실제 Gradle 신규 다운로드 및 CI 검증은 미수행. 앱 코드, 서명, 권한, 보안 설정은 변경하지 않았다. 설치 차단 원인과 별개의 일반적인 빌드 공급망 개선이다.
- 다음: 신뢰된 빌드 환경의 resolved dependency graph 및 SBOM, 공식 출처 대조 후 verification metadata 검토, 공개 취약점 검사 및 결과 검증. 단순 체크섬 추가에 대한 APK 전체 재빌드는 불필요하며 차단된 기존 APK를 실행하지 않는다. D01 차단/D02–D08 미검증. 할당량 조회 불가로 단계별 저장.

### 설치 차단 후속 — Rust 잠금 파일 SBOM 확보
- 기존 APK 소스 d8c84a5faec188c4c3d2cbf61df8b234495a1668의 app/src/main/rust/stores/Cargo.lock (blob 2fac37c5aa822698617c14fa23f2370249092258)을 실제 읽어 CycloneDX 1.5 JSON 생성. docs/security/rust-lock-d8c84a5.cdx.json, 커밋 520bcad5288a18b545c8f99e6ef6a88e0c998bad. 원격 재조회하여 생성 내용과 동일함 및 JSON 파싱 성공 확인.
- 외부 crates.io 패키지 188개, 로컬 root 포함 189개 dependency nodes, 407 edges. 이름/버전/외부 체크섬 형식/고유 ID 확인, 모든 edge는 이름 및 필요시 버전으로 정확히 하나의 node에 해석. 모호하거나 누락된 참조 0. Cargo.lock 기재 SHA-256을 사용하였으며 실제 crate 다운로드 및 별도 해시 검증은 하지 않았다. 전체 target 및 build/test 의존성을 포함하는 잠금 파일 기준이며 Android APK에 실제 포함된 패키지 목록과 동일하다는 주장은 하지 않는다. CycloneDX 공식 schema validator 및 공개 CVE 조회 미수행.
- 이전 Gradle 8.10.2-all checksum 값은 공식 https://gradle.org/release-checksums/ 에서 재확인 일치. 다음: OCR=true/localAI=false 구성의 Gradle releaseRuntimeClasspath 해석 결과 및 아티팩트 해시 확보, Gradle/native SBOM과 공개 취약점 검사, 보안 검토. 현재 설치 차단 원인 미확정, D01 차단/D02–D08 미검증. APK 실행/재서명/권한 변경/전체 빌드 없음. 할당량 수치 조회 불가로 단계별 저장.

### 설치 차단 후속 — Gradle 런타임 인벤토리 도구 추가
- tools/ci/gradle-runtime-inventory.init.gradle 신규 커밋 68ddaabdeb2f25105f4c91e35e5da3a701fd883c, 원격 내용 동일 확인. :app:releaseRuntimeClasspath의 실제 선택 component/edge 및 외부 Maven artifact 이름·바이트·SHA-256을 JSON으로 기록하며 미해결 의존성/빈 결과/알 수 없는 참조는 실패 처리하도록 작성. OCR=true/localAI=false를 명시하고 다른 Gradle task와 동시 실행하지 않도록 검사. 기존 앱/빌드/보호 설정 변경 없음.
- 신뢰 가능한 로컬 SDK/JDK/Gradle 환경에서 단독 실행할 명령: ./gradlew --no-configuration-cache --console=plain -I tools/ci/gradle-runtime-inventory.init.gradle -PexperimentalImageOcr=true -PexperimentalLocalAi=false -PskipRust=true :app:auditReleaseRuntimeInventory. 결과 app/build/reports/security/release-runtime-inventory.json. 기존 CI 환경의 pack 요구를 우회하지 않는다. 실제 실행 시 Gradle/Android SDK 및 필요한 소스 준비가 선행되어야 한다. 이 환경 PATH에 Gradle/Groovy 없음(java 존재), 실제 실행·컴파일·CI 검증 미수행. 결과 파일은 아직 없으며 전이 의존성 목록 확보 완료로 판정하지 않는다. 현재 원격 도구에 push run 전체 목록/dispatch 기능 없음. 이후 검증 환경에서 실행하여 문제 수정 후 원본 APK와 비교; 후속 소스에서 재해석한 graph가 원본 빌드와 같다고 단정하지 않는다.
- 로컬 원본 artifact 내부 APK 해시 e8cb6c70216be904f7f597c4ab5963a3cefe93c3aa8f8ec40701d6556c77ff70 재확인, META-INF/*.version 63개 읽기 전용 추출. 버전 형태 62개, androidx.arch.core_core-runtime 1개는 버전 대신 Gradle task property 문자열이므로 별도 확인 필요. kotlinx_coroutines_core/android 1.9.0(직접 선언 Android 1.7.3와 차이), camera 1.4.2, webkit 1.12.1, Compose UI/runtime 1.6.1 확인. 실제 APK 일부 메타데이터에 한하며 전체 SBOM 또는 악성 여부 판단 아님.
- Gradle 공식 8.10.2-all 체크섬 https://gradle.org/release-checksums/ 현 세션 재확인 일치. 취약점 조회 및 원본 Gradle graph 미완료. D01 차단/D02-D08 미검증. 재빌드/서명/설치/보안 설정 변경 없음. 할당량 수치 조회 불가로 단계별 기록.

### 설치 차단 후속 — 별도 Gradle 감사 CI 연결
- 성공 빌드 46124010382의 packs artifact 15255309103을 실제 다운로드해 로컬 Python으로 SHA-256 6fea4d5864fda57b2a2470bccb5436115206d37f1768a5cdc8ee64377d1bffd3 및 ZIP CRC 정상 확인. 정확히 7개 tar.zst 파일(comet/directaudio/powervr/butler/proot/tracking/xbox), 중복 없음. 내부 실행 또는 tar 해제 없음.
- .github/workflows/ai-gradle-runtime-audit.yml 신규 커밋 fc5570398fa56919a5377c90faa8b036ebc8427a, 원격 내용 일치 확인. 개발 브랜치의 감사 관련 경로 push에만 연결, contents/actions read, checkout credentials 비보존, 성공 run/source 및 packs ZIP digest 고정, 안전한 정해진 이름만 신규 파일로 복사. 기존 CI pack 존재 요구를 유지하고 APK assemble/package 및 설치 명령 없이 JDK17/SDK34에서 standalone inventory 실행하도록 설정. 결과 JSON의 sourceCommit, trackedTreeClean, configuration, profile 검사 및 별도 artifact upload. 원본 APK의 graph가 아닌 현 감사 소스의 재해석 결과로 취급. 기존 build.yml, 앱 코드, 키 및 보호 설정 변경 없음.
- CI 정의 push 커밋 저장 완료. 연결 도구에서 push run 목록 조회/dispatch를 지원하지 않아 실제 실행 ID, 상태, 성공, 결과 artifact는 아직 확인하지 못했다. 다음: Actions의 AI Gradle runtime audit 실행 결과와 로그 확인, 실패 시 원인 수정; 성공 시 JSON 및 그래프 검증, 취약점 검사. APK 재빌드/설치 없음. 설치 차단 미해결, D01 차단/D02-D08 미검증. 할당량 조회 불가로 체크포인트 저장.

### 설치 차단 후속 — RustSec 취약점 감사 CI 추가
- Gradle 감사 커밋 fc5570398fa56919a5377c90faa8b036ebc8427a의 combined status 및 PR용 workflow 조회 모두 빈 목록. 이는 CI 실행 여부나 성공을 증명하지 않는다. push run ID/결과/Gradle 인벤토리 미확인 상태 유지.
- 로컬 PATH에서 cargo, cargo-audit, osv-scanner 미발견. 공식 RustSec cargo-audit 사용 문서 및 0.23.0 버전 문서 확인(https://rustsec.org/, https://docs.rs/crate/cargo-audit/0.23.0). .github/workflows/ai-rust-dependency-audit.yml 신규 커밋 bf191dca039bf663c4b921c981accdc9b7b820f7, 원격 파일 재조회 일치. 개발 브랜치 전용 push 감사 workflow로 contents:read, checkout credentials 비보존. 원본 d8c84a5의 Cargo.lock blob 2fac37c5aa822698617c14fa23f2370249092258 일치 확인 후 격리된 임시 디렉터리에 복사, cargo-audit 0.23.0 --locked 설치 및 공식 RustSec 감사 JSON/종료코드/스캐너 버전/lock SHA256 기록. 앱 프로젝트 빌드나 패키지 실행 없이 lockfile 감사만 수행하도록 정의. 결과 실패/경고를 숨기지 않고 artifact 저장하도록 작성. 실제 스캐너 설치/실행 및 취약점 결과는 아직 미확인.
- 다음: Actions에서 AI Rust dependency audit 및 AI Gradle runtime audit의 실행 ID, 로그, artifact 확인. RustSec DB 시점/감사 경고 및 실제 영향 검토, 필요한 의존성 수정과 회귀 검증. Gradle 그래프 및 native 취약점 검증 별도. 설치 차단 원인 미확정, D01 차단/D02-D08 미검증. APK/앱 코드/서명/보안 설정 변경 없음. 할당량 조회 불가로 단계별 체크포인트 저장.

### 설치 차단 후속 — 공개 RustSec 권고 확인 및 수정 후보 CI
- 기존 Gradle/Rust 감사 커밋의 combined status 조회는 빈 목록. push CI 실행 ID와 결과는 여전히 미확인. 로컬 PATH에 java만 확인되고 cargo/cargo-audit/Gradle/gh 없음. 실행되지 않은 검사를 통과로 판단하지 않는다.
- 원본 Cargo.lock/SBOM과 공식 RustSec 권고 직접 대조: rustls 0.23.40 (RUSTSEC-2026-0285/0346/0348/0349, 수정판 0.23.46), rustls-webpki 0.103.13 (RUSTSEC-2026-0382, 수정판 0.103.16), webpki-roots 1.0.7 (RUSTSEC-2026-0381, 수정판 1.0.10), tokio 1.52.3 (RUSTSEC-2026-0386, I/O unsound 경고, 수정판 1.53.3). 출처 https://rustsec.org/advisories/ 의 각 ID. 실제 사용 기능/활성 feature/실행 환경의 영향 및 설치 차단과의 인과관계 미확인. 전체 취약점 검사 결과 아님.
- Cargo.toml의 reqwest 0.12 rustls-tls/gzip 및 tokio 1 rt/net/time/sync 선언 확인. .github/workflows/ai-rust-security-update-candidate.yml 추가, 커밋 98f9049338573e9d9e07df03397ec33399404ab7, 원격 재조회 일치. 원본 lock blob 고정, CI에서 Cargo 공식 resolver로 네 패키지의 지정 수정판 후보 생성, 선택 버전 및 registry checksum 존재 검증, 후보 diff/해시 기록, 기존 cargo test --locked 및 RustSec 재감사, 실패 포함 artifact 보존. 읽기 전용 토큰, APK 빌드/배포/자동 커밋 없음. Cargo.lock/Cargo.toml 실제 원격 변경 없음. 실행/테스트 및 후보 해결 가능성 미확인.
- 다음: GitHub Actions에서 세 감사 작업의 실제 실행 URL/ID 및 artifact 확인. 도구에서 push 목록을 얻지 못하면 사용자가 Actions 링크를 제공할 필요가 있다. 후보 lock의 변경 전체/출처/해시/테스트 및 신규 경고 검토 후에만 개발 브랜치에 반영하고 기존 Android/네이티브 회귀 및 보안 검증을 진행. 현재 D01 차단, D02-D08 미검증. 앱 코드/서명/권한/설치 설정 변경, APK 재빌드 없음. 잔여 할당량 조회 불가로 원격 체크포인트 갱신.

### 2026-11-28 사용자 승인 — 공식 0.7.11 기반 통합 전환
- 새 브랜치 ai-0711-integration-next를 공식 c6169cba808d82f4d247b00bf01827506c1db54d에서 생성, compare identical 확인. 기존 AI 브랜치 ai-independent-apk-verification-next / 4cca491bd6897e39112967c36e78baf8eee06de2 보존. 기존 빌드 d8c84a5 및 보안 감사 작업 보존. 공식 main/release 변경 금지.
- 기준 cdff94e72c45da50af43bbceff28effaf0179caf 대비 기존 AI 703 commits, 공식 510 commits; 비교 응답 각각 300개 파일 제한, 공통 변경 최소 25개 확인. 이는 완전한 충돌 목록이 아님. 공식 targetSdk 34/versionCode20/0.7.11 및 신규 shortcuts module 유지. 이전 AI 28/19/0.7.1. 공식 소스 권한 30개와 이전 AI 23개 차이 별도 검토. 보안 차단 원인은 미확정이며 SDK 변경 자체를 해결로 간주하지 않는다.
- 현재 새 브랜치는 통합 전 공식 소스임. 개발 전용, APK 빌드/설치/배포 금지(통합 및 검증 완료 전). 다음: ai-core 모듈 이식 및 기존 공식 :shortcuts 보존, AI host 및 UI/기능을 의존 순서별 포팅, 신규 SDK/서비스/권한/공식 데이터 분리 검토, 새 Cargo/Gradle 의존성 기준으로 감사 재실행, 회귀 통과 후 사용자 상시 승인 범위에서 개발용 APK 빌드. 보안 차단 우회 금지. 기존 D01 차단, 나머지 실기 미검증. 할당량 조회 불가로 새 브랜치에 체크포인트 우선 저장.

### 0.7.11 통합 1단계 — AI Core 소스 이식
- ai-0711-integration-next: ai-core/build.gradle 및 AiCore.java, GlossaryStore.java, ModelRelease.java, SignedGlossary.java 총 5개 파일을 이전 AI HEAD 4cca491bd6897e39112967c36e78baf8eee06de2에서 복사. 모든 파일 원격 재조회 blob SHA가 이전 원본과 일치. settings.gradle은 공식 :app/:shortcuts 유지하고 :ai-core만 추가, 커밋 0caa30fdd2d9b7a7a0ee9c634756c1f28ed6cfb5. 모듈 등록까지 완료, app 의존성 및 UI/host 연결 미완료, 컴파일/테스트 미수행. 아직 공식 앱과 동일한 applicationId인 통합 중간 소스이므로 설치용 APK 생성 금지. 공식 main과 기존 AI 브랜치는 변경하지 않음.
- 공식 0.7.11 Cargo.lock blob 6a470b3beae093190d06da5b224492083bf641f5, 191 packages. 보안 검토 대상 실제 버전: rustls 0.23.40, rustls-webpki 0.103.13, tokio 1.52.3, webpki-roots 1.0.7. 새 lock 기준으로 공식 권고 및 실제 감사 재검증 필요. 기존 0.7.1 후보 lock 무조건 적용 금지.
- 다음: :ai-core Java17 컴파일 및 테스트 이식, 공식과 AI 변경의 전체 충돌 목록 확보(비교 API 300개 제한), 호스트 모듈/파일 진단/설정/번역/성능/호환성 포팅, 공식 신규 shortcuts 및 데이터 경계 검토. 독립 패키지/서명은 데이터 보호 목적으로만 기존 정책에 따라 검토하며 설치 차단 탐지 회피 금지. 기존 D01 차단/D02-D08 미검증. APK 빌드 없음. 할당량 수치 조회 불가로 단계별 인계 저장.

### 0.7.11 통합 — 공식 업데이트 대응 자동화 1단계
- 사용자 요청에 따라 향후 업데이트 반복 수작업을 줄이는 자동 분석/격리 통합/회귀/복구를 우선 개발. tools/ci/ai_upstream_preview.py 커밋 c6fc7d739df6be99d63b35dcbd9beaa3e0238fed. 고정 full SHA 입력, 단일 공통 조상 확인, git diff 전체 경로(NUL 구분), git merge-tree --write-tree --name-only --no-messages -z로 충돌 미리보기, 중요 파일 검토 표시, release_approved=false. 작업 트리/인덱스/ref 변경 없음(merge-tree는 Git 객체를 생성할 수 있음). 실제 충돌/중요 경로를 자동 무시하지 않는다.
- 로컬 Git 2.47.3의 임시 모의 저장소에서 깨끗한 병합 및 ref/작업트리 보존, 충돌 경로 검출, AndroidManifest 중요 변경 표시, 비고정 입력 거부 4/4 통과. 로컬 코드 blob c8b0629c80d8cb59916d8e09e15dc5cfa4354972와 원격 일치. 실제 전체 DroidDeck 병합 검증은 미완료.
- .github/workflows/ai-upstream-preview.yml 커밋 deadb33d80fae5ba8fb58810a3e75fd37c7cf16e. 개발 브랜치 전용 push, contents:read, 전체 이력, 기존 AI 4cca491bd6897e39112967c36e78baf8eee06de2 및 공식 c6169cba808d82f4d247b00bf01827506c1db54d 입력 고정. 충돌 시 실패 종료 및 JSON artifact 보존, 자동 push/병합/빌드/배포 없음. CI 결과/실행 ID 미확인. 현재는 입력 고정 1회 검사 자동화이며 정기 업데이트 감지·안전한 자동 수정·회귀 빌드·복구는 미완성. GitHub scheduled workflow는 default branch 제약이 있으므로 공식 main 변경 없이 별도 자동화 설계를 검토한다.
- 다음: 실제 preview report 확보 및 전체 충돌 목록 검토, 독립 작업 공간에서 충돌 없는 변경만 후보화하고 중요 경계는 수동 검토, 테스트 기반 수정 및 회귀 gate, 업데이트 감지/중복 방지/실패 시 이전 성공판 유지. 기존 0.7.11 AI Core 이식 상태 보존. APK 생성/설치 및 보안 차단 원인 미검증, D01 차단/D02-D08 미검증. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 2단계
- 신규 tools/ci/ai_upstream_candidate.py 커밋 2fbcabf5aa0aa5c661c0098a107950577cc4e644. upstream 변경을 문서/일반 Java·Kotlin 회귀검증 대상/수동검토 대상으로 분류. 중요 보안/권한/저장소/빌드/서비스 경계 및 미분류 파일은 보수적으로 수동 검토. 충돌 또는 수동검토 경로가 있으면 후보 생성 차단. 정상 merge-tree의 실제 AI 대비 변경 경로도 재검토. 후보 트리만 기록하며 refs, index, worktree, 배포를 변경하지 않는다. 경로 기반 휴리스틱은 완전한 보안 검사가 아니며 모든 후보는 회귀 및 보안 검토 필수. release_approved=false.
- tools/ci/test_ai_upstream_candidate.py 커밋 6e39c5e59467ad324a8db086c9bf3a194e85ac58. 실제 로컬 Git 2.47.3 임시 저장소에서 6/6 통과: 정상 후보의 양쪽 코드 및 ref/HEAD/worktree 보존, 충돌 차단, manifest 차단, 미확인 바이너리 차단, 변경 없음, 경로 분류. 로컬 및 원격 blob 각각 bdfb5685770d4e7304cca7cfbe37505dbda2780a / 51691c8aad440ec6c57e8a4f3b7c004d94e7060e 일치.
- .github/workflows/ai-upstream-preview.yml 수정 06a02f9cb5693dce622e04bd6370fd2b61549973: 신규 단위시험 및 고정 0.7.11/AI 입력 후보 검사 연결, 후보 상태와 트리 ID 보고, blocked 시 CI 실패 유지, 결과 artifact 보존. 원격 내용 확인; 실제 GitHub 실행 ID/결과 미확인. 후보 트리는 일회성 runner 객체이며 배포 가능한 APK 또는 지속 가능한 merge commit이 아님.
- 다음: 실제 전체 merge 보고서 확인, 중요 경계와 충돌 해결, 검증 가능한 후보의 격리 저장/회귀 실행, 자동 업데이트 감지와 중복 방지 및 성공판 유지. 일반 소스 자동 오류 수정은 아직 미구현. 0.7.11 AI Core 이식 외 앱 기능 추가 포팅/컴파일/설치/전체 빌드 없음. 설치 차단 원인 미확정, 공식 main 및 기존 AI 브랜치 보존. 할당량 조회 불가로 단계별 체크포인트 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 3단계
- tools/ci/ai_upstream_repair.py 추가 d52d169834d23119703af78be786cc7a75c64073. 단순 CRLF/LF 차이에 한해 양쪽 내용이 동일하거나 한쪽이 공통 조상과 내용상 동일한 경우 실제 변경을 보존하는 수정 제안 생성. 안전 경로 분류 재사용, 일반 비실행 UTF-8 파일/256KiB 이하/세 revision 모두 존재 조건. 중요한 경로, 바이너리, 삭제/추가/모드 변경, 실제 양쪽 내용 충돌은 보류. 선택 blob 및 SHA-256 기록. 제안만 생성, 실제 수정 적용/commit/ref 변경/배포 없음.
- tools/ci/test_ai_upstream_repair.py 추가 4f9e2e7d4ee772cacfdf672c0145db7e4456f642. 실제 로컬 unittest 6/6 성공: CRLF 양방향, 동일 내용, 실제 내용 충돌, 의미 있을 수 있는 공백 보존, 임시 Git 저장소 충돌 및 ref/HEAD/worktree 보존/누락 파일 처리. 로컬과 원격 blob b4d716e78f4e1ba6e10072aa072e84a000a8053e / 8eb1e04e21e34ccd91df9c7be52351ec8ea1aad5 일치. 기존 preview/candidate 로컬 blob 일치 확인.
- CI 수정 2a009401843f6d0f9c77e0ec9893eb8b1848eecb: 관련 테스트와 제안 JSON 추가, 고정 입력/미적용/미승인 확인, 기존 충돌 실패 유지 및 두 JSON 보존. 원격 내용 일치. 실제 GitHub CI 실행/보고서는 미확인. 다음: 전체 충돌 보고서 확보, 검증된 제안의 격리 적용 및 회귀 테스트, 실제 0.7.11 AI 통합. 신규 업데이트 감지 및 자동 빌드/복구 미완성. 공식 main/기존 AI 유지, APK 빌드/설치 없음. 잔여 할당량 조회 불가로 단계별 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 4단계
- tools/ci/ai_upstream_apply.py 커밋 15033ec366a59fcb6a50e6ce40ef9b5e656e0062: 고정 입력에서 제안을 재계산하고 모든 충돌에 검증된 제안이 있으며 수동 검토 경로가 없을 때만 임시 GIT_INDEX_FILE에서 merge-tree의 해당 blob 교체 및 write-tree. 실제 변경 경로/선택 blob 재검증, 후보 상태 regression_required/no_change/blocked 및 release_approved=false 유지. 기존 ref/HEAD/index/worktree에 적용하지 않는다. 후보 Git 객체 생성은 가능. 경로 분류는 휴리스틱이며 회귀·보안 검증을 대체하지 않는다.
- tools/ci/test_ai_upstream_apply.py 커밋 0d685a8119a1932d56c3c5a004c25abbc8a30a1a. 로컬 Git 2.47.3, Python unittest 실제 신규 5/5 및 기존 후보/제안 시험 포함 17/17 통과. 실제 CRLF 충돌 수정, 양쪽 파일 유지, 기존 staged 변경/HEAD/ref 보존, 중요 manifest 동반 변경 차단, 실제 내용 충돌 차단, clean/no-change 확인. 로컬/원격 blob 409287e43ba185218a84dc09bff81546a6fb9c8d 및 3e7cf98f673be3282ebad352c40d344e8207505e 일치.
- CI 수정 23555e388d0af0d54be56933860aed008ccff243: 테스트/후보 생성과 JSON 보고, 초기 충돌 상태 및 종료코드 일치 확인, 최종 적용 후보가 blocked이면 실패 유지. 자동 push/배포/설치 없음. 실제 GitHub 실행 ID/결과 및 0.7.11 전체 충돌 보고서 미확인. 다음: 후보 Git 객체를 검증 가능한 격리 산출물로 보존하고 회귀 검증 연결, 실제 0.7.11 포팅, 신규 공식 변경 감지 및 실패 시 기존 성공판 유지. 공식 main/이전 AI 브랜치 보존. APK 빌드/실기 없음. 잔여 할당량 조회 불가로 단계별 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 5단계
- tools/ci/ai_upstream_artifact.py 커밋 9dc555e8e44eb713ed1f6588ed7508aa896c2af3: regression_required 후보만 Git binary patch 및 provenance manifest(SHA 고정 입력, tree, patch SHA-256/크기, release_approved=false)로 새 디렉터리에 저장. 최대 16MiB 패치. 임시 Git index에서 원본 AI commit으로부터 patch 재적용 후 tree 일치 확인, 검증 시 고정 입력 후보를 독립 재계산하고 manifest/크기/digest/tree 일치 확인. blocked/no_change 저장 거부, 기존 출력 덮어쓰기 거부, ref/HEAD/index/worktree 변경 없음. 후보 승인/빌드/설치 기능 아님.
- tools/ci/test_ai_upstream_artifact.py 커밋 9d01315a20a53ec482b2aa98d558bd041fff56ed. 실제 로컬 Git 2.47.3, Python unittest 신규 5/5 및 전체 22/22 통과. 복원 및 staged 변경 보호, 패치 변조, 승인값 위조, 중요 변경 차단, 잘못된 트리/출력 덮어쓰기 차단. 로컬/원격 blob 각각 630d7ec3a9150e534925d5267acc56a4c7e5622f / 4a1f47adf82b8cb697af7a8671ee13a37219220e 일치. 기존 7개 소스/시험 로컬 hash도 기존 원격 기록과 일치.
- CI 수정 9583d78339a258dd77338be6219fdc2dd7dccec8: candidate_status 및 종료코드 일치 확인, 적격 후보만 export/verify 및 report tree 대조 후 전체 보고 디렉터리 artifact 업로드(30일 보관). blocked는 실패 유지 및 JSON 보존. 원격 내용 일치. 실제 GitHub run 및 전체 0.7.11 보고서 미확인. 신규 업데이트 감지/후보 격리 빌드 및 테스트/성공판 관리/실제 AI 기능 포팅은 미완료. 다음: 검증 후보에 대한 별도 작업공간 및 회귀 테스트 실행, 실제 0.7.11 충돌 보고서 확보 및 포팅. 공식 main/기존 AI 브랜치 보존. APK 빌드/설치 없음. 잔여 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 6단계
- tools/ci/ai_upstream_core_check.py 커밋 80a9d2d67446c6536440c48548f794be6788e537. 후보 artifact를 독립 재검증한 뒤 정확한 후보 tree에서 ai-core/src/main/java의 일반 UTF-8 Java 파일만 임시 디렉터리에 복원, 경로·모드·개별/전체 크기 제한 검사. javac --release 17 -proc:none, 빈 classpath/sourcepath, 90초 제한으로 컴파일하며 후보의 빌드 스크립트나 생성된 클래스를 실행하지 않는다. 실패 및 compiler output 보고, android_build_verified=false/release_approved=false. 기존 작업트리/refs 변경 없음. Android 전체 빌드와 실제 기기 검증을 대체하지 않는다.
- test_ai_upstream_core_check.py 커밋 13a6292b8eec6c64c7bc639ad1b366ff587ee094. 현재 로컬 Git 2.47.3, javac 21.0.12.1에서 Java 17 target, 신규 4/4 및 전체 26/26 unittest 실제 실행 성공(2.250초). 테스트용 Git 후보 정상 컴파일 및 ref/HEAD/작업트리 보존, 컴파일 실패 감지, 변조 artifact 차단, Java 소스 누락 거부 확인. 실제 DroidDeck 전체 후보 또는 실제 AI Core의 0.7.11 통합 컴파일 검증은 아직 없음. 로컬/원격 blob bdfe1493eeb49baaf69905ff9b450de112143104 / 33e8570682eb5ce54fca067361cd4a09d4fdf3a1 일치.
- .github/workflows/ai-upstream-preview.yml 수정 7cfc929f91a57bb3f61d99085e9d8c7bfb122965. javac 확인, 신규 테스트/변경 경로 연결, 적격 후보 artifact 검증 후 AI Core 컴파일 및 후보 tree/status/미승인 JSON 확인. 컴파일 실패 시 CI 실패 유지, 보고 보존. 원격 저장 확인. 실제 GitHub run/전체 충돌 보고서는 미확인. 현재 Gradle 로컬 미설치; APK 빌드/설치 없음. 다음: 실제 0.7.11 전체 충돌 보고서 확보 및 포팅, 통합된 실제 AI Core 검증, Android 회귀·보안 gate, 신규 업데이트 감지/중복 방지 및 이전 성공판 보존. 공식 main/기존 AI 브랜치 보존. 잔여 할당량 조회 불가로 단계별 체크포인트 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 7단계
- tools/ci/ai_upstream_observe.py 커밋 87303d5e66166f87d71ec52ea6aad927733f5aec 및 test_ai_upstream_observe.py 커밋 345cf82ea9049c9ae7b6f3eafb053516b4e165d9: 고정 baseline/AI/upstream SHA 및 정책 SHA256 조합으로 관측 키 생성, 동일/신규/비정상 이력 분류, 이전 관측 입력 시 반복 여부 표시. 반복은 참고용이며 검증 생략이나 출시 승인 근거가 아님. 기존 정상판/ref 변경 없음. 로컬 Git 2.47.3에서 실제 전체 32/32 unittest 통과(2.371초), 신규 6개 포함. 신규 로컬/원격 blob 0ecc39c8149f8050ea8ef373c5a3eed8ea692d3c 및 e90615a3c55e6aca95abafa5c81cb4f68470bc32 일치.
- 실제 GitHub compare: 기준 c6169cba808d82f4d247b00bf01827506c1db54d 대비 개인 jcy565/DroidDeck main 91 commits behind, 공식 Droid-Deck/DroidDeck main 72 commits ahead, 공식 app/build.gradle versionName 0.7.11/versionCode20/targetSdk34 유지. 공식 저장소 main을 감시 대상으로 정하고 현 0.7.11 통합 기준은 고정. 공식 main 최신 SHA는 이 세션에서 별도 확인하지 않음.
- .github/workflows/ai-upstream-observe.yml 신규 5a81d10b3b5c4397eeb4614fd584b4549dae4924: 개발 브랜치 관련 경로 push 시 읽기 전용 checkout, 공식 공개 URL의 refs/heads/main을 ls-remote로 조회하고 정확한 SHA만 fetch, 정책 digest/관측 JSON 기록 및 artifact 보존. 기존 main/ref 및 기준/출시 변경 없음. 실제 CI 실행 ID/결과 미확인. 정기 스케줄 및 공식 업데이트 자체에 따른 독립 트리거, 관측 이력 지속 저장/중복 작업 생략, 새 SHA 자동 후보 검사 연계, 검증된 정상판 승격/복구는 미완료. 다음: 관측된 SHA의 격리 preview 연계, 신뢰된 이력/실행 조건 설계 및 실제 0.7.11 AI 포팅/회귀 검사. GitHub 기본 브랜치 수정 없이 진행. 기존 AI와 공식 main 보존, APK 빌드/설치 없음. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 통합 — 업데이트 대응 자동화 8단계
- tools/ci/ai_upstream_pipeline.py 신규 1ad0e0553971698928913e33a869bbd511bad364, test_ai_upstream_pipeline.py 신규 160f3a5f0e276e8c314417b55c2de91a95f21916. 공식 SHA 관측 -> baseline이 AI 조상인지 확인 -> 변경/충돌 분석 및 제한된 복구 -> 후보 artifact 생성/무결성 검증 -> 격리 Java Core 컴파일 -> 최종 JSON 상태 연결. unchanged/no_change/blocked/history_review_required/core_compile_failed/android_regression_required 분리, 후자도 Android 검증·출시 승인 false, 기존 정상판 및 ref 변경 없음. 동일 후보 tree 일치 검사. 신규 출력만 생성, 검증 실패 시 승격 없음.
- 실제 로컬 Git 2.47.3, javac 21.0.12.1에서 unittest 전체 38/38 성공(3.860초), 신규 6/6 포함: 정상 후보 및 ref/HEAD/worktree 보존, 컴파일 오류, 중요 파일 차단, 동일 SHA, 역행 이력, baseline 미포함 AI 입력 거부. 로컬/원격 신규 blob 63bf379a322c791dd5f4db9658a67160b353b28e / 9aa53e59d1d10c438ed608693f03eafe892b3e5f 일치.
- .github/workflows/ai-upstream-observe.yml 수정 e402ffb9c5e6b4d94e3b1e9d2e549bbaed3fe181: 공식 main의 고정 fetch SHA를 관측/후보/컴파일 통합 파이프라인에 전달, 전체 단위시험 실행, 상태/종료코드/미승인 및 clean worktree 확인, 보고와 후보 artifact 보존. read-only, 공식 main 및 기존 AI 브랜치 유지. 실제 GitHub 실행 ID/결과 및 실제 공식 최신 SHA에 대한 검사 결과 미확인. 정기 트리거, 지속적인 중복 방지 및 신뢰된 정상판 관리, Android 전체 회귀와 실제 0.7.11 AI 포팅 미완료. 다음: 기존 AI Core 및 0.7.11 실제 기능 포팅·검증을 우선 병행하고, 승인된 정상판 기록/실패 보존 규칙 및 자동 검증 연결을 강화. 전체 APK 빌드/설치 없음. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 실제 AI 기능 통합 — 성능 비교 및 집중 모드 기반
- 기존 AI HEAD 4cca491bd6897e39112967c36e78baf8eee06de2에서 OptimizationComparison.kt(719641ddb7baaa3894b9f2f8dae5f8dfb8e915a9), OptimizationComparisonTest.kt(182b9f99ceed486a39f84afcdb8f28fc5391b30a), GameplayBudget.kt(9fc1ed04b5b6d18240bacf51aa07f69b6b403dfd), GameplayBudgetTest.kt(c7c7ff5a24bef803c8eb4542cb99116e029dc264) 4개 파일 이식, 원본/원격 blob 모두 일치. 0.7.11 SessionState의 running/phase/installing/stopRequested 선언 확인. 호출부, SmartMemory 캐시 해제, 실제 게임 중 동작은 아직 포팅/검증 미완료.
- app/build.gradle의 dependencies에 implementation project(':ai-core') 한 줄 추가(8949aab65fd54435259de262bc6a7b1573706092). 기존 공식 :shortcuts 및 SDK34/버전 0.7.11 유지. AI Core Gradle 연결 정의만 확인, 실제 Gradle/Java AI Core 전체 컴파일 미수행. 현재 applicationId 공식판과 동일한 통합 중간 상태이므로 설치용 빌드 금지.
- 실제 로컬 kotlinc -jvm-target 17 및 java 실행: OptimizationComparison.kt 로컬 git hash-object fe1d679067d7caafd45320fecc133d4740236abe가 원격과 일치. 독립 Kotlin check harness 7,009/7,009 assertions 성공(일반 판정/잘못된 입력 9개, 1,000개 baseline별 7개 경계 판정). JUnit/Android Gradle 테스트는 이 환경 미설치로 미실행, 실제 게임 FPS/실기 검증 아님. GameplayBudget Android API 통합 테스트도 미실행.
- 다음: 실제 AI Core 및 GameplayBudget Android 컴파일, SmartMemory의 공식 캐시 API 확인, GamePatchStore/TranslationJson/GameConfigPreview/LocalGameAdvisor 의존 순서별 포팅, 호환성 이력 및 UI 연결, 공식 Shortcuts의 패키지/데이터 경계 검토 후 독립 패키지 구성 및 회귀 테스트. 자동 업데이트 38개 테스트는 이전 단계 기록이며 이번에 재실행하지 않음. 기존 GitHub CI 실행 결과 미확인. 기존 main/AI 브랜치 보존, APK 빌드/설치 없음. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 실제 AI 통합 — 설정 최적화/로그/파일 관리
- 기존 AI HEAD 4cca491bd6897e39112967c36e78baf8eee06de2에서 TranslationLimits.kt(9e6a7adb052aae3b5358f6d1d1571cce5ab2a0d8), TranslationJson.kt(b52802f389c5ab37d49297b249402c2c35667660), TranslationTable.kt(f2ef41b31208ecc29603bdfd46d529e8cc033720), GamePatchStore.kt(72c92d07c151ec9b290cf01756882a3a63f90376), GameConfigPreview.kt(5226d84ab0aae0787164c3f3e4f3e72c5c81210a), LocalGameAdvisor.kt(4e21504ef98144a9ecab6b909b3a0847a2f6dd01), GamePatchAuthor.kt(4f0464ab764d3afcfde123a354d63680346f0160), TranslationDraftStore.kt(a2744f193879c603d1159b540816b74c950e1e22) 8개 파일 이식 및 blob 일치 검증. 설정 미리보기/해상도/FPS 제한 제안, 로그 진단, 제한된 파일/ZIP 처리, 백업·복원, 번역 사전/표·초안 기능의 기반. UI 및 호출부는 미연결, 실제 Android 파일 쓰기/게임 구동 미검증.
- GamePatchTest.kt(d1d2d144ba0889269b2ee4a6cd442e10fb01def2) 31개, GamePatchAuthorTest.kt(f8550e0c2c00b744ac90a5bd4be558e7c9206e3e) 8개, TranslationJsonTest.kt(7fa333a9404231dbb386445ff65ef95889687fde) 3개 @Test 선언 이식, blob 일치. 총 42개 선언이며 현재 브랜치 실행 결과 아님.
- .github/workflows/ai-0711-game-file-tests.yml 신규 bd7f5ce03d331b6a055a2932e27ecdb36e477eac, 원격 내용 일치. 개발 브랜치 경로 push 시 독립 Java17 AI Core 컴파일, Kotlin JVM 17 및 JUnit4로 이전 3개 테스트와 OptimizationComparisonTest 실행. 기존 workflow의 Maven JUnit 의존성 확보 방식 재사용. Android Gradle/APK/설치 없이 수행, contents read. 이 세션에선 실제 신규 Kotlin/Java 컴파일과 CI 실행 결과 미확인. 이전 38/38 자동화와 7,009 성능 비교 assertion 기록은 재실행하지 않음.
- 다음: SmartMemory/캐시 API 및 OptimizationRecovery 이식, GameRunHistory/호환성 이력 연결, UI 호출부와 집중 모드 적용, 신규 CI 결과 확인, 공식 Shortcuts 및 데이터 경계 검토, 독립 패키지 구성과 Android 회귀. 공식 main/기존 AI 브랜치 보존. 설치 차단 원인 미확정, APK 생성/설치 없음. 잔여 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 실제 AI 통합 — Smart Memory, 저장공간 및 복구 제안
- 이전 AI HEAD 4cca491bd6897e39112967c36e78baf8eee06de2에서 FolderUsage.kt(81c3ee4c735c2af9bfcf14b04488fe881673abb8), SmartMemory.kt(321a41ace5615c0280f76cfa0e4781f89c57566f), OptimizationRecovery.kt(f276279038f936286da350e54c382c09135a307c), SmartMemoryTest.kt(d1f7260db7102289dd009bb599df559044802fb3), OptimizationRecoveryTest.kt(b39d12734d059cf289f7a8d04d5d61ff8ecb5d0e) 이식. 원본과 원격 blob 모두 일치. 복구는 읽기 전용 제안이며 자동 파일 복원 아님.
- 공식 SteamTouchBindings.kt의 기존 24MiB LruCache를 이전 AI의 lazy cache 및 releaseCachedIcons 방식으로 최소 변경(fae981576a8476138363ee4f87d086759d79c475). 초기화 전 캐시 생성 없이 반환, 기존 Bitmap 참조만 해제하고 직접 recycle하지 않음. App.kt(c49d62764383bd4910a4b520855c205ca650f3ae) 기존 공식 초기화 및 secondary process 분기 유지, 기존 process 판별 결과가 packageName과 동일할 때에만 SmartMemory onTrimMemory/onLowMemory 연결. 기존 GameplayBudget의 idle 조건 유지. 두 원격 파일 재조회 내용 일치. Android 컴파일/실기/메모리 절약량 및 동시 실행 상태 전환 검증 미완료.
- 로컬 /mnt/data/dd-memory-check의 FolderUsage.kt git hash-object fa1b4e1f2a9abe8f135169650ea582cf9e2ea49e 원격 일치. kotlinc -jvm-target 17 및 java -jar 실제 실행, 독립 check harness 8/8 통과: 파일 수/바이트/임시 용량/중복 후보/정상 완료/탐색 한도/중단 체크포인트/원본 및 외부 데이터 보존. 외부 디렉터리 심볼릭 링크를 제외한 fixture 사용. 기존 JUnit 9개 선언(메모리5, 복구4) 이식 완료, Android/JUnit 실행 아님. APK 생성/설치 없음.
- 다음: GameRunHistory 및 호환성 데이터베이스 이식, 실제 앱 UI와 집중 모드/파일 진단 연결, 공식 Shortcuts 및 독립 데이터/패키지 경계 검토. 기존 CI 결과 미확인 및 전체 0.7.11 Android 컴파일 미완료. 공식 main/기존 AI 브랜치 유지, 설치 차단 원인 미확정. 할당량 조회 불가로 단계별 원격 체크포인트 저장.

### 0.7.11 실제 AI 통합 — 게임 호환성 DB 및 실행 이력
- 이전 AI HEAD 4cca491bd6897e39112967c36e78baf8eee06de2의 호환성 소스 11개 이식 및 각 원본/원격 blob 일치 검증: GameHistoryQuery(45db0495a60a881bcfcf193e10e7488d2aefd9ad), GameRunHistory(d92f8dc5de6a07c3a2095279e4004001b23952aa), GameHistorySummary(d56ba0eb4a413a6c82f73006b7bb3f13bb642b96), GameHistoryAdvice(b3ef068cd910fd72c0e1057cff176f636de90c70), HistoryReadFeedback(3e6e38e534a03ae336d18a5932dfa7b7dc2cec63), GameCompatibilityService(3c3fc16a85dc207134fd48c83ab81bd530a0b79e), GameHistoryByVersion(eb58afd1e8086e058a03e95370de4aecb6bb02a1), GameHistoryTimeline(a51736795243cc2b8930ae0163514ddb3cc6e238), GameObservationDraft(7de2d3da2006c49c935f4f8f0932a393a782a2df), InstalledGameVersion(495227f6bb4671b13a4ed21fdb27dcf0bfa16148), GameLaunchRecorder(9007dcac27ac15ae2f321ea2f75661340553be35). 로컬 SQLite 500개 기록 한도, 게임/버전 범위 검증, 메모리·게임 상태에 따른 조회 보류, 관측 근거 제한, bounded launch request queue 유지. 요청은 UNKNOWN으로 기록하며 실제 플레이 성공으로 간주하지 않음.
- 기존 관련 Test 파일 12개, 총 52개 @Test 선언 이식 및 원본/원격 blob 일치: GameHistoryQueryTest(6), GameHistorySummaryTest(2), GameHistoryAdviceTest(3), GameRunHistoryTest(11), GameCompatibilityServiceTest(4), GameLogReportTest(3), GameHistoryByVersionTest(3), GameHistoryTimelineTest(3), GameObservationDraftTest(6), InstalledGameVersionTest(6), HistoryReadFeedbackTest(3), GameLaunchRecorderTest(2). 이번 세션에서 테스트 실행/Android 컴파일 없음. 이전 CI 결과도 미확인.
- 공식 0.7.11 GameLaunchLink.validId API 동일 확인. GameLaunchIntent.read는 기존 AI의 String?에서 공식 GameRequest?로 변경되어 Steam 이외 store 및 program 요청도 처리함. 기존 intent/launch 파일을 통째로 덮어쓰지 않음. GameLaunchRecorder 호출부 및 GameCompatibilityPanel은 아직 미연결. 다음: 실제 공식 launch publication 호출부 파악, 성공적으로 전달된 Steam 요청만 UNKNOWN 관측으로 연결(다른 store 혼동 금지), GameCompatibilityPanel 의존성 및 UI 포팅, 실제 DB/Android 회귀 검증. 공식 Shortcuts/패키지 분리 및 데이터 경계 검토 필요. 공식 main과 이전 AI 유지, APK 빌드/설치 없음, 설치 차단 원인 미확정. 잔여 할당량 조회 불가로 단계별 체크포인트 저장.

### 0.7.11 실제 AI 통합 — 실행 기록 호출부 및 호환성 UI
- 공식 0.7.11 SessionService.kt에서 기존 staged.renameTo(request)가 성공하고 pending Steam 요청 상태가 초기화된 뒤 steam.game_launch_requested 이벤트가 기록되는 위치 확인. 해당 이벤트 바로 뒤 GameLaunchRecorder.publishedSteamRequest(this, gameId) 연결(35bd4bb5191410f17bb804d6f8e5c8e6333a0450). 공식 exit-watch token/previousGameId 처리 및 실패 재시도 유지. 실행 중 Steam client에 실제 publish된 요청만 UNKNOWN 관측으로 저장, 플레이 가능 판정 아님. 초기 Steam 시작/타 store/NoSteam launch 기록은 아직 미연결. 자동 기록은 기존 bounded executor 사용.
- GameCompatibilityPanel.kt 이식 cd43bc2a097d2f5b3334698db7ac4fee18b95bd5. 이전 panel에서 아직 이식되지 않은 SettingsExperimentPreviewPanel/RuntimeFexPreviewPanel/SettingsRecoveryReviewPanel 호출 3개만 제외, 조회/요약/타임라인/직접 관측 저장 및 명시적 버전 확인 흐름 유지. GameDetail.kt c71203c382cdb1dba79c5d7ed86f1e3b032e2d52: 기존 공식 탭/동작 보존, Steam source 및 유효 ID, non-ADDED/non-cloud, non-shortcut-picker 조건에서 AI History 탭 표시. Steam 설치 버전 필드가 공식 Library 모델에 없으므로 null(미확인) 전달; 실제 설치 버전 및 타 스토어/프로필별 UI 연결은 후속 작업. 세 파일 원격 재조회 내용 일치. 이번에는 Android/Compose compile 및 JUnit 실행 없음. 실제 UI 및 DB 저장 동작 미검증.
- GitHub workflow별 runs GET URL 조회 시 connector INVALID_ARGUMENT으로 결과 확인 실패. CI 결과 여전히 미확인. 다음: 공식 Library manifest 읽기 경로에 설치 버전 검증 연결 및 공식 GameDetail regression, AI 파일 관리자 UI/설정 미리보기 호출부 의존성 이식, independent package 및 Shortcuts 데이터 경계 검토, Android source/회귀 테스트와 실제 CI 결과 확인. 공식 main 및 이전 AI 브랜치 보존, APK 생성/설치 없음. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 실제 AI 통합 — 설치 버전 인식
- 공식 Library.kt의 SteamGame에 nullable installedVersion 필드를 기존 soundtrack 뒤에 기본값 null로 추가, withArt가 이를 보존하도록 연결. 기존 steamGames의 실제 manifest text와 gameFiles가 있는 게임에 한해 InstalledGameVersion.steamBuildId(text, appId) 적용, soundtrack 제외. 공식 shared library/strictRead/onlyAppIds/기존 설치 판정 유지. 커밋 cf42d5e5831e2a11cdb3108c3d859ce1e9ab1098, 원격 blob deeb1f4eaa7f13030982d1817c090cde1c8063e8. LibraryCache는 변경하지 않음: 캐시에서 복원된 행의 installedVersion은 기본 null이고 실제 manifest 재조회 후 확인됨. GameDetail.kt 68ba45cc6a19c64cffd19df4eb70740852d3ed8c: soundtrack 제외, AI History에 최근 관측 설치 버전 전달. 기록 시 실제 실행 버전의 명시적 확인은 기존 panel에서 유지. 두 원격 내용 재검증 완료.
- 로컬 /tmp/dd-version-check/InstalledGameVersion.kt의 git hash-object f3c64e74ecc626aac03af2a6f783ef2df0e7a686이 기존 원격 parser와 일치. kotlinc -jvm-target 17 및 java -jar 실제 독립 검사 10/10 통과: 정상, 업데이트 중, 앱 ID 불일치, 0/잘못된 build ID, 중복, 잘린 manifest, 추가 데이터, 중첩 build ID 무시 및 최상위 build 누락. 기존 JUnit 6개는 실행하지 않음. .github/workflows/ai-0711-game-file-tests.yml 수정 506ab7ae430b3ca4d352da2d207c914cb101932e: InstalledGameVersion 소스 및 JUnit 추가, 원격 내용 일치. CI 실제 실행 결과 미확인. Android 모델/UI 전체 컴파일 및 기기 동작 미검증.
- 공식 FileManagerScreen.kt와 이전 GamePatchDialog.kt 검토: 후자는 GameAgent/AiHostBridge/모델/번역/교환 등 추가 미이식 의존성이 다수여서 통째 복사하지 않음. 다음: 게임 파일 관리자에 읽기 전용 로그 분석/설정 미리보기부터 안전하게 연결하고 기존 쓰기 기능은 게임 root·백업·승인·집중 모드 조건을 갖춰 단계적 통합; 이후 독립 패키지/Shortcuts 데이터 경계 검토 및 Android 회귀. 기존 main/이전 AI 유지, APK 생성/설치 없음, 잔여 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 실제 AI 통합 — 파일 관리자 읽기 전용 AI 분석
- GameFileAnalysis.kt 신규 06ed9f9631605639fbad82fbd7be84952b94205d, 원격 blob 77ead45beb4161301e217f644f4da6b43cdfd84c. 사용자가 선택한 log/ini/cfg만 UTF-8/일반 파일/최종 symlink 제외/1MiB 읽기 한도 및 기존 설정 preview 262144자 한도 적용. 기존 LocalGameAdvisor의 로그 진단과 720p/900p/30FPS 제안 사용. 원본/제안 전체 텍스트가 아닌 진단 및 변경 항목만 반환, 파일 쓰기/네트워크 없음. 지원 형식/설정 조건은 기존 advisor 제한 유지. 경로 전체의 symlink 격리나 원자적 파일 스냅샷을 보장하지 않으므로 쓰기 승인 근거로 쓰지 않음.
- GameFileAnalysisDialog.kt 신규 e61dc36a61649b90d9f6d6dc48276c200f6378f6: 사용자가 누를 때만 IO coroutine에서 분석, GameplayBudget 및 ensureActive 체크포인트, 닫을 때 scope 취소, 원본 수정 없음. TextEditorScreen.kt 수정 552f7a2d29bfb597c870a7d0cb53081f7e160347: 지원 확장자/READY/저장된 상태/UTF8/nonbinary/nonlossy일 때만 AI 버튼 활성. 기존 편집/저장/탐색 유지. 아직 자유문장 AI 처리와 백업 후 적용/복구 UI 미연결.
- GameFileAnalysisTest.kt 신규 2c952c9f7a0ca664f7db15eca1e82f927c895ff6, 5개 JUnit 선언: 진단의 원본 및 raw log 비노출, 설정 preview, 잘못된 모드/확장자, 중단, 초과 크기/최종 symlink 거부. .github/workflows/ai-0711-game-file-tests.yml 수정 26984347880eecc763aac351d3e864d8a1f14194로 CI 컴파일/실행 목록 연결. 신규 및 변경 파일 원격 재조회 내용 일치. 이번 세션에 로컬 Kotlin/JUnit 및 GitHub CI 실행 확인 없음, Android Compose 컴파일/실기 미검증.
- 다음: 실제 게임 root에 제한된 설정 적용/백업/복원 승인 UI 및 재검증, 기존 GameAgent/AI Host 의존성 단계적 이식, 독립 패키지/Shortcuts 데이터 경계 검토 및 Android 회귀. 공식 main/이전 AI 유지, APK 생성/설치 없음. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 실제 AI 통합 — 게임 설정 변경 승인 기반
- GameSettingsApproval.kt 신규 3ae8b05faa8f75ce604fc6be9c274dbaf7592aa6, 경로 별칭 추가 거부 2d614f70b63b1ab9c2d8f2298e56d91f8dbdb0c1, 최종 원격 blob c601e23d7f5f63f964701b40b90ceaf5e69eca0b. 신뢰된 실제 설치 게임 root를 호출자가 제공해야 함. root 내부 ini/cfg 일반 파일만 대상으로 경로 dot segment/내부 symlink/범위 외 파일 거부, 기존 1MiB 읽기 제한 및 UTF-8/설정 제안 제한 유지. prepare에서 canonical root/상대 경로/모드/원본 및 변경본 SHA256/변경 목록을 고정; apply에서 동일 계획 재계산·일치 검사 후 기존 GamePatchStore의 백업 무결성 검증 및 atomic replace 사용, 쓰기 직전 호출자 checkWrite와 root/target 재확인. 동시 외부 writer 및 경로 교체에 대한 원자적 락 보장은 아님. 일반 파일 관리자 initialDir/intent는 신뢰된 game root가 아니므로 실제 write UI 미연결 상태 유지. 복구는 기존 GamePatchStore 기능, 전용 복구 승인 UI 미구현.
- GameSettingsApprovalTest.kt 6개 JUnit 선언 신규 b6b36e55a18b875ae734759dad79464e69cd4064 및 e8ac484aa2ea05502b34522905c1e41e37d03107. 승인 후 정확한 원본 백업/복원, 변경 파일 거부, 다른 root/대상 거부, symlink/경로 별칭 거부, budget 거부 검사. CI 목록 연결 a0ed43b5fc2ee08d95a66cc3fc9c991f699fb2c8. 신규/수정 파일 원격 재조회 일치. 이번 세션 실제 컴파일/JUnit/CI 실행 미확인; 로컬 Kotlin 설치 확인했으나 의존 소스 및 JUnit 런타임 로컬 미확보. 테스트 통과로 간주하지 않음.
- 공식 GameDetail.FileButtons의 game.gameFiles와 파일 선택 API 확인. 다음: 명시적 게임 상세 화면 진입/검증된 설치 root에서만 선택 및 적용 가능하도록 구현, 원본 변경 승인·복원 대상 고정 및 실제 SessionState/launch와 쓰기 동시성 제어 검토. 기존 read-only AI 버튼은 유지. 실제 앱 독립 package/Shortcuts 데이터 경계 및 Android regression, GameAgent/AI host 의존 이식 남음. main/이전 AI 보존, APK 생성/설치 없음. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 보안 우선 점검 — 공급망 및 패키지 격리
- 현재 통합판 app applicationId com.droiddeck.launcher, targetSdk34/minSdk26, shortcuts com.droiddeck.shortcuts targetSdk34, 두 모듈 공개 testkey signingConfigs 사용 확인. 공식 c6169cba808d82f4d247b00bf01827506c1db54d와 양쪽 source manifest blob 동일(앱 30개 uses-permission 이름, shortcuts 0개). 이는 source manifest 기준으로 merged APK 권한/외부 컴포넌트 검증 아님. 앱 allowBackup=false, network_security_config의 cleartext 예외 localhost/127.0.0.1 확인. 권한 제거는 공식 기능/호환성 검토 뒤 수행. Gradle verification-metadata.xml 현재 트리에 없음. 기존 APK 설치 차단 원인/악성 코드 여부 미확정; 보호 기능 비활성화/우회 금지.
- Gradle 공식 체크섬 https://gradle.org/release-checksums/ 에서 8.10.2-all SHA256 2ab88d6de2c23e6adae7363ae6e29cbdd2a709e992929b48b6530fd0c7133bd6 및 wrapper JAR 2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046 확인. gradle/wrapper/gradle-wrapper.properties에 distributionSha256Sum 고정(9278a663d4a06dbc8267ae2ca9eaa4baa46cea7a). .github/workflows/ai-0711-security-preflight.yml 신규 8ad21347957f483b80c09056212a9ec9670ba2e8: 별도 read-only CI로 wrapper JAR 및 properties 검증, 두 모듈의 baseline 대비 소스 권한/패키지/testkey/Gradle 검증 메타데이터 여부 JSON 보고, APK 검증/출시 승인 false. 이 단계는 보고용이며 전체 빌드의 필수 gate는 아직 아님. GitHub 실행 결과 및 실제 wrapper JAR 해시 일치 여부 미확인.
- 기존 .github/workflows/build.yml 개발 브랜치 사본의 source job 시작 전 ai-0711-integration-next ref/head_ref이면 명시적 실패하도록 추가(623467d1090f1ec39bc1a4c46525a975ff712816). 패키지/서명 분리 전 해당 workflow APK 생성 금지. 다른 로컬 빌드까지 강제 차단하는 장치는 아님. 원격 변경 3개 재조회 일치, APK 빌드/설치 없음. 공식 main/이전 AI 유지. 다음: ShortcutsRelay/보조 APK package/signature 권한·프로토콜 경계, 독립 패키지 및 내부 authority/action/URI 분리, 개인 서명키 비공개 관리 설계, APK binary 서명/merged manifest/의존성 SBOM 및 취약점 검사, 실제 CI 확인. 설치 차단 해결 검증 전 설치 권장 금지. 승인 UI의 실제 쓰기 연결 보류. 잔여 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 보안 통합 — 앱 및 Shortcuts 식별자 분리
- app/build.gradle 0e13e3b6cd284efd8f8bacf95c8cff95d9358f74: applicationId com.droiddeck.launcher.ai 및 versionName 0.7.11-ai. shortcuts/build.gradle e87d4ea25324d805b25a7fb4dcc86cfbb345a1bd: applicationId com.droiddeck.shortcuts.ai. Java/Kotlin namespace 및 컴포넌트 클래스명은 유지. 기존 debug localSuffix 옵션 존재, 별도 검토 필요. 실제 Android 앱 설치 및 파일 저장 경로 분리 검증 아님.
- Relay.java 14b69abcf3cc72d5cb4dde3fe2c342704f475f5f: AI companion/host package, relay action 및 descriptor 구분, SERVICE 클래스명은 기존 namespace 유지. shortcuts manifest 4a557fc306a63dec3207cc0c327e2e2ac92b005b, app manifest 5844cee8a4e2bd0e80064bb3e2a7ecdf8f48d3a6: AI relay action/deep link scheme/launch action. ShortcutsService.java 00ddeedc271b7fa7ef2a76cb4df141e11d7ffcd3: 명시 host package 검색 및 동일 signature 확인 유지. ShortcutsRelay.kt 8f0179158b62dd5a7f538e9a4bd399db6bbe690e: cached UID 신뢰 제거, 매 요청에 AI host/companion UID 및 signature 일치/기존 서비스 활성화 확인. 공개 testkey 상태에서는 동일 서명 자체를 독립 보안 경계로 간주할 수 없으므로 APK 패키징 차단 유지. 보조 앱 실제 accessibility 사용/권한 변경/설치 없음.
- GameLaunchLink.kt 32f9aaccf8214087387939c88f0a38a5597fb82f, GameLaunchIntent.kt 7bb8689a804c5bca07b2c092035d65769ba27b89, FrontEndActivity.kt 0ee4d2cf21381648abdff01056416001ccea9b6d: droiddeck-ai 게임/설정 URI 및 AI 전용 실행 action 연결. 관련 테스트 GameLaunchLinkTest 91b3e4c11c5b1a45c417d7e7cabfe962564c0a45, GameLaunchIntentTest c4d706abafa7f517258cc830d3837adf85f8af06, GameFilesTest ed9e210bce250df7ae41caf02b0974dae78bdf0d 기대값 업데이트/공식 URI 거부 케이스. soundtrack 등 다른 link 생성자/manifest action/공유 파일 경로 전수 검토 미완료.
- AppUpdates.kt 54b3ae37ce19813559a0666311d4710742e14700: releaseSigned 초기값/예외 fallback false, 독립 패키지 공식 업데이트 자격 부여하지 않음. 기존 catalog package 검사 유지. 독립 업데이트 서명 정책 별도 필요. 공식 카탈로그 조회 자체를 제거한 것은 아님.
- tools/ci/audit_app_paths.py 9740588bcdece17c1f6624e4ba8c92ee1556ff3f 기존 경로 인벤토리 이식 및 shortcuts/공식 URI 규칙 추가. ai-0711-security-preflight.yml a816723a8d405be97d8fdaaa557b1e236ad3f6b8에서 JSON 보고 artifact 연결. 휴리스틱 탐지이며 전수 격리 증명 아님. 모든 변경 파일 원격 재조회 일치. 이번 세션 실제 테스트/CI/Android compile 미실행 또는 미확인. 공개 testkey 및 Gradle verification metadata 부재, 기존 APK 설치 차단 원인 미확정. 공식 main/기존 AI 브랜치 유지, APK 생성/설치 없음. 다음: 전체 공유 경로/URI/action/공식 variant 및 nested APK 검토, 두 APK의 비공개 키 signing 설정(키를 저장소에 올리지 않음), 신뢰된 의존성 검증 및 실제 CI 확인. 할당량 조회 불가로 체크포인트 저장.

### 0.7.11 보안 통합 — 비공개 AI 서명 정책
- tools/ci/AiSigningPolicy.java 신규 eb58c22ed5def87f6f260c4c213326c35467c5f2, blob 210058c510f6db7b8519935a428979595a0f5a99. Java17, checkout 외부의 절대 경로 regular PKCS12, 최종 symlink 및 checkout 내부 canonical 경로 거부, alias/private-key/유효 기간/고정 SHA256 cert 검사, 이전 공개 test cert a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc 및 공식 cert b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0 거부. DD_AI_KEYSTORE/DD_AI_KEY_ALIAS/DD_AI_STORE_PASSWORD/DD_AI_KEY_PASSWORD/DD_AI_CERT_SHA256 환경 변수 사용, 비밀번호/키/상세 예외를 출력하지 않음. 비공개 키는 사용자가 통제하는 checkout 외부 저장소로만 관리. 실제 사용자 키 발급/등록/비밀번호 요청 없음. 기존 공식 testkey 파일 및 공식 배포 도구는 변경하지 않음.
- 로컬 /tmp/dd-signing-check에서 실제 javac --release 17 및 Java 실행 9/9 사례 성공: 임시 fixture 정상, 누락, 틀린 pin, 내부 키 경로, symlink, 틀린 alias, 틀린 password, 공개/공식 pin 거부. 원격 Java blob 로컬 일치. 임시 테스트 키는 테스트 종료 시 제거. test_ai_signing_policy.py d89811d525bec77deec6d83e8c48c4e9747876bc로 동일 fixture 케이스 회귀시험 저장, 원격 내용 일치; 이 저장된 Python 파일 자체 재실행하지 않음. ai-0711-security-preflight.yml 4ced9d9f9488ee5729d90f0329e2e777d52fdf8e에 회귀 테스트 연결, 실제 CI 결과 미확인.
- gradle/ai-signing.gradle b3852c1077d8fe7d7c80fb888b3176ef2ba4de8a 및 8239fa7eab65a89ea863f8c91743cd6a7bb1a324: 두 Android 모듈 공유 external signing config, 누락시 signingConfig null, 알려진 debug/release validateSigning/package/bundle/signBundle task에 verifyAiSigning 의존 연결. app/build.gradle 04c3c5144519459a24d88c131343f9a4c9076a29, shortcuts/build.gradle 6988a484feaea5f2d1e3878eb780fc92e41a31ed: 공개 testkey signingConfigs 삭제, shared script 적용, host debug suffix 거부. 원격 변경 재조회 일치. 실제 Gradle/AGP task graph 및 우회 가능한 기타 packaging 경로 미검증, 별도 workflow 차단 유지, 실제 서명 및 APK 생성 없음. 비공개 인증서 pin이 실제 키 소유권/안전성을 보장하는 것은 아님. 공식 updater의 독립 앱 차단 유지; 공식 RELEASE_SIGNER 값은 공식 카탈로그 검증 맥락이므로 변경하지 않음.
- 다음: 실제 Gradle signing task fail-closed 검증, 모든 패키징 경로/공식 배포 스크립트 영향, 두 APK의 signer 일치 및 공개 cert 거부를 binary 검사로 연결, signature permission/보조 APK 선택적 비활성화 검토, 공유 데이터 및 잔여 URI 경계, Gradle dependency verification/Rust audit 및 권한 점검. 실제 설치 차단 원인 미확정, 경고/보호 우회 금지. 공식 main/이전 AI 유지. 할당량 조회 불가로 체크포인트 저장.
