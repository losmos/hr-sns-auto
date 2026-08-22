# Instagram profile display name / metrics extraction 개선

## 시작

루트 AGENTS.md를 따른다.

먼저 다음을 읽는다.

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md

그리고 최신 다음 코드를 확인한다.

- InstagramBrowserExtractor.java
- InstagramProfileBrowserSnapshot.java
- InstagramBrowserEnrichmentResult.java
- InstagramBrowserExtractorTest.java
- InstagramBrowserClientTest.java

## 실제 live 결과

Reel author 추출과 profile navigation은 이제 성공한다.

실제 browser observation 예:

```text
PARTIAL

username:
kjmbc

display name:
남구 월산로116번길 17, Gwangju 503-728

followers:
확인 불가

following:
확인 불가

posts:
3698
```

display name은 실제 계정명이 아니라 profile 내부 주소가 잘못 선택된 것이다.

## 실제 Instagram profile DOM

macOS headed Playwright Chromium에서
다음 profile을 확인했다.

```text
https://www.instagram.com/nurschema_studycafe/
```

### meta

```text
og:title:
Nurschema의 공부방 | 간호사가 되기 위한 임상 공부(@nurschema_studycafe) • Instagram 사진 및 동영상

og:description:
팔로워 3,554명, 팔로잉 2명, 게시물 81개 - ...
```

### visible header text

```text
nurschema_studycafe
Nurschema의 공부방 | 간호사가 되기 위한 임상 공부
게시물 81
팔로워 3568
팔로우 2
🌸Nurse+Schema라는 뜻입니다.
🍀풀 영상 유튜브 참고!!
...
www.youtube.com/...
팔로우
메시지 보내기
...
```

### header links

```text
href=/nurschema_studycafe/  text=""
href=#                       text="nurschema_studycafe"
href=#                       text="팔로워 3568"
href=#                       text="팔로우 2"
```

즉 현재 Instagram UI에서는 follower/following anchor href가
`/followers/`, `/following/`이 아니라 `#`일 수 있다.

또 visible header와 meta 값이 다를 수 있다.

실제 관찰:

```text
visible header follower = 3568
og:description follower  = 3554
```

따라서 visible header를 authoritative source로 우선하고
meta는 fallback으로만 사용해야 한다.

## 현재 root cause

현재 follower/following extraction:

```text
main header a[href$='/followers/']
main header a[href$='/following/']
```

에 의존한다.

현재 live UI에서는 href="#"이므로 null이 된다.

현재 display name extraction도
metric이 등장한 뒤 첫 profile text를 선택하는 fallback이 있어,
실제 display name이 metric 이전에 있는 구조에서 bio/address를
display name으로 오인할 수 있다.

## 목표

실제 profile header 기준으로 다음을 안정적으로 추출한다.

```text
username
displayName
postCount
followerCount
followingCount
biographyExcerpt
verified
privateAccount
```

이번 live fixture에서는 최소:

```text
username      = nurschema_studycafe
displayName   = Nurschema의 공부방 | 간호사가 되기 위한 임상 공부
postCount     = 81
followerCount = 3568
followingCount= 2
```

이어야 한다.

## 1. follower / following / post extraction

href에 의존하지 않고 visible `main header` text를 우선 사용한다.

지원 label:

### posts

```text
게시물
posts
post
```

### followers

```text
팔로워
followers
follower
```

### following

```text
팔로우
팔로잉
following
```

현재 existing metric parser를 재사용한다.

예:

```text
게시물 81
팔로워 3568
팔로우 2
```

를 각각 field별로 파싱한다.

중요:

전체 header에서 첫 번째 숫자를 무조건 재사용하지 않는다.

각 field label과 대응되는 숫자를 field-specific하게 찾아야 한다.

기존 href 기반 selector가 일부 UI에서는 동작할 수 있으므로
필요하면 secondary source로 유지할 수 있다.

권장 우선순위:

```text
1. visible header text
2. 기존 semantic locator
3. meta fallback
```

## 2. display name

visible header에서 username exact line을 찾는다.

그 username line 이후,
첫 metric line 이전에 위치한
첫 의미 있는 profile text를 display name candidate로 사용한다.

실제 fixture:

```text
nurschema_studycafe
Nurschema의 공부방 | 간호사가 되기 위한 임상 공부
게시물 81
```

→ display name:

```text
Nurschema의 공부방 | 간호사가 되기 위한 임상 공부
```

다음은 display name candidate에서 제외한다.

- username 자체
- metric line
- Follow/Message/Edit profile 등 control
- blank
- external URL
- highlight label
- overly long text

기존 255 code point 제한은 유지한다.

현재의
"metricsSeen 이후 첫 text를 display name으로 선택"
하는 fallback은 제거하거나 수정한다.

bio/address를 display name으로 오인하지 않아야 한다.

## 3. meta fallback

visible header에서 특정 값을 못 구한 경우에만
HTML metadata를 fallback으로 사용할 수 있다.

대상:

```text
meta[property='og:title']
meta[property='og:description']
meta[name='description']
```

### display name fallback

예:

```text
Nurschema의 공부방 | 간호사가 되기 위한 임상 공부(@nurschema_studycafe) • Instagram 사진 및 동영상
```

에서 expected username을 기준으로 display name을 추출할 수 있다.

locale 문구 전체에 과도하게 의존하지 않는다.

가능하면:

```text
(<@username 또는 @username>)
```

