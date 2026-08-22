# Handoff

## 마지막 갱신일

- 2026-08-23 02:42:01 KST

# 중단기 작업 기억

## Browser enrichment 상태

- Meta API Discovery Inbox와 operator-triggered local Playwright browser enrichment vertical slice가 구현돼 있다.
- browser는 Inbox의 기존 permalink에서 공개 post author/profile screening metadata만 읽는다. API sync만으로 열리지 않고 session 준비, 단건, batch 모두 운영자 명시 action으로 시작한다.
- 기본 persistent profile은 `.local/instagram-browser-profile`이며 cookie/local storage를 포함할 수 있어 git, DB, 로그, fixture, report에 포함하지 않는다.
- background/bulk crawling, 검색·follower/following 목록 순회, private endpoint, Instagram external action, stealth·CAPTCHA/challenge/rate-limit 우회는 계속 금지한다.
- Candidate 자동 생성·연결과 기존 EligibilityPolicy는 아직 수정하지 않았다.

## 남은 live 검증

- 사용자 macOS headed Playwright Chromium에서 Reel author 추출과 canonical profile navigation은 live 성공했다.
- 실제 profile header에서 follower/following anchor가 `/followers/`·`/following/`이 아니라 `href="#"`일 수 있음이 확인됐다.
- 실제 display name은 username 바로 다음이자 첫 metric 이전 line에 위치한다. Metric 이후의 bio/address를 display name으로 선택하지 않아야 한다.
- visible header follower `3568`과 `og:description` follower `3554`처럼 화면값과 metadata 값이 다를 수 있음이 확인됐다. Visible header를 authoritative source로 사용하고 metadata는 누락값 fallback으로만 사용한다.
- 이번 parser 개선은 synthetic fixture로만 검증했다. 사용자의 기존 macOS persistent profile에서 display name, post/follower/following count와 최종 `SUCCESS`/`PARTIAL` 상태를 live 재검증해야 한다.
- 실패 시 raw HTML, page 전체 text, screenshot, cookie, session directory, query/fragment를 공유하지 않는다.
- challenge/checkpoint/CAPTCHA가 보이면 즉시 중단하고 자동 해결을 시도하지 않는다.

# 직전 작업 기억

## Instagram profile field extraction

- Profile metric별 source priority는 `visible main header line → 기존 href suffix locator 및 labeled header anchor → og:description/name=description`이다.
- Post는 `posts`·`post`·`게시물`, follower는 `followers`·`follower`·`팔로워`, following은 `following`·`팔로잉`·`팔로우` label과 같은 text에 결합된 숫자만 field별로 파싱한다.
- Visible 값이 있으면 metadata 값을 읽어 덮어쓰지 않는다. `href="#"` anchor도 header text와 labeled anchor fallback으로 처리한다.
- Display name은 exact username line 다음부터 첫 metric line 전까지만 탐색한다. Username, metric, control, external URL, highlight boundary, 255 code point 초과 text를 제외한다.
- Header에서 display name을 얻지 못하면 bounded `og:title`·description content에서 exact expected username marker 앞 text만 fallback으로 사용한다. Metric 이후의 bio/address를 display name fallback으로 사용하지 않는다.
- Biography는 metric block 뒤에서 시작하고 username, display name, metric을 제외하며 external URL, control, highlight boundary에서 중단한다. 기존 최대 300자 excerpt를 유지한다.
- Verified/private와 `InstagramProfileBrowserSnapshot.isPartial()` 정책은 변경하지 않았다.

## Synthetic 검증

- 구현 전 Korean live DOM, English, metadata display fallback, display name 없음, 주소 오인 fixture 6개가 실패해 기존 root cause를 재현했다.
- Korean `href="#"`, visible `3568` 대 metadata `3554` 우선순위, field별 Korean/English count, metadata-only 세 metric fallback, username 다음/metric 이전 display name, bio/address 경계를 검증한다.
- 필수 profile field가 모두 있으면 `isPartial()` false이고 display name이 없으면 true인 기존 정책을 검증한다.
- `./mvnw -Dtest=InstagramBrowserExtractorTest,InstagramBrowserClientTest test`: 40개 전체 통과했다.
- 기존 Reel author extraction과 URL/author safety fixture를 모두 유지했다.
- 실제 Instagram network와 persistent session은 Codex sandbox에서 호출하지 않았다.

## Maven과 Docker 검증

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 접근 권한 거부로 실행하지 못했다.
- `./mvnw test`: 총 113개, failures 0, errors 11이다. PostgreSQL 비의존 102개는 통과했고 DB 연결 테스트 11개만 container 미기동으로 error가 발생했다.
- `./mvnw package`: 같은 113개 중 DB 연결 error 11개로 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: compile과 executable jar package에 성공했다. 전체 package 성공을 대신하지 않는다.
- `git diff --check`: 통과했다.

## 변경 파일

- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractor.java`
- `src/test/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractorTest.java`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 작업 전 파일 보존과 범위

- 작업 시작 전 미추적 `prompts/tasks/fix_instagram_profile_metrics_and_display_name.md`는 수정하지 않았다.
- Meta hashtag discovery, Candidate domain, EligibilityPolicy, DB migration, browser external action과 stealth/evasion은 수정하지 않았다.
- Reel author extraction, `/reel`·`/reels` alias, profile reels tab route, post root와 author safety heuristic은 변경하지 않았다.
- 최신 사용자 요청이 profile field parser 수정을 명시해 이전 추천 작업 중 Candidate identity vertical slice는 수행하지 않았다.
- 사용자 답변이 필요한 질문이나 blocker는 없어 clarification request를 만들지 않았다.

## 다음 추천 작업

1. 사용자 macOS에서 Docker Desktop과 애플리케이션을 시작하고 기존 Playwright session으로 `nurschema_studycafe`가 author인 Discovery item의 `브라우저 정보 가져오기`를 실행한다.
2. Observation에서 display name `Nurschema의 공부방 | 간호사가 되기 위한 임상 공부`, post `81`, follower는 실행 시점 visible header 값, following `2`가 저장되는지 확인한다.
3. Header와 metadata count가 다르면 visible header 값이 최종 observation에 남고, 필수 field가 모두 있으면 상태가 `SUCCESS`인지 확인한다.
4. 실패 시 raw HTML, cookie, session, token을 공유하지 않고 안전한 화면 field와 상태만 공유한다. Challenge/checkpoint/CAPTCHA가 보이면 즉시 중단한다.
5. Docker Desktop이 실행되는 사용자 환경에서 `./mvnw test`, `./mvnw package` 전체 성공을 확인한다.
6. Browser enrichment가 안정화되면 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- Profile metric anchor의 href 형태를 field identity로 가정하지 않는다. Label과 대응 숫자를 함께 확인한다.
- 전체 header의 첫 숫자를 여러 metric에 재사용하지 않는다.
- Visible header 값은 metadata보다 우선하며 metadata는 null field만 보완한다.
- Display name은 metric 이후의 bio, 주소, external URL, control, highlight label에서 추론하지 않는다.
- `/{username}/reels/`는 profile tab이고 `/reels/{shortcode}/`는 Reel post이다. 첫 segment `reels`는 계속 profile username 예약어이다.
- `reel`과 `reels` post route는 shortcode가 같을 때만 같은 identity이다. 다른 shortcode, `p`와 `reel`, home/explore/external은 계속 다른 위치로 취급한다.
- final URL이 home/explore/external 또는 다른 post이면 main fallback을 넓히지 않는다.
- main fallback 범위를 늘리거나 첫 profile link를 그대로 author로 선택하지 않는다.
- caption mention을 author로 추론하지 않는다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않는다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
