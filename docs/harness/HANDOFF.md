# Handoff

## 마지막 갱신일

- 2026-08-20 02:37:01 KST

# 중단기 작업 기억

## 이번 범위

- 기존 Meta API Discovery Inbox를 유지하면서 operator-triggered Instagram Browser Enrichment vertical slice를 구현했다.
- browser enrichment는 Inbox에 이미 저장된 Instagram post permalink에서 author profile link를 확인하고 공개 profile screening metadata를 읽는다.
- Candidate 자동 생성·연결과 기존 Candidate eligibility 정책은 수정하지 않았다.
- API sync만으로 browser가 열리지 않는다. session 준비, 단건, batch 모두 운영자가 명시적으로 버튼을 눌러야 시작한다.

## 최신 결정과 안전 경계

- `DEC-20260817-no-unauthorized-instagram-collection`의 웹 UI read 전체 제외 부분과 `DEC-20260817-browser-automation-mvp-exclusion`은 `DEC-20260819-operator-triggered-browser-enrichment`로 부분 또는 전체 superseded됐다.
- API Discovery는 계속 우선 경로이고 browser는 API에서 얻지 못한 공개 post author/profile metadata만 보강한다.
- background scheduler, bulk/무한 crawling, 검색·follower/following 목록 순회, private endpoint, follow, like, comment, DM을 구현하지 않았다.
- stealth plugin, fingerprint spoofing, proxy rotation, CAPTCHA/challenge/checkpoint/rate-limit 우회를 구현하지 않았다.
- raw HTML, screenshot, image/video binary, password, cookie/session 값을 DB·source·fixture·report에 저장하지 않는다.

## 구현 구조

- Microsoft Playwright Java `1.61.0` dependency를 추가했다.
- `InstagramBrowserClient`가 persistent Chromium context 하나와 page 하나를 재사용한다. 기본 navigation timeout은 20초이고 초기 500ms settle 뒤 post container 4초, 최초 article link 1.2초의 명시적 wait를 사용한다.
- `InstagramBrowserExtractor`가 post author link와 profile/post field selector·fallback을 한 곳에 격리한다. semantic header/heading을 우선하고, 없으면 visible post article의 첫 30개 link 중 상단 12개만 대상으로 동일 profile 반복, visible username 일치, accessible label 일치를 DOM 순서대로 확인한다. caption text에서 username을 추측하지 않는다.
- `InstagramMetricParser`가 `523`, `1,234`, `4.8천`, `1.2만`, `4.8K`, `1.2M`과 decimal comma를 처리한다. multiplier 적용 후 `HALF_UP`으로 정수화하며 불확실한 형식은 null로 둔다.
- `InstagramBrowserEnrichmentService`의 fair `ReentrantLock`이 session 준비·단건·batch를 서로 배타적으로 실행한다.
- batch는 browser observation이 없는 `NEW` item을 `publishedAt DESC, id DESC`로 조회해 기본 10건, 설정 1~15건을 하나씩 순차 처리한다.
- 로그인 또는 challenge/checkpoint, browser binary 없음, 같은 profile directory 사용 중 상태에서는 남은 batch를 중단한다.
- 각 item observation 저장은 독립 repository transaction이므로 앞선 성공 결과를 이후 item 실패로 rollback하지 않는다.

## 설정과 session 보안

- `INSTAGRAM_BROWSER_AUTOMATION_ENABLED`: 기본 `false`이다.
- `INSTAGRAM_BROWSER_USER_DATA_DIR`: 기본 `.local/instagram-browser-profile`이다.
- `INSTAGRAM_BROWSER_HEADLESS`: 기본 `false`이다.
- `INSTAGRAM_BROWSER_BATCH_SIZE`: 기본 `10`, 허용 범위 `1..15`이다.
- `.local/` 전체를 `.gitignore`에 추가했다.
- persistent profile에는 cookies/local storage가 있을 수 있으므로 commit, source 포함, DB 복사, 로그·report 출력이 금지된다.
- 애플리케이션은 Instagram ID/PW를 입력받거나 저장하지 않는다. 로그인은 열린 Chromium에서 사람이 직접 수행한다.
- browser automation disabled 상태에서는 Playwright를 생성하지 않으며 기존 API Discovery 흐름이 그대로 유지된다.

