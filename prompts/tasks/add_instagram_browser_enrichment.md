# Instagram Browser Enrichment vertical slice

## 작업 시작 규칙

루트 `AGENTS.md`를 따른다.

읽기 순서:

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`
3. 필요한 경우 `docs/harness/DIRECTORY_MAP.md`
4. 이번 작업과 직접 관련된 discovery 코드와 테스트만 읽는다.

현재 main 기준 직전 구현은 Instagram Discovery Inbox이다.

기존 Candidate/Evidence/Eligibility 영역은 이번 작업에서 가능한 한 수정하지 않는다.

## 사용자 최신 결정

기존 PROJECT_CONTEXT에는 다음과 같은 결정이 존재한다.

- Instagram browser automation MVP 제외
- Instagram 웹 UI 무단 scraping을 전제로 설계하지 않음

사용자가 2026-08-19 명시적으로 방향을 변경했다.

새 방향:

- 공식 Meta API가 제공하지 않는 공개 Instagram 프로필 정보를 얻기 위해 로컬 browser automation을 사용한다.
- Playwright를 사용한 operator-triggered browser enrichment를 허용한다.
- 스터디/개인 도구 성격이다.
- API Discovery는 계속 우선 사용한다.
- Browser automation은 API에서 부족한 author/profile 정보를 보강하는 역할이다.
- Instagram에 대한 follow, like, comment, DM 등의 external action 자동화는 여전히 이번 범위가 아니다.

PROJECT_CONTEXT의 기존 결정과 최신 사용자 결정이 충돌하므로
최신 사용자 결정을 source of truth에 반영한다.

기존 결정을 단순 삭제해 과거 맥락을 잃기보다는
superseded/변경됨을 명확하게 기록한다.

## 중요한 범위 제한

이번 browser automation은 다음만 수행한다.

- 사용자가 명시적으로 버튼을 누른 경우 실행
- Discovery Inbox에 이미 존재하는 permalink를 출발점으로 사용
- 공개 화면에 표시되는 post author/profile metadata를 읽음
- Instagram 프로필로 navigation
- 구조화된 screening metadata 저장

다음은 하지 않는다.

- background scheduler
- 무한 crawling
- follower/following 목록 순회
- 검색 결과 전체 순회
- 계정 follow
- like
- comment
- DM
- stealth plugin
- fingerprint spoofing
- CAPTCHA/Challenge 우회
- proxy rotation
- rate-limit 회피
- private endpoint 호출
- Instagram password DB/source 저장
- session cookie DB 저장
- raw HTML 장기 저장
- screenshot 기본 저장
- image/video binary 저장

Instagram login/challenge/checkpoint가 나오면 우회하지 않고
LOGIN_REQUIRED 또는 ACTION_REQUIRED 상태로 종료한다.

## 목표

Discovery Inbox의 게시물에 대해 Playwright를 사용하여:

1. 실제 Instagram post page를 연다.
2. post author username을 찾는다.
3. author profile page로 이동한다.
4. 화면에 공개된 기본 프로필 정보를 최대한 추출한다.
5. Discovery item과 연결해 저장한다.
6. Inbox에서 선별 정보로 보여준다.
7. 한 건뿐 아니라 사용자가 명시적으로 실행한 NEW item 최대 10건을 순차 처리할 수 있다.

## Playwright

Java용 Microsoft Playwright를 사용한다.

현재 작업 시점 공식 Playwright Java 문서에서 확인된 artifact 예제 version은:

`1.61.0`

이다.

`pom.xml`에 최소 dependency만 추가한다.

브라우저는 Chromium을 우선 사용한다.

기본 실행은 headed mode이다.

브라우저 binary 설치는 source build와 분리한다.

사용자에게 필요한 설치 명령을 HANDOFF와 마지막 출력에 기록한다.

예상 설치 방식은 공식 Playwright CLI 방식이다.

```bash
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

현재 프로젝트에서 plugin prefix resolution 문제가 있으면
불필요한 build plugin을 추가하기 전에 더 단순한 공식 Maven invocation을 검토한다.

테스트 실행 때 browser binary가 없다는 이유로 전체 unit test가 실패하게 만들지 않는다.
실제 Playwright live browser test는 명시적인 manual/smoke test로 분리한다.

## 브라우저 session

