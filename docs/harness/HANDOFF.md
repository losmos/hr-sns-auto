# Handoff

## 마지막 갱신일

- 2026-08-23 02:03:55 KST

# 중단기 작업 기억

## Browser enrichment 상태

- Meta API Discovery Inbox와 operator-triggered local Playwright browser enrichment vertical slice가 구현돼 있다.
- browser는 Inbox의 기존 permalink에서 공개 post author/profile screening metadata만 읽는다. API sync만으로 열리지 않고 session 준비, 단건, batch 모두 운영자 명시 action으로 시작한다.
- 기본 persistent profile은 `.local/instagram-browser-profile`이며 cookie/local storage를 포함할 수 있어 git, DB, 로그, fixture, report에 포함하지 않는다.
- background/bulk crawling, 검색·follower/following 목록 순회, private endpoint, Instagram external action, stealth·CAPTCHA/challenge/rate-limit 우회는 계속 금지한다.
- Candidate 자동 생성·연결과 기존 EligibilityPolicy는 아직 수정하지 않았다.

## 남은 live 검증

- 사용자 macOS headed Playwright Chromium의 실제 Reel 화면에서 main의 visible author href가 `/{username}/reels/` 형태임이 확인됐다. 동일 username href가 avatar와 username link로 두 번 반복됐다.
- 기존 `profileUsernameFromUrl()`이 정확히 1개 path segment만 허용해 해당 link를 모두 제외했고, 실제 실패 diagnostic의 `rootLinks=12`에도 `profileLinks=0`, `candidates=-`가 된 것이 root cause이다.
- 정확한 2-segment `/{username}/reels/`를 canonical profile identity로 지원하도록 수정했다. `/reels/{shortcode}/`와 `/reels/audio/...`의 첫 segment `reels`는 계속 예약어로 제외한다.
- 사용자의 기존 macOS browser profile과 이전 실패 item으로 live retest가 필요하다. retest에서는 author 추출과 canonical profile root navigation까지 확인해야 한다.
- 실패 시 새 compact diagnostic만 공유하고 raw HTML, page 전체 text, screenshot, cookie, session directory, query/fragment는 공유하지 않는다.
- challenge/checkpoint/CAPTCHA가 보이면 즉시 중단하고 자동 해결을 시도하지 않는다.

# 직전 작업 기억

## Instagram author profile Reels tab compatibility

- `profileUsernameFromUrl()`은 canonical `/{username}/`와 정확히 2개 segment인 `/{username}/reels/`를 지원한다. Absolute Instagram HTTPS URL에도 같은 규칙을 적용한다.
- 첫 segment는 기존 `USERNAME_PATTERN`, `..` 거부, `NON_PROFILE_PATHS` 예약어 검사를 그대로 통과해야 한다. 두 번째 segment는 정확히 소문자 `reels`여야 한다.
- `NON_PROFILE_PATHS`의 `reels`를 유지하므로 `/reels/{shortcode}/`, `/reels/audio/...`, `/reels/`에서 `reels`를 username으로 인식하지 않는다. explore, accounts, p, reel 등 기존 non-profile route도 계속 제외한다.
- Author username을 `/{username}/reels/`에서 얻어도 기존 `profileLink(username)`을 재사용해 profile URL은 `https://www.instagram.com/{username}/`로 canonicalize한다. `InstagramBrowserClient`는 수정하지 않았다.
- Main fallback은 첫 profile-like link를 선택하지 않고 기존 bounded 범위와 동일 username 반복 evidence를 그대로 사용한다. Caption mention inference도 추가하지 않았다.

## Synthetic 검증

- 구현 전 `InstagramBrowserExtractorTest` 32개 중 profile Reels tab parser와 실제 live DOM 형태 main fallback 테스트 2개가 실패해 root cause를 재현했다.
- Canonical profile, relative·absolute profile Reels tab, Reel post, Reel audio, explore tag, accounts, p, reel, 3-segment suffix 거부를 검증한다.
- 실제 link 순서 fixture에서 `profileLinks=4`, `candidates=dr_howoo,dino.the.nomad`, 선택 author `dr_howoo`, canonical profile URL `https://www.instagram.com/dr_howoo/`를 확인했다.
- `./mvnw -Dtest=InstagramBrowserExtractorTest,InstagramBrowserClientTest test`: 33개 전체 통과했다.
- 기존 external host, encoded suspicious path, reserved path, caption mention-only, commenter/recommended author 오탐 방지와 home/explore/external/different-post 안전 경계 테스트를 모두 유지했다.
- 실제 Instagram network와 persistent session은 Codex sandbox에서 호출하지 않았다.

## Maven과 Docker 검증

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 접근 권한 거부로 실행하지 못했다.
- `./mvnw test`: 총 106개, failures 0, errors 11이다. PostgreSQL 비의존 95개는 통과했고 DB 연결 테스트 11개만 container 미기동으로 error가 발생했다.
- `./mvnw package`: 같은 106개 중 DB 연결 error 11개로 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: compile과 executable jar package에 성공했다. 전체 package 성공을 대신하지 않는다.
- `git diff --check`: 통과했다.

## 변경 파일

- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractor.java`
- `src/test/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractorTest.java`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 작업 전 파일 보존과 범위

- 작업 시작 전 미추적 `prompts/tasks/support_instagram_profile_reels_tab_route.md`는 수정하지 않았다.
- Meta hashtag discovery, Candidate domain, EligibilityPolicy, DB migration, browser external action과 stealth/evasion은 수정하지 않았다.
- `/reel/`·`/reels/` post alias, post root 선택, main fallback 범위와 author safety heuristic은 변경하지 않았다.
- 최신 사용자 요청이 실제 macOS DOM 결과에 따른 profile parser 수정을 명시해 이전 추천 작업 중 live retest와 Candidate identity vertical slice는 수행하지 않았다.
- 사용자 답변이 필요한 질문이나 blocker는 없어 clarification request를 만들지 않았다.

## 다음 추천 작업

1. 사용자 macOS에서 Docker Desktop과 애플리케이션을 시작하고 Playwright session을 준비한 뒤 `/discovery`의 이전 `AUTHOR_EXTRACTION_FAILED` item에서 `브라우저 정보 가져오기`를 다시 실행한다.
2. `/reels/{shortcode}/` 화면에서 `/{username}/reels/` author pair가 profile candidate로 잡히고 canonical `https://www.instagram.com/{username}/` profile navigation과 observation 저장까지 성공하는지 확인한다.
3. 실패 시 compact diagnostic의 `profileLinks`와 `candidates`만 포함해 공유한다. raw HTML, cookie, session, token은 공유하지 않고 challenge/checkpoint/CAPTCHA가 보이면 즉시 중단한다.
4. Docker Desktop이 실행되는 사용자 환경에서 `./mvnw test`, `./mvnw package` 전체 성공을 확인한다.
5. browser enrichment가 안정화되면 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- `/{username}/reels/`는 profile tab이고 `/reels/{shortcode}/`는 Reel post이다. 첫 segment `reels`는 계속 profile username 예약어이다.
- `reel`과 `reels` post route는 shortcode가 같을 때만 같은 identity이다. 다른 shortcode, `p`와 `reel`, home/explore/external은 계속 다른 위치로 취급한다.
- final URL이 home/explore/external 또는 다른 post이면 main fallback을 넓히지 않는다.
- main fallback 범위를 늘리거나 첫 profile link를 그대로 author로 선택하지 않는다.
- caption mention을 author로 추론하지 않는다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않는다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
