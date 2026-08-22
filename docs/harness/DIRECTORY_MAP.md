# Directory Map

이 문서는 프로젝트 개념 구조가 아니라 저장소의 주요 디렉토리와 파일 역할을 설명하는 문서이다.

## Root structure

```text
.
├── AGENTS.md
├── CLAUDE.md
├── GEMINI.md
├── README.md
├── .env.local.example
├── agent_outputs/
│   ├── .gitkeep
│   ├── clarification_requests/
│   │   └── .gitkeep
│   ├── llm_context/
│   │   └── .gitkeep
│   ├── reports/
│   │   └── .gitkeep
│   └── run_logs/
│       └── .gitkeep
├── docs/
│   ├── harness/
│   │   ├── README.md
│   │   ├── QUICKSTART.md
│   │   ├── PROJECT_CONTEXT.md
│   │   ├── HANDOFF.md
│   │   ├── DIRECTORY_MAP.md
│   │   ├── CLARIFICATION_FORMAT.md
│   │   ├── archive/
│   │   │   └── .gitkeep
│   │   └── flows/
│   │       ├── .gitkeep
│   │       ├── README.md
│   │       ├── multi_user_workflow.md
│   │       └── sync_from_template.md
│   └── spikes/
│       └── instagram_native_discovery.md
├── prompts/
│   ├── .gitkeep
│   ├── harness/
│   │   ├── README.md
│   │   ├── audit_doc_drift.md
│   │   ├── generate_chat_llm_context.md
│   │   ├── reset_for_new_project.md
│   │   ├── sync_from_template.md
│   │   └── verify_task_result.md
│   └── tasks/
│       └── .gitkeep
├── src/
│   ├── main/
│   │   ├── java/com/losmos/hrsnsauto/
│   │   │   ├── HrSnsAutoApplication.java
│   │   │   ├── candidate/
│   │   │   └── discovery/
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── db/migration/V1__...sql ~ V5__...sql
│   │       ├── static/css/app.css
│   │       └── templates/
│   │           ├── candidates/
│   │           └── discovery/index.html
│   └── test/
│       ├── java/com/losmos/hrsnsauto/candidate/
│       ├── java/com/losmos/hrsnsauto/discovery/
│       └── resources/
└── scripts/
    ├── run-local.sh
    ├── instagram_native_discovery_probe.py
    └── test_instagram_native_discovery_probe.py
```

## Root files

- `AGENTS.md`: AI Agent가 따라야 하는 공통 작업 규칙이다. 작업 시작, 모호성 처리, 문서 갱신, 검증 원칙을 정의한다.
- `CLAUDE.md`: Claude Code가 자동으로 읽는 진입점이며, 공통 작업 규칙이 `AGENTS.md`임을 명시하는 포인터 파일이다.
- `GEMINI.md`: Gemini CLI가 자동으로 읽는 진입점이며, 공통 작업 규칙이 `AGENTS.md`임을 명시하는 포인터 파일이다.
- `README.md`: local 실행, 최초 설정, Meta token과 Playwright Chromium 관리 방법을 안내한다.
- `.env.local.example`: 비밀값을 제외한 local 실행 설정 예시이다. 실제 `.env.local`은 gitignored된다.
- `.gitignore`: git에 포함하지 않을 실행 산출물과 임시 파일 규칙을 정의한다.

## docs/harness/

- `docs/harness/README.md`: 하네스의 목적, 구성, 기본 작업 흐름을 설명하는 문서이다.
- `docs/harness/QUICKSTART.md`: reset, 문서 드리프트 감사, context snapshot 생성, 일반 작업 실행, 작업 결과 검증, clarification request 확인 흐름을 빠르게 안내하는 문서이다.
- `docs/harness/PROJECT_CONTEXT.md`: 프로젝트 목적, 배경, 목표, 제약사항, 확정된 사실, 결정 사항, 미확정 질문, 참고 산출물, 다음 작업 기준을 담는 장기 프로젝트 맥락 source of truth이다.
- `docs/harness/HANDOFF.md`: 중단기 작업 기억과 직전 작업 기억을 담는 인수인계 문서이다.
- `docs/harness/DIRECTORY_MAP.md`: 저장소의 디렉토리와 파일 역할을 설명하는 문서이다.
- `docs/harness/CLARIFICATION_FORMAT.md`: clarification request의 파일 형식 명세와 처리 흐름을 정의하는 참조 문서이다.
- `docs/harness/flows/`: 기능, 업무, 운영 흐름별 상세 문서를 저장하는 디렉토리이다.
- `docs/harness/flows/README.md`: flows 디렉토리의 용도, 페이지 인덱스, 인덱스 갱신 규칙, 페이지 간 링크 관례를 정의하는 인덱스 문서이다.
- `docs/harness/flows/multi_user_workflow.md`: 여러 개발자가 브랜치에서 하네스 문서를 갱신할 때의 병합 규칙을 설명하는 가이드 문서이다.
- `docs/harness/flows/sync_from_template.md`: 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 흐름을 설명하는 가이드 문서이다.
- `docs/harness/archive/`: 오래된 컨텍스트, 인수인계, 결정 기록을 보관할 수 있는 디렉토리이다.

