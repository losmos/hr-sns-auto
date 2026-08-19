# Handoff

## 마지막 갱신일

- 2026-08-19 22:58:45 KST

# 중단기 작업 기억

## 이번 범위

- 첫 실사용 가능한 Instagram Discovery Inbox vertical slice를 구현했다.
- 공식 Meta Graph API의 hashtag lookup과 recent media 첫 page를 운영자 수동 sync로 가져오며 hashtag당 최대 25개로 제한한다.
- Discovery item은 아직 Candidate가 아니다. 작성자 username 입력, Candidate 생성·연결, username history와 stable Meta identity는 다음 vertical slice로 남겼다.
- Candidate domain은 수정하지 않았고 후보 목록에 Discovery Inbox 진입 링크만 추가했다.

## 사용자 live Meta 검증 결과

- Facebook Page와 Instagram Creator 계정 연결이 정상 확인됐다.
- 사용자 검증 시점 Graph API version은 `v26.0`이었다. production 코드는 version을 추측하거나 하드코딩하지 않는다.
- 자기 Professional account의 `id`, `username`, `name`, `followers_count`, `media_count` 조회가 성공했다.
- `HASHTAG_LOOKUP`과 `RECENT_MEDIA`가 성공했고 실제 recent media 27건을 얻은 실행이 있었다.
- Hashtag recent media에서 제품에 사용할 수 있다고 확인한 field는 `id`, `caption`, `media_type`, `permalink`, `timestamp`이다.
- `username`·`owner` 직접 field 요청은 unsupported field 계열 오류였고 media ID follow-up은 permission/object access 계열 오류였다.
- 현재 공식 경로에서 hashtag media 작성자 username을 자동 식별한다고 가정하지 않는다.
- 외부 username Business Discovery는 User Access Token과 Page Access Token 모두 현재 app에서 `(#10) Application does not have permission for this action`으로 실패했다.
- Business Discovery는 Candidate 등록의 필수 dependency가 아니라 optional enrichment이다.
- 특정 IG User ID와 access token은 source, DB, 로그, 문서에 기록하지 않았다.

## 구현 구조

- Flyway `V4__create_instagram_discovery_inbox.sql`이 hashtag, item, item-hashtag association을 만든다.
- 기본 hashtag `의사스타그램`, `약사스타그램`, `피부과`를 모두 활성 상태로 seed한다.
- Hashtag 입력은 trim, leading `#` 제거, `Locale.ROOT` 소문자 정규화를 수행한다. 애플리케이션 검사와 DB normalized unique 제약을 함께 사용한다.
- Media는 Instagram media ID로 upsert한다. 재수집 시 duplicate row를 만들지 않고 `lastSeenAt`, metadata, source association만 갱신하며 기존 review 상태를 유지한다.
- Caption은 Unicode code point 기준 최대 500자 excerpt만 저장한다. raw Graph response와 Instagram media binary는 저장하지 않는다.
- 같은 media가 여러 hashtag에서 발견되면 item row 하나와 여러 association으로 보존한다.
- Review 상태는 `NEW`, `OPENED`, `DISMISSED`만 사용한다. redirect endpoint로 원문을 열 때 `NEW → OPENED`를 기록하고, `DISMISSED`는 링크 재열기로 되돌리지 않는다.
- 한 hashtag Graph 요청 실패는 sanitized 실패 결과로 기록하고 다른 hashtag의 정상 observation은 유지한다. DB 제약 위반은 전체 transaction을 rollback해 무결성을 우선한다.

## Meta client와 보안

- 환경변수 `META_ACCESS_TOKEN`, `META_GRAPH_API_VERSION`, `META_IG_USER_ID`를 application property에 연결했다.
- credential이 없거나 version 형식이 잘못돼도 애플리케이션은 기동할 수 있고, sync 시점에만 안전한 설정 오류를 표시한다.
- token은 `Authorization: Bearer` header에만 넣고 query parameter에 넣지 않는다.
- HTTP status와 Meta error `code`, `type`, `message`만 운영자 오류에 남긴다. token, raw payload, trace field, paging cursor는 저장하거나 UI에 노출하지 않는다.
- 첫 page만 읽고 response의 paging cursor를 따라가지 않는다.
- permalink는 Instagram HTTPS host만 허용하며 scraping, browser automation, username parsing, Business Discovery를 호출하지 않는다.

