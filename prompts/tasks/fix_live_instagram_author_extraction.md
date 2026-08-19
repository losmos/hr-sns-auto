# Live Instagram post author extraction 보강

## 시작

루트 AGENTS.md를 따른다.

먼저:

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md

를 읽는다.

그 다음 최신 browser enrichment 관련 코드와 테스트를 읽는다.

특히:

- InstagramBrowserClient.java
- InstagramBrowserExtractor.java
- InstagramBrowserExtractorTest.java
- InstagramBrowserErrorSanitizer.java

## 실제 live smoke 결과

사용자가 macOS local GUI 환경에서 Playwright Chromium을 정상 실행했다.

서버의 DISPLAY 문제는 macOS local 실행으로 해결됐다.

실제 Instagram 로그인/session 준비 후
Discovery Inbox의 실제 게시물에서
`브라우저 정보 가져오기`를 실행하면:

`AUTHOR_EXTRACTION_FAILED: 게시물의 author profile link를 화면에서 확인하지 못함`

이 발생한다.

따라서:

- Playwright browser launch 성공
- Instagram navigation 성공
- login/challenge gate 통과
- post unavailable 판정 통과
- 실패 지점은 `InstagramBrowserExtractor.extractPost()`의 author extraction

이다.

## 현재 문제

현재 author selector가 다음 semantic structure에 과도하게 의존한다.

- `main article header a[href]`
- `[role='dialog'] article header a[href]`
- `main article h1 a[href]`
- `main article h2 a[href]`

실제 Instagram desktop DOM이 HTML `header/h1/h2`를 사용하지 않거나
avatar link의 visible text가 비어 있을 수 있다.

또한 BrowserClient는 DOMContentLoaded 후 500ms fixed settle 이후
바로 extraction을 수행한다.

실제 SPA render timing에서는 author area가 아직 준비되지 않았을 수 있다.

## 목표

현재 Instagram live desktop UI에 대해 author extraction을
더 robust하게 만든다.

단:

- caption 내용으로 username 추측 금지
- raw HTML 저장 금지
- private endpoint 금지
- stealth/anti-detection 금지
- comment/follow/DM 등 external action 금지

## 1. post container wait

navigation 직후 fixed 500ms만 의존하지 않는다.

합리적인 bounded wait를 사용해
post article 또는 지원되는 post root가 나타날 시간을 준다.

무한 wait 금지.

기존 locator timeout 8초 범위 또는 그보다 작은 명시적 timeout을 사용한다.

실제 DOM이 없는 경우 기존 failure로 안전하게 종료한다.

## 2. author extraction 전략

generated CSS class 이름에 의존하지 않는다.

우선 semantic header selector를 유지할 수 있지만
fallback을 추가한다.

### fallback A: article-scoped profile links

post article 또는 dialog article 내부의 visible `a[href]`를
DOM 순서대로 제한된 개수만 수집한다.

Instagram profile URL로 해석 가능한 것만 후보로 만든다.

기존 `profileUsernameFromUrl()`과
NON_PROFILE_PATHS validation을 재사용한다.

다음은 제외한다.

- /p/
- /reel/
- /reels/
- /explore/
- /accounts/
- /direct/
- /stories/
- 기타 기존 NON_PROFILE_PATHS
- 외부 host

caption text에서 @mention을 파싱해서 후보를 만들지 않는다.

### 후보 신뢰도

article 상단의 author area 특성을 이용한다.

가능하면 다음 순서로 강하게 판단한다.

1. article 내 초반 profile-like href가 동일 username으로 반복됨
   - avatar link + username link 같은 구조
2. profile URL username과 visible link text가 일치
3. aria-label/title 등 accessible label에 username이 명시됨
4. semantic header 영역에 존재

첫 번째 article profile link라는 이유만으로
아무 링크나 무조건 author로 확정하지 않는다.

다만 avatar link가 text가 없어도,
동일 profile href가 username visible link와 함께 반복되면
author로 인정할 수 있다.

commenter, caption mention 등의 link를 author로 오인하지 않도록 한다.

DOM order와 article 상단 범위를 사용할 수 있다.

과도한 heuristic scoring framework는 만들지 않는다.
작고 설명 가능한 deterministic policy로 구현한다.

## 3. relative / absolute href

Instagram이 반환하는:

`/doctor_name/`

과:

`https://www.instagram.com/doctor_name/`

둘 다 정상 처리한다.

