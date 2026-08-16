# Instagram-native Candidate Discovery probe

## 목적

이 probe는 정식 Discovery module을 구현하기 전에 공식 Meta Instagram API만으로 hashtagged media 작성자의 account identity 또는 username을 얻을 수 있는지 확인한다.

- production provider, Spring service, repository, DB table, scheduler를 만들지 않는다.
- caption, permalink, shortcode에서 username을 추측하지 않는다.
- Instagram HTML scraping, private API, browser automation을 사용하지 않는다.
- 실제 API 응답에서 명시적으로 반환된 `username`만 후보로 집계한다.

실행 파일은 `scripts/instagram_native_discovery_probe.py`이다. Python standard library만 사용한다.

## 공식 근거와 확인 한계

2026-08-17에 확인한 [Meta 공식 Instagram API Postman collection](https://www.postman.com/meta/instagram/folder/23987686-3a75357f-e106-47ef-a8d9-af1aadf85365)은 다음을 명시한다.

- Facebook Login 방식은 Instagram Business·Creator Professional Account를 대상으로 한다.
- Page와 Professional Instagram Account 연결이 필요하다.
- hashtagged media 탐색을 지원한다.
- consumer account에는 접근할 수 없다.

[Meta 공식 Instagram API 문서](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api)는 `graph.facebook.com`, versioned path, 연결 Page의 `instagram_business_account` metadata 조회 예시를 제공한다.

이번 구현 시점에는 Meta의 세부 hashtag reference page를 직접 열어 현재 endpoint와 field 목록을 끝까지 대조하지 못했다. 따라서 널리 사용되는 다음 Graph path를 `GraphUrlBuilder`에 격리했으며, 최신 사실로 확정하지 않는다.

- `GET /{version}/ig_hashtag_search?user_id={ig-user-id}&q={hashtag}`
- `GET /{version}/{hashtag-id}/recent_media?user_id={ig-user-id}&fields=...`
- `GET /{version}/{media-id}?fields=...`

실제 versioned HTTP response가 endpoint와 field capability의 source of truth이다. live 실행 전후에 현재 Meta 공식 hashtag reference를 다시 확인해야 한다.

## Probe 단계

### Phase 1: prerequisite preflight

- `META_IG_USER_ID`가 있으면 해당 object를 최소 `id` field로 읽을 수 있는지 확인한다.
- 없으면 `GET /me/accounts`에서 연결된 `instagram_business_account` ID를 찾는다.
- Page access token field를 요청하거나 출력하지 않는다.
- 연결 계정이 여러 개이면 자동 선택하지 않고 `META_IG_USER_ID` 지정을 요구한다.
- pagination은 Graph가 반환한 cursor만 다시 parameter로 전달하며 보고서에서는 cursor를 redaction한다.

### Phase 2~4: capability probe

각 capability는 독립된 request로 확인한다.

| Capability | 확인 내용 |
| --- | --- |
| `HASHTAG_LOOKUP` | hashtag text를 hashtag ID로 조회할 수 있는가 |
| `RECENT_MEDIA` | author field가 없는 baseline recent media를 조회할 수 있는가 |
| `MEDIA_USERNAME` | recent media response에서 `username`을 직접 받을 수 있는가 |
| `MEDIA_OWNER` | recent media response에서 `owner` identity를 직접 받을 수 있는가 |
| `FOLLOWUP_MEDIA_USERNAME` | 반환된 media ID의 follow-up GET에서 `username`을 받을 수 있는가 |
| `FOLLOWUP_MEDIA_OWNER` | 반환된 media ID의 follow-up GET에서 `owner` identity를 받을 수 있는가 |

각 결과는 `SUPPORTED`, `UNSUPPORTED`, `AUTH_BLOCKED`, `UNKNOWN` 중 하나이며 HTTP status와 sanitized Graph error를 함께 기록한다. 성공 응답에 media sample이 없으면 field capability를 단정하지 않고 `UNKNOWN`으로 둔다.

baseline field는 `id,caption,media_type,permalink,timestamp`이다. field-level unsupported error가 나면 `id`만으로 한 번 재시도해 metadata field 거부를 `recent_media` edge 거부로 오판하지 않는다. caption은 응답 shape 확인에만 사용하며 human summary나 JSON report에 저장하지 않는다.

## 환경 변수와 prerequisite

필수 환경 변수는 다음과 같다.

- `META_ACCESS_TOKEN`: 현재 Meta app에서 발급한 token이다.
- `META_GRAPH_API_VERSION`: `vXX.X` 형식으로 명시한다. script가 최신 version을 추측하지 않는다.

선택 환경 변수는 다음과 같다.

- `META_IG_USER_ID`: probe를 실행하는 연결 Professional Instagram Account ID이다.
- `DISCOVERY_HASHTAGS`: comma-separated hashtag이다. 없으면 `의사스타그램,약사스타그램,피부과` 세 개만 사용한다.

live 호출에는 최소한 다음 외부 prerequisite가 필요하다.

- Facebook Login 방식으로 구성된 Meta app
- 연결된 Facebook Page와 Instagram Business 또는 Creator account
- 해당 Page/account metadata와 public hashtag media를 읽을 수 있는 현재 version의 permission·feature·access level
- 위 app과 연결 관계에서 유효한 access token

Meta의 공식 collection에는 `pages_show_list`, `instagram_basic`, `pages_read_engagement` 등이 안내되어 있지만, hashtag-specific permission과 app access 조건은 version과 app 상태의 실제 Graph error로 다시 확인한다.

## 실행 방법

token이 shell history에 남지 않도록 prompt에서 입력한다.

```bash
read -rsp 'META_ACCESS_TOKEN: ' META_ACCESS_TOKEN
export META_ACCESS_TOKEN
export META_GRAPH_API_VERSION=vXX.X
export META_IG_USER_ID='<connected-professional-instagram-account-id>'
export DISCOVERY_HASHTAGS='의사스타그램,약사스타그램,피부과'
python3 scripts/instagram_native_discovery_probe.py
unset META_ACCESS_TOKEN
```

`META_IG_USER_ID`를 생략하면 연결 account 자동 탐색을 시도한다. 여러 account가 발견되면 명시적으로 하나를 지정해야 한다.

기본 JSON report는 다음 ignored 경로에 생성한다.

```text
agent_outputs/run_logs/YYYYMMDD_HHMMSS_instagram_discovery_probe.json
```

경로 지정과 human-only 실행은 다음과 같다.

```bash
python3 scripts/instagram_native_discovery_probe.py \
  --json-output agent_outputs/run_logs/manual_instagram_discovery_probe.json

python3 scripts/instagram_native_discovery_probe.py --no-json-report
```

기본 요청량은 hashtag 3개, capability call당 media 25개, follow-up media 3개이다. 한 실행의 hashtag는 최대 10개, candidate summary는 최대 15개로 제한한다. pagination으로 recent media를 추가 수집하지 않는다.

## 판정 기준

- `FEASIBLE`: 명시적 username을 서로 다른 여러 media에서 반복적으로 얻고 unique candidate를 둘 이상 만들 수 있다.
- `PARTIALLY_FEASIBLE`: username sample이 한정적이거나 owner identity만 있거나 media sample이 없어 coverage를 확정할 수 없다.
- `NOT_FEASIBLE_WITH_CURRENT_OFFICIAL_PATH`: hashtag media sample은 있으나 모든 독립 경로에서 author identity와 username을 얻지 못하거나 현재 versioned path가 unsupported로 확인된다.
- `PREREQUISITE_BLOCKED`: token, permission, app access, Page, Professional Account 또는 core request 실행 조건이 live probe를 막는다.
- `NOT_RUN`: 필수 환경 변수가 없어 HTTP request를 전혀 보내지 않았다.

후보 dedupe는 username 대소문자를 구분하지 않는다. report에는 처음 관찰한 표기, 발견 hashtag, unique source media 수만 저장한다.

종료 코드는 다음과 같다.

- `0`: live probe가 `FEASIBLE`, `PARTIALLY_FEASIBLE`, `NOT_FEASIBLE_WITH_CURRENT_OFFICIAL_PATH` 중 하나로 완료됐다.
- `1`: unexpected local error로 feasibility를 판정하지 못했다.
- `2`: 설정 누락으로 `NOT_RUN`이다.
- `3`: `PREREQUISITE_BLOCKED`이다.
- `4`: JSON report를 쓸 수 없다.

## Security와 데이터 최소화

- `Authorization: Bearer` header만 사용하며 `access_token` query parameter를 만들지 않는다.
- token, Authorization header, paging cursor를 stdout·stderr·JSON report에서 redaction한다.
- raw HTTP response, caption, permalink, Page access token을 report에 저장하지 않는다.
- live username은 ignored run log와 현재 process output에만 나타나며 committed fixture나 문서에 복사하지 않는다.
- JSON report file mode는 `0600`으로 설정한다.
- `.env`, token, app secret을 저장소에 만들거나 commit하지 않는다.

## 로컬 검증

외부 API를 unit test dependency로 사용하지 않는다. synthetic response만으로 URL, Bearer header, redaction, error classification, identity parsing, candidate dedupe를 확인한다.

```bash
python3 scripts/instagram_native_discovery_probe.py --help
python3 -m unittest -v scripts.test_instagram_native_discovery_probe
```

credentials가 없는 상태의 `NOT_RUN` 확인은 다음처럼 ignored report 대신 `/tmp`를 사용할 수 있다.

```bash
env -u META_ACCESS_TOKEN -u META_GRAPH_API_VERSION \
  python3 scripts/instagram_native_discovery_probe.py \
  --json-output /tmp/instagram_discovery_probe_not_run.json
```
