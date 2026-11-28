# 개발 상태

인계 시점 HEAD는 c9abc2f이다. 기준 앱 0.7.1에서 분기했다.

| 기능 | 상태 | 증거 |
|---|---|---|
| INI/CFG 단일 키 미리보기 | 구현됨 | GameConfigPreview, GamePatchDialog, FileManagerScreen |
| 백업과 복원 | 구현됨 | 105c283, 6d1804d, 9410961 |
| 불완전한 백업 거부 | 테스트 코드 추가됨 | c9abc2f |
| APK 목록 및 소스 경로 감사 | 구현됨 | 45354946426, 45359358765 |
| 독립판 실기 설치 및 게임 실행 | 미검증 | S23+ 설치 및 실행 결과 필요 |

Steam, Epic, GOG 소스가 있다. Ubisoft와 EA의 전용 스토어 통합은 확인되지 않았다. 플랫폼 소스의 존재와 게임 실행 성공을 별도로 기록한다.

상태는 계획됨, 구현됨, 자동 테스트 통과, 빌드 성공, 실제 기기 검증 완료로 기록한다. 최근 테스트·변경은 아래에 누적한다.

## 독립판 구현

인계 문서 커밋 c32cdae 이후 별도 설치 ID와 공개 로그·세이브·스크린샷 폴더, 백업 본문 SHA-256, 제한된 UTF-8 텍스트 패치, 한국어 로그 규칙과 16:9 해상도 비교 계획을 구현했다. 로컬 JVM에서 GamePatchTest 13개와 Python APK 인벤토리 테스트 4개가 통과했다. 소스 경로 감사 JSON을 재생성했다.

로컬 전체 Python 테스트 2,111건은 실패 88, 오류 46, 건너뜀 72를 기록했다. 네트워크 소켓, 네이티브 도구 및 런타임 환경 관련 실패가 포함된다. 전체 테스트 통과나 Android 실기 검증을 의미하지 않는다.

## 성능 측정·파일 관리

082304f에서 독립 APK 빌드를 시작했다. Actions의 스토어 호스트 테스트와 Python·네이티브 회귀 테스트 작업이 통과했다. 후속 코드에서는 실제 Gamescope 게임 프레임을 10초 측정하고 FPS와 P95 프레임타임을 같은 기기·게임의 이전 측정과 비교한다. 게임·프로세스·출력 변경이나 샘플 누락 시 중단한다. SQLite에 최근 500건만 보관한다. 비교는 수동으로 고른 장면의 관측값이며 변경이 개선을 일으켰다는 인과 판정은 하지 않는다.

파일 속성에서 폴더 사용량, 임시 파일 후보와 같은 이름·크기의 중복 후보를 조회할 수 있다. 단일 UTF-8 파일 또는 같은 이름의 파일 하나만 담은 ZIP으로 텍스트 패치를 미리보고 적용한다. 백업을 디스크에 동기화하고 다시 읽어 검증한다. 폴더·ZIP·구형 백업을 포함한 JVM 테스트 16건이 통과했다.

## 공식 업데이트와 회귀 검토

5c1b080을 공식 main 1534cf1에 대해 기준 태그 0.7.1(cdff94e)으로 시험 병합했다. 공식판 변경 범위는 1,260개 파일이다. AndroidManifest.xml, FileManagerScreen.kt, GameFilesTest.kt, GameLaunchIntentTest.kt 네 파일이 충돌했다. 비교는 임시 트리에서 수행했다. 업데이트 적용은 보류했다.

독립 빌드 비교 단계는 공식 APK의 고정 SHA-256, 실제 권한, 인증서, provider authority, taskAffinity 및 변경 파일을 검사한다. 파일시스템 쓰기 검사 중 기존 steamapps/.writable을 지울 수 있는 문제를 찾아 고유 임시 파일로 교체하고 회귀 테스트를 추가했다. 720p·900p 및 지원하는 Unreal 설정의 30 FPS 제한 계획을 구현했다. 로컬 JVM 테스트 17건을 통과했다.

## 스마트폰 사용과 복구

