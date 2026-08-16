# Instagram-native Candidate Discovery feasibility spike

## 목적

정식 Discovery module 구현 전에 다음 가설을 실제 Meta Instagram API 호출로 검증한다.

    Instagram 내부 hashtag signal
        ↓
    public hashtagged media
        ↓
    media 작성 Instagram account 식별
        ↓
    username 후보 dedupe
        ↓
    하루 후보 5~15개 생성 가능

이번 작업은 production Discovery 구현이 아니다.

핵심은 "공식 Instagram API만으로 hashtagged media에서
후보 Instagram account identity를 얻을 수 있는가?"를 검증할 수 있는
재현 가능한 probe를 만드는 것이다.

## 사용자 목적에 대한 최신 방향

후보 discovery 단계에서는 의료기관 공식 홈페이지나 외부 의료인 명부에서
사람을 먼저 찾지 않는다.

Instagram 플랫폼 안에서 활동 중인 계정을 먼저 발견하는 방식을 우선한다.

Discovery 단계에서는 실제 정식 의사·약사인지 100% 증명할 필요가 없다.

약간의 false positive는 허용한다.

중요한 것은:

- 한국 의료계열처럼 보이는가
- Instagram 활동이 있는가
- 개인 또는 전문직 중심 계정인가
- follower가 지나치게 크지 않은가
- 모발이식 경쟁 영역으로 명확히 보이지 않는가
- 과거에 이미 처리한 후보가 아닌가

이다.

엄격한 profession/evidence 검증과 기존 EligibilityPolicy는
후속 검토 단계의 안전장치로 유지한다.

이번 spike에서는 기존 Candidate/Eligibility domain을 변경하지 않는다.

## 현재 공식 API 전제

2026-08-17 기준 Meta 공식 Instagram API 문서에서 확인한 범위:

- Instagram API with Facebook Login은 Business/Creator Professional 계정을 대상으로 한다.
- hashtagged media 탐색 기능을 제공한다고 공식적으로 명시한다.
- 다른 Instagram Business/Creator의 basic metadata/metrics 조회 기능을 제공한다.
- Facebook Login 방식은 Page와 Professional Instagram Account 연결을 요구한다.
- Consumer(non-Business/non-Creator) Instagram account에는 접근할 수 없다.

그러나 이번 프로젝트에 결정적인 다음 사항은 아직 실제 호출로 확인해야 한다.

    hashtagged media 결과 또는 media follow-up request를 통해
    작성자의 username/account identity를 실제로 얻을 수 있는가?

이것을 추측으로 구현하지 않는다.

실제 Graph API 응답으로 확인한다.

## 먼저 읽을 문서

AGENTS.md 지침을 따른다.

순서:

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

그 후 필요한 현재 repository 구조만 확인한다.

## 구현 전략

production Java package에 Instagram provider를 만들지 않는다.

이번 spike에서는 다음을 추가한다.

    scripts/instagram_native_discovery_probe.py

Python standard library만 사용한다.

외부 pip dependency를 추가하지 않는다.

필요하면 다음 문서도 추가한다.

    docs/spikes/instagram_native_discovery.md

`docs/spikes/`가 없으면 생성해도 된다.

probe 성공 전:

- Spring service
- repository
- DB table
- scheduler
- provider abstraction

을 만들지 않는다.

## 환경 변수

script는 secret을 source code에 저장하지 않는다.

최소:

    META_ACCESS_TOKEN
    META_GRAPH_API_VERSION

을 환경변수로 받는다.

예:

    META_GRAPH_API_VERSION=vXX.X

API version을 코드에서 최신이라고 추측해서 고정하지 않는다.

가능하면:

    META_IG_USER_ID

를 받는다.

IG user id가 없을 경우,
현재 access token으로 안전하게 조회 가능한 연결 Page/Instagram Professional Account
metadata를 이용해 IG User ID를 찾을 수 있다면 preflight 기능으로 지원해도 된다.

단:

- Page access token을 출력하지 않는다.
- access token 전체를 stdout/stderr/log에 절대 출력하지 않는다.
- 여러 Instagram account가 발견되어 자동 선택이 위험하면
  `META_IG_USER_ID`를 지정하라고 명확히 종료한다.

hashtag는:

    DISCOVERY_HASHTAGS

comma-separated 환경변수로 받는다.