Instagram 사용자명/비밀번호를 애플리케이션에 받거나 저장하지 않는다.

Playwright persistent browser context를 사용한다.

프로젝트 기본 session directory 예:

`.local/instagram-browser-profile/`

정확한 디렉토리는 config/environment로 override 가능하게 한다.

예:

`INSTAGRAM_BROWSER_USER_DATA_DIR`

기본 경로는 project-local 또는 user-local의 명확한 비공개 디렉토리로 선택한다.

반드시 gitignore한다.

이 디렉토리에는 cookies/local storage 등 인증정보가 있을 수 있으므로:

- git commit 금지
- source 포함 금지
- 로그에 내용 출력 금지
- DB 복사 금지

한다.

동일 user-data directory를 동시에 여러 Chromium instance가 사용할 수 없다는 점을 고려해
동시 browser enrichment 실행을 막거나 직렬화한다.

## 설정

최소 설정:

- `INSTAGRAM_BROWSER_AUTOMATION_ENABLED`
  - 기본 false
- `INSTAGRAM_BROWSER_USER_DATA_DIR`
- `INSTAGRAM_BROWSER_HEADLESS`
  - 기본 false
- `INSTAGRAM_BROWSER_BATCH_SIZE`
  - 기본 10
  - 최대 15

browser automation이 disabled인 상태에서도
애플리케이션은 정상 기동하고 기존 API Discovery가 정상 동작해야 한다.

## UI

Discovery Inbox 상단에 browser automation 상태를 보여준다.

예:

```text
브라우저 보강: 비활성
```

또는:

```text
브라우저 보강: 활성
```

활성 상태일 때 다음 기능을 제공한다.

### Session 준비

`Instagram 브라우저 열기 / 로그인 확인`

버튼을 제공한다.

이 버튼은 persistent context의 Chromium을 열고 Instagram을 표시한다.

사용자가 직접 로그인한다.

로그인 성공 여부를 password/cookie 값을 읽어 판단하지 않는다.
Instagram page state를 통해 최소한의 session-ready 상태만 판단한다.

브라우저를 닫아도 session directory는 유지된다.

### item 단건

각 Discovery item에:

`브라우저 정보 가져오기`

버튼을 추가한다.

처리 후 화면에 가능한 범위에서 다음을 표시한다.

- author username
- display name
- follower count
- following count
- post count
- biography excerpt
- profile URL
- verified 여부
- private/public 여부
- profile observedAt

원본 post에서 화면에 공개적으로 보이는 경우 다음도 optional하게 기록한다.

- like count
- comment count
- view/play count

해당 값이 화면에서 안정적으로 얻어지지 않으면 null로 둔다.
없는 값을 추정하지 않는다.

### batch

상단에:

`NEW 10건 브라우저 정보 보강`

과 같은 명시적 버튼을 제공한다.

- NEW 상태 중 browser enrichment가 없는 item 우선
- publishedAt 최신순
- config batch size만 처리
- 최대 15
- 순차 실행
- parallel browser tabs/crawlers 금지

각 item별 성공/부분성공/실패를 독립 기록한다.

한 item 실패 때문에 앞에서 성공한 item 결과를 폐기하지 않는다.

## 저장 모델

현재 DiscoveryItem에 무작정 모든 profile field를 추가하기보다
코드를 확인하고 가장 단순하면서 의미가 분명한 모델을 선택한다.

선호 모델은 item별 최신 browser observation이다.

예:

`DiscoveryBrowserObservation`

최소 필드 개념:

- id
- discoveryItemId
- authorUsername
- authorDisplayName
- profileUrl
- followerCount nullable
- followingCount nullable
- postCount nullable
- biographyExcerpt nullable
- verified nullable
- privateAccount nullable
- postLikeCount nullable
- postCommentCount nullable
- postViewCount nullable
- observedAt
- status
- errorCode/errorSummary nullable

상태 예:

- SUCCESS
- PARTIAL
- LOGIN_REQUIRED
- ACTION_REQUIRED
- FAILED

실제 naming은 기존 프로젝트 스타일을 따른다.

한 DiscoveryItem당 현재 최신 observation 하나를 유지하는 구조로 단순하게 시작해도 된다.

향후 username history/Candidate identity는 별도 vertical slice이므로
이번 작업에서 과도하게 설계하지 않는다.

Profile 숫자 데이터는 negative 금지.