## docs/spikes/

- `docs/spikes/`: production 구현 전에 외부 capability와 기술 가설을 재현 가능하게 검증하는 spike 문서를 저장한다.
- `docs/spikes/instagram_native_discovery.md`: 공식 Meta Instagram API의 hashtag media author identity capability probe 범위, 실행법, 판정 기준을 설명한다.

## scripts/

- `scripts/run-local.sh`: `.env.local`, macOS Keychain 또는 hidden prompt, Docker Compose PostgreSQL을 준비하고 Spring Boot local 실행을 시작한다.
- `scripts/instagram_native_discovery_probe.py`: Python standard library만으로 Instagram-native candidate discovery 가능성을 live Graph API에서 확인하는 독립 probe이다.
- `scripts/test_instagram_native_discovery_probe.py`: 외부 network 없이 probe의 URL, redaction, classification, parsing, dedupe를 확인하는 synthetic unit test이다.

## src/main/java/

- `com.losmos.hrsnsauto.candidate`: 수동 Candidate 등록, evidence 저장, deterministic eligibility 판정과 기존 MVC 흐름을 구현한다.
- `com.losmos.hrsnsauto.discovery`: Discovery hashtag 설정, Instagram media inbox, 다중 hashtag association, review 상태, Meta Graph client, 수동 sync service와 `/discovery` MVC 흐름을 구현한다.
- `MetaInstagramClient`: `META_ACCESS_TOKEN`, `META_GRAPH_API_VERSION`, `META_IG_USER_ID` 설정으로 hashtag lookup과 recent media 첫 page만 호출한다. Bearer header와 sanitized error 경계를 담당한다.
- `DiscoveryService`: hashtag normalization·enable 상태, media ID upsert, source association, `NEW`·`OPENED`·`DISMISSED` 전이를 담당한다.
- `InstagramBrowserClient`: Playwright persistent Chromium context 하나와 page 하나를 재사용하며 navigation timeout, login/challenge, browser binary/profile 오류 경계를 담당한다.
- `InstagramBrowserExtractor`: post author profile link와 공개 profile/post metadata selector·fallback을 격리한다. generated CSS class와 caption username 추측에 의존하지 않는다.
- `InstagramBrowserEnrichmentService`: operator-triggered session 준비, 단건, observation 없는 최신 `NEW` item 순차 batch와 단일 실행 lock을 담당한다.
- `DiscoveryBrowserObservation`: Discovery item별 최신 browser screening observation과 `SUCCESS`·`PARTIAL`·`LOGIN_REQUIRED`·`ACTION_REQUIRED`·`FAILED` 상태를 저장한다.
- `InstagramMetricParser`: 한국어·영문 compact visible count를 명시된 HALF_UP 규칙으로 nonnegative 정수화한다.

## src/main/resources/

- `application.properties`: PostgreSQL/Flyway, optional Meta 환경변수와 기본 disabled인 Instagram browser 환경변수 mapping을 정의한다.
- `db/migration/V1__baseline.sql` ~ `V3__add_hair_transplant_evidence_finding.sql`: 기존 Candidate schema migration이다.
- `db/migration/V4__create_instagram_discovery_inbox.sql`: Discovery hashtag, media item, item-hashtag association schema와 기본 hashtag 3개를 추가한다.
- `db/migration/V5__create_discovery_browser_observations.sql`: item별 최신 browser observation, nonnegative count, 상태·오류 제약을 추가한다.
- `templates/candidates/`: Candidate 목록·등록·상세 Thymeleaf 화면이다.
- `templates/discovery/index.html`: hashtag 관리, API sync, browser 상태·session·단건·순차 batch, screening observation과 media review action을 제공하는 Discovery Inbox 화면이다.
- `static/css/app.css`: Candidate와 Discovery 화면이 공유하는 CSS이다.