- 파일 관리자에서 복사한 INI/CFG를 열고 기존 키를 지정하거나 ‘900p 균형’ 등 지원 명령을 선택한다. 원본 및 제안 값 확인 후 게임을 닫고 ‘백업 후 적용’을 누른다. GameUserSettings.ini에서는 기존 ResolutionSizeX/Y 또는 FrameRateLimit만 수정한다.
- LOG 파일을 열고 ‘로그 분석’을 누른다. 신호가 있으면 관측된 오류 코드와 확인 방법이 표시된다.
- 소유한 UTF-8 패치는 게임의 대상 파일을 먼저 열고 문서 선택기로 가져온다. ZIP은 대상과 이름이 같은 최상위 파일 하나만 허용한다. 읽기·해제 한도는 각각 1 MiB이다. 페이지를 넘겨 내용을 검토한 뒤 적용한다.
- 되돌리려면 같은 파일을 다시 열고 ‘최근 변경 복원’을 누른다. 현재 내용이 적용본과 달라지면 덮어쓰기를 거부한다. 이때 바뀐 파일의 복사본을 먼저 확보하고 차이를 확인한다. 앱 전용 백업은 앱 삭제로 함께 사라질 수 있다.
- 게임의 텔레메트리 화면에서 ‘게임 성능 10초 측정’을 누른다. 같은 장면에서 변경 전후 측정을 반복한다. 화면 이탈 또는 ‘측정 중단’으로 중단할 수 있다. 출력 해상도와 게임 내부 렌더 해상도는 별개로 기록·해석한다.
- 파일 관리자에서 폴더 속성을 열고 ‘폴더 사용량 분석’을 누른다. 최대 10,000개 항목과 깊이 32까지 살핀다. 이름·크기 중복은 후보이며 삭제는 실행하지 않는다.

## 2026-11-25 개발 브랜치 인계

기준 안정판은 a367bc7, 추가 개발 브랜치는 ai-compatibility-history이다. 79ea124에서 앱 메모리 경고와 유휴 상태의 Steam 터치 아이콘 캐시 해제, 실행 상태에 따른 폴더 검사 중단, 게임 상세 화면의 호환성 이력, 장면과 출력 해상도별 실제 프레임 비교를 연결했다. b2175c6에서 메모리 경고 테스트가 Android 상수를 참조하도록 변경했다. JVM 테스트 23건, APK 목록 검사 4건, CI 검사 6건이 로컬에서 통과했다. 실제 기기 설치와 성능은 미검증이다.

