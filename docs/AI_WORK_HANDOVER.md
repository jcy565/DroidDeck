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
