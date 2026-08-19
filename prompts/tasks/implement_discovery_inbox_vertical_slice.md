# Instagram Discovery Inbox vertical slice 구현

## 작업 시작 규칙

반드시 루트 `AGENTS.md`를 따른다.

읽기 순서는 다음과 같다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`
3. 구조 파악이 필요하면 `docs/harness/DIRECTORY_MAP.md`
4. 기존 Candidate 구현과 기존 Instagram probe 중 이번 작업에 필요한 파일만 탐색해서 읽는다.

repo 전체를 무작정 읽지 않는다.

## 사용자와 실제 live Meta API에서 새로 확인된 사실

기존 문서에는 아직 반영되지 않은 실제 검증 결과이다.

### 연결 및 자기 계정

- Facebook Page ↔ Instagram Creator 계정 연결이 정상 확인됐다.
- Graph API version은 사용자가 실제 테스트한 시점에 `v26.0`이었다.
- version을 코드에 최신값으로 추측하거나 하드코딩하지 않는다.
- `META_GRAPH_API_VERSION` 환경변수에서 받는다.
- 테스트 Creator의 IG User ID는 실제 환경에서 확인했지만 특정 사용자 ID를 production source에 하드코딩하지 않는다.
- `META_IG_USER_ID` 환경변수로 받는다.
- Access Token은 source, DB, 로그, 문서에 기록하지 않는다.
- `META_ACCESS_TOKEN` 환경변수로만 받는다.

자기 Professional account에 대해 다음 필드 조회가 실제 성공했다.

- `id`
- `username`
- `name`
- `followers_count`
- `media_count`

### Hashtag discovery

실제 live probe에서 다음이 성공했다.

- `HASHTAG_LOOKUP: SUPPORTED`
- `RECENT_MEDIA: SUPPORTED`
- 실제 recent media가 반환됐다.

실제 hashtag recent media 응답에서 이번 제품 기능에 사용할 수 있다고 확인한 정보는 다음이다.

- media `id`
- `caption`
- `media_type`
- `permalink`
- `timestamp`

반면 hashtag media의 작성자 식별은 성공하지 못했다.

- `username` field 직접 요청: unsupported field 계열 오류
- `owner` field 직접 요청: unsupported field 계열 오류
- media ID follow-up으로 username/owner 조회: permission/object access 계열 오류
- 따라서 hashtag media → author username 자동 식별을 production에서 가정하지 않는다.

### Business Discovery

외부 username에 대한 Business Discovery는 현재 환경에서:

- User Access Token: `(#10) Application does not have permission for this action`
- Page Access Token: 동일하게 `(#10)`

이었다.

또 일반 Consumer/Personal 계정을 임의 username으로 공식 API에서 enrichment할 수 있다고 가정하지 않는다.

따라서:

- Business Discovery는 이번 작업 범위가 아니다.
- Candidate 등록의 필수 dependency로 만들지 않는다.
- Professional/Personal 여부와 관계없이 향후 사람이 확인한 username을 Candidate로 관리할 수 있어야 한다.
- optional enrichment는 후속 작업이다.

### 최신 제품 방향

이번 시스템은 Professional 계정만 찾는 시스템이 아니다.

공개 Instagram에서 의료계 네트워킹 후보가 될 수 있는 일반 계정도 대상이다.

공식 API로 stable Meta identity를 얻지 못한 후보는 향후 내부 Candidate ID와 username/history 기반으로 관리하고,
Meta ID나 IGSID 등 stronger identity는 얻을 수 있을 때 추가 연결한다.

이번 작업에서는 Candidate identity 변경을 아직 구현하지 않는다.

## 이번 작업 목표

첫 실사용 가능한 `Instagram Discovery Inbox` vertical slice를 구현한다.

사용자가 UI에서:

1. 기본 hashtag를 확인한다.
2. hashtag를 추가/비활성화/재활성화할 수 있다.
3. `최근 게시물 가져오기`를 누른다.
4. 활성 hashtag별 Meta hashtag lookup + recent media를 실행한다.
5. 수집된 게시물이 DB에 중복 없이 저장된다.
6. 어떤 hashtag로 발견됐는지 보존된다.
7. Discovery Inbox에서 최신 게시물을 확인한다.
8. 실제 Instagram permalink를 열 수 있다.
9. 이미 확인한 게시물과 신규 게시물을 구분할 수 있다.

이 작업의 목적은 Candidate 생성이 아니라
`Instagram-native media discovery → 운영자 검토 inbox`를 실제 제품 기능으로 만드는 것이다.

## 범위

### 1. Discovery hashtag 설정

DB에 사용자가 관리할 수 있는 hashtag 설정을 저장한다.

초기 기본값:

- `의사스타그램`
- `약사스타그램`
- `피부과`

Flyway migration으로 초기값을 넣는다.

현재 schema version이 V3이므로 신규 migration은 충돌 여부를 확인한 후 다음 version을 사용한다.

hashtag에는 최소 다음 개념이 필요하다.

- id
- keyword
- enabled
- createdAt
- updatedAt

입력 시:

- 앞뒤 whitespace 제거
- 사용자가 `#피부과`처럼 입력하면 leading `#` 제거
- 빈 문자열 금지
- 동일 hashtag 중복 생성 방지
- 영문 hashtag도 합리적으로 case-insensitive 중복 방지

삭제 대신 enable/disable을 우선한다.
기존 history와 source association을 보존해야 하기 때문이다.

UI에서:

- 활성 hashtag 목록
- 비활성 hashtag 목록 또는 상태 표시
- 새 hashtag 추가
- enable/disable

을 편하게 할 수 있게 한다.

과도한 SPA/JavaScript framework를 추가하지 않고 기존 Spring MVC + Thymeleaf 스타일을 따른다.

### 2. Meta Instagram Graph API client

기존 `scripts/instagram_native_discovery_probe.py`의 실제 검증된 endpoint 구성,
Bearer token 처리,
redaction 원칙을 참고한다.

production Java 코드에서 필요한 최소 기능만 만든다.

- hashtag ID lookup
- hashtag recent media 조회

환경변수:

- `META_ACCESS_TOKEN`
- `META_GRAPH_API_VERSION`
- `META_IG_USER_ID`

를 사용한다.

token은:

- DB 저장 금지
- query parameter 금지
- 로그 출력 금지
- exception/message 출력 시 redaction
- UI 출력 금지

한다.

애플리케이션은 Meta credential이 없어도 기동 가능해야 한다.

credential/config가 없는 상태에서 사용자가 sync 버튼을 누르면
명확하지만 secret을 포함하지 않는 설정 오류를 UI에 보여준다.

API version은 코드가 최신값을 추측하지 않는다.

Graph API base URL이나 HTTP transport는 synthetic test가 가능하도록 최소한의 test seam을 둔다.
불필요한 provider hierarchy나 거대한 abstraction은 만들지 않는다.

### 3. recent media 수집

각 enabled hashtag에 대해:

1. hashtag lookup
2. recent media 조회

를 수행한다.

첫 release에서는:

- hashtag당 최대 25개
- 첫 page만
- scheduler 없음
- manual 버튼 실행만

으로 제한한다.

pagination, background job, retry queue는 이번 범위가 아니다.

한 hashtag 실패가 다른 hashtag에서 이미 정상 조회된 결과를 무조건 폐기하게 만들 필요는 없다.
가능하면 hashtag별 성공/실패를 구분해 운영자에게 요약한다.

하지만 데이터 integrity가 깨지는 partial transaction 구조는 만들지 않는다.

### 4. Discovery item 저장

Instagram 원본 media 파일은 저장하지 않는다.

최소 다음 데이터를 저장한다.

- internal id
- Instagram media id
- media type
- permalink
- publishedAt
- firstDiscoveredAt
- lastSeenAt
- caption excerpt
- review status

Instagram media id는 unique identity로 사용해 동일 게시물이 다음 sync에서 다시 나와도 duplicate row를 만들지 않는다.

caption 전체를 장기간 복제 저장하지 않는다.
운영자가 inbox에서 내용을 판단할 수 있는 적절한 길이의 excerpt만 저장한다.
예: 최대 500자 정도.
정확한 길이는 기존 프로젝트 스타일과 DB 제약을 보고 단순하게 결정한다.

raw Graph response를 DB에 저장하지 않는다.

### 5. 발견 hashtag association

같은 게시물이 여러 hashtag 검색에서 발견될 수 있다.

따라서:

- media row를 hashtag마다 복제하지 않는다.
- 한 DiscoveryItem에 여러 DiscoveryHashtag가 연결될 수 있게 한다.
- 이후 어떤 hashtag가 유용한 discovery source였는지 분석할 수 있도록 association을 보존한다.

현재 단계에서 통계 dashboard는 만들지 않는다.

### 6. review 상태

이번 slice에는 최소 다음 상태만 구현한다.

- `NEW`
- `OPENED`
- `DISMISSED`

의미:

- NEW: 아직 Instagram 원문을 열어보지 않은 신규 item
- OPENED: 운영자가 Instagram 원문을 열어본 item
- DISMISSED: 후보 검토 대상에서 제외한 item

`CANDIDATE_CREATED`는 아직 구현하지 않는다.
다음 author-identification/Candidate-link slice에서 추가 또는 확장한다.

상태 전이는 단순하게 유지한다.

### 7. Discovery Inbox UI

예시 route는 기존 convention을 확인한 뒤 `/discovery` 계열로 구현한다.

화면 상단:

- Discovery 제목
- hashtag chip/list
- hashtag 추가
- enable/disable
- `최근 게시물 가져오기` 버튼
- 마지막 sync 결과 요약

게시물 영역:

- NEW 개수
- OPENED/DISMISSED 개수
- 기본적으로 NEW를 먼저 보여준다.
- 필요하면 전체 보기 filter 제공
- publishedAt 최신순
- media type
- caption excerpt
- 발견 hashtag
- published timestamp
- `Instagram에서 열기`
- `관심 없음`

을 제공한다.

`Instagram에서 열기`는 실제 API에서 받은 permalink를 사용한다.

가능하면 우리 서버의 redirect endpoint를 통해 OPENED 상태를 기록한 뒤 외부 Instagram permalink로 redirect한다.
open 동작 때문에 username, author, DOM 등을 자동 수집하지 않는다.

permalink 페이지 scraping, Playwright, Selenium, private endpoint 사용은 금지한다.

`관심 없음`은 DISMISSED로 전환한다.

UI는 기존 Candidate UI와 시각적/구조적 스타일을 최대한 맞춘다.
이번 작업을 위해 별도 frontend framework를 추가하지 않는다.

### 8. 보안 및 데이터 최소화

절대 하지 않는다.

- Access Token source commit
- Access Token DB 저장
- token log 출력
- raw Graph API response 저장
- Instagram image/video binary 다운로드 및 저장
- permalink HTML scraping
- browser automation
- author username 추론
- caption/permalink 문자열에서 username parsing
- Business Discovery 호출
- DM/comment/follow/like action

### 9. Candidate 영역

기존 Candidate / Evidence / Eligibility 코드는 이번 작업에서 가능한 한 수정하지 않는다.

Discovery와 Candidate의 실제 연결은 다음 작업이다.

이번 작업에서는 Discovery package/module을 Candidate와 느슨하게 분리해 구현하되,
미래를 위한 과도한 interface/DDD 구조를 만들지 않는다.

### 10. 오류 처리

Graph API 오류는 HTTP status와 Meta error code/type/message에서
운영자가 문제를 알 수 있는 수준으로 표시하되 secret/cursor/raw payload는 노출하지 않는다.

외부 API 실패 때문에 기존 inbox 데이터를 잃지 않는다.

중복 sync가 idempotent하도록 한다.

## 테스트

기존 테스트 스타일을 먼저 확인한다.

최소 다음을 검증한다.

### hashtag

- default hashtag migration
- normalization
- duplicate 방지
- enable/disable

### Meta client

network 없는 synthetic test로:

- versioned URL/path/query 구성
- Authorization Bearer header
- token이 query에 들어가지 않음
- token redaction
- hashtag lookup parsing
- recent media parsing
- malformed/error response 처리

### Discovery persistence/service

- 동일 media id 재수집 시 row duplicate 없음
- lastSeenAt 갱신
- 서로 다른 hashtag에서 같은 media 발견 시 media는 1건, association은 여러 개
- caption excerpt 제한
- NEW → OPENED
- DISMISSED

### Controller/UI

- Discovery page rendering
- hashtag add/enable/disable
- manual sync action
- open redirect
- dismiss
- config 없는 경우 안전한 오류 표시

가능하면 실제 Meta network를 unit/integration test에서 호출하지 않는다.

## migration / PostgreSQL

PostgreSQL을 기준으로 구현한다.
H2를 추가하지 않는다.

새 schema는 Flyway migration으로 관리한다.
`spring.jpa.hibernate.ddl-auto` 등에 의존해 schema를 자동 생성하지 않는다.

## 실제 live 결과 문서 반영

이번 작업 종료 시 `docs/harness/HANDOFF.md`와
필요한 경우 `docs/harness/PROJECT_CONTEXT.md`를 반드시 갱신한다.

특히 기존 문서의 다음 stale 내용은 실제 사용자 live 검증 사실로 수정한다.

- live probe가 NOT_RUN이라는 기록
- author identity 미검증 상태
- hashtag feasibility 미확정 상태

새 사실:

- hashtag lookup 성공
- recent media 성공
- 실제 media 27건을 얻은 실행이 있었음
- hashtag recent media에서 author username/owner 자동 식별 실패
- current official path 기준 hashtag → author candidate 자동 discovery는 불완전
- hashtag media → permalink → 운영자가 author 확인하는 반자동 Discovery Inbox 방향 선택
- 외부 Business Discovery는 User token/Page token 모두 현재 app에서 `#10` permission failure
- Business Discovery는 Candidate 등록 필수 dependency가 아니라 optional enrichment로 취급
- 일반 Consumer/Personal 후보도 제품 대상에서 제외하지 않음
- stable Meta ID가 없는 후보도 향후 내부 Candidate identity + username/history로 관리하는 방향

장기적으로 유효한 결정이면 PROJECT_CONTEXT에 올리고,
이번 구현 실행 상태는 HANDOFF에 둔다.

`docs/harness/DIRECTORY_MAP.md`도 실제 새 discovery package/template/migration 등의 주요 구조가 추가되면 갱신한다.

## 이번 작업에서 하지 않을 것

- 작성자 username 입력 UI
- Candidate 생성/연결
- username history
- Meta stable identity 모델
- Business Discovery
- IGSID
- InteractionHistory
- DM/comment draft
- ranking
- 오늘의 5~15명 추천
- scheduler
- paging
- browser automation
- Instagram scraping
- authentication/security framework 추가

위 항목들은 후속 vertical slice이다.

## 성공 기준

다음을 모두 만족하면 완료이다.

1. PostgreSQL migration이 정상 적용된다.
2. 기본 hashtag 3개가 존재한다.
3. UI에서 hashtag add/enable/disable이 가능하다.
4. Meta config가 있는 환경에서 manual sync가 실제 hashtag lookup/recent media 구조를 호출할 수 있다.
5. 동일 media를 반복 수집해도 DB duplicate가 생기지 않는다.
6. 여러 hashtag source association이 보존된다.
7. Discovery Inbox에서 media 정보를 볼 수 있다.
8. permalink를 열면 OPENED가 기록된다.
9. item을 DISMISSED 처리할 수 있다.
10. Meta token/raw response/original media를 저장하거나 노출하지 않는다.
11. Candidate 기존 기능과 기존 37개 테스트를 깨뜨리지 않는다.
12. 신규 테스트가 추가되어 핵심 경계를 검증한다.
13. `git diff --check`가 통과한다.
14. HANDOFF/PROJECT_CONTEXT가 실제 live 결과 및 최신 제품 방향과 일치한다.

## 검증

AGENTS.md 지침대로 가능하면:

- `docker compose up -d postgres`
- `docker compose ps`
- `./mvnw test`
- `./mvnw package`
- `git diff --check`

를 실행한다.

Codex sandbox에서 Docker 권한 등으로 전체 DB test가 불가능하면:

- 실패 원인을 명확히 기록한다.
- DB 비의존 테스트를 가능한 범위에서 수행한다.
- test skip이나 production 동작 완화로 억지 통과시키지 않는다.
- 사용자의 실제 환경에서 실행해야 할 명령을 마지막에 적는다.

## 마지막 출력

작업 완료 후 반드시 다음을 요약한다.

- 구현한 기능
- 핵심 설계
- 새 migration
- 새 route/UI
- 테스트 결과
- 실행하지 못한 검증과 이유
- 보안 관련 확인
- 변경 파일
- 다음 추천 작업

다음 추천 작업은 원칙적으로:

`Discovery item → 운영자가 author username 입력 → Candidate 연결`

vertical slice여야 한다.

P0 blocker가 실제로 발견되면 관련 구현을 임의로 진행하지 말고
AGENTS.md에 따라 clarification request를 남긴다.
