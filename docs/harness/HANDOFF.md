# Handoff

## 마지막 갱신일

- 2026-08-23 01:27:30 KST

# 중단기 작업 기억

## Browser enrichment 상태

- Meta API Discovery Inbox와 operator-triggered local Playwright browser enrichment vertical slice가 구현돼 있다.
- browser는 Inbox의 기존 permalink에서 공개 post author/profile screening metadata만 읽는다. API sync만으로 열리지 않고 session 준비, 단건, batch 모두 운영자 명시 action으로 시작한다.
- 기본 persistent profile은 `.local/instagram-browser-profile`이며 cookie/local storage를 포함할 수 있어 git, DB, 로그, fixture, report에 포함하지 않는다.
- background/bulk crawling, 검색·follower/following 목록 순회, private endpoint, Instagram external action, stealth·CAPTCHA/challenge/rate-limit 우회는 계속 금지한다.
- Candidate 자동 생성·연결과 기존 EligibilityPolicy는 아직 수정하지 않았다.

## 남은 live 검증

- 최신 사용자 macOS headed Chromium live retest에서도 `postContainer=missing, articleLinks=0, profileLinks=0, candidates=-`로 author 추출이 실패했다.
- browser launch, application 실행, enrichment 호출은 성공했으므로 initial header fallback 문제가 아니라 실제 post root 자체를 semantic `article`로 찾지 못한 문제임이 확인됐다.
- semantic article 우선순위는 유지하고, actual final URL이 요청한 `/p/{shortcode}`, `/reel/{shortcode}`, `/tv/{shortcode}`와 일치할 때만 visible `main` 또는 `[role='main']`을 fallback root로 쓰도록 변경했다.
- final URL의 page classification과 query/fragment가 제거된 `finalPath`, root 종류와 bounded link count를 failure diagnostic에 추가했다.
- 사용자의 기존 macOS browser profile로 이전 실패 item을 다시 보강해 `postRoot=main`과 실제 author 추출 성공 여부를 확인해야 한다.
- 실패 시 새 compact diagnostic만 공유하고 raw HTML, page 전체 text, screenshot, cookie, session directory, query/fragment는 공유하지 않는다.
- challenge/checkpoint/CAPTCHA가 보이면 즉시 중단하고 자동 해결을 시도하지 않는다.

# 직전 작업 기억

## Instagram live post root fallback

- `InstagramBrowserClient.enrich()`는 navigation과 기존 login/action-required 처리 후 실제 `page.url()`을 분류한다.
- 허용 final URL은 `https`, host `instagram.com` 또는 `www.instagram.com`, 정확히 2개 segment인 지원 post path이다. query, fragment, trailing slash는 identity 비교에서 제외한다.
- 요청 permalink와 final URL의 post type·shortcode가 다르거나 home/explore/외부 URL이면 author extraction으로 숨기지 않고 `POST_UNAVAILABLE`로 종료한다.
- post root 우선순위는 `[role='dialog'] article:visible` → `main article:visible` → post URL에서만 `main:visible` → `[role='main']:visible`이다.
- `PostRootType`은 `ARTICLE`, `MAIN_FALLBACK`, `NONE`만 표현한다. semantic article을 1.5초 기다린 뒤 main을 최대 1.2초 기다리고 기존 3회·400ms 고정 retry를 유지한다.
- article은 기존 semantic/early trusted candidate 정책을 유지한다. main fallback은 초반 최대 12개 link만 검사하고 동일 username href 반복을 단일 선행 navigation link보다 우선한다.
- main fallback의 단일 `@mention`과 반복된 caption `@mention` text는 author로 인정하지 않는다. exact visible username 또는 명시적인 aria-label/title은 trusted evidence로 유지한다.
- profile 후보는 지원하지 않는 Instagram path, multi-segment path, encoded path, 외부 host를 계속 제외한다.
- diagnostic 형식은 `page`, `finalPath`, `postRoot`, `main`, `article`, `dialog`, `rootLinks`, `profileLinks`, 최대 3개 `candidates`만 포함한다.

## Synthetic 검증

- `./mvnw -Dtest=InstagramBrowserExtractorTest,InstagramBrowserClientTest test`: 23개 전체 통과했다.
- 기존 `main > article`, article 없는 post `main`, unrelated 선행 link 뒤 반복 author, author 뒤 mention/commenter, caption mention-only, home, explore, external, post root 없음, 안전 diagnostic을 실제 network 없이 검증했다.
- client test에서 home/explore/external과 다른 post shortcode redirect가 `POST_UNAVAILABLE`이며 query/fragment token marker가 요약에 포함되지 않는 것을 검증했다.
- 실제 Instagram network와 persistent session은 Codex sandbox에서 호출하지 않았다.

## Maven과 Docker 검증

- `docker compose up -d postgres`, `docker compose ps`: sandbox의 Docker socket 접근 권한 거부로 실행하지 못했다.
- `./mvnw test`: 총 96개, failures 0, errors 11이다. PostgreSQL 비의존 85개는 통과했고 DB 연결 테스트 11개만 container 미기동으로 error가 발생했다.
- `./mvnw package`: 같은 DB 연결 error 11개로 test 단계에서 실패했다.
- `./mvnw package -DskipTests`: compile과 executable jar package에 성공했다. 전체 package 성공을 대신하지 않는다.
- `git diff --check`: 통과했다.

## 변경 파일

- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserClient.java`
- `src/main/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractor.java`
- `src/test/java/com/losmos/hrsnsauto/discovery/InstagramBrowserClientTest.java`
- `src/test/java/com/losmos/hrsnsauto/discovery/InstagramBrowserExtractorTest.java`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 작업 전 파일 보존과 범위

- 작업 시작 전 미추적 `prompts/tasks/fix_instagram_post_root_fallback.md`는 수정하지 않았다.
- Meta hashtag discovery, Candidate domain, EligibilityPolicy, DB migration, browser external action과 stealth/evasion은 수정하지 않았다.
- 최신 사용자 요청이 browser live 실패 보완을 명시해 이전 추천 작업 중 local token lifecycle smoke와 Candidate identity vertical slice는 수행하지 않았다.
- 사용자 답변이 필요한 질문이나 blocker는 없어 clarification request를 만들지 않았다.

## 다음 추천 작업

1. 사용자 macOS에서 기존 persistent profile과 이전 실패 item으로 `브라우저 정보 가져오기`를 다시 실행한다.
2. 성공 시 author username이 실제 post author인지, diagnostic의 `page=post`, `finalPath`, `postRoot=main`이 예상과 맞는지 확인한다.
3. 실패 시 compact diagnostic만 공유한다. challenge/checkpoint/CAPTCHA가 보이면 즉시 중단한다.
4. Docker Desktop이 실행되는 사용자 환경에서 `./mvnw test`, `./mvnw package` 전체 성공을 확인한다.
5. browser enrichment가 안정화되면 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 진행한다.

## 주의할 점

- final URL이 home/explore/external 또는 다른 post이면 main fallback을 넓히지 않는다.
- main fallback 범위를 늘리거나 첫 profile link를 그대로 author로 선택하지 않는다.
- caption mention을 author로 추론하지 않는다.
- session profile은 일반 Chrome 기본 profile과 공유하지 않는다.
- local thin slice에는 인증이 없으므로 외부 network에 노출하지 않는다.