## 저장 모델과 migration

- Flyway `V5__create_discovery_browser_observations.sql`을 추가했다.
- 한 `DiscoveryItem`당 최신 `DiscoveryBrowserObservation` 하나를 unique FK로 유지한다.
- profile field는 author username, display name, profile URL, follower/following/post count, biography 최대 300자 excerpt, verified, private/public, observedAt이다.
- post field는 화면에 보일 때만 like, comment, view/play count를 저장한다. 값을 얻지 못하면 null이며 추정하지 않는다.
- 모든 numeric count는 애플리케이션과 DB에서 음수를 거부한다.
- 상태는 `SUCCESS`, `PARTIAL`, `LOGIN_REQUIRED`, `ACTION_REQUIRED`, `FAILED`이다.
- 안전한 오류 코드는 disabled/config/busy/binary/profile-in-use/login/action/post unavailable/author extraction/profile unavailable/timeout/unexpected DOM/persistence를 구분한다.

## UI와 route

- `GET /discovery` 상단에 `브라우저 보강: 활성/비활성` 상태와 session 보안 안내를 표시한다.
- `POST /discovery/browser/session`: `Instagram 브라우저 열기 / 로그인 확인`이다.
- `POST /discovery/items/{itemId}/browser-enrichment`: item 단건 `브라우저 정보 가져오기`이다.
- `POST /discovery/browser-enrichment/new`: observation 없는 최신 `NEW` item 순차 batch이다.
- item card에 최신 observation 상태, profile field, optional post metric, observedAt, 안전한 오류 요약을 표시한다.
- 기존 `POST /discovery/sync` Meta hashtag sync와 review route는 변경하지 않았다.

# 직전 작업 기억

## Live author extraction 실패와 수정

- 사용자 macOS local GUI 환경에서 headed Playwright Chromium 실행과 Instagram session 준비·navigation이 성공했다.
- 실제 Discovery item의 `브라우저 정보 가져오기`에서 login/challenge gate와 post unavailable 판정까지 통과한 뒤 `AUTHOR_EXTRACTION_FAILED: 게시물의 author profile link를 화면에서 확인하지 못함`이 발생했다.
- 실패 원인은 actual Instagram desktop DOM이 초기의 conservative `header/h1/h2` selector 및 non-empty visible label 조건과 일치하지 않은 것으로 판단했다.
- post navigation의 고정 500ms settle만 의존하지 않고 visible `main article` 또는 dialog article을 최대 4초 기다리며, article link를 최대 1.2초 기다린 뒤 400ms 간격으로 최대 3회 author 후보를 다시 읽는다.
- semantic author link를 우선 유지하고 fallback은 선택한 article 내부 visible link만 최대 30개 수집한다. 상단 12개 안에서 같은 username profile href 반복, href username과 `innerText` 일치, `aria-label`·`title`에 username 명시를 순서대로 확인한다.
- 상대·절대 Instagram profile URL과 명확한 query/hash를 허용하되 기존 `NON_PROFILE_PATHS`, multi-segment, percent-encoded path, external host 거부를 유지하고 protocol-relative external host도 거부한다.
- 실패 요약에는 `postContainer`, article visible link 수, profile-like link 수, 검증된 candidate username 최대 3개만 넣는다. raw HTML, 전체 caption/page text, arbitrary URL, session 정보는 넣지 않는다.
- profile metric extraction, Candidate/Eligibility, V5 schema와 Meta API Discovery는 수정하지 않았다.

