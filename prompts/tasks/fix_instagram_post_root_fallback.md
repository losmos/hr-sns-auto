# Instagram live post root fallback 및 진단 개선

## 시작

루트 AGENTS.md를 따른다.

먼저 읽는다.

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md

그리고 현재 최신:

- InstagramBrowserClient.java
- InstagramBrowserExtractor.java
- InstagramBrowserExtractorTest.java
- browser observation/result/error 관련 코드

를 읽는다.

## 현재 live 결과

macOS local headed Playwright Chromium에서 browser enrichment는 실행된다.

실제 Discovery item의 `브라우저 정보 가져오기` 결과:

AUTHOR_EXTRACTION_FAILED:
게시물의 author profile link를 화면에서 확인하지 못함
(postContainer=missing, articleLinks=0, profileLinks=0, candidates=-)

따라서:

- browser launch 성공
- application 실행 성공
- enrichment 호출 성공
- failure는 post DOM extraction 단계
- 현재 extractor의 `article` 기반 POST_CONTAINER_SELECTOR가 실제 live UI를 찾지 못함

이다.

## 현재 구현 문제

현재 post root는 주로:

- `[role='dialog'] article:visible`
- `main article:visible`

만 허용한다.

실제 Instagram desktop SPA는 post 상세 화면에서도
항상 semantic `<article>`을 제공한다고 가정할 수 없다.

또 현재 failure diagnostic만으로는
navigation 후 실제 `page.url()`이:

- 요청한 post인지
- Instagram home으로 redirect됐는지
- 다른 화면인지

확인할 수 없다.

## 목표

1. 실제 최종 URL을 안전하게 검증한다.
2. article이 없는 Instagram post 상세 화면에 대해
   conservative `main` fallback을 제공한다.
3. failure diagnostic만 보고 다음 live 문제를 파악할 수 있게 한다.
4. author 오탐 방어는 유지한다.

## 1. navigation 후 final URL 검증

`InstagramBrowserClient.enrich()`에서 post navigation 후
실제 `page.url()`을 확인한다.

요청 permalink와 완전히 동일한 문자열일 필요는 없다.
query/hash/trailing slash 차이는 허용할 수 있다.

단 final URL의:

- scheme = https
- host = instagram.com 또는 www.instagram.com
- path가 지원되는 post path
  - /p/{shortcode}
  - /reel/{shortcode}
  - 필요 시 기존 지원 /tv/{shortcode}

인지 확인한다.

로그인/challenge redirect는 기존 pageState가 먼저 처리한다.

그 외 home/explore/unknown path로 redirect된 경우
AUTHOR_EXTRACTION_FAILED로 숨기지 말고
적절한 navigation/post unavailable 또는 unexpected page error로 구분한다.

새 enum 추가가 과도하면 기존 error code 중 의미상 가장 가까운 것을 사용한다.

## 2. post root selection

기존 semantic article을 최우선으로 유지한다.

우선순위:

1. `[role='dialog'] article:visible`
2. `main article:visible`

없으면, **final URL이 실제 지원 post URL인 경우에만**:

3. `main:visible`
4. 필요 시 `[role='main']:visible`

를 fallback post root로 사용할 수 있다.

Instagram 외 URL이나 home/explore에서는 main fallback을 쓰지 않는다.

generated CSS class에 의존하지 않는다.

## 3. main fallback author extraction

`main` 전체를 post scope로 쓸 때
첫 profile link를 무조건 author로 인정하지 않는다.

기존 trusted candidate 정책을 유지/재사용한다.

우선 strong evidence:

- 동일 username profile href가 초반 범위에서 반복
  - avatar + username link 형태
- href username과 visible text가 정확히 일치
- aria-label/title에 username이 명확히 존재

다음 경로는 제외:

- /p/
- /reel/
- /reels/
- /explore/
- /accounts/
- /direct/
- /stories/
- 기타 기존 NON_PROFILE_PATHS
- external host

caption의 `@mention` text를 파싱해서 author로 만들지 않는다.

추천 계정이나 commenter를 author로 오인하지 않도록
검사 범위는 bounded한다.