biography는 원문 전체를 장기간 저장하지 말고
screening에 필요한 합리적인 excerpt limit을 둔다.

raw HTML은 저장하지 않는다.

## Browser extractor 설계

Instagram DOM은 변경될 수 있으므로
Controller/Service에 selector와 parsing logic을 흩뿌리지 않는다.

browser navigation/extraction을 한 작은 boundary에 격리한다.

예:

- `InstagramBrowserClient`
- `InstagramBrowserExtractor`

정확한 class naming은 기존 코드 스타일을 따른다.

### 추출 우선순위

post page:

1. 현재 post에 연결된 작성자 profile link를 화면 DOM에서 식별
2. username을 link/path와 visible author area의 일관성으로 확인
3. `/p/`, `/reel/`, `/explore/`, `/accounts/` 등 명백한 non-profile path는 author로 판단하지 않음
4. caption 텍스트에서 username을 추측하지 않음

profile page:

공개 화면에 표시되는 값을 읽는다.

- username
- display name
- followers
- following
- posts
- biography
- verified indicator
- private/public indicator

한 selector 하나에만 의존하지 말고
가능하면 semantic/accessible locator 또는 명확한 link/label을 우선한다.

CSS class hash나 minified generated class name을 도메인 전체에 하드코딩하지 않는다.

필요한 selector/fallback은 한 클래스에 격리하고 상세 주석을 남긴다.

## Metric parser

Instagram visible count 표현을 정수로 normalization하는 parser를 분리한다.

한국어/영문에서 현실적으로 만날 수 있는 표현을 테스트한다.

예:

- `523`
- `1,234`
- `4.8천`
- `1.2만`
- `4.8K`
- `1.2M`

불확실하거나 지원하지 않는 표현을 억지로 숫자로 추정하지 않는다.

반올림 규칙을 명시하고 테스트한다.

locale 표기 차이를 처리하되 과도한 NLP는 만들지 않는다.

## 오류/상태

다음을 구분한다.

- browser automation disabled
- browser binary missing
- session/login required
- Instagram challenge/checkpoint
- post unavailable/deleted
- author extraction failed
- profile unavailable/private
- partial field extraction
- navigation timeout
- unexpected DOM

사용자 메시지는 원인 파악이 가능하되
cookie/session/token/raw HTML/전체 page text를 포함하지 않는다.

Instagram이 challenge/checkpoint/CAPTCHA를 표시하면
자동 해결하거나 우회하지 않는다.

## timeout / load

browser navigation timeout을 명시적으로 둔다.

무한 wait 금지.

batch는 반드시 순차 처리한다.

item 사이에 비정상적으로 초고속 반복 요청을 만들 필요가 없도록
작은 고정/보수적 delay를 둘 수 있다.

이 delay의 목적은 탐지 회피가 아니라
UI 안정화와 과도한 동시 부하 방지이다.

랜덤 human-like delay, stealth timing, anti-detection logic은 넣지 않는다.

## Candidate와의 관계

이번 단계에서는 Candidate 자동 생성까지 구현하지 않는다.

Browser observation은 Discovery screening 정보이다.

다음 작업에서:

DiscoveryBrowserObservation
→ author username 확인
→ Candidate 연결

로 이어질 수 있게만 한다.

기존 Candidate eligibility 정책은 수정하지 않는다.

## 기존 API Discovery

현재 Meta hashtag lookup/recent media 구현은 유지한다.

Browser automation은 API Discovery를 대체하지 않는다.

흐름은:

```text
Meta API hashtag sync
        ↓
Discovery Inbox
        ↓
사용자 명시적 browser enrichment
        ↓
author/profile screening info
```

이다.

API sync 실행만으로 브라우저가 자동으로 뜨지 않게 한다.

## 테스트

실제 instagram.com network를 자동 test suite에서 호출하지 않는다.

### 반드시 추가할 unit/synthetic test

- metric parser
- profile URL/username validation
- author candidate link filtering
- partial extraction result mapping
- browser disabled 상태
- batch size validation (default 10, max 15)
- error sanitization
- persistence
- controller/UI route

### synthetic HTML

필요하다면 실제 Instagram HTML을 fixture로 복사하지 말고
테스트 목적의 최소 synthetic HTML을 직접 만든다.

실제 account username, profile content, cookies, HTML dump는 repository에 commit하지 않는다.

