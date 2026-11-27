# DroidDeck AI 개발 명세

공식 0.7.1 기준 cdff94e72c45da50af43bbceff28effaf0179caf에서 이어지는 독립 Android 게임 실행 환경이다. 개발 브랜치는 ai-assistant-stable이다. 스마트폰과 무료 GitHub Actions에서 관리한다. 오프라인 규칙 기반 한국어 명령과 결과 설명을 우선한다. 로컬 모델은 메모리·속도·라이선스 평가 후 결정한다.

| 기능 | 완료 조건 |
|---|---|
| 게임 진단 | 로그의 관측 사실과 가설을 구분하고 요청 설정, 저장 파일, 실행 측정값을 비교 |
| 최적화 | 해상도·그래픽·API·드라이버·호환 레이어·실행 옵션의 관련 설정 한 그룹을 제안하고, 승인·백업·중단·회귀 복원·반복 제한 제공 |
| 성능 기록 | 실제 FPS·프레임타임·CPU·GPU·메모리·온도·배터리를 가능한 범위에서 전후 비교 |
| 호환성 DB | 게임·기기·런타임 버전별 성공 및 실패와 측정값을 로컬 저장 |
| 플랫폼 | Steam, Epic, GOG부터 확인하고 Ubisoft·EA는 공식 로그인과 정식 실행 절차로 검증 |
| 모드 및 번역 패치 | 사용자가 고른 자료의 경로·형식·용량·충돌 검증, 미리보기와 복원 |
| 저장공간 | 게임·캐시·중복 후보·백업 사용량 표시, 삭제 전 승인 |
| 공식 업데이트 | 변경 검토, 격리된 병합 시험, 회귀 테스트와 복구 |

공식 앱과 데이터 보호가 우선이다. 로그인 정보 및 분석 로그의 외부 전송, 유료 API, 보안·열 보호·DRM 우회, 자동 세이브 삭제를 금지한다. 화면 번역과 커뮤니티 공유는 제외한다. 설치·기기 호환성 판정에는 해당 기기의 측정 결과가 필요하다.

## 사용자 추가 요청: 직접 제작 기능

간단한 설정형 모드 제작과 한글 패치 제작을 개발 범위에 포함한다. 먼저 기존 INI/CFG 스칼라 키와 제한된 id/text TSV 텍스트를 지원하고, 미리보기·별도 ZIP 내보내기·백업 후 적용을 제공한다. 사용자 번역 입력과 자동 AI 번역은 별도 단계이며 로컬 모델은 아직 탑재되지 않았다. 게임별 바이너리, 엔진별 형식, 다중 파일 및 자동 번역은 미완료. 원본 및 사용자 데이터 보호 조건은 유지한다.

### 파일 기반 한글 패치 제작 원칙 (사용자 확인)

실시간 화면 번역·OCR·화면 오버레이는 개발 범위에서 제외한다. 게임 실행 전/후 파일의 번역 대상 추출, 배치 자동 번역(추후 오프라인 모델 검토), 직접 편집, 초안 저장·재개, 변수/형식 검증, 패치 파일 생성·백업 후 적용에 집중한다. 초안은 앱 전용 저장소에 사용자가 명시적으로 저장하며 대상 경로와 원본 SHA-256으로 구분한다. 다른 파일이나 업데이트된 원본에 자동 재사용하지 않는다.

## User-confirmed primary direction: integrated local AI agent

Prioritize real on-device language model inference and an integrated natural-language assistant over further isolated rule buttons. The user wants the AI to analyze selected game files, create simple mods and Korean patches directly in DroidDeck, diagnose game problems and propose tested optimization/recovery plans. Existing deterministic components remain tools for validation, preview, approved backup/application and rollback, not a substitute for the language model. No live screen translation. No paid/cloud inference, arbitrary shell execution, security bypass or uncontrolled file writes. Initial milestone: local model + strict structured tool proposals against a user-selected file; later broaden file discovery and bounded multi-step plans. Model/runtime selection and actual hardware evaluation remain mandatory.
