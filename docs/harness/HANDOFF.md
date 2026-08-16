# Handoff

## 마지막 갱신일

- 2026-08-17 03:40:58 KST

# 중단기 작업 기억

## 이번 범위

- production Discovery 구현 전에 공식 Meta Instagram API의 hashtagged media author identity capability를 실제 응답으로 확인할 독립 probe를 추가했다.
- Python standard library만 사용하며 Spring service, repository, DB table, scheduler, provider abstraction은 추가하지 않았다.
- Instagram-native signal을 최초 discovery에서 우선하고 strict profession evidence와 기존 EligibilityPolicy는 후속 eligibility/review gate로 유지한다는 최신 사용자 방향을 source of truth에 반영했다.

## Probe 현재 상태

- 실행 파일은 `scripts/instagram_native_discovery_probe.py`이다.
- synthetic test는 `scripts/test_instagram_native_discovery_probe.py`이며 외부 network에 의존하지 않는다.
- 사용법과 판정 기준은 `docs/spikes/instagram_native_discovery.md`에 기록했다.
- version은 `META_GRAPH_API_VERSION=vXX.X`로 반드시 입력하며 코드가 최신 version을 추측하지 않는다.
- `META_IG_USER_ID`가 없으면 연결 Page metadata의 `instagram_business_account` ID를 탐색한다. 여러 account가 있으면 자동 선택하지 않는다.
- 기본 hashtag는 `의사스타그램`, `약사스타그램`, `피부과` 세 개이며 기본 recent-media limit은 25, follow-up media는 3개, candidate summary는 15개이다.

## Capability와 판정

- `HASHTAG_LOOKUP`, `RECENT_MEDIA`, `MEDIA_USERNAME`, `MEDIA_OWNER`, `FOLLOWUP_MEDIA_USERNAME`, `FOLLOWUP_MEDIA_OWNER`를 각각 독립 request로 분리한다.
- 각 capability는 `SUPPORTED`, `UNSUPPORTED`, `AUTH_BLOCKED`, `UNKNOWN` 중 하나와 HTTP status, sanitized Graph error를 기록한다.
- baseline metadata field가 unsupported이면 `id`만으로 한 번 재시도해 field 거부를 `recent_media` edge 거부로 오판하지 않는다.
- candidate username은 실제 API response의 명시적 `username` field에서만 수집한다. caption, permalink, shortcode, URL parsing을 사용하지 않는다.
- username은 case-insensitive dedupe하며 최대 15개만 summary와 JSON report에 표시한다.

## 공식 endpoint 확인 상태

- 2026-08-17 Meta 공식 Postman 자료에서 Facebook Login 방식의 Professional Account 대상, 연결 Page prerequisite, hashtagged media capability, `graph.facebook.com` 사용을 확인했다.
- 이번 작업에서는 Meta 세부 hashtag reference page를 직접 열어 현재 endpoint와 field 목록을 끝까지 검증하지 못했다.
- 널리 사용되는 `/ig_hashtag_search`, `/{hashtag-id}/recent_media`, `/{media-id}` path는 `GraphUrlBuilder`에 격리했다.
- live official endpoint verification required 상태이며, 실제 versioned Graph HTTP response를 source of truth로 사용해야 한다.

## Security와 출력

- access token은 `Authorization: Bearer` header에만 넣고 query parameter로 만들지 않는다.
- token, Authorization header, paging cursor는 output 전에 redaction한다.
- Page access token field, raw response, caption, permalink를 report에 저장하지 않는다.
- 기본 JSON report는 git ignored `agent_outputs/run_logs/`에 mode `0600`으로 저장한다.
- `.gitignore`에 Python bytecode와 `__pycache__/` 제외 규칙을 추가했다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다.
- `DEC-20260817-instagram-native-discovery-first`를 추가해 Instagram-native 최초 discovery, 외부 directory 비필수, raw discovery false positive 일부 허용, SNS 활동성·의료 네트워킹 가치 우선, strict evidence의 후속 gate 역할, 공식 API 우선 spike를 기록했다.
- 기존 Instagram 무단 scraping 금지, browser automation MVP 제외, EligibilityPolicy 결정은 유지했다.

## 사용자 제공 실제 개발 환경 검증 사실

- V3 기준 `./mvnw test`는 37개 전체 통과, failures 0, errors 0, skipped 0이다.
- V3 기준 `./mvnw package`는 `BUILD SUCCESS`이다.
- Flyway schema version은 3이며 `success = true`가 확인됐다.
- 이는 사용자의 실제 개발 환경 결과이며 이번 Codex sandbox 결과와 구분한다.

## Live Meta 실행 상태

- Codex 환경의 `META_ACCESS_TOKEN`, `META_GRAPH_API_VERSION`, `META_IG_USER_ID`, `DISCOVERY_HASHTAGS`는 모두 unset이었다.
- live Graph API request는 0건이며 feasibility는 `NOT_RUN`이다.
- credential 부재를 API 실패나 `NOT_FEASIBLE_WITH_CURRENT_OFFICIAL_PATH`로 판정하지 않았다.
- `/tmp/instagram_discovery_probe_not_run.json`으로 `NOT_RUN`, exit code 2, request count 0, mode `0600`을 확인했다.

