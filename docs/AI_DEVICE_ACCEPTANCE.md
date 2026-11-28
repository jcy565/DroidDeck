# DroidDeck AI 실기기 검증 체크리스트

대상: Galaxy S23+ (8 GB RAM). 현재 검증 기록은 없음. 이 문서는 테스트 계획이며 완료 보고서가 아니다.

## 0. APK 확인 및 설치 전

- [ ] 정확한 소스 커밋과 Actions 실행 번호, APK SHA-256, 패키지 ID 및 서명 인증서 지문을 기록한다.
- [ ] 개발용 공개 테스트 키 서명 APK를 정식 배포판으로 간주하지 않는다. 실제 설치 시험은 신뢰할 수 있는 독립 서명과 정상적인 Android 설치 절차를 확인한 뒤 진행한다.
- [ ] 공식 앱과 독립판의 패키지 ID, 데이터 경로, 저장 파일 및 세이브 보호를 확인한다. 공식 앱 삭제, 데이터 초기화 또는 보안 설정 해제를 요구하지 않는다.
- [ ] 설치 실패 시 Android가 표시하는 정확한 오류를 기록하고 중단한다.

## 1. AI 및 텍스트 제작

- [ ] 복사한 테스트용 INI/CFG에서 현재 값과 제안 값이 일치하는지 확인. 취소하면 원본이 그대로인지 확인.
- [ ] 테스트용 CSV/TSV/JSON에서 원문 변수와 줄 순서 보존, 초안 저장/재개, ZIP 출력 확인. 원본과 출력본의 해시를 기록.
- [ ] ChatGPT 공유 시 미리보기와 공유된 본문이 일치하는지 확인. 민감한 데이터나 로그 전체를 공유하지 않는다.
- [ ] 다른 요청 ID, 오래된 원본, 누락된 변수 등의 응답이 적용되지 않는지 확인.
- [ ] 앱 재시작 후 저장된 요청·초안이 복원되는지 확인.
- [ ] 최신 staging 통합 이후 작업 목록 저장/복원과 오래된 원본 차단을 확인한다.

## 2. 이미지 제작

- [ ] 앱 내부 또는 사용자 소유의 복사본 PNG로 OCR → 영역 수정 → 번역 → 검토 → ZIP 내보내기.
- [ ] 일본어/중국어/라틴 문자, 회전, TGA 및 지원되는 DDS 파일을 개별 시험. 실제 지원 범위와 오류를 기록.
- [ ] 패치 불러오기, 백업, 읽기 전용 진단과 앱 내부 교체 시험이 구분되는지 확인.
- [ ] 게임 파일에 대한 실제 이미지 패치 적용/복원은 아직 UI 비활성. 실기에서 안전한 저장소와 세션 배제 조건을 검증하기 전 활성화하지 않는다.

## 3. 게임 실행 및 성능

- [ ] 같은 기기에서 공식판/검증된 독립판의 버전과 설정을 별도로 기록. 가능하면 동일 장면을 반복해 FPS와 P95 프레임타임을 측정.
- [ ] 메모리, 온도, GPU/CPU 관측값, 로그의 실패 신호를 기록. 조건이 다른 측정은 직접 비교하지 않는다.
- [ ] 실제 게임 실행 중 선택적 AI 분석과 저장소 검사 중단 여부 확인.
- [ ] 게임별 호환성 이력이 버전별로 분리되는지 확인.
- [ ] 게임 파일 수정 전에 사용자 승인 및 원본 백업이 요구되는지 확인. 위험한 자동 적용은 금지.

## 4. 결과 기록 양식

| 항목 | 기록 |
|---|---|
| 소스/Actions 실행 | 미검증 |
| APK 해시/인증서/패키지 | 미검증 |
| Android 버전 및 기기 | 미검증 |
| 테스트 ID 및 사용한 파일 복사본 | 미검증 |
| 실제 관측값 / 예상값 | 미검증 |
| 오류 로그(민감 정보 제거) | 미검증 |
| 원본/세이브 보존 확인 | 미검증 |
| 결과: 통과/실패/보류 | 미검증 |

AI 로컬 모델은 기본 비활성이며 별도의 실험적 빌드, 호환 모델, 라이선스·메모리·추론 검증이 선행되어야 한다.