이전 안정판 Actions 실행 45385418392에서는 APK 컴파일 후 Android 테스트 3,865건 중 11건이 실패했다. 개발판 빌드는 포크의 Actions를 다시 활성화한 뒤 [실행 45595329653](https://github.com/jcy565/DroidDeck/actions/runs/45595329653)에서 시작했다. 대상 소스는 b2175c6이다. 공개 AOSP 테스트 키를 쓰는 개발 아티팩트이며 독립 배포용 개인 서명, 실제 설치 차단 원인, 게임 세이브 보호와 공유 경로 분리는 실기기 검증이 남아 있다. 다음 단계는 빌드·테스트·APK 비교 결과 확인과 실제 측정 및 복구 경로 검증이다.


## 2026-11-28 전체 통합 현황
- 통합 빌드 기준: ai-chatgpt-exchange-next / 7ee193009ae965cb42c6d2869882bd04ee9a94d7. 실행 46074435272 진행 중. 이전 46036246987은 OpenCV AAR 404로 Gradle 전 실패. 이전 45986669287은 APK와 전체 Android 단위 테스트 성공, 최신 코드와 동일하지 않음.
- 호환성 버전 이력, 성능 비교, 설정 승인/백업/복구, 게임 우선 처리: 소스 구현, 최신 실기 측정 미검증. 드라이버 변경/복원 범위 제한.
- ChatGPT 수동 공유 및 JSON 검증/요청 복원: 구현, 실제 Android 공유 미검증. 선택적 로컬 모델: 실험적, 기본 비활성, 실제 추론·메모리·SDK 호환 미검증.
- 텍스트 모드/번역: 제한된 INI/CFG, CSV/TSV/flat JSON, 초안/단일 파일 패치 및 작업 목록. 저장된 목록은 별도 staging, 다중 파일 적용 및 엔진별 바이너리 미완료.
- 정적 이미지 번역: Latin/일본어/중국어 OCR, 영역 검토, PNG/TGA/제한된 DDS, 배치와 ZIP, 원본 백업 및 진단 구현. 실제 게임 이미지 적용/복구 UI 비활성. 최신 PNG zlib 검사 staging, Android 미검증.
- 독립 설치/모듈: 별도 패키지와 :ai-core 계약, 서명된 AI 데이터 업데이트 기반. 실제 독립 배포 인증서, 설치/업데이트 및 S23+ 게임 실행 미검증.
- 플랫폼: Steam/Epic/GOG 소스 존재, 실제 게임별 실행 보장 없음. Ubisoft/EA 별도 통합 미확인.
- 우선순위: 통합 CI 성공 → staging 통합 재검증 → 신뢰 가능한 독립 서명 및 설치 → ChatGPT 공유/OCR/게임 실행/복구 및 성능 실측 → 로컬 모델 검증. 실기 검증 전 위험한 자동 적용 비활성 유지.


## 최신 통합 현황 (2026-11-28)
- 통합 후보 ai-png-compose-integration-next, 소스 cb1b98a, Actions 46089730585: [{"name":"Pin source tree","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"Xbox ARM64 backend","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"Store engines host tests","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"Python and native regression tests","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"components-runner / Pick runner","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"Prepare verified components","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"build-runner / Pick runner","status":"completed","conclusion":"success","active":[],"failed":[]},{"name":"Compile APK and Android tests","status":"in_progress","conclusion":null,"active":["Build"],"failed":[]}]. 7개 사전 작업 성공, 최종 Android 결과 미확정. 직전 교환 브랜치 빌드 70: APK 컴파일 성공, Android 4,228건 중 4,226 통과, Compose idle timeout 2건. 이전 성공 빌드는 최신 소스 검증이 아님.
- 후속 브랜치 ai-integrated-fixture-queue-next에 작업 목록 동시 변경 방지, 화면별 작업 취소, 이미지 목록 SHA-256 및 구형 호환성, 이미지 초안 경로 보호, 이미지/텍스트 탐색·목록 검증 일치 반영. 7313693, 6de08ca, cfb6bf3의 텍스트 검증 개선은 실제 독립 Kotlin 35개 검사 통과(파서 등 일부 테스트 대역 사용). 전체 Android 테스트 미실행.
- 구현 수준과 출시 준비도를 구분한다. 기능 구현 기준 약 85%는 주관적 추정이며 가중치가 정의된 공식 지표가 아니다. 실기 검증은 0건 확인. 실제 게임 적용·복원 및 자동 드라이버 변경은 검증된 범위 밖에서는 계속 제한한다. 최신 통합 빌드 완료 전 실행 중인 브랜치 변경 금지. 다음은 전체 XML 및 Compose 부분집합 결과 분석, 후속 통합, 신뢰 가능한 서명·설치 및 Galaxy S23+ 실측.


## 현재 통합 안정화 상태 (2026-11-28)
- 직전 통합 실행 46089730585는 종료(failure). APK 컴파일·테스트 키 서명·서명 검증·인벤토리·공식 0.7.1 비교 성공. Android 전체 테스트는 Robolectric native runtime Maven HTTP 429로 시작하지 못했으며 전체 및 17개 진단 로그에 JUnit XML 없음(0 관측, 성공으로 해석 금지). 이전 정상 실행의 4,228건 중 4,226 통과와 Compose timeout 2건은 해결 여부 미확인.
- 통합 브랜치 ai-png-compose-integration-next는 c7118bfa. SHA-512 검증 native runtime 사전 준비 및 Gradle 전용 로컬 저장소 설정 반영. 새 push 실행 번호는 연결 도구의 PR 전용 실행 목록과 빈 combined status로 확인 불가. 실제 다운로드/Gradle 테스트 결과 불명. 기존 통합 브랜치를 재푸시하거나 취소하지 않음.
- 후속 ai-integrated-ci-diagnostic-next에는 CI 진단 생략 판단, 다운로드 무결성 재시도, 텍스트 패치/초안/재사용/작업 목록 및 이미지 초안/목록/문자 검증을 반영. 345dc6d와 79bf438은 게임 경로와 백업 저장소의 겹침 방지. 정확한 소스 및 독립 Kotlin 검사 결과는 AI_HANDOVER.md 참고. 독립 검사는 Android 전체 테스트가 아님.
- 구현률 약 85%는 기존 주관적 추정치를 유지하며 새로 정량 산출하지 않았다. 텍스트와 이미지 검증 완료 범위는 실제 파일을 사용한 독립 검사; Android OCR, 공유, 설치, 실행, 성능 및 AI 모델 추론은 여전히 실기 미검증. 이미지 실제 적용/복구 UI는 비활성 유지.
- 다음 최우선: 신규 실행 번호 확보 및 종료 결과/XML 확인, 후속 브랜치 비교 및 비강제 통합, 전체 필수 CI, 신뢰 가능한 서명 및 실기 설치, RE7 등 게임별 오류/성능 측정 및 복구 검증. Ubisoft/EA 통합 및 범용 바이너리 번역은 미완료. 실행 목록: https://github.com/jcy565/DroidDeck/actions?query=branch%3Aai-png-compose-integration-next
