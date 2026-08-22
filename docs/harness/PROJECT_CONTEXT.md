# Project Context

## 프로젝트 목적

- `hr-sns-auto`는 압구정에서 모발이식병원을 운영하는 석지웅 원장의 개인 브랜딩과 의료계 네트워킹 업무를 지원한다.
- Instagram에서 다른 의료 섹터의 의사·약사와 자연스럽게 교류하고 장기적인 관계를 형성하도록 후보 발굴, 적합성 검증, 콘텐츠 분석, 댓글·DM 초안, 승인 업무를 지원한다.
- 무작위 광고 발송이나 대량 spam 자동화를 목적으로 하지 않는다.

## 배경

- 발신자는 석지웅 원장이다.
- 주 사용자는 병원 직원인 운영자이며 최종 승인자 역할을 한다.
- 시스템이 매일 적합한 신규 후보를 제시하고, 운영자가 근거와 문안을 검토한 뒤 외부 행동 여부를 결정하는 흐름이다.
- 첫 release에서 시스템은 외부 Instagram action을 실행하지 않고 운영자의 manual execution을 지원한다.
- 2026-08-16에 범용 하네스를 새 프로젝트용으로 초기화했고, 2026-08-17에 첫 요구사항 분석·공식 Instagram API 가능성 조사와 사용자 Q1~Q8 결정 반영을 수행했다.
- 2026-08-17에 Spring Boot 애플리케이션 skeleton과 PostgreSQL baseline을 확인하고 첫 Candidate → Evidence → Eligibility thin vertical slice를 구현했다.
- 2026-08-17에 최초 후보 발굴은 외부 directory보다 Instagram-native signal을 우선한다는 방향을 확정하고, 공식 hashtag media author identity capability를 실제 응답으로 확인하기 위한 독립 Python probe를 추가했다.
- 2026-08-19에 연결된 Facebook Page와 Instagram Creator 계정으로 live probe를 완료했다. Hashtag lookup과 recent media는 성공했지만 hashtag media의 작성자 identity는 공식 응답에서 얻지 못했고, 외부 Business Discovery도 현재 app 권한에서 차단됨을 확인했다.
- 2026-08-19에 hashtag media를 Candidate로 바로 만들지 않고 permalink를 운영자가 검토하는 Instagram Discovery Inbox vertical slice를 구현했다.
- 2026-08-19에 공식 API가 주지 않는 공개 post author/profile 선별 정보를 보강하기 위해 운영자가 명시적으로 실행하는 local Playwright browser enrichment vertical slice를 구현했다.

## 목표

