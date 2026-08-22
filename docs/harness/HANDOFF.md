# Handoff

## 마지막 갱신일

- 2026-08-20 15:47:58 KST

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

## Local 실행 UX

- 일상적인 local 실행을 `./scripts/run-local.sh` 한 줄로 통합했다.
- `.env.local`이 없으면 API version `v26.0`, IG User ID, port `18080`, browser enabled `true`, headless `false`를 대화형으로 묻고 mode `600` 파일을 생성한다.
- `.env.local`은 `source`나 `eval`하지 않고 허용된 `KEY=value`만 파싱한다. 필수값, boolean, port, batch size를 검증하며 `META_ACCESS_TOKEN` key는 명시적으로 거부한다.
- `.env.local.example`에는 non-secret 설정과 빈 IG User ID만 두고 실제 `.env.local`을 `.gitignore`에 추가했다. 기존 `.local/` ignore는 유지했다.
- access token은 현재 process, macOS Keychain service `hr-sns-auto-meta-access-token`, hidden prompt 순서로 결정한다. token 값은 출력하거나 `.env.local`에 기록하지 않는다.
- `--reset-token`은 대화형 terminal을 먼저 확인한 뒤 프로젝트 전용 Keychain entry만 삭제하고 새 token을 hidden prompt로 받는다.
- `docker compose up -d postgres` 후 health 상태를 최대 60초 기다린다. Docker 명령, Compose plugin, daemon 오류를 구분하며 down, volume 삭제, Flyway clean, 종료 cleanup은 수행하지 않는다.
- `--install-browser`는 Playwright 1.61.0의 `com.microsoft.playwright.CLI install chromium`을 Maven으로 실행한 뒤 일반 실행을 계속한다.
- 실행 전 server, masked IG User ID, browser mode/data dir, PostgreSQL, token source와 `/discovery` URL을 표시하고 `exec ./mvnw spring-boot:run`으로 전환한다.

## 문서와 장기 정책

- root `README.md`를 local 실행 primary 문서로 추가하고 최초 실행, token 교체, Chromium 설치, persistent data 유지 방법을 기록했다.
- `docs/harness/PROJECT_CONTEXT.md`에 `DEC-20260820-local-config-secret-boundary`를 추가했다.
- `docs/harness/DIRECTORY_MAP.md`에 README, env example, local launcher 역할을 반영했다.

## 검증 상태

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 권한 거부로 실행하지 못했다.
- `bash -n scripts/run-local.sh`, `./scripts/run-local.sh --help`, validator helper 검증, 실행 권한, `.env.local`·`.local/` ignore 확인은 통과했다.
- Synthetic 최초 실행에서 `.env.local` mode `600`, 기본값, hidden token 미출력·미저장을 확인했다. 검증용 `.env.local`은 제거했다.
- Playwright CLI `--help`가 Maven `exec:3.6.3`과 현재 dependency에서 성공해 `install chromium` invocation을 확인했다. browser binary 전체 설치는 실행하지 않았다.
- `./mvnw test`: 총 87개, failures 0, errors 11이다. PostgreSQL 비의존 76개는 통과했고 DB 연결 테스트 11개만 Docker 미기동으로 error가 발생했다.
- `./mvnw package`: 같은 DB 연결 error 11개로 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: compile과 executable jar package에 성공했다. 전체 package 성공을 대신하지 않는다.
- `git diff --check`: 통과했다.
- 실제 macOS Keychain, 실제 Meta token, instagram.com network, persistent browser profile은 검증 과정에서 건드리지 않았다.

## 변경 파일

- `.gitignore`
- `.env.local.example`
- `README.md`
- `scripts/run-local.sh`
- `docs/harness/DIRECTORY_MAP.md`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 작업 전 파일 보존

- 작업 시작 전 미추적 `prompts/tasks/improve_local_run_experience.md`는 수정하지 않았다.
- 요청 범위와 안전 경계가 명확해 clarification request는 만들지 않았다.

## 다음 추천 작업

1. 사용자 macOS에서 Docker Desktop을 실행하고 `./scripts/run-local.sh --install-browser`를 최초 1회 실행한다. Chromium이 이미 있으면 `./scripts/run-local.sh`만 실행한다.
2. 사용자 환경에서 `./mvnw test`, `./mvnw package` 전체 성공을 확인한다.
3. `http://localhost:18080/discovery`에서 기존 session과 이전 실패 item을 live 재검증한다.
4. browser enrichment가 안정화되면 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- `.env.local`에 token을 넣지 않고 Keychain 또는 hidden prompt를 사용한다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않는다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