예:

    의사스타그램,약사스타그램,피부과

기본값을 둔다면 2~3개의 보수적 소수 hashtag만 사용한다.

대량 hashtag request를 기본 동작으로 만들지 않는다.

## Probe 단계

### Phase 1: Authentication / prerequisite preflight

현재 token과 IG User ID로 Graph API 호출이 가능한지 확인한다.

성공/실패를 구조화해서 표시한다.

token 자체는 표시하지 않는다.

permission 또는 account prerequisite 오류가 나면
Meta API가 반환한 error code/type/message를 secret 제거 후 기록한다.

이를 application bug처럼 숨기지 않는다.

### Phase 2: hashtag lookup

각 hashtag에 대해 공식 hashtag lookup 경로를 호출한다.

현재 Meta Graph API의 실제 endpoint 형식을 사용한다.

endpoint나 parameter를 기억으로 임의 생성하지 않는다.

repository에서 확인할 수 없고 Codex 인터넷 접근도 없어
정확한 현재 Meta 공식 endpoint를 검증할 수 없는 경우:

- 과거 endpoint를 최신 사실인 것처럼 확정하지 않는다.
- script의 URL builder를 명확히 격리한다.
- HANDOFF에 "live official endpoint verification required"를 기록한다.

단, 현재 널리 사용되는 Graph API hashtag endpoint 구조를 probe 대상으로
구현하는 경우에도 실제 HTTP response가 source of truth이다.

### Phase 3: hashtagged recent media

hashtag ID를 얻으면 public recent media를 조회한다.

먼저 author identity가 필요 없는 baseline field만 요청한다.

예:

    id
    caption
    media_type
    permalink
    timestamp

현재 API에서 허용되지 않는 field가 확인되면
실제 Graph error에 맞춰 probe에서 분리한다.

baseline 호출이 되는지부터 확인한다.

### Phase 4: author identity capability probe

이 단계가 이번 작업의 핵심이다.

다음 가능성을 각각 독립적으로 probe한다.

A. hashtag recent media response에서 username field 요청

B. hashtag recent media response에서 owner/account 계열 field 요청

C. 반환된 media ID에 follow-up GET을 수행해
   username 또는 owner/account identity를 얻을 수 있는지 확인

특정 field가 허용되지 않아도 전체 script를 즉시 실패시키지 않는다.

각 capability를 다음처럼 분리한다.

    HASHTAG_LOOKUP
    RECENT_MEDIA
    MEDIA_USERNAME
    MEDIA_OWNER
    FOLLOWUP_MEDIA_USERNAME
    FOLLOWUP_MEDIA_OWNER

각각:

    SUPPORTED
    UNSUPPORTED
    AUTH_BLOCKED
    UNKNOWN

중 하나로 결과를 기록한다.

실제 HTTP status와 sanitized Graph error를 함께 남긴다.

## Candidate extraction

실제 API 응답에서 username을 합법적으로 얻은 경우에만
candidate username으로 추출한다.

username을 다음에서 추측하지 않는다.

- caption text
- permalink string parsing
- media shortcode
- guessed Instagram URL
- HTML scraping
- undocumented/private endpoint

실제 API response가 account identity를 제공해야 한다.

username을 얻을 수 있다면 hashtag 간 중복을 제거한다.

간단한 출력 예:

    candidates:
      - username: example_doctor
        discoveredBy:
          - 의사스타그램
          - 피부과
        sourceMediaCount: 3

정식 ranking은 이번 범위가 아니다.

최대 15개 정도만 summary에 표시해도 된다.

## 성공 판정

### FEASIBLE

공식 API 응답을 통해 hashtag media 작성자의
Instagram account identity/username을 반복적으로 얻을 수 있고,
여러 media에서 unique candidate account를 만들 수 있음.

이 경우:

    official Instagram-native discovery via hashtag is feasible

로 판정한다.

실제 후보가 5~15개 나오면 매우 강한 성공 신호이다.

### PARTIALLY_FEASIBLE

hashtag media는 정상 조회되지만 account identity coverage가 제한적이거나
Professional account 일부에 대해서만 candidate를 얻을 수 있음.

실제 제한을 명시한다.

### NOT_FEASIBLE_WITH_CURRENT_OFFICIAL_PATH

hashtag media는 얻지만 작성자의 account identity를 얻을 수 없어
unique candidate username list를 만들 수 없음.