## 자동화 검증 상태

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 권한 거부로 PostgreSQL을 시작하거나 health 확인하지 못했다.
- `./mvnw -Dtest=InstagramBrowserExtractorTest test`: 14개 모두 통과했다. 요구된 synthetic article 8개 사례, accessible label, bounded wait/retry와 diagnostic 제한을 포함한다.
- `./mvnw test`: 총 87개, failures 0, errors 11이다. 새 테스트와 기존 DB 비의존 테스트 76개는 통과했고 PostgreSQL 의존 테스트 11개만 connection 단계에서 error가 발생했다.
- `./mvnw package`: 같은 PostgreSQL connection error 11개 때문에 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: production/test compile과 executable jar 패키징에 성공했다. 이는 전체 package 성공을 대신하지 않는다.
- `git diff --check`: HANDOFF 최종 변경까지 포함해 통과했다.
- Codex에서는 실제 instagram.com network나 사용자의 persistent browser session을 호출하지 않았다. 수정된 selector의 live retest는 사용자 macOS 환경에서 필요하다.

## 사용자 환경 live retest

1. PostgreSQL과 전체 자동화 검증을 먼저 완료한다.

```bash
docker compose up -d postgres
docker compose ps
./mvnw test
./mvnw package
```

2. 기존 전용 persistent profile을 그대로 사용해 headed mode로 애플리케이션을 실행한다.

```bash
INSTAGRAM_BROWSER_AUTOMATION_ENABLED=true \
INSTAGRAM_BROWSER_USER_DATA_DIR=.local/instagram-browser-profile \
INSTAGRAM_BROWSER_HEADLESS=false \
INSTAGRAM_BROWSER_BATCH_SIZE=10 \
./mvnw spring-boot:run
```

3. `http://localhost:8080/discovery`에서 `Instagram 브라우저 열기 / 로그인 확인`을 눌러 기존 session이 `READY`인지 확인한다.
4. 이전에 실패한 실제 Discovery item 1건에서 `브라우저 정보 가져오기`를 다시 누른다.
5. author username과 canonical profile URL이 실제 게시물 author와 일치하고 profile metadata까지 저장되는지 확인한다.
6. 실패하면 오류 요약의 `postContainer`, `articleLinks`, `profileLinks`, `candidates` 값만 공유한다. raw HTML, screenshot, cookie, session directory 내용은 공유하지 않는다.
7. caption mention/commenter가 있는 item 1~2건에서 author 오탐이 없는지 확인한다.
8. challenge/checkpoint/CAPTCHA가 표시되면 즉시 중단하고 자동 해결을 시도하지 않는다.

## 변경 파일

- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserClient.java`: author extraction 실패에 compact diagnostic을 연결했다.
- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractor.java`: post wait/retry, article-scoped fallback, label/URL validation과 안전한 diagnostic을 추가했다.
- `src/test/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractorTest.java`: synthetic HTML과 bounded wait/retry 회귀 테스트를 추가했다.
- `docs/harness/HANDOFF.md`: live 사실, 수정 내용, 검증 결과와 재검증 절차를 기록했다.

## 작업 전 파일 보존

- 작업 시작 전 존재한 미추적 `prompts/tasks/fix_live_instagram_author_extraction.md`는 읽거나 수정하지 않았다.
- clarification request는 만들지 않았다. 요청 범위와 안전 조건이 명확해 P0/P1/P2 질문이 없었다.
- `docs/harness/PROJECT_CONTEXT.md`에는 새 장기 결정이 없어 수정하지 않았다.

## 다음 추천 작업

1. 사용자 macOS PostgreSQL 환경에서 전체 87개 테스트와 package를 통과시킨다.
2. 위 절차로 이전 실패 item과 caption mention/commenter가 있는 item을 live 재검증한다.
3. author 성공 후 profile DOM에서 새 문제가 실제 확인될 때만 profile extractor를 작은 범위로 수정한다.
4. live browser enrichment가 안정화되면 `Browser observation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- Instagram DOM과 visible label은 변경될 수 있으므로 generated CSS class를 추가하지 않고 `InstagramBrowserExtractor`의 semantic/article fallback만 조정한다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않고 이 도구 전용 directory를 사용한다.
- observation은 공개 screening 정보이지 profession/identity eligibility 확정 evidence가 아니다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