현재 MAX_EARLY_AUTHOR_LINKS 같은 제한을 재사용하거나
main fallback 전용으로 작고 설명 가능한 범위를 둔다.

## 4. post root abstraction

가능하면 내부적으로:

- ARTICLE
- MAIN_FALLBACK
- NONE

정도를 표현하는 작은 abstraction을 둔다.

과도한 framework는 만들지 않는다.

post extraction에서 article이라는 이름에 종속된 변수명도
필요한 범위에서 `postRoot` 등으로 정리한다.

## 5. diagnostic 개선

실패 메시지에 token/session/raw HTML 없이 다음을 compact하게 포함한다.

예:

```text
page=post,
finalPath=/p/ABC123/,
postRoot=main,
main=1,
article=0,
dialog=0,
rootLinks=24,
profileLinks=4,
candidates=foo,bar
```

또는 redirect라면:

```text
page=home,
finalPath=/,
...
```

### 절대 포함 금지

- query string 전체
- fragment
- raw HTML
- caption 전체
- page 전체 text
- cookie
- localStorage
- sessionStorage
- Meta access token
- Instagram session 정보

final URL은 full URL 대신
안전한 path/classification만 노출한다.

## 6. page classification

작은 helper로 최소:

- POST
- LOGIN
- CHALLENGE/ACTION_REQUIRED
- HOME
- OTHER_INSTAGRAM
- EXTERNAL

정도를 구분해도 좋다.

단 이번 요구보다 과도하게 확장하지 않는다.

## 7. wait/retry

기존 bounded wait/retry는 유지한다.

article만 기다리다가 4초를 전부 소비한 후 fallback하는 구조보다
post root 관점에서 합리적으로 기다린다.

예:

- semantic article 등장 가능성 잠깐 기다림
- 없으면 post URL + visible main 확인
- bounded retry

random timing이나 anti-detection behavior는 만들지 않는다.

## 8. 테스트

실제 Instagram network를 unit test에서 호출하지 않는다.

synthetic HTML/Page fixture로 최소 다음을 검증한다.

### A. 기존 article
`main > article`
→ 기존 author extraction 성공

### B. article 없는 main
`main` 안에:
- author avatar `/doctor.one/`
- author username `/doctor.one/` visible `doctor.one`
- post/content elements

→ main fallback으로 doctor.one 성공

### C. article 없는 main + unrelated navigation
main 안에 unrelated link가 앞에 존재하더라도
반복/label evidence가 있는 actual author를 선택

### D. commenter/mention
author 이후 other.user profile link 존재
→ author 유지

### E. final URL home
`https://www.instagram.com/`
→ main fallback author extraction을 수행하지 않음

### F. final URL explore
→ 수행하지 않음

### G. external URL
→ 수행하지 않음

### H. final URL post + main 없음
→ 안전하게 failure

### I. diagnostic
- finalPath는 path만
- query/token 없음
- main/article/dialog/rootLinks/profileLinks 포함
- raw HTML 없음

기존 browser extractor tests는 모두 유지한다.

## 9. scope 제한

변경하지 않는다:

- Meta hashtag discovery
- Candidate domain
- EligibilityPolicy
- DB migration
- browser external action
- comment/follow/DM
- stealth/evasion
- CAPTCHA bypass

## 10. HANDOFF

다음을 기록한다.

- live retest에서 여전히 postContainer=missing
- initial header fallback이 아니라 post root 자체의 문제임이 확인됨
- semantic article 우선 + post-URL-gated main fallback을 추가
- final path/page classification diagnostic 추가
- automated test 결과
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

- 기존 article DOM 지원 유지
- article 없는 post main DOM 지원
- post URL에서만 main fallback
- home/explore/external에서 fallback 금지
- first profile link 단순 선택 금지
- caption mention inference 금지
- final page diagnostic 제공
- token/session/raw HTML 노출 없음
- unit tests 성공
- 전체 tests/package 성공
- git diff --check 성공

## 마지막 출력

- root cause
- post root selection 정책
- main fallback author 정책
- final URL 검증 방식
- diagnostic 예시
- 테스트/package 결과
- 변경 파일
- live 재검증 방법