### DB

PostgreSQL/Flyway를 유지한다.
H2 추가 금지.

현재 V4 이후 신규 migration version을 사용한다.

## live smoke test

자동 unit suite와 별도로 사용자가 직접 실행할 smoke test 절차를 문서화한다.

최소 절차:

1. Playwright Chromium 설치
2. `INSTAGRAM_BROWSER_AUTOMATION_ENABLED=true`
3. headed browser 실행
4. browser session에서 사람이 Instagram 로그인
5. Discovery Inbox의 실제 item 1건에 `브라우저 정보 가져오기`
6. username/profile metrics 확인
7. 최대 3건 정도 추가 테스트
8. challenge/checkpoint가 뜨면 중단

live Instagram smoke test는 Codex sandbox에서 실행하지 않는다.

## 보안/로컬 데이터

`.local/` 또는 실제 session directory가 gitignore인지 확인한다.

session/cookies가 다음에 절대 포함되지 않도록 한다.

- git diff
- test fixture
- log
- exception
- report
- DB

Access Token 보안 원칙도 기존대로 유지한다.

## 프로젝트 문서 결정 변경

`docs/harness/PROJECT_CONTEXT.md`를 반드시 수정한다.

기존:

- `DEC-20260817-no-unauthorized-instagram-collection`
- browser automation MVP 제외 관련 결정/제약

을 최신 사용자 결정과 충돌하지 않도록 갱신한다.

의미는 다음처럼 분명해야 한다.

- background/bulk scraping/private endpoint는 여전히 하지 않는다.
- 그러나 operator가 명시적으로 실행하는 local Playwright browser enrichment는 허용한다.
- 용도는 API가 제공하지 않는 공개 post author/profile screening metadata 보강이다.
- external Instagram action 자동 실행은 여전히 금지한다.
- challenge/anti-bot 우회 기능은 구현하지 않는다.

새 결정 ID를 추가한다면 예:

`DEC-20260819-operator-triggered-browser-enrichment`

PROJECT_CONTEXT의 목표와 다음 작업 기준도 최신 방향에 맞춘다.

`HANDOFF.md` 역시 이번 구현과 검증 상태를 갱신한다.

`DIRECTORY_MAP.md`는 browser automation package/config 등이 주요 구조로 추가되면 갱신한다.

## 성공 기준

1. 기존 Meta API Discovery가 그대로 동작한다.
2. browser automation disabled 상태에서 app/test/package가 정상이다.
3. Playwright persistent headed browser session을 열 수 있는 구조가 구현된다.
4. Instagram ID/PW를 애플리케이션이 저장하지 않는다.
5. Discovery item 1건 browser enrichment route/UI가 구현된다.
6. 최대 10건 기본 batch enrichment가 구현된다.
7. batch max는 15이다.
8. author username을 browser DOM에서 얻는 구조가 존재한다.
9. profile follower/following/post count/bio 등을 가능한 범위에서 추출한다.
10. post like/comment/view는 보일 때만 optional 저장한다.
11. partial result를 허용한다.
12. raw HTML/screenshot/cookie를 DB와 git에 저장하지 않는다.
13. challenge/CAPTCHA 우회가 없다.
14. external follow/like/comment/DM action이 없다.
15. Flyway migration 성공
16. 기존 + 신규 tests 성공
17. `./mvnw package` 성공
18. `git diff --check` 성공
19. PROJECT_CONTEXT/HANDOFF가 최신 결정과 일치한다.

## 검증

가능하면:

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

를 실행한다.

Codex sandbox에서 browser binary/display/network가 없어 live browser test를 수행할 수 없는 것은
정상적인 환경 제약으로 기록한다.

그 이유로 unit test를 skip하거나 production 코드를 약화시키지 않는다.

## 마지막 출력

반드시 다음을 보고한다.

- 구현한 browser enrichment 구조
- Playwright dependency/version
- session 저장 위치와 보안
- 새 migration
- 새 UI/routes
- 추출하는 profile/post field
- partial/error 처리
- 신규/전체 테스트 결과
- package 결과
- live browser test 미수행 여부
- 변경 파일
- 실제 사용자 smoke test 명령
- 다음 추천 작업

다음 추천 작업은 원칙적으로:

`Browser observation → Candidate 연결 + username/history identity`

이다.