## 5. 최신 빌드 확인 및 실기 시험 순서 (2026-11-28)

- 기준 성공판: [46096539410](https://github.com/jcy565/DroidDeck/actions/runs/46096539410), 소스 292e853b11ffc5640e2f77effa0b0430515dc85c, 전체 Android 4,263/4,263 통과. APK SHA-256: `21070a0e138ac0a83cc631d9d590d110e22ce6369fcd0aa13b7505f23578f9cf`. 내부 파일 1,012개 일치 확인.
- 성능 개선 통합 후보: [46098470596](https://github.com/jcy565/DroidDeck/actions/runs/46098470596), 소스 a44476097f9a64f1f383e5c86e8f02f57ddba83b. CI 인벤토리상 APK SHA-256 `8bb326baf83571552f9d7cdba2d213b7e35a758a3eae3de2265b547138dd8430`. Android 전체 테스트 실패로 종료. DB 테스트 2건의 AutoCloseable 형변환 예외와 BLE 작업 스레드의 예외가 실제 로그에 기록됐다. JUnit XML은 생성되지 않았다. 해당 APK는 진단용 아티팩트만 보존됐으며 실기 후보로 승인하지 않는다. 수정판의 전체 CI 성공과 신뢰 가능한 독립 서명이 먼저 필요하다.
- 두 개발판의 패키지는 `com.droiddeck.launcher.ai`, CI 공개 테스트 인증서 지문은 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`. 테스트 인증서는 배포 신뢰를 제공하지 않는다. 개인 키는 본인이 관리하며 공유하거나 저장소에 올리지 않는다. 신뢰 가능한 독립 서명 및 정상 설치 절차 확인이 선행되어야 한다.

| 시험 ID | 절차 | 합격 기준 | 상태 |
|---|---|---|---|
| D01 | 원본 앱·세이브 백업 및 설치 식별자 확인 | 원본 앱 삭제/초기화 없이 두 앱 분리, 오류 발생 시 중단 | 미검증 |
| D02 | 독립판 첫 실행 및 종료/재실행 | 충돌 없음, 원본 공유 저장소 변경 없음 | 미검증 |
| D03 | 복제한 INI/CFG 변경 미리보기·취소·적용·복구 | 취소 후 원본 해시 유지, 복구 후 백업 해시 일치 | 미검증 |
| D04 | 테스트용 텍스트 번역 및 ChatGPT 공유 | 변수 보존, 잘못된 응답 거부, 초안 재개 | 미검증 |
| D05 | 테스트용 이미지 OCR 및 ZIP 내보내기 | 검토한 영역과 출력 일치, 게임 파일 변경 없음 | 미검증 |
| D06 | 소유한 게임 실행 및 호환성 이력 | 결과/버전/오류 기록, 강제 적용 없음 | 미검증 |
| D07 | 같은 장면 성능 비교 | 게임/출력/드라이버/Proton 기록, 프레임 중단 시 측정 거부 | 미검증 |
| D08 | 게임 실행 중 AI/스토리지 검사 제한 | 기능 제한이 작동하고 세이브 보존 | 미검증 |

### RE7 검증 기록

D01~D03 통과 후 소유한 게임에서만 시험한다. 최초 실행은 설정을 변경하지 않고 관측한다. 실행 환경, Android 버전, 게임 버전, Proton, 그래픽 드라이버, 실제 출력 해상도, 게임 내부 렌더 설정을 별도로 기록한다. 검은 화면/무음/메모리 종료가 관측되면 오류 유형과 시간, 민감 정보를 제거한 관련 로그만 기록한다. 이전 오류가 재현되는지 확인하되 원인을 단정하지 않는다. 실행 실패 상태에서 FPS를 비교하지 않는다. 실행 성공 후 동일 장면의 반복 측정으로 FPS/P95를 기록한다. 실제 게임 파일에 변경을 적용하기 전 복제 파일에서 D03을 통과해야 한다. 실기 설치나 게임 실행 성공은 아직 확인되지 않았다.


## 6. 공유 응답·작업 재개용 합성 검증 자료

소스 후보 f30d1dccaa2acd140590a0321e2c5bdd0a6c01c8의 실기 시험을 위해 별도 ZIP 테스트 키트를 생성했다. ZIP SHA-256: `0ea09b9a8d19014f3ada28da65aac1f688182841a4bb317fd87a3b81b1ef52fb`. ZIP은 대화에서 별도로 제공하며 저장소에는 바이너리를 올리지 않았다. 15개 항목의 ZIP 무결성, 포함된 SHA256SUMS의 13개 파일 해시, 합성 PNG 크기(640x240), JSON 기본 구조를 로컬 Python으로 검사했다. DroidDeck 내부 처리 또는 실기 성공을 뜻하지 않는다.

| 시험 | 합성 입력 | 예상 결과 |
|---|---|---|
| D09 | GameUserSettings.ini | 720p 미리보기, 원본 유지·복구 시 해시 일치 |
| D10 | menu.tsv, options.csv, dialog.json | 변수 유지, 번역 초안 및 내보낸 결과 검토 |
| D11 | 합성 menu.tsv의 ChatGPT 요청 | 중복 응답, 다른 응답 비교·취소, 재시작, 작업 센터에서 재개, 잘못된 ID 및 원본 거부 |
| D12 | synthetic_memory.log, synthetic_device.log | 메모리 신호와 장치 손실 구분, 자동 변경 없음 |
| D13 | ocr_sample.png | START/OPTIONS 인식과 초안·내보내기, 원본 유지 |
| D14 | duplicate.tsv, nested.json | 지원하지 않는 입력 거부 |

모든 시험은 미검증 상태다. 실제 게임 파일 대신 별도 복사본으로 시행한다. 신뢰 가능한 독립 서명과 전체 CI 성공 확인 전에는 설치를 권하지 않는다. 기존 빌드 브랜치와 코드에는 변경을 가하지 않는다. GitHub 연결 도구의 combined status와 PR 전용 실행 조회, 공개 웹 조회에서도 f30d1dc push 실행 번호를 확보하지 못했다. 실행 성공·실패 여부는 아직 모른다.


## 7. 합성 파일 기반 실제 Kotlin 엔진 검증

검증 기준 소스: `f30d1dccaa2acd140590a0321e2c5bdd0a6c01c8`. 로컬 JDK 21 및 kotlinc에서 실제 제품 소스 7개와 독립 KitCheck.kt를 함께 컴파일하고, ZIP에서 꺼낸 합성 테스트 입력 및 예상 결과를 사용했다. Git blob SHA가 기준 소스와 일치함을 확인했다. Android 테스트 대역 없이 실제 JVM 파일과 ZIP 처리 사용. 실제 게임 파일은 변경하지 않았다.

- 결과: 85/85 검사 통과. INI 최적화 후보 3개 및 720p 예상 결과, TSV/CSV/JSON 행 ID와 변수 보존 및 변수 누락 차단, 네 파일 모두 원본 유지·ZIP 단일 항목/바이트/재가져오기·패치 적용·백업 검색·오래된 미리보기 거부·외부 변경 후 복원 거부·손상 백업과 잘못된 대상 거부·바이트 단위 원본 복원 확인. 중복 TSV와 중첩 JSON 거부, 합성 Android/GPU 메모리 신호와 장치 손실 분리 및 알 수 없는 로그 처리 확인.
- 소스 blob: GameConfigPreview 7f7adfce8f72acf04fda8571398727e0f3bb5fc1; GamePatchAuthor 84a3c21b377fe8351d8f89b36a4b7e50de3e5dca; GamePatchStore 41bfa1b43d9b505c142e7a725336bda3290ec1b4; LocalGameAdvisor 5215b096fb45b2e402c0a1ac5430785ab13f59b4; TranslationJson 935bbc68d039daabbb13eff34f654d90e542d5ef; TranslationLimits 34c8befcc6266a0dbb8a20f88a31a441ce012030; TranslationTable 01868fff6a572ceba4d98a688febd56879b4fe02.
- 실행 명령: `kotlinc GameConfigPreview.kt GamePatchAuthor.kt GamePatchStore.kt LocalGameAdvisor.kt TranslationJson.kt TranslationLimits.kt TranslationTable.kt KitCheck.kt -include-runtime -d kitcheck.jar && java -jar kitcheck.jar fixtures`. 출력 마지막 줄: `TOTAL PASS 85`. 이번 검사는 D09/D10/D12/D14의 호스트 엔진 범위 일부에 해당한다. Android UI, 저장소 권한, OCR, ChatGPT 앱 공유, 로컬 AI, 실제 게임 실행 및 실제 기기 시험은 모두 미검증 상태 유지. 성능 향상이나 RE7 오류 해결을 주장하지 않는다.
- 최신 빌드의 combined status 및 PR 기반 실행 목록은 비어 있고, 컨테이너의 공개 GitHub API 요청은 DNS 오류로 실패했다. 빌드 상태 미확인. 기존 CI 실행에 영향을 주지 않기 위해 제품 코드와 워크플로는 변경하지 않았다.


## 8. ChatGPT 수동 교환 엔진 및 작업 재개 호스트 검증

기준 소스 f30d1dccaa2acd140590a0321e2c5bdd0a6c01c8. 실제 제품 Kotlin 13개와 AiCore.java를 Git blob SHA로 대조한 후 JDK 21 / kotlinc 및 설치된 kotlinx-coroutines-core-jvm.jar를 사용해 /tmp/dd-exchange-host에서 컴파일·실행했다. Android 및 AI 모델 대역 없이 실제 요청/응답 처리 엔진, 체크포인트와 초안 디스크 파일을 사용했다. 응답은 직접 작성한 합성 JSON이며 실제 ChatGPT 호출은 없었다.

- 실행 결과 `TOTAL PASS 20533`: 요청 생성·원본/초안 결합·재개·응답 거부·설정/로그 작업 53개 검사와 1~8개 행의 모든 기존 초안 조합, 시작 위치 및 1~5개 요청 한도에 대한 20,480개 범위 검사. 기존 번역 보존, 순환 작업 순서, 다른 요청 ID/파일/확장자/원본/초안/요청 문구 거부, 잘못된 변수 및 비대상 문장·중복 JSON 키 거부, 변경되지 않은 번역만 담은 응답 거부, 유효한 응답 수락 후 명시적 초안 저장, 오래된 초안 덮어쓰기 차단, 원본 파일 보존, 수치 설정 미리보기와 로그 증거 분리 확인. 요청 전문에 대상 경로가 없고 체크포인트에 대상 경로 및 합성 원문이 없는 경우도 확인했다.
- 추가 검증 소스 blob: AiCore a259cad8f9963c31f39da29a263d804f31b32510; AiHostBridge c2fbd001e374fdb955c31e5257fd508807005104; ChatGptExchange 887b3dd2db048400e1633121b768dcda7dd5420d; GameAgent c0fa166fafd43f8bdd3348c08f664515db6af9ec; TranslationAgentWindow f646b3b2031dd584a7b13299636af1a8ca14ace7; TranslationDraftStore 2cb8137058cb583372b367b8dba94a0ab613c5eb; ExchangeCheckpoint a9a34ed8c1cbec64af4906515b006c5f67f0725a; 나머지 패치 엔진 blob은 7절과 동일.
- 실행: `javac -d classes AiCore.java`, `kotlinc *.kt -cp "classes:$KLIB/kotlinx-coroutines-core-jvm.jar" -include-runtime -d exchange.jar`, `java -cp "exchange.jar:classes:$KLIB/kotlinx-coroutines-core-jvm.jar" com.droiddeck.launcher.files.CheckKt`. KLIB는 설치된 Kotlin lib 디렉터리. Check.kt는 별도 임시 검증 코드다. 이 검사는 Android/JUnit 전체 테스트나 실기 D11 통과가 아니다.
- f30d1dc의 연결 도구 combined status 및 PR 기반 workflow 목록이 여전히 비어 있음. 실제 push run의 ID/결과는 미확인. 제품 코드/빌드 정책 및 기존 통합 ref 유지. 다음 단계는 실행 번호 또는 결과 URL 확보, 실제 전체 CI 확인 후 신뢰 가능한 독립 서명과 기기 검증. 단순 호스트 검사 반복으로 출시 준비도를 높여 표시하지 않는다.