query/hash가 붙는 현실적인 profile URL도 검토하되
profile identity path 자체가 명확한 경우에만 허용한다.

기존 보안 validation을 약화하지 않는다.

## 4. visible label

현재 innerText가 없으면 aria-label만 확인한다.

필요하면 제한적으로:

- innerText
- aria-label
- title

을 순서대로 사용할 수 있다.

전체 page text를 author identity로 사용하지 않는다.

## 5. bounded retry

SPA rendering 때문에 첫 extraction에서 후보가 없다면
짧은 bounded retry/wait 후 한 번 이상 재시도할 수 있다.

예:

- article visible wait
- author-like link wait
- 2~3번의 짧은 deterministic retry

random human timing을 사용하지 않는다.

총 timeout은 명확하게 bounded한다.

## 6. 안전한 diagnostic

AUTHOR_EXTRACTION_FAILED가 발생했을 때
다음 정도의 구조화된 diagnostic을 결과/error summary에
추가할 수 있게 한다.

예:

- post container 발견 여부
- article 내부 visible link count
- profile-like candidate count
- candidate username 최대 3개

다음은 절대 포함하지 않는다.

- raw HTML
- cookie
- localStorage/sessionStorage
- Authorization
- 전체 caption
- 전체 page text
- access token
- password
- query에 민감정보가 있을 수 있는 arbitrary URL 전체 dump

candidate username/profile path는 공개 screening 정보이므로
소수만 diagnostic에 사용할 수 있다.

단 production UI가 너무 장황해지지 않도록 compact하게 한다.

## 7. profile extraction은 이번 실패 범위에서 과도하게 수정하지 않는다

현재 live failure는 author extraction 단계다.

author 추출 성공 후 실제 profile DOM에서 새 문제가 확인되기 전에는
profile metric extraction을 대규모로 재작성하지 않는다.

필요한 공통 helper 수정만 허용한다.

## 테스트

실제 instagram.com network는 unit test에서 호출하지 않는다.

synthetic HTML로 최소 다음을 추가한다.

### case 1
semantic `<header>` 없는 article:

- avatar `/doctor.one/` text 없음
- username `/doctor.one/` text `doctor.one`

→ doctor.one 추출 성공

### case 2
avatar와 username 동일 profile href 반복

→ author 추출 성공

### case 3
article 안에 caption mention/commenter profile link도 존재

- author links가 먼저 존재
- 뒤에 other.user 존재

→ author만 선택

### case 4
navigation/global profile link는 article 밖

→ author 후보로 사용하지 않음

### case 5
non-profile paths

- /p/foo/
- /reel/bar/
- /explore/
- /accounts/login/

→ 후보 제외

### case 6
absolute Instagram profile URL

→ 성공

### case 7
external host

→ 제외

### case 8
no valid author

→ Optional.empty / AUTHOR_EXTRACTION_FAILED 유지

기존 extractor tests도 모두 유지한다.

## 기존 정책

이번 수정은 live selector compatibility fix다.

Candidate domain, EligibilityPolicy, V5 DB schema,
Meta API Discovery를 변경하지 않는다.

새 migration을 만들지 않는다.

## 문서

HANDOFF.md에 다음 live 사실을 기록한다.

- macOS local headed Playwright Chromium 실행 성공
- Instagram session/navigation 성공
- 첫 live item enrichment가 AUTHOR_EXTRACTION_FAILED
- 원인은 actual Instagram DOM과 initial conservative author selector mismatch로 판단
- 이번 fix 내용
- 최종 automated test 결과
- live retest는 사용자 환경에서 필요

PROJECT_CONTEXT에 새 장기 결정은 필요 없다.

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

실제 Instagram live 호출은 Codex에서 하지 않는다.

## 성공 기준

- semantic header가 없어도 synthetic article에서 author 추출 성공
- avatar text가 없어도 username link와 조합하여 성공
- article 밖 navigation link를 사용하지 않음
- caption text username inference 없음
- commenter/profile mention 오탐 방어
- bounded wait/retry
- raw HTML/session data 저장 없음
- 기존 tests 성공
- 전체 tests 성공
- package 성공
- git diff --check 성공

## 마지막 출력

다음을 보고한다.

- live failure root cause
- 변경된 author extraction fallback
- wait/retry 방식
- diagnostic 방식
- tests 결과
- package 결과
- 변경 파일
- 사용자가 다시 수행할 live smoke 절차