앞의 부분을 bounded하게 사용한다.

### metrics fallback

og:description 예:

```text
팔로워 3,554명, 팔로잉 2명, 게시물 81개 - ...
```

에서 field-specific parsing한다.

단 visible header value가 존재하면 meta 값으로 overwrite하지 않는다.

즉 fixture에서는:

```text
header follower 3568
meta follower 3554
```

이면 최종값은 반드시:

```text
3568
```

이다.

## 4. biography

현재 header 구조:

```text
username
displayName
metrics...
bio lines...
external URL
controls...
highlight labels...
```

이다.

biography는:

- username 제외
- displayName 제외
- metric lines 제외
- control labels 제외

후 profile description 영역의 text만 bounded하게 사용한다.

가능하면 다음 boundary에서 중단한다.

- external profile link
- Follow/Message 등의 controls
- highlight area

단 DOM 구조가 불명확하면 기존 conservative extraction을 유지하고
이번 slice에서 과도한 heuristic을 만들지 않는다.

meta[name=description]에는 bio 전체가 포함될 수 있으나,
locale/template text와 metric prefix도 섞여 있으므로
visible bio가 있으면 visible source를 우선한다.

## 5. verified/private

기존 동작을 유지한다.

이번 실제 fixture 때문에 verified/private heuristic을
불필요하게 확대하지 않는다.

## 6. PARTIAL / SUCCESS

`InstagramProfileBrowserSnapshot.isPartial()` 정책은 유지한다.

이번 개선으로 displayName/follower/following/post 등이 정상 추출되면
verified/private도 기존대로 값이 있을 경우
자동으로 SUCCESS가 되어야 한다.

status를 억지로 SUCCESS로 변경하지 않는다.

## 7. 테스트

실제 Instagram network는 호출하지 않는다.

synthetic profile fixture로 최소 다음을 검증한다.

### A. 현재 live Korean DOM

header:

```text
nurschema_studycafe
Nurschema의 공부방 | 간호사가 되기 위한 임상 공부
게시물 81
팔로워 3568
팔로우 2
🌸Nurse+Schema라는 뜻입니다.
🍀풀 영상 유튜브 참고!!
팔로우
메시지 보내기
```

header links:

```text
href="#" text="팔로워 3568"
href="#" text="팔로우 2"
```

결과:

```text
displayName = Nurschema의 공부방 | 간호사가 되기 위한 임상 공부
postCount = 81
followerCount = 3568
followingCount = 2
```

### B. header vs meta discrepancy

header:

```text
팔로워 3568
```

ogDescription:

```text
팔로워 3,554명, 팔로잉 2명, 게시물 81개 ...
```

결과 follower는:

```text
3568
```

이어야 한다.

### C. meta-only fallback

visible metric이 일부 없고 meta에만:

```text
팔로워 3,554명, 팔로잉 2명, 게시물 81개
```

가 있으면 fallback으로 각각 추출한다.

### D. href="#" metrics

`href="#"`여도 visible text 기준으로 metrics를 추출한다.

### E. display name before metrics

username 바로 다음 line의 display name을 선택한다.

### F. address/bio 오인 방지

예:

```text
kjmbc
광주MBC
게시물 3698
팔로워 ...
팔로우 ...
남구 월산로116번길 17, Gwangju ...
```

에서 displayName은:

```text
광주MBC
```

이고 주소가 아니어야 한다.

### G. English fixture

예:

```text
doctor_one
Doctor One
125 posts
9,876 followers
102 following
```

도 field-specific extraction이 동작한다.

### H. no display name

username 다음에 바로 metrics가 시작하면
displayName은 null 또는 meta fallback을 사용한다.

bio를 display name으로 쓰지 않는다.

### I. PARTIAL

필수 profile fields가 모두 존재하면 `isPartial()` false.

누락이 있으면 기존대로 true.

## 안전

저장/출력하지 않는다.

- raw HTML
- cookie
- localStorage
- sessionStorage
- access token

meta tag는 필요한 text만 bounded parsing하고 raw page source를 저장하지 않는다.

## scope 제한

이번 작업에서 변경하지 않는다.

- Reel author extraction
- `/reel` / `/reels` alias
- `/{username}/reels/` author route
- Candidate domain
- Meta hashtag discovery
- DB migration
- follow/like/comment/DM
- browser stealth/evasion
- challenge/CAPTCHA handling

## HANDOFF

다음을 기록한다.

- author/profile navigation은 live 성공
- follower/following href가 `#`임을 실제 DOM에서 확인
- display name은 metrics 이전 username 다음 line에 위치함
- visible header와 og metadata의 follower count가 다를 수 있음
- visible header 우선, meta fallback 정책
- macOS live retest 필요

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

Codex sandbox에서 실제 Instagram network는 호출하지 않는다.

## 성공 기준

- href="#" profile metrics 지원
- follower/following/post field별 추출
- visible header가 meta보다 우선
- display name을 username 다음 / metrics 이전에서 추출
- bio/address display name 오인 방지
- meta fallback 지원
- 기존 Reel author extraction 유지
- tests/package/diff-check 성공

## 마지막 출력

다음을 보고한다.

- 기존 실패 원인
- profile field별 source priority
- display name extraction 규칙
- metric extraction 규칙
- meta fallback 규칙
- synthetic test 결과
- Maven/package 결과
- 변경 파일
- macOS live retest 방법