- 현재 우선순위는 Instagram browser enrichment를 실제 headed Chromium session으로 smoke 검증한 뒤 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity` vertical slice를 구현하는 것이다.
- Discovery가 생성하는 raw 후보는 후속 eligibility 검토 전의 lead이다. Instagram 활동성과 의료계 네트워킹 가능성을 우선하며 의료직군 false positive를 일정 범위 허용한다.
- 매일 모든 필수 eligibility 검증을 통과한 `ELIGIBLE` 신규 Instagram 후보를 운영자가 검토할 수 있도록 최대 15명 제시한다. 15명은 quota가 아니다.
- 실제 의사·약사 여부, 한의 계열 여부, 모발이식 관련 여부, follower 10,000 미만 여부, 최근 활동 여부를 독립적으로 판정하고 사람이 확인할 근거를 남긴다.
- 후보의 실제 최근 콘텐츠에 grounded된 댓글과 DM 초안을 만든다.
- 중복·과잉 접근을 interaction history와 cooldown으로 방지한다.
- 후보 품질, 문안 승인·수정, 관계 형성 성과를 측정할 수 있게 한다.
- 모발이식 계열 false negative는 매우 심각한 오류로 취급하고 0건을 목표로 한다.

## 제약사항

- 저장소 작업은 루트 `AGENTS.md`의 규칙을 따른다.
- Instagram background/bulk scraping, 무한 crawling, 검색·follower/following 목록 순회와 private endpoint 호출을 전제로 설계하지 않는다. 다만 운영자가 Discovery Inbox의 기존 permalink를 대상으로 명시적으로 실행하는 local Playwright read-only browser enrichment는 허용한다.
- 최초 discovery는 공식 Meta/Instagram API의 hashtag 등 Instagram-native signal을 우선한다. 외부 의료기관 홈페이지나 의료인 directory를 사람을 처음 찾기 위한 필수 source로 두지 않으며, 후속 profession·identity evidence 보강에는 허용된 공개 source를 사용할 수 있다.
- discovery entry에서는 제한적인 의료직군 false positive를 허용하지만 strict profession·identity·hair-transplant·follower evidence와 기존 EligibilityPolicy는 후속 eligibility/review 안전장치로 유지한다.
- 공식 문서로 확인하지 못한 기능은 구현 가능하다고 확정하지 않는다.
- 의사·약사만 대상이며 한의사·한의원·한방병원, 비의료인, 개인을 특정할 수 없는 기관 계정, follower 10,000 이상은 hard exclude이다.
- 모발이식, 탈모수술, 헤어라인 교정, hair transplant, hair restoration surgery, FUE·FUT 중심 계정과 실제 서비스에서 모발이식을 주요 업무로 하는 계정은 hard exclude이다.
- 모발이식 여부가 불명확하거나 필수 evidence가 부족하면 `ELIGIBLE`로 통과시키지 않고 `REVIEW_REQUIRED`로 보낸다.
- generation과 external execution을 분리하고, execution은 provider capability와 사람 승인 상태를 별도로 검사한다.
- Browser automation은 공개 post author/profile screening metadata 읽기와 profile navigation에만 사용한다. follow, like, comment, DM 등 external Instagram action 실행에는 사용하지 않는다.
- Instagram 원본 media를 기본 저장하지 않고 공개 전문 정보·permalink·구조화 사실·최소 excerpt·관찰 시점을 중심으로 저장한다.
- API capability, permission, rate limit은 구현 전에 고정 API version과 실제 계정 조건으로 재검증한다.
- 공식 Instagram API 가능 범위를 우선 사용한다. 2026-08-19 live probe에서 hashtag media author identity와 외부 username enrichment 한계를 확인했으므로, 부족한 공개 author/profile 정보에 한해 운영자 명시 실행 browser enrichment를 보조 경로로 사용한다.
- Meta Business Discovery와 특정 Search API는 MVP 필수 dependency로 두지 않는다.
- Instagram Professional/Personal account type만으로 후보를 제외하지 않는다. 공식 API에서 stable Meta identity를 얻지 못해도 향후 내부 Candidate ID와 운영자가 확인한 username/history를 기준으로 관리하고, Meta ID나 IGSID는 얻을 수 있을 때 추가 연결한다.
- Meta Graph API version, 연결 Instagram User ID, access token은 각각 `META_GRAPH_API_VERSION`, `META_IG_USER_ID`, `META_ACCESS_TOKEN` 환경변수에서만 받는다. token은 query parameter, source, DB, 로그, 문서, UI에 기록하지 않는다.
- Local primary launcher는 gitignored `.env.local`의 non-secret 설정만 환경변수로 변환한다. 대화형 macOS에서는 `META_ACCESS_TOKEN`을 Keychain service `hr-sns-auto-meta-access-token`, process environment, hidden terminal prompt 순서로 받고, 비대화형 automation에서는 명시적 process environment만 사용한다. `.env.local`에는 token을 저장하지 않는다.
- Instagram username/password는 애플리케이션이 입력받거나 저장하지 않는다. Playwright persistent profile은 기본 `.local/instagram-browser-profile/`에 두고 `INSTAGRAM_BROWSER_USER_DATA_DIR`로 override할 수 있으며 cookie/local storage가 있을 수 있어 git, DB, 로그, fixture, report에 포함하지 않는다.
- CAPTCHA, challenge, checkpoint, rate limit과 anti-bot control을 우회하지 않는다. stealth plugin, fingerprint spoofing, proxy rotation, random human-like timing을 구현하지 않는다.
- 확정 기술 스택은 Java 21, Spring Boot 4.1.0, Spring MVC, Thymeleaf, Spring Data JPA, PostgreSQL 18.4, Flyway, Docker Compose, Maven Wrapper이다.
- local thin slice에는 Spring Security와 로그인을 구현하지 않는다. 외부 네트워크 배포 또는 실제 운영 전에 named operator 인증과 권한을 반드시 결정하고 구현해야 한다.

## 확정된 사실

- 프로젝트 이름과 slug는 모두 `hr-sns-auto`이다.
- 운영자는 병원 직원이고 최종 승인자이다.
- 후보 분류 상태는 `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED`를 사용하도록 설계한다.
- 후보 판정마다 source URL, 관찰값, 관찰일 등 사람이 확인할 evidence가 필요하다.
- 실제 Instagram 로그인, follow, like, comment, DM 전송은 2026-08-17 분석 작업 범위에서 수행하지 않았다.
- Spring Boot 애플리케이션 skeleton, Maven Wrapper, Docker Compose PostgreSQL, Flyway baseline이 존재한다.
- baseline에서 `./mvnw test`, PostgreSQL health, Spring Boot 연결, Flyway migration, JPA 초기화 성공이 확인됐다.
- 사용자의 실제 개발 환경에서 V3 기준 `./mvnw test` 37개 전체 통과, `./mvnw package` 성공, Flyway schema version 3과 `success = true`가 확인됐다.
- Candidate와 CandidateEvidence 영속성, deterministic EligibilityPolicy, 수동 입력·목록·상세 Thymeleaf UI가 첫 thin vertical slice로 구현됐다.
- Facebook Page와 Instagram Creator 계정의 연결이 live 환경에서 정상 확인됐다.
- 사용자 live 검증 당시 Graph API version은 `v26.0`이었으나 현재 version을 코드에서 추측하거나 고정하지 않는다.
- Local launcher의 startup token validation은 configured Instagram User object를 `fields=id`로 읽고 응답 ID가 `META_IG_USER_ID`와 일치할 때만 성공한다. OAuth error `code 190`만 invalid/expired replacement 대상으로 분류하고 다른 API·network·응답 오류에서는 기존 Keychain 값을 보존한다.
- 연결된 자기 Professional account에서 `id`, `username`, `name`, `followers_count`, `media_count` 조회가 성공했다. 실제 account ID와 access token은 source와 문서에 기록하지 않는다.
- 2026-08-17 기준 Business Discovery는 이미 알고 있는 username의 공개 Business·Creator metadata와 일부 media를 조회하는 검증 기능이며 조건 기반 account search가 아니다.
- Live probe에서 `HASHTAG_LOOKUP`과 `RECENT_MEDIA`가 성공했고 실제 recent media 27건을 얻은 실행이 있었다. 확인된 제품 사용 가능 field는 media `id`, `caption`, `media_type`, `permalink`, `timestamp`이다.
- Hashtag recent media의 `username`·`owner` 직접 요청은 unsupported field 계열 오류였고, media ID follow-up 조회는 permission/object access 계열 오류였다. 따라서 현재 공식 경로의 hashtag media에서 author username이나 owner를 production이 자동 식별한다고 가정하지 않는다.
- 외부 username Business Discovery는 현재 app에서 User Access Token과 Page Access Token 모두 `(#10) Application does not have permission for this action`으로 실패했다. 일반 Consumer/Personal account를 임의 username으로 공식 API에서 enrichment할 수 있다고 가정하지 않는다.
- `scripts/instagram_native_discovery_probe.py`는 versioned hashtag lookup, baseline recent media, direct `username`·`owner`, media follow-up `username`·`owner`를 독립적으로 호출하고 실제 API response만으로 capability를 판정한다.
- Flyway V4와 `discovery` package에 hashtag 설정, Meta Graph client, idempotent recent media 수집, 다중 hashtag association, `NEW`·`OPENED`·`DISMISSED` 검토 상태, Spring MVC/Thymeleaf Discovery Inbox가 구현됐다.
- Microsoft Playwright Java `1.61.0`, persistent Chromium context, final URL/page 분류, semantic article 우선·지원 post URL 한정 main fallback extractor, localized metric parser와 단일 실행 lock이 구현됐다. 기본 browser automation은 disabled이고 headed mode이며 batch size는 기본 10, 허용 범위 1~15이다.
- macOS headed Playwright live navigation에서 Reel permalink가 `/reel/{shortcode}/` 요청 후 `/reels/{shortcode}/` final URL에 도달할 수 있음이 확인됐다. 두 route는 동일 shortcode일 때 같은 canonical Reel post identity로 처리하고 `reels`는 profile username 예약 경로로 계속 제외한다.
- macOS headed Playwright의 실제 Reel main에서 author href가 `/{username}/reels/` 형태임이 확인됐다. Profile parser는 canonical `/{username}/`와 정확한 2-segment `/{username}/reels/`를 같은 username으로 처리하고, 후속 navigation URL은 `https://www.instagram.com/{username}/`로 canonicalize한다. `/reels/{shortcode}/`와 `/reels/audio/...`는 profile route가 아니다.
- Flyway V5 `discovery_browser_observations`는 Discovery item당 최신 browser observation 1개를 저장한다. author username/display name/profile URL, follower/following/post count, biography 최대 300자 excerpt, verified/private 여부와 화면에 있을 때만 post like/comment/view count를 저장하며 raw HTML, screenshot, media binary, cookie는 저장하지 않는다.
- Browser observation 상태는 `SUCCESS`, `PARTIAL`, `LOGIN_REQUIRED`, `ACTION_REQUIRED`, `FAILED`이다. 로그인·challenge/checkpoint는 우회하지 않고 batch를 중단하며, 다른 item의 성공 observation은 독립 transaction으로 유지한다.
- 2026-08-17 기준 공식 API는 타 계정 게시물에 새 댓글 작성, 게시물 좋아요, 계정 follow, 선제 cold DM을 지원하지 않는다.
- 2026-08-17 기준 Messaging API는 상대의 선행 메시지가 필요하며, commenter private reply도 자사 media에 상대가 댓글을 남긴 경우에 한정된다.
- 위 API 사실은 시간에 따라 변경될 수 있으므로 구현 또는 외부 실행 범위 변경 전에 공식 문서를 다시 확인해야 한다.
- `docs/harness/PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이고 `docs/harness/HANDOFF.md`는 중단기·직전 작업 기억을 관리한다.

## 결정 사항

- `DEC-20260817-no-unauthorized-instagram-collection` (`DEC-20260819-operator-triggered-browser-enrichment`로 부분 superseded): background/bulk Instagram scraping, private endpoint, 무한 crawling과 목록 순회는 계속 금지한다. “웹 UI read 전체 금지” 부분은 운영자가 기존 Discovery permalink에 대해 명시적으로 실행하는 제한된 local read-only enrichment를 허용하도록 변경됐다.
- `DEC-20260817-separate-generation-execution`: 후보 발굴·판정·콘텐츠 분석·문안 생성 영역과 실제 Instagram action 실행 영역을 분리한다.
- `DEC-20260817-daily-eligible-candidate-cap`: 일일 목표는 필수 검증을 모두 통과한 운영자 검토 가능 신규 `ELIGIBLE` 후보 최대 15명이다. 15명 미달을 허용하며 숫자를 위해 기준을 낮추지 않는다.
- `DEC-20260817-initial-target-market`: 초기 대상은 대한민국의 한국어 Instagram 계정이며 모발이식 분야를 제외한 의사·약사를 폭넓게 다룬다. 초기 진료과 quota는 두지 않는다.
- `DEC-20260817-instagram-native-discovery-first`: 최초 discovery는 Instagram hashtag 등 플랫폼 내부 활동 signal을 우선한다. 외부 의료기관·의료인 directory는 최초 discovery의 필수 source가 아니며, raw 후보 단계에서는 한국 의료계열·개인 또는 전문직 중심·적정 follower·비경쟁 영역으로 보이는 활동 계정을 폭넓게 찾아 제한적인 profession false positive를 허용한다. 실제 가치는 SNS 활동성과 의료계 네트워킹 가능성으로 보고, strict profession evidence와 기존 EligibilityPolicy는 discovery entry 조건이 아니라 후속 eligibility/review 안전장치로 적용한다. 공식 API 가능 범위를 먼저 live 확인한다는 조건은 2026-08-19 결과로 충족됐고 부족한 정보의 browser 보강은 `DEC-20260819-operator-triggered-browser-enrichment`를 따른다.
- `DEC-20260817-profession-identity-evidence`: Instagram bio·category만으로 의사·약사를 확정하지 않는다. 강한 공개 근거 1개와 Instagram identity 일치 근거를 요구하고, 강한 단일 근거가 없으면 독립적인 공개 source 2개 이상을 검토한다. 부족·상충 근거는 `REVIEW_REQUIRED`이다.
- `DEC-20260817-hair-ambiguity-review`: `HAIR_TRANSPLANT` 공개 evidence는 `SUPPORTS_NOT_RELATED`, `SUPPORTS_RELATED`, `INCONCLUSIVE` 방향을 명시한다. `NOT_RELATED` gate에는 `SUPPORTS_NOT_RELATED` evidence만 사용하며 유효한 source URL이 있는 strong 1개 또는 서로 다른 URL의 weak 2개 이상을 요구한다. `NOT_RELATED`와 `SUPPORTS_RELATED` evidence가 상충하면 `REVIEW_REQUIRED`이고, 기준 미충족·`INCONCLUSIVE`·`UNKNOWN`도 통과시키지 않으며 `RELATED`는 hard exclude로 분류한다. 방향성이 없던 기존 `HAIR_TRANSPLANT` evidence는 migration에서 `INCONCLUSIVE`로 이관하고 저장된 eligibility 상태를 일괄 재계산하지 않는다.
- `DEC-20260817-recent-activity-ranking`: 최근 30일 활동을 우선하고 30일 초과만으로 제외하지 않는다. 90일 초과는 낮은 우선순위 또는 `REVIEW_REQUIRED`로 취급할 수 있으며 최근 활동은 기본적으로 ranking 요소이지 hard exclude가 아니다.
- `DEC-20260817-first-release-mode`: 첫 release는 `APPROVAL_REQUIRED + MANUAL_EXECUTION`이다. 시스템은 후보·evidence·eligibility·content 분석·comment/DM draft·독립 승인·결과 기록을 담당하고 운영자가 Instagram action을 직접 수행한다.
- `DEC-20260817-outreach-sequencing`: 후보당 하루 신규 outbound action은 최대 하나이다. 실제 content interaction을 먼저 검토하고 DM은 다른 시점의 별도 action·별도 approval로 다룬다.
- `DEC-20260817-cooldown-suppression`: cold DM 무응답이면 같은 목적으로 재발송하지 않고 동일 post comment는 한 번만 허용한다. 후보 단위 cooldown 기본값은 30일이며 거절·연락 중단 요청·차단은 permanent suppression이다. 변경은 새 `PolicyVersion`으로 관리한다.
- `DEC-20260817-browser-automation-mvp-exclusion` (2026-08-19 superseded): 당시 MVP에서 Playwright·Selenium browser automation을 제외한 결정이다. 최신 범위는 `DEC-20260819-operator-triggered-browser-enrichment`가 대체한다.
- `DEC-20260819-operator-triggered-browser-enrichment`: API Discovery를 계속 우선 사용하되 API에서 얻을 수 없는 공개 post author/profile screening metadata는 운영자가 버튼으로 명시 실행한 local Playwright persistent Chromium에서 보강한다. 출발점은 Inbox에 이미 저장된 permalink이고 단건 또는 observation 없는 최신 `NEW` item 최대 10건 기본·15건 상한을 순차 처리한다. background scheduler, bulk/list crawling, private endpoint, credential·cookie·raw HTML·screenshot·binary 저장, stealth·fingerprint·proxy·CAPTCHA/challenge/rate-limit 우회와 follow·like·comment·DM 자동화는 금지한다.
- `DEC-20260817-meta-read-integration-optional`: Meta Business Discovery는 MVP blocker나 필수 기능이 아니다. 연결 Creator account와 Page는 확인됐지만 현재 app의 User token·Page token 모두 외부 username 조회가 `#10` permission failure이므로, 향후 permission과 대상 account 조건이 맞을 때 read-only optional enrichment로만 검토한다.
- `DEC-20260819-semi-manual-instagram-discovery-inbox`: 공식 hashtag lookup과 recent media를 media discovery source로 사용하되 author identity를 추론하지 않는다. hashtag media → 실제 permalink → 운영자가 author를 확인하는 반자동 Inbox를 사용하며 Discovery item 자체는 Candidate가 아니다.
- `DEC-20260819-candidate-identity-without-meta-id`: Professional/Personal 여부와 관계없이 사람이 확인한 일반 Instagram account도 제품 대상에서 제외하지 않는다. stable Meta ID가 없는 후보는 향후 내부 Candidate ID와 username/history로 관리하고 stronger Meta identity는 얻을 수 있을 때 연결한다.
- `DEC-20260819-discovery-inbox-v1-boundary`: 첫 Discovery Inbox는 활성 hashtag별 첫 page 최대 25개를 운영자의 수동 sync로만 가져온다. media ID로 idempotent upsert하고 다중 hashtag source를 보존하며 caption은 최대 500자 excerpt만 저장한다. scheduler, pagination, raw response 저장, media binary 저장, author 추론, Candidate 연결은 포함하지 않는다.
- `DEC-20260819-meta-credential-boundary`: Meta access token, API version, IG User ID는 환경변수에서만 받고 version과 ID를 production source에 하드코딩하지 않는다. token은 Bearer header에만 사용하고 query, DB, 로그, 문서, UI에 노출하지 않는다.
- `DEC-20260820-local-config-secret-boundary`: local 실행은 `./scripts/run-local.sh`를 primary 경로로 사용한다. non-secret 값만 gitignored `.env.local`에 두고 애플리케이션 환경변수로 변환하며, Meta access token은 `.env.local`에 저장하지 않고 프로젝트 전용 macOS Keychain, 명시적 process environment, hidden prompt 중 하나에서 주입한다. PostgreSQL data와 persistent browser profile은 실행 종료 시 유지한다.
- `DEC-20260823-local-meta-token-lifecycle`: 일상적인 대화형 macOS 실행은 Keychain을 process environment보다 우선해 과거 shell export가 persistent token을 가리지 않게 한다. 모든 source는 Spring Boot 시작 전에 configured IG User의 최소 read endpoint로 검증하며 OAuth `code 190`만 hidden replacement 흐름으로 보낸다. 새 token은 최대 3회 검증하고 valid일 때만 `security add-generic-password ... -U`로 저장한다. permission, rate limit, network와 기타 오류는 replacement로 보내거나 Keychain 값을 삭제하지 않는다. `debug_token`, App Secret, 만료 예정일, token exchange는 이 범위에 포함하지 않는다.
- `DEC-20260817-public-data-minimization`: username, permalink, 구조화 사실, 판정 evidence, 필요한 최소 excerpt, 관찰 시점을 중심으로 저장하고 Instagram 원본 media를 기본 보관하지 않는다. 외부 AI provider 전달도 생성 목적의 최소 범위로 제한한다.
- `DEC-20260817-task-prompts-versioned`: secret·민감 정보를 제외한 `prompts/tasks/*.md`는 작업 의도와 재현성을 위한 프로젝트 기록으로 기본 Git commit 대상이다.
- `DEC-20260817-application-stack`: Java 21과 Spring Boot 4.1.0 기반 Spring MVC/Thymeleaf 애플리케이션, Spring Data JPA, PostgreSQL 18.4, Flyway, Docker Compose, Maven Wrapper를 현재 기술 스택으로 사용한다.
- `DEC-20260817-thin-vertical-slice-first`: 정식 제품 구조를 먼저 확장하지 않고 Candidate 수동 입력 → Evidence 입력 → deterministic Eligibility 판정 → 목록·상세 UI의 실제 업무 가치를 먼저 검증한다.
- `DEC-20260817-local-auth-deferred`: local thin slice의 기능 가치 검증에는 Spring Security와 로그인을 넣지 않는다. 이는 shared anonymous production access 허용 결정이 아니며 외부 배포·실제 운영 전에 인증과 권한을 구현한다.
- 구체 evidence TTL, approval TTL, 90일 초과 후보의 두 허용 처리 중 하나, 보유 기간과 실제 AI·Search provider는 아직 확정 Decision이 아니다.

## 미확정 질문

- `P1 Investigation`: 외부 네트워크 배포 또는 실제 운영 전 named operator 인증 방식과 최소 role을 확정해야 한다.
- `P1 Investigation`: follower evidence TTL·임계값 인접 재확인 범위와 approval TTL을 정해야 한다.
- `P1 Investigation`: 공개 profile·content의 구체 보유·삭제 기간과 실제 AI provider의 학습·보유·subprocessor 조건을 정해야 한다.
- `P1 Investigation`: 특정 Search API를 사용하려면 공식 이용조건, 가격, 신규 이용 가능성, query quality와 저장 제한을 비교해야 한다.
- `P2 Non-blocking`: 90일 초과 비활성 후보를 낮은 ranking의 `ELIGIBLE`로 유지할지 `REVIEW_REQUIRED`로 보낼지 선택해야 한다.
- `P1 Investigation`: Browser observation의 author username을 기존 Candidate에 연결하거나 새 Candidate로 만드는 identity·중복 처리 흐름과 username history를 설계해야 한다.
- `P1 Investigation`: Meta ID 또는 IGSID를 나중에 얻었을 때 내부 Candidate identity와 안전하게 병합하는 규칙, username history의 유효기간과 충돌 처리를 정해야 한다.
- 현재 상세 설계 문서 완료를 막는 blocker는 없다.

## 참고 산출물

- 요구사항·기술 가능성·초기 아키텍처 보고서: `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- MVP 상세 설계·구현 계획: `agent_outputs/reports/mvp_implementation_plan.md`
- 답변 완료된 과거 질문 기록: `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`
- 과거 구현 전 결정 질문지: `agent_outputs/clarification_requests/20260817_005758_mvp_implementation_decisions.md`의 기술 스택·local 인증 P0는 2026-08-17 최신 사용자 방향으로 해소됐다.
- 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`에 저장한다.
- 실행 로그는 `agent_outputs/run_logs/`, 사용자 답변이 필요한 질문지는 `agent_outputs/clarification_requests/`에 저장한다.

## 다음 작업 기준

- 실제 환경에서 Playwright Chromium을 설치하고 `INSTAGRAM_BROWSER_AUTOMATION_ENABLED=true`로 headed persistent session, 사람 로그인, 단건 1개와 최대 3개 추가 item의 공개 profile 추출 및 V5 migration을 smoke 검증한다. challenge/checkpoint가 표시되면 즉시 중단한다.
- 다음 vertical slice는 `DiscoveryBrowserObservation → Candidate 연결 + username/history identity`이다. Business Discovery 필수화와 Candidate identity 전체 재설계는 포함하지 않는다.
- 첫 thin vertical slice는 실제 운영자 샘플로 입력 편의성, evidence 판정 사유의 이해 가능성, 후속 eligibility false positive·false negative를 계속 검증한다.
- 검증에서 확인된 문제만 다음 작은 구현 범위로 정하고, 기존 상세 roadmap의 후속 기능을 한꺼번에 확장하지 않는다.
- Meta read-only probe와 production client의 synthetic 검증은 credential 없이 유지한다. live sync에는 연결 Professional Account, Page, Meta App permission, 현재 version, IG User ID, token이 필요하다.
- 모발이식 hard-exclude, evidence 방향 상충·불충분 review, evidence 부족 fail-closed, follower 경계값은 golden fixture를 유지한다.
- 긴 조사 전문은 이 문서에 누적하지 않고 관련 `agent_outputs/` 경로를 연결한다.
- 작업 종료 시 `docs/harness/HANDOFF.md`를 갱신한다.
