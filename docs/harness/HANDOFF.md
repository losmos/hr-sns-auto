# Handoff

## 마지막 갱신일

- 2026-08-20 01:52:47 KST

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
- `InstagramBrowserClient`가 persistent Chromium context 하나와 page 하나를 재사용한다. 기본 navigation timeout은 20초이고 DOM 초기 안정화를 위한 고정 500ms wait만 사용한다.
- `InstagramBrowserExtractor`가 post author link와 profile/post field selector·fallback을 한 곳에 격리한다. post semantic header/heading의 profile link와 visible username이 일치할 때만 author로 인정하며 caption에서 username을 추측하지 않는다.
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

## DiscoveryPersistenceTest 격리 수정

- browser enrichment commit `43c45ec`은 검증 전에 원격 `main`에 push된 상태이며 revert나 history rewrite를 하지 않았다.
- 사용자 PostgreSQL에서 V5 migration은 성공했다.
- 사용자 환경의 첫 전체 검증은 76개 중 failures 2, errors 0이었고 두 failure 모두 `DiscoveryPersistenceTest`에서 발생했다.
- 원인은 shared dev DB의 기존 live `DiscoveryItem`과 `DiscoveryBrowserObservation`, mutable hashtag 상태를 무시한 최신 row·전체 count·정확한 enabled 집합 assertion이었다.
- persistence test는 고유 synthetic media ID로 자기 item을 조회하고 item별 observation ID 보존을 검증하도록 수정했다.
- caption fixture는 supplementary emoji를 사용하고 UTF-16 `String.length()`가 아니라 Unicode code point 500개 계약을 검증한다.
- default hashtag 검증은 현재 enabled 집합이 정확히 3개라고 가정하지 않고 seed keyword row 존재만 확인한다.
- production 코드와 Flyway V5 migration은 수정하지 않았고 DB cleanup 명령도 실행하지 않았다.

## 검증 상태

- `docker compose up -d postgres`, `docker compose ps`: Docker socket 권한 거부로 실행하지 못했다.
- 이 sandbox에는 별도 PostgreSQL 실행 binary가 없고 `localhost:5432` 연결도 실패했다.
- `./mvnw test`: 총 76개, failures 0, errors 11이다. DB 비의존 65개는 통과했지만 PostgreSQL을 쓰는 테스트는 connection 단계에서 중단돼 수정한 persistence assertion 자체는 실행되지 않았다.
- `./mvnw package`: 같은 11개 DB connection error로 test 단계에서 실패했다.
- `./mvnw test-compile`: production/test source 컴파일에 성공했다.
- `./mvnw package -DskipTests`: executable jar 패키징에 성공했다. 이는 전체 package 성공을 대신하지 않는다.
- 사용자 PostgreSQL의 기존 30건 이상 live Discovery data가 있는 상태에서 `./mvnw test`, `./mvnw package`를 다시 실행해야 최종 검증이 완료된다.
- Codex sandbox에서는 browser binary/display/live Instagram network를 사용한 smoke test를 수행하지 않았다.

## 사용자 환경 smoke test

1. PostgreSQL과 전체 build를 먼저 검증한다.

```bash
docker compose up -d postgres
docker compose ps
./mvnw test
./mvnw package
```

2. 공식 Playwright CLI 방식으로 이 dependency version의 Chromium을 별도 설치한다.

```bash
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

3. 기본 headed mode와 private project-local profile로 애플리케이션을 실행한다.

```bash
INSTAGRAM_BROWSER_AUTOMATION_ENABLED=true \
INSTAGRAM_BROWSER_USER_DATA_DIR=.local/instagram-browser-profile \
INSTAGRAM_BROWSER_HEADLESS=false \
INSTAGRAM_BROWSER_BATCH_SIZE=10 \
./mvnw spring-boot:run
```

4. `http://localhost:8080/discovery`에서 `Instagram 브라우저 열기 / 로그인 확인`을 누르고 열린 Chromium에서 사람이 직접 로그인한다.
5. 실제 Discovery item 1건의 `브라우저 정보 가져오기`를 누르고 username, profile URL, follower/following/post count, bio excerpt, verified/private와 observedAt을 확인한다.
6. 최대 3건 정도를 추가 확인하고 post like/comment/view가 보이지 않을 때 null로 남는지 확인한다.
7. challenge/checkpoint/CAPTCHA가 표시되면 즉시 중단하고 자동 해결을 시도하지 않는다.
8. session directory가 `git status --short`와 application log, DB observation에 나타나지 않는지 확인한다.

## 변경 파일

- `src/test/java/com/losmos/hrsnsauto/discovery/DiscoveryPersistenceTest.java`: global-state assertion을 synthetic media identity와 item별 observation identity 기반으로 교체했다.
- `docs/harness/HANDOFF.md`: 사용자 환경의 최초 failure와 후속 격리 수정, sandbox 검증 한계를 기록했다.

## 작업 전 파일 보존

- 작업 시작 전 존재한 미추적 `prompts/tasks/fix_discovery_persistence_test_isolation.md`는 읽거나 수정하지 않았다.
- clarification request는 만들지 않았다. 요청 범위와 안전 조건이 명확해 P0 blocker가 없었다.

## 이전 추천 작업과의 관계

- 이전 Handoff의 live browser smoke보다 최신 사용자의 persistence test 격리 수정을 먼저 수행했다.
- live browser smoke와 `Browser observation → Candidate 연결`은 미수행 상태로 남겼다.

## 다음 추천 작업

1. 기존 30건 이상 live Discovery data가 있는 사용자 PostgreSQL에서 전체 76개 테스트와 package를 다시 실행해 격리 수정을 최종 확인한다.
2. 위 headed live smoke 절차로 selector fallback과 한국어/영문 화면 metric을 검증하고 실제 화면에서 확인된 문제만 작은 수정으로 반영한다.
3. 다음 vertical slice로 `Browser observation → Candidate 연결 + username/history identity`를 구현한다.

## 주의할 점

- Instagram DOM과 visible label은 변경될 수 있으므로 generated CSS class를 controller/service에 추가하지 않고 `InstagramBrowserExtractor`의 semantic fallback만 수정한다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않고 이 도구 전용 directory를 사용한다.
- observation은 공개 screening 정보이지 profession/identity eligibility 확정 evidence가 아니다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
