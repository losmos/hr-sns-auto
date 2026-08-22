# Handoff

## 마지막 갱신일

- 2026-08-23 01:49:44 KST

# 중단기 작업 기억

## Browser enrichment 상태

- Meta API Discovery Inbox와 operator-triggered local Playwright browser enrichment vertical slice가 구현돼 있다.
- browser는 Inbox의 기존 permalink에서 공개 post author/profile screening metadata만 읽는다. API sync만으로 열리지 않고 session 준비, 단건, batch 모두 운영자 명시 action으로 시작한다.
- 기본 persistent profile은 `.local/instagram-browser-profile`이며 cookie/local storage를 포함할 수 있어 git, DB, 로그, fixture, report에 포함하지 않는다.
- background/bulk crawling, 검색·follower/following 목록 순회, private endpoint, Instagram external action, stealth·CAPTCHA/challenge/rate-limit 우회는 계속 금지한다.
- Candidate 자동 생성·연결과 기존 EligibilityPolicy는 아직 수정하지 않았다.

## 남은 live 검증

- 사용자 macOS headed Playwright live 결과에서 `/reel/DcVip-KgV37/` 요청이 `/reels/DcVip-KgV37/` final URL에 도달하며 `page=other_instagram`으로 잘못 분류됨이 확인됐다.
- Brave에서 `/discovery` UI를 열고 Playwright 전용 Chromium에서 Instagram을 여는 browser 분리는 이번 실패 원인이 아니다. navigation은 완료됐고 지원 route 분류에서 먼저 차단됐다.
- `/reel/{shortcode}`와 `/reels/{shortcode}`를 같은 canonical Reel identity로 처리하도록 수정했다. 사용자의 기존 macOS browser profile과 이전 실패 item으로 live retest가 필요하다.
- retest에서는 `/reels/{shortcode}/` final URL이 POST로 통과하고 기존 semantic article 또는 post 한정 main fallback에서 실제 author를 추출하는지 확인해야 한다.
- 실패 시 새 compact diagnostic만 공유하고 raw HTML, page 전체 text, screenshot, cookie, session directory, query/fragment는 공유하지 않는다.
- challenge/checkpoint/CAPTCHA가 보이면 즉시 중단하고 자동 해결을 시도하지 않는다.

# 직전 작업 기억

## Instagram Reel route alias compatibility

- `SUPPORTED_POST_PATH_PATTERN`은 `p`, `reel`, `reels`, `tv`와 shortcode가 있는 정확히 2개 segment post path를 지원한다.
- `PostIdentity` 생성 시 route type을 소문자로 만든 뒤 `reels`만 canonical type `reel`로 정규화한다. shortcode는 그대로 보존하므로 같은 shortcode의 `reel`/`reels`만 동일 identity이다.
- query, fragment, trailing slash를 identity 비교에서 제외하는 기존 정책은 유지했다.
- `NON_PROFILE_PATHS`의 `reels`를 유지해 `/reels/`를 profile username으로 인정하지 않는다. shortcode 없는 `/reels/`도 post가 아니다.
- `InstagramBrowserClient`는 기존 `pageLocation()`과 `isExpectedPostUrl()`을 사용하므로 client flow 변경 없이 alias를 지원한다.
- author extraction heuristic, semantic article 우선순위, post 한정 main fallback 범위, Candidate domain, Meta hashtag discovery, DB schema, external action, challenge/CAPTCHA 처리는 변경하지 않았다.

## Synthetic 검증

- 구현 전 `InstagramBrowserExtractorTest`에서 `/reels/{shortcode}` POST 분류·post URL 검증과 양방향 alias 4건이 실패해 live 결함을 재현했다.
- `/reels/{shortcode}` POST 분류, post URL 허용, `reel → reels`, `reels → reel`, 다른 shortcode 거부, shortcode 없는 `/reels/` 거부, profile username 오인 방지의 7개 테스트를 추가했다.
- `./mvnw -Dtest=InstagramBrowserExtractorTest,InstagramBrowserClientTest test`: 30개 전체 통과했다.
- 기존 author/main fallback과 home/explore/external/different-post 안전 경계 테스트를 모두 유지했다.
- 실제 Instagram network와 persistent session은 Codex sandbox에서 호출하지 않았다.

## Maven과 Docker 검증

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 접근 권한 거부로 실행하지 못했다.
- `./mvnw test`: 총 103개, failures 0, errors 11이다. PostgreSQL 비의존 92개는 통과했고 DB 연결 테스트 11개만 container 미기동으로 error가 발생했다.
- `./mvnw package`: 같은 DB 연결 error 11개로 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: compile과 executable jar package에 성공했다. 전체 package 성공을 대신하지 않는다.
- `git diff --check`: 통과했다.

## 변경 파일

- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractor.java`
- `src/test/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractorTest.java`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 작업 전 파일 보존과 범위

- 작업 시작 전 미추적 `prompts/tasks/support_instagram_reels_route_alias.md`는 수정하지 않았다.
- Meta hashtag discovery, Candidate domain, EligibilityPolicy, DB migration, browser external action과 stealth/evasion은 수정하지 않았다.
- 최신 사용자 요청이 Reel route alias 수정을 명시해 이전 추천 작업 중 macOS root fallback live retest와 Candidate identity vertical slice는 수행하지 않았다.
- 사용자 답변이 필요한 질문이나 blocker는 없어 clarification request를 만들지 않았다.

## 다음 추천 작업

1. 사용자 macOS에서 Docker Desktop과 애플리케이션을 시작하고 `/discovery`의 이전 실패 item에서 `브라우저 정보 가져오기`를 다시 실행한다.
2. `/reels/DcVip-KgV37/` final URL이 더 이상 `page=other_instagram`·`POST_UNAVAILABLE`로 차단되지 않고 실제 author observation으로 이어지는지 확인한다.
3. 실패 시 compact diagnostic만 공유한다. challenge/checkpoint/CAPTCHA가 보이면 즉시 중단한다.
4. Docker Desktop이 실행되는 사용자 환경에서 `./mvnw test`, `./mvnw package` 전체 성공을 확인한다.
5. browser enrichment가 안정화되면 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- `reel`과 `reels`는 shortcode가 같을 때만 같은 identity이다. 다른 shortcode, `p`와 `reel`, home/explore/external은 계속 다른 위치로 취급한다.
- final URL이 home/explore/external 또는 다른 post이면 main fallback을 넓히지 않는다.
- main fallback 범위를 늘리거나 첫 profile link를 그대로 author로 선택하지 않는다.
- caption mention을 author로 추론하지 않는다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않는다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