## src/test/

- `java/com/losmos/hrsnsauto/candidate/`: 기존 Candidate domain·repository·service·MVC 회귀 테스트이다.
- `java/com/losmos/hrsnsauto/discovery/MetaInstagramClientTest.java`: 외부 network 없이 URL, Bearer header, response parsing, error redaction을 검증한다.
- `java/com/losmos/hrsnsauto/discovery/DiscoveryHashtagServiceTest.java`: hashtag normalization, duplicate, enable 상태를 검증한다.
- `java/com/losmos/hrsnsauto/discovery/DiscoveryPersistenceTest.java`: PostgreSQL에서 V4 seed, media idempotency, source association과 review 상태를 검증한다.
- `java/com/losmos/hrsnsauto/discovery/DiscoveryControllerTest.java`: `/discovery` page와 API/browser form/action route를 검증한다.
- `java/com/losmos/hrsnsauto/discovery/InstagramMetricParserTest.java`: plain/grouped/한국어·영문 compact count, HALF_UP, 불확실값 거부를 검증한다.
- `java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractorTest.java`: profile URL·username 검증과 author link/caption/non-profile path filtering을 검증한다.
- `java/com/losmos/hrsnsauto/discovery/InstagramBrowserEnrichmentServiceTest.java`: disabled 경계, 순차 batch 독립 저장과 login-required 중단을 검증한다.
- `java/com/losmos/hrsnsauto/discovery/DiscoveryBrowserObservationTest.java`: partial/failure field mapping, negative count 거부를 검증한다.
- `java/com/losmos/hrsnsauto/discovery/InstagramBrowserErrorSanitizerTest.java`: credential, session directory와 multiline page detail 정제를 검증한다.

## prompts/

- `prompts/harness/`: 하네스 운영용 재사용 프롬프트를 저장하는 디렉토리이다.
- `prompts/harness/README.md`: 디렉토리 용도와 각 프롬프트 파일의 역할을 안내하는 로컬 인덱스이다.
- `prompts/harness/generate_chat_llm_context.md`: 새 대화형 LLM에게 전달할 context snapshot 생성 프롬프트이다.
- `prompts/harness/reset_for_new_project.md`: 하네스를 새 개발 프로젝트에 복사한 뒤 작업 맥락과 임시 산출물을 초기화하는 프롬프트이다.
- `prompts/harness/sync_from_template.md`: 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 프롬프트이다.
- `prompts/harness/verify_task_result.md`: 이미 실행한 작업 결과를 새 컨텍스트에서 읽기 전용으로 다시 채점하는 검증 프롬프트이다.
- `prompts/harness/audit_doc_drift.md`: 하네스 문서와 실제 저장소 상태의 드리프트를 감사하는 프롬프트이다.
- `prompts/tasks/`: AI Agent에게 줄 일반 작업 단위 프롬프트를 저장하는 디렉토리이다.

## agent_outputs/

- `agent_outputs/`: AI Agent 실행 산출물의 상위 디렉토리이다. 루트에는 일반 분석 Markdown 산출물을 직접 쌓지 않고 목적별 하위 디렉토리에 저장한다.
- `agent_outputs/reports/`: 긴 분석, 계획, 리뷰, 감사 보고서를 저장하는 디렉토리이다.
- `agent_outputs/clarification_requests/`: 작업 중단 질문과 분석 결과에서 나온 사용자 확인 질문지를 저장하는 디렉토리이다.
- `agent_outputs/run_logs/`: 실행 로그가 필요한 작업의 로그를 저장하는 디렉토리이다.
- `agent_outputs/llm_context/`: 새 대화형 LLM에게 전달할 context snapshot을 저장하는 디렉토리이다.

## 갱신 기준

- 저장소의 주요 디렉토리나 하네스 운영 파일이 추가, 제거, rename되면 이 문서를 갱신한다.
- 이 문서에는 파일과 디렉토리의 역할을 적고, 장기 프로젝트 판단이나 결정 사항은 `PROJECT_CONTEXT.md`에 기록한다.