## 이번 작업 delta

- `scripts/instagram_native_discovery_probe.py`: preflight, 6개 capability, error classification, explicit username extraction, dedupe, human/JSON summary를 추가했다.
- `scripts/test_instagram_native_discovery_probe.py`: synthetic redaction, Bearer header, URL builder, classification, baseline fallback, identity parsing, dedupe, end-to-end feasible 판정을 추가했다.
- `docs/spikes/instagram_native_discovery.md`: 공식 근거, endpoint 재검증 한계, prerequisites, 실행법, 판정, security를 기록했다.
- `docs/harness/PROJECT_CONTEXT.md`: 최신 discovery 방향과 사용자 제공 V3 검증 사실을 반영했다.
- `docs/harness/DIRECTORY_MAP.md`: 새 `docs/spikes/`와 `scripts/` 역할을 반영했다.
- `.gitignore`: Python runtime bytecode를 제외했다.
- production Java domain과 Candidate/Eligibility 구현은 수정하지 않았다.

## 검증 상태

- `python3 scripts/instagram_native_discovery_probe.py --help`: 성공했다.
- `python3 -m py_compile ...`: 성공했다.
- `python3 -m unittest -v scripts.test_instagram_native_discovery_probe`: synthetic test 16개가 모두 통과했다.
- credentials 제거 상태의 probe: `NOT_RUN`, exit 2, HTTP request 0건, JSON mode `0600`을 확인했다.
- `docker compose up -d postgres`, `docker compose ps`: `/var/run/docker.sock` 권한 거부로 실패했다.
- `./mvnw test`: 37개 중 30개 통과, failures 0, PostgreSQL 연결이 필요한 7개가 errors로 실패했다.
- `./mvnw package`: 동일한 PostgreSQL 연결 errors 7로 실패했다.
- `./mvnw -Dtest=CandidateEvidenceTest,EligibilityPolicyTest,CandidateServiceTest,CandidateControllerTest test`: DB 비의존 30개가 모두 통과했다.
- `./mvnw -DskipTests package`: 실행 가능한 Spring Boot JAR 생성에 성공했다.
- `git diff --check`: 통과했다.
- `git status --short`, `git diff --stat`, `git diff`: 확인했으며 작업 전 미추적 task prompt 외에 예상한 source·문서 변경만 있다.

## 변경 파일

- `.gitignore`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `docs/harness/DIRECTORY_MAP.md`
- `docs/spikes/instagram_native_discovery.md`
- `scripts/instagram_native_discovery_probe.py`
- `scripts/test_instagram_native_discovery_probe.py`

## 생성 산출물

- 실제 account data를 포함한 committed fixture나 문서는 만들지 않았다.
- `NOT_RUN` JSON은 `/tmp`에만 만들었다.
- clarification request는 만들지 않았다.
- 작업 시작 전 존재한 미추적 `prompts/tasks/spike_instagram_native_discovery.md`는 수정하지 않았다.

## 다음 추천 작업 상세

1. Meta app, 연결 Facebook Page, Business 또는 Creator Instagram account, 현재 version의 permission·feature·access level, 유효 token을 준비한다.
2. 현재 Meta 공식 hashtag reference에서 endpoint와 permission을 다시 대조한다.
3. token을 shell prompt로 입력한 뒤 `META_GRAPH_API_VERSION`, 가능하면 `META_IG_USER_ID`, `DISCOVERY_HASHTAGS`를 설정하고 probe를 실행한다.
4. ignored JSON report에서 6개 capability, identity coverage, unique candidate 수, Graph error를 검토한다.
5. live 결과가 `FEASIBLE`일 때만 production Discovery 설계를 별도 작업으로 시작한다. 불가능하거나 제한적이면 그 결과를 기록하고 scraping·private API·browser automation을 자동 추가하지 않는다.

## 이전 추천 작업과의 관계

- 최신 사용자의 Instagram-native discovery feasibility spike 요청을 직전 Handoff의 운영자 sample 확인보다 우선했다.
- V3 전체 DB 검증 미확인은 사용자가 제공한 실제 개발 환경의 37/37, package 성공, Flyway V3 결과로 해소됐다.
- 운영자 sample을 통한 evidence 입력·상충 사유 검증은 미수행 추천 작업으로 남긴다.

## 사용 에이전트

- Codex를 사용했다.

## 주의할 점

- 현재 feasibility는 공식 경로가 불가능하다는 뜻이 아니라 credential이 없어 live 실행하지 않은 `NOT_RUN`이다.
- standard hashtag path는 code에 격리됐지만 현재 공식 reference와 live 응답으로 재확인해야 한다.
- live username은 process output과 ignored run log에만 두고 source, fixture, 문서에 복사하지 않는다.
- local thin slice에 인증이 없으므로 외부 network에 노출하지 않는다.
