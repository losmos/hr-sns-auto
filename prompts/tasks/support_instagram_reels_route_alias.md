# Instagram /reel/ ↔ /reels/ route alias 지원

## 시작

루트 AGENTS.md를 따른다.

먼저 다음을 읽는다.

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md

그리고 최신 browser enrichment 관련 코드를 확인한다.

특히:

- InstagramBrowserExtractor.java
- InstagramBrowserClient.java
- InstagramBrowserExtractorTest.java
- InstagramBrowserClientTest.java

## 실제 live 결과

macOS headed Playwright live test 결과:

```text
POST_UNAVAILABLE:
Instagram 게시물 화면이 아닌 위치로 이동됨
(page=other_instagram,
 finalPath=/reels/DcVip-KgV37/,
 postRoot=none,
 main=1,
 article=0,
 dialog=0,
 rootLinks=0,
 profileLinks=0,
 candidates=-)
```

현재 구현의 supported post path는:

- `/p/{shortcode}`
- `/reel/{shortcode}`
- `/tv/{shortcode}`

이고 `/reels/{shortcode}`는 지원하지 않는다.

실제 Instagram navigation에서는 reel permalink가
`/reels/{shortcode}/` 형태의 final URL에 도달할 수 있음이 live 확인됐다.

Brave에서 `/discovery` UI를 열고 Playwright 전용 Chromium에서 Instagram을 여는 구조는 이번 실패 원인이 아니다.

## 목표

`/reel/{shortcode}`와 `/reels/{shortcode}`를
동일한 Reel post identity의 route alias로 처리한다.

## 구현

SUPPORTED_POST_PATH_PATTERN 또는 equivalent parsing에서 다음을 지원한다.

- p
- reel
- reels
- tv

단 `reel`과 `reels`는 서로 다른 post type으로 취급하지 않는다.

PostIdentity 생성 시 canonical post type을 사용한다.

예:

```text
reel  -> reel
reels -> reel
```

따라서:

```text
requested:
https://www.instagram.com/reel/ABC123/

final:
https://www.instagram.com/reels/ABC123/
```

는 같은 게시물이어야 한다.

반대로:

```text
requested:
https://www.instagram.com/reel/ABC123/

final:
https://www.instagram.com/reels/OTHER456/
```

는 다른 게시물이므로 false여야 한다.

query/hash/trailing slash 기존 처리 정책은 유지한다.

## 중요

현재 `NON_PROFILE_PATHS`에 `reels`가 포함되어 있다면 유지한다.

`/reels/`를 profile username으로 오인해서는 안 된다.

이번 수정은 URL route compatibility fix다.

다음은 변경하지 않는다.

- author extraction heuristic
- main fallback 범위
- Candidate domain
- Meta hashtag discovery
- DB schema
- browser external actions
- challenge/CAPTCHA 처리

## 테스트

최소 다음을 추가한다.

### 1. reels page classification

```text
pageLocation("https://www.instagram.com/reels/ABC123/")
```

→ POST

### 2. reels post URL validation

```text
isInstagramPostUrl("https://www.instagram.com/reels/ABC123/")
```

→ true

### 3. reel → reels alias

```text
requested /reel/ABC123/
final     /reels/ABC123/
```

→ `isExpectedPostUrl == true`

### 4. reels → reel alias

```text
requested /reels/ABC123/
final     /reel/ABC123/
```

→ true

### 5. different shortcode

```text
requested /reel/ABC123/
final     /reels/OTHER456/
```

→ false

### 6. shortcode 없는 reels

```text
https://www.instagram.com/reels/
```

→ post 아님

### 7. profile username 오인 방지

```text
profileUsernameFromUrl("/reels/")
```

→ profile username으로 인정하지 않음

기존 tests 모두 유지한다.

## HANDOFF

다음을 기록한다.

- live finalPath가 `/reels/{shortcode}/`임이 확인됨
- Brave UI와 Playwright Chromium 분리는 원인이 아님
- `/reel/`과 `/reels/`를 동일 Reel identity alias로 normalize
- 사용자 macOS live retest 필요

## 검증

```bash
docker compose up -d postgres
docker compose ps

./mvnw test
./mvnw package

git diff --check
git status --short
git diff --stat
git diff
```

Codex sandbox에서 실제 Instagram live 호출은 하지 않는다.

## 성공 기준

- `/reels/{shortcode}`가 POST로 분류됨
- `reel/reels` 동일 shortcode는 동일 identity
- 다른 shortcode는 동일 identity 아님
- `reels`는 profile username으로 오인하지 않음
- 기존 author/main fallback 정책 그대로
- tests/package/diff-check 성공

## 마지막 출력

다음을 보고한다.

- root cause
- canonicalization 방식
- 추가 테스트
- 전체 검증 결과
- 변경 파일
- live retest 방법