이 경우 절대:

- Instagram 웹 scraping
- private API
- browser automation
- 비공식 endpoint

를 자동으로 추가하지 않는다.

그 결과 자체가 spike의 성공적인 발견이다.

### PREREQUISITE_BLOCKED

Meta app/token/Page/Professional Account/permission 조건 때문에
live API까지 도달하지 못함.

이 경우 어떤 prerequisite가 필요한지
실제 Graph error와 공식 전제를 기준으로 기록한다.

## 출력

script는 사람이 읽을 수 있는 summary와 JSON report를 모두 지원하면 좋다.

예:

    agent_outputs/run_logs/..._instagram_discovery_probe.json

단 `agent_outputs/run_logs/`는 git ignore 대상이다.

실제 후보 username, caption 등 live public account data를
committed fixture나 문서에 복사하지 않는다.

committed source에는 synthetic fixture만 사용한다.

## Security

절대 commit하지 않는다.

- access token
- app secret
- Page access token
- `.env`
- raw secret-bearing HTTP request

로그 출력 전에 token 문자열을 반드시 redaction한다.

API request URL을 출력해야 한다면 access_token query parameter가 없는
sanitized URL만 출력한다.

가능하면 Authorization Bearer header를 사용한다.

## Test

external API를 실제 unit test dependency로 만들지 않는다.

script 내부 parsing/capability classification을 테스트해야 한다면
synthetic JSON fixture나 Python standard-library unittest를 사용한다.

production Maven test에 Meta API network dependency를 추가하지 않는다.

기존:

    ./mvnw test
    ./mvnw package

는 그대로 offline/repeatable해야 한다.

## PROJECT_CONTEXT 변경

이번 사용자 방향을 source of truth에 최소한으로 반영한다.

기존 source들과 모순되지 않게 다음을 기록한다.

- discovery는 Instagram-native signal을 우선한다.
- 외부 의료기관/의료인 directory를 최초 discovery의 필수 source로 두지 않는다.
- discovery 단계의 의료직군 false positive는 어느 정도 허용한다.
- 후보의 실제 가치 기준은 SNS 활동성과 의료계 네트워킹 가능성이다.
- strict profession evidence는 discovery entry 조건이 아니라 후속 eligibility/review 안전장치이다.
- 공식 API로 가능한 범위를 먼저 spike하고, 불가능하다고 확인되기 전 browser automation을 추가하지 않는다.

기존 Instagram 무단 scraping 금지 Decision은 유지한다.

## HANDOFF 변경

이번 작업:

- spike script
- 사용 방법
- 현재 live execution 여부
- actual API blocker가 있다면 blocker
- 다음 action

을 기록한다.

또한 사용자의 실제 개발 환경에서 V3 기준:

    ./mvnw test
    Tests run: 37
    Failures: 0
    Errors: 0
    Skipped: 0

    ./mvnw package
    BUILD SUCCESS

    Flyway schema version 3
    success = true

가 확인됐다는 사실도 최신 상태로 반영한다.

## 이번 작업에서 하지 않을 것

- Candidate edit
- Discovery DB table
- production provider abstraction
- scheduler
- automatic daily job
- LLM
- candidate scoring framework
- Search API
- 의료기관 홈페이지 crawler
- Instagram browser automation
- Selenium
- Playwright
- Instagram HTML scraping
- private/undocumented Instagram API
- comment/DM send
- Spring Security

## 검증

가능한 범위:

    python3 scripts/instagram_native_discovery_probe.py --help

synthetic/local test가 있다면 실행한다.

그리고 기존 app regression 확인:

    docker compose up -d postgres
    docker compose ps
    ./mvnw test
    ./mvnw package
    git diff --check
    git status --short
    git diff --stat
    git diff

live Meta credentials가 Codex 환경에 없으면
실제 API가 호출되지 않은 것을 실패로 위장하지 않는다.

`PREREQUISITE_BLOCKED` 또는 `NOT_RUN`으로 정확히 기록한다.

## 마지막 출력

짧게 다음을 출력한다.

- 추가한 spike 파일
- 공식 API capability 가설
- live API 실행 여부
- 현재 feasibility 판정
- 필요한 Meta prerequisite
- 기존 Maven regression 결과
- source document 갱신 여부
- 다음 사용자가 실행할 명령
