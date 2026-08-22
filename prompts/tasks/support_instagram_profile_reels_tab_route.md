# Instagram author `/username/reels/` profile route 지원

## 시작

루트 AGENTS.md를 따른다.

먼저 다음을 읽는다.

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md

그리고 최신 다음 코드를 확인한다.

- InstagramBrowserExtractor.java
- InstagramBrowserExtractorTest.java
- InstagramBrowserClient.java
- InstagramBrowserClientTest.java

## 실제 live 결과

macOS headed Playwright Chromium에서 실제 Reel 화면:

```text
https://www.instagram.com/reels/DcVyTw1tpwA/
```

에서 main의 visible link를 확인한 결과 다음 형태가 관찰됐다.

```text
0:
href=/dr_howoo/reels/
text=
aria=dr_howoo님의 릴스

1:
href=/dr_howoo/reels/
text=dr_howoo
aria=dr_howoo님의 릴스
```

그 뒤에도 동일한 패턴으로 다른 Reel author들이 존재했다.

예:

```text
/dino.the.nomad/reels/
/dino.the.nomad/reels/

/yh_j_s_mom/reels/
/yh_j_s_mom/reels/
```

동시에 다음과 같은 non-profile route도 존재했다.

```text
/explore/tags/의사/
/reels/audio/39043968185201847/
```

현재 live enrichment 결과:

```text
AUTHOR_EXTRACTION_FAILED:
(page=post,
 finalPath=/reels/DcV-0jXE05s/,
 postRoot=main,
 main=1,
 article=0,
 dialog=0,
 rootLinks=12,
 profileLinks=0,
 candidates=-)
```

## Root cause

현재 `profileUsernameFromUrl()`은 profile URL을 사실상:

```text
/{username}/
```

형태의 정확히 1개 path segment만 인정한다.

실제 Instagram Reel UI의 author link는:

```text
/{username}/reels/
```

형태이므로 2개 segment라 모두 profile candidate에서 탈락한다.

따라서 root link 자체는 존재하지만:

```text
profileLinks=0
```

이 된다.

## 목표

다음을 동일한 profile username으로 인식한다.

```text
/dr_howoo/
→ dr_howoo

/dr_howoo/reels/
→ dr_howoo
```

단 Instagram의 다른 `reels` route를 profile로 오인해서는 안 된다.

## 구현

`profileUsernameFromUrl()` 또는 이에 해당하는 profile route parser를 확장한다.

지원 형태:

### canonical profile

```text
/{username}/
```

### profile Reels tab

```text
/{username}/reels/
```

두 번째 형태에서는:

- segment 수가 정확히 2
- 두 번째 segment가 정확히 `reels`
- 첫 번째 segment가 기존 USERNAME_PATTERN을 만족
- 첫 번째 segment가 NON_PROFILE_PATHS가 아님
- `..` 등 기존 username validation 유지

이어야 한다.

absolute Instagram URL도 동일하게 지원한다.

예:

```text
https://www.instagram.com/dr_howoo/reels/
```

→ `dr_howoo`

## 반드시 거부할 route

다음은 profile username이 아니다.

```text
/reels/DcVyTw1tpwA/
/reels/audio/39043968185201847/
/explore/tags/doctor/
/accounts/login/
/p/ABC123/
/reel/ABC123/
```

특히:

```text
/reels/audio/{id}/
```

에서 `reels`를 username으로 인식하면 안 된다.

현재 `NON_PROFILE_PATHS`의 `reels` 예약어 정책은 유지한다.

## canonical profile URL

`/{username}/reels/`에서 author username을 얻더라도
저장하거나 이후 profile navigation에 사용하는 URL은 기존과 동일하게:

```text
https://www.instagram.com/{username}/
```

canonical profile root를 사용한다.

즉:

```text
/dr_howoo/reels/
```

에서 추출:

```text
username = dr_howoo
profileUrl = https://www.instagram.com/dr_howoo/
```

이어야 한다.

기존 `profileLink(username)`가 이를 이미 제공한다면 재사용한다.

