# Handoff

## 마지막 갱신일

- 2026-08-23 00:56:23 KST

# 중단기 작업 기억

## Browser enrichment 상태

- Meta API Discovery Inbox와 operator-triggered local Playwright browser enrichment vertical slice가 구현돼 있다.
- browser는 Inbox의 기존 permalink에서 공개 post author/profile screening metadata만 읽는다. API sync만으로 열리지 않고 session 준비, 단건, batch 모두 운영자 명시 action으로 시작한다.
- 기본 persistent profile은 `.local/instagram-browser-profile`이며 cookie/local storage를 포함할 수 있어 git, DB, 로그, fixture, report에 포함하지 않는다.
- background/bulk crawling, 검색·follower/following 목록 순회, private endpoint, Instagram external action, stealth·CAPTCHA/challenge/rate-limit 우회는 계속 금지한다.
- Candidate 자동 생성·연결과 기존 EligibilityPolicy는 아직 수정하지 않았다.

## 남은 live 검증

- 사용자 macOS headed Chromium에서 session 준비와 post navigation은 성공했다.
- 실제 item의 author link 추출 실패에 대응해 visible article wait/retry와 article-scoped conservative fallback을 추가했고 synthetic extractor 테스트 14개는 통과했다.
- 기존 전용 profile로 이전 실패 item과 caption mention/commenter가 있는 item 1~2건을 다시 확인해야 한다.
- 실패 시 `postContainer`, `articleLinks`, `profileLinks`, `candidates` diagnostic만 공유하고 raw HTML, screenshot, cookie, session directory는 공유하지 않는다.
- challenge/checkpoint/CAPTCHA가 보이면 즉시 중단하고 자동 해결을 시도하지 않는다.

# 직전 작업 기억

## Local Meta token lifecycle UX

- 일상 실행은 계속 `./scripts/run-local.sh` 한 줄이다.
- 대화형 macOS source precedence를 `Keychain > process environment > hidden prompt`로 변경했다. Keychain이 있으면 과거 shell의 `META_ACCESS_TOKEN` export는 선택되지 않는다.
- 비대화형 CI/automation은 명시적인 process environment token을 사용할 수 있다. 어떤 source든 startup validation을 통과해야 한다.
- validation은 `GET https://graph.facebook.com/{version}/{META_IG_USER_ID}?fields=id` read-only 요청을 Bearer header로 보내고 configured ID가 일치하는지 확인한다.
- Graph error `code 190`만 invalid/expired로 분류해 hidden replacement prompt로 보낸다. permission, rate limit, network, HTTP와 예상하지 못한 response는 replacement로 보내지 않고 Keychain을 변경하지 않은 채 실행을 중단한다.
- 새 token은 최대 3회 입력할 수 있고, valid일 때만 macOS Keychain에 저장한다. 일반 invalid replacement는 저장 여부를 묻고 `--reset-token`은 env와 기존 Keychain을 무시해 valid 새 값으로 Keychain을 교체한다.
- 기존 Keychain entry는 새 valid token의 `security add-generic-password ... -U` 성공 전 삭제하지 않는다. write 후 같은 값을 다시 읽어 실제 update 성공도 확인한다.
- curl Bearer header와 Keychain write command는 stdin으로 전달해 token을 child process argv에 넣지 않는다. token은 validation 전 child environment에서 제거하고 Spring Boot `exec` 직전에만 export한다.
- `.env.local` token 금지, raw token/prefix 비출력, `debug_token`·App Secret·만료 예정일·automatic exchange 제외 정책을 유지한다.

## Synthetic 검증

- `bash -n scripts/run-local.sh`, `bash -n scripts/test_run_local.sh`, `./scripts/run-local.sh --help`: 통과했다.
- `./scripts/test_run_local.sh`: 10개 전체 통과했다.
- valid response, OAuth code 190, permission code 10, curl stdin Bearer 전달, Keychain stdin `-U`, 대화형 Keychain 우선순위, invalid 새 token 재시도 후 valid만 저장, network failure 시 Keychain 보존, `--reset-token`, 비대화형 env source를 실제 network 없이 검증했다.
- fixture output에 기존·신규 synthetic raw token이 나타나지 않는지 확인했다.
- 실제 Meta token, Meta network, macOS Keychain은 요청에 따라 건드리지 않았다.

## Maven과 Docker 검증

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 접근 권한 거부로 실행하지 못했다.
- `./mvnw test`: 총 87개, failures 0, errors 11이다. PostgreSQL 비의존 76개는 통과했고 DB 연결 테스트 11개만 container 미기동으로 error가 발생했다.
- `./mvnw package`: 같은 DB 연결 error 11개로 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: compile과 executable jar package에 성공했다. 전체 package 성공을 대신하지 않는다.
- `git diff --check`: 통과했다.

## 변경 파일

- `README.md`
- `scripts/run-local.sh`
- `scripts/test_run_local.sh`
- `docs/harness/DIRECTORY_MAP.md`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 작업 전 파일 보존과 범위

- 작업 시작 전 미추적 `prompts/tasks/improve_local_meta_token_lifecycle.md`는 수정하지 않았다.
- 최신 사용자 요청이 local token lifecycle 개선을 명시해 이전 추천 작업인 browser live 재검증과 Candidate identity vertical slice는 수행하지 않았다.
- 요청 범위와 안전 경계가 명확해 clarification request는 만들지 않았다.

## 다음 추천 작업

1. 사용자 macOS에서 expired Keychain token과 valid 새 token으로 `./scripts/run-local.sh`를 smoke 검증하고 Keychain update/read-back을 확인한다.
2. Docker Desktop이 실행되는 사용자 환경에서 `./mvnw test`, `./mvnw package` 전체 성공을 확인한다.
3. 기존 추천 작업인 `/discovery` browser enrichment live 재검증을 이어간다.
4. browser enrichment가 안정화되면 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- `.env.local`에 token을 넣지 않는다. 일상적인 macOS token store는 프로젝트 전용 Keychain이다.
- Graph `code 190` 외 오류에서 token 교체를 유도하거나 Keychain 값을 삭제하지 않는다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않는다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