# 직전 작업 기억

## MetaInstagramClient Spring bean 수정

- `MetaInstagramClient`에 production constructor와 package-private synthetic test constructor가 함께 있지만 주입 대상 constructor가 표시되지 않아 Spring Framework 7.0.8이 기본 constructor를 찾다가 bean 생성을 실패했다.
- `MetaInstagramProperties`를 받는 production constructor에만 `@Autowired`를 명시했다. 의미 없는 no-arg constructor는 추가하지 않았고 기존 synthetic transport seam은 유지했다.
- Meta credential은 여전히 bean 생성 시 검증하지 않는다. 빈 설정으로 Spring bean을 만든 뒤 실제 sync 진입점의 `validateConfiguration()`에서 환경변수 이름만 포함한 안전한 설정 오류를 반환한다.

## 새 route와 UI

- `GET /discovery`: hashtag 설정, 상태 count, filter, 최신 게시물 inbox를 표시한다.
- `POST /discovery/hashtags`: hashtag를 추가한다.
- `POST /discovery/hashtags/{id}/enable`, `/disable`: history 삭제 없이 상태를 전환한다.
- `POST /discovery/sync`: 활성 hashtag를 수동 sync하고 hashtag별 성공·실패 summary를 flash로 표시한다.
- `GET /discovery/items/{id}/open`: `OPENED`를 기록한 뒤 저장된 Instagram permalink로 redirect한다.
- `POST /discovery/items/{id}/dismiss`: `DISMISSED`로 전환한다.
- 전체·`NEW`·`OPENED`·`DISMISSED` filter와 각 상태 count를 제공하며 전체 보기에서는 `NEW`를 먼저, 같은 상태에서는 publishedAt 최신순으로 정렬한다.

## 테스트 추가

- Discovery Inbox 신규 테스트는 19개이다.
- Meta synthetic test에 빈 credential로 실제 Spring bean을 생성하고 검증을 사용 시점까지 미루는 constructor regression test를 추가했다.
- Meta synthetic test는 versioned URL, query, Bearer header, token query 배제, lookup/recent parsing, `+0000` timestamp, malformed response, Instagram permalink validation, Graph error field 제한, token redaction, config 누락을 검증한다.
- Hashtag service test는 normalization, case-insensitive duplicate 방지, 빈 값 거부, disable·reenable을 검증한다.
- PostgreSQL persistence test는 V4 기본 hashtag, 반복 media idempotency, `lastSeenAt`, caption 500자, 다중 source association, review 상태, hashtag별 partial API failure를 검증한다.
- MVC test는 page rendering, hashtag add·enable·disable, manual sync summary, 안전한 config 오류, open redirect, dismiss를 검증한다.

## 검증 상태

- `docker compose up -d postgres`: Docker socket 권한 거부로 실행하지 못했다.
- `docker compose ps`: 같은 Docker socket 권한 거부로 health를 확인하지 못했다.
- constructor regression test는 수정 전 `No default constructor found`를 재현했고 수정 후 `MetaInstagramClientTest` 6개가 모두 통과했다.
- `./mvnw -Dtest=MetaInstagramClientTest,DiscoveryHashtagServiceTest,DiscoveryControllerTest,CandidateEvidenceTest,EligibilityPolicyTest,CandidateServiceTest,CandidateControllerTest test`: DB 비의존 46개가 모두 통과했다.
- `./mvnw test`: 총 56개 중 failures 0, PostgreSQL 연결이 필요한 기존 7개와 신규 3개가 sandbox의 localhost TCP 차단(`SocketException: Operation not permitted`)으로 errors로 끝났다.
- `./mvnw package`: test 단계에서 동일한 PostgreSQL 연결 errors 10개로 실패했다.
- `./mvnw -DskipTests package`: 실행 가능한 Spring Boot JAR 생성에 성공했다.
- `git diff --check`: 통과했다.
- 사용자 환경에서 PostgreSQL을 사용할 수 있으므로 전체 test와 package 재검증이 남았다.
- 실제 credential이 이 workspace에 없으므로 production Java client로 live Meta 호출은 수행하지 않았다.