## author 선택

현재 main fallback의 bounded author selection 정책은 유지한다.

실제 live DOM에서 첫 author는:

```text
/dr_howoo/reels/
/dr_howoo/reels/
```

로 동일 username href가 avatar + username link 형태로 두 번 반복된다.

현재 동일 username 반복을 strong evidence로 사용하는 기존 로직을 재사용한다.

첫 profile-like link 하나를 무조건 author로 선택하는 방식으로 바꾸지 않는다.

caption mention inference도 추가하지 않는다.

## 테스트

실제 network는 호출하지 않는다.

최소 다음을 추가한다.

### 1. canonical profile

```text
profileUsernameFromUrl("/dr_howoo/")
```

→ `dr_howoo`

기존 동작 유지.

### 2. profile reels tab

```text
profileUsernameFromUrl("/dr_howoo/reels/")
```

→ `dr_howoo`

### 3. absolute profile reels tab

```text
profileUsernameFromUrl(
  "https://www.instagram.com/dr_howoo/reels/"
)
```

→ `dr_howoo`

### 4. Reel post route

```text
profileUsernameFromUrl("/reels/DcVyTw1tpwA/")
```

→ empty

### 5. Reel audio

```text
profileUsernameFromUrl(
  "/reels/audio/39043968185201847/"
)
```

→ empty

### 6. explore tag

```text
profileUsernameFromUrl("/explore/tags/doctor/")
```

→ empty

### 7. 실제 live DOM 형태 author 선택

root links를 다음 순서로 구성한다.

```text
/dr_howoo/reels/         text=""
/dr_howoo/reels/         text="dr_howoo"
/explore/tags/의사/
/reels/audio/39043968185201847/
/dino.the.nomad/reels/   text=""
/dino.the.nomad/reels/   text="dino.the.nomad"
```

main fallback author:

```text
dr_howoo
```

를 선택해야 한다.

### 8. canonical profile URL

`/dr_howoo/reels/` author를 선택한 결과의 profile URL은:

```text
https://www.instagram.com/dr_howoo/
```

이어야 한다.

### 9. 기존 security/safety tests

- external host 거부
- encoded suspicious path 거부
- reserved path 거부
- caption mention-only 거부
- different commenter/recommended author 오탐 방지

기존 테스트를 모두 유지한다.

## diagnostic

이번 수정 후 동일 DOM이라면 최소:

```text
profileLinks > 0
candidates=dr_howoo,...
```

가 가능해야 한다.

성공하면 diagnostic이 UI에 표시되지 않아도 된다.

raw HTML, cookie, session, token은 저장/출력하지 않는다.

## scope 제한

변경하지 않는다.

- `/reel/` ↔ `/reels/` post alias
- post root selection
- Meta hashtag discovery
- Candidate domain
- DB schema
- browser external actions
- follow/like/comment/DM
- challenge/CAPTCHA 처리
- stealth/evasion

## HANDOFF

다음을 기록한다.

- 실제 Chromium DOM을 확인함
- Reel author profile href가 `/{username}/reels/` 형태임이 확인됨
- 기존 parser가 1-segment profile만 허용해 profileLinks=0이었던 것이 root cause
- `/{username}/reels/`를 canonical profile identity로 지원
- `/reels/{shortcode}`와 `/reels/audio/...`는 profile에서 제외
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

- `/{username}/reels/`에서 username 추출
- canonical `/{username}/` 기존 동작 유지
- `/reels/{shortcode}` profile 오인 없음
- `/reels/audio/...` profile 오인 없음
- 실제 live 형태의 repeated author pair에서 dr_howoo 선택
- canonical profile URL은 `https://www.instagram.com/{username}/`
- 기존 author safety 정책 유지
- tests/package/diff-check 성공

## 마지막 출력

다음을 보고한다.

- root cause
- 지원 profile route
- 거부 route
- canonicalization 방식
- author 선택 결과
- 테스트 결과
- 전체 Maven 결과
- 변경 파일
- macOS live retest 방법