## 변경 파일

- `src/main/java/com/losmos/hrsnsauto/discovery/`: hashtag·item domain, repositories, Meta client/config, sync service, MVC controller와 form/result model을 추가했다.
- `src/main/resources/db/migration/V4__create_instagram_discovery_inbox.sql`: Discovery schema와 기본 hashtag를 추가했다.
- `src/main/resources/templates/discovery/index.html`: Discovery Inbox UI를 추가했다.
- `src/main/resources/static/css/app.css`: 기존 UI 스타일을 확장했다.
- `src/main/resources/templates/candidates/list.html`: Discovery Inbox 진입 링크를 추가했다.
- `src/main/resources/application.properties`: 세 Meta 환경변수 mapping을 추가했다.
- `src/test/java/com/losmos/hrsnsauto/discovery/`: synthetic client, service, persistence, MVC 테스트를 추가했다.
- `src/test/java/com/losmos/hrsnsauto/HrSnsAutoApplicationTests.java`: 세 Meta 설정을 명시적으로 비운 상태에서 full context를 검증하도록 고정했다.
- `docs/harness/PROJECT_CONTEXT.md`, `docs/harness/HANDOFF.md`, `docs/harness/DIRECTORY_MAP.md`: live 검증 사실, 제품 결정, 구현 구조와 다음 작업을 반영했다.

## 작업 전 파일 보존

- 작업 시작 전 존재한 미추적 `prompts/tasks/implement_discovery_inbox_vertical_slice.md`는 수정하지 않았다.
- clarification request는 만들지 않았다. 이번 요청을 막는 P0 blocker는 발견되지 않았다.

## 다음 추천 작업 상세

1. 사용자 환경에서 PostgreSQL을 시작하고 전체 56개 테스트와 V4 migration, credential 없는 `contextLoads()`, `./mvnw package`를 검증한다.
2. 실제 credential을 process 환경변수로만 설정해 `/discovery`에서 기본 hashtag와 추가 hashtag의 manual sync를 확인한다.
3. 실제 item 재sync가 duplicate를 만들지 않고 hashtag association과 `lastSeenAt`을 갱신하는지 확인한다.
4. 다음 vertical slice로 `Discovery item → 운영자가 author username 입력 → Candidate 연결`을 구현한다.
5. 다음 slice에서도 caption·permalink에서 username을 파싱하지 않고 운영자 확인값을 source로 사용한다.

## 이전 추천 작업과의 관계

- 이전 Handoff의 live probe 추천 작업은 사용자의 실제 환경 검증으로 완료됐다.
- 이전의 `NOT_RUN`, author identity 미검증, hashtag feasibility 미확정 상태는 더 이상 유효하지 않아 사용자 live 결과로 교체했다.
- 기존 Candidate sample evidence 입력·상충 사유 운영 검증은 이번 최신 Discovery Inbox 요청보다 우선하지 않아 미수행 상태로 남긴다.

## 사용 에이전트

- Codex를 사용했다.

## 주의할 점

- `v26.0`은 사용자 live 검증 당시 version일 뿐 production 기본값이 아니다.
- Hashtag media의 author identity가 없으므로 Discovery item을 Candidate로 자동 생성하지 않는다.
- 일반 Consumer/Personal account도 제품 대상에서 제외하지 않지만 공식 API enrichment 가능성을 가정하지 않는다.
- local thin slice에 인증이 없으므로 외부 network에 노출하지 않는다.
