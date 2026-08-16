# Instagram 의료인 네트워킹 지원 시스템 요구사항 분석 및 초기 설계

## 문서 정보

- 작성일: 2026-08-17
- 조사 기준일: 2026-08-17 KST
- 대상 프로젝트: `hr-sns-auto`
- 범위: 요구사항 분석, 공식 API 가능성 조사, 초기 논리 아키텍처 설계
- 범위 밖: 애플리케이션 구현, Instagram 로그인, follow·like·comment·DM 실행
- 우선 근거: Meta 개발자 문서, Meta 공식 Instagram Postman 컬렉션, Instagram 약관·도움말

## 핵심 결론

- 이 시스템은 현재 공식 API만으로 완전 자동화할 수 없다. 특히 타 계정 게시물에 새 댓글을 작성하는 기능, 게시물 좋아요, 계정 팔로우, 선제 cold DM은 공식 API에서 지원되지 않는다.
- 공식 Business Discovery는 이미 알고 있는 정확한 username의 공개 Business/Creator 계정을 조회·검증하는 기능이다. 직업·지역·전문 분야 조건으로 계정을 찾아주는 검색 API가 아니다.
- 하루 최대 15명의 적합 후보를 확보하려면 검색엔진의 허용된 공개 검색 결과, 운영자 seed, 병원·학회·전문가 공개 디렉터리, 수동 입력을 조합해야 한다. Instagram 웹 UI의 무단 자동 수집을 후보 발굴 수단으로 사용하지 않는다.
- 추천 초기 운영 형태는 `APPROVAL_REQUIRED + MANUAL_EXECUTION`이다. 시스템이 후보·근거·댓글·DM 초안을 만들고 직원이 승인한 뒤 Instagram 앱에서 직접 실행하며 결과를 기록한다.
- `FULL_AUTO`는 데이터 모델과 인터페이스에만 확장 지점으로 둔다. 현재 목표 액션에는 실행 가능한 공식 API가 없으므로 기능 플래그를 비활성화한다.
- 모발이식 계열 false negative는 일반 분류 오류와 다르게 처리한다. 모발이식 관련 여부를 충분히 부정할 근거가 없으면 `ELIGIBLE`이 아니라 `REVIEW_REQUIRED`로 분류한다.
- 하루 15명은 품질 상한이지 충족해야 하는 할당량으로 취급하지 않는다. 근거가 부족한 후보를 숫자를 채우기 위해 통과시키면 안 된다.

## 판정 상태 정의

| 상태 | 의미 |
| --- | --- |
| `OFFICIAL_API_SUPPORTED` | 공식 문서가 해당 용도의 직접 지원을 명시한다. |
| `PARTIALLY_SUPPORTED` | 공식 지원은 있으나 계정 유형, 소유권, 대화 시작 조건, 필드 가용성 등으로 목표의 일부만 가능하다. |
| `NOT_SUPPORTED` | 현재 공식 문서에 목표 용도의 엔드포인트가 없거나 공식 문서가 반대 조건을 명시한다. |
| `NEEDS_VERIFICATION` | 문서가 동적이거나 앱·권한·버전·실계정 조건에 따라 달라 구현 전 읽기 전용 spike가 필요하다. |

`NOT_SUPPORTED`는 “기술적으로 브라우저를 조작할 수 없다”는 뜻이 아니다. 공식 API를 이용해 정책적으로 안정적인 제품 기능으로 제공할 수 없다는 뜻이다.

## 1. 요구사항 정리

### 1.1 업무 목적과 사용자

- 발신자는 압구정에서 모발이식병원을 운영하는 석지웅 원장이다.
- 주 사용자는 병원 직원인 운영자이며 최종 승인자 역할을 한다.
- 목적은 무작위 광고 발송이 아니라 다른 의료 섹터 의사·약사와 자연스럽고 지속적인 관계를 형성하는 것이다.
- 시스템은 관계 형성을 보조한다. 관계의 진정성과 외부 액션의 책임은 사람에게 남긴다.

### 1.2 일일 목표

- 매일 최대 15명의 신규 적합 후보를 운영 화면에 제시한다.
- “신규”는 같은 사람이나 같은 Instagram profile이 과거 후보·interaction history에 존재하지 않음을 뜻해야 한다.
- 15명을 채우지 못하더라도 근거 부족 계정을 자동 통과시키지 않는다.
- 후보 수, `ELIGIBLE` 수, 승인 대기 수를 서로 다른 지표로 관리한다.

### 1.3 필수 적합 조건

- 실제 개인 의사 또는 약사임을 확인할 수 있다.
- 한의사·한의원·한방병원이 아니다.
- follower가 확인 시점에 10,000명 미만이다.
- 모발이식 계열 의료인이 아니다.
- 석지웅 원장과 다른 의료 섹터에 종사한다.
- 병원 공식 계정만 있고 실제 개인 의료인을 특정할 수 없는 경우는 제외한다.
- 최근 게시 활동은 우선순위 요소로 사용한다. “최근”의 일수와 비활성 계정의 hard exclude 여부는 아직 미확정이다.

### 1.4 모발이식 계열 hard exclude

다음 근거 중 하나라도 실제 주요 업무나 반복 콘텐츠로 확인되면 `INELIGIBLE`이다.

- 모발이식, 탈모수술, 헤어라인 교정
- 모발이식 전문 병원 또는 해당 병원의 핵심 의료인
- `hair transplant`, `hair restoration surgery`
- FUE·FUT 등 모발이식 수술 중심 서비스나 콘텐츠
- 명목상 다른 진료과이지만 병원 서비스나 최근 콘텐츠에서 모발이식을 주요 업무로 다루는 경우

단어가 한 번 등장했다는 이유만으로 즉시 제외하지는 않는다. 교육 목적 언급, 환자 협진, 일회성 뉴스 공유일 수 있기 때문이다. 반대로 키워드가 보이지 않는다는 이유만으로 안전 판정하지도 않는다. 서비스 페이지, 소속 병원, 프로필, 최근 콘텐츠를 함께 보고 다음처럼 처리한다.

- 강한 긍정 근거: `INELIGIBLE`
- 상충하거나 불완전한 근거: `REVIEW_REQUIRED`
- 신뢰할 수 있는 다중 근거로 다른 전문 분야가 확인되고 모발이식 서비스가 확인되지 않음: 해당 gate 통과

### 1.5 관리 정보

후보별로 다음 정보를 현재값과 관찰 시점을 함께 관리한다.

- Instagram username, profile URL, account type
- 이름, 직업, 전문 분야, 소속 병원·약국
- follower 수와 관찰 시점·출처
- 프로필 소개와 필요한 최소 excerpt
- 최근 콘텐츠의 permalink, 유형, 게시 시각, 분석 요약
- 발견 경로와 source query 또는 seed
- 선정 근거와 제외 조건별 evidence
- dimension별 confidence와 전체 eligibility 상태
- 최초 발견일, 마지막 확인일, evidence 만료 시각
- 과거 접근·응답·거절·차단·skip 기록과 다음 접근 가능 시각

### 1.6 기능 요구사항

1. 여러 허용된 source에서 후보 username을 발굴하고 중복 제거한다.
2. 의사·약사, 한의사, 모발이식, follower, 최근 활동을 독립 dimension으로 판정한다.
3. 판정마다 사람이 열어 볼 수 있는 URL, 관찰값, 관찰일, 짧은 근거를 남긴다.
4. 근거가 충분한 실제 최근 콘텐츠만 분석한다.
5. 콘텐츠에 grounded된 댓글과 DM 초안을 생성하고 반복·과장·광고 표현을 검사한다.
6. 운영자가 후보와 초안을 approve, edit, reject, skip할 수 있다.
7. 승인 revision과 실제 실행 revision을 일치시키고 모든 상태 변경을 감사 가능하게 기록한다.
8. generation과 external execution을 분리한다.
9. provider별 capability를 실행 시점에도 다시 확인한다.
10. cooldown과 interaction history로 중복·과잉 접근을 방지한다.

### 1.7 비기능 요구사항

- 정책 준수: 공식 API와 명시적으로 허용된 source를 우선한다.
- 설명 가능성: 단일 점수만 보이지 않고 dimension별 evidence와 판정 이유를 보인다.
- 보수적 실패: 데이터 없음, stale evidence, API 오류는 적합 판정이 아니라 review로 실패한다.
- 감사 가능성: 누가 어떤 revision을 언제 승인·수정·실행 표시했는지 남긴다.
- 개인정보 최소화: 업무 목적에 필요한 공개 전문 정보와 최소 콘텐츠 excerpt만 저장한다.
- 변경 내성: API 버전, permission, provider capability, policy version을 데이터에 기록한다.
- 안전한 재시도: 외부 실행은 idempotency key와 `UNKNOWN` 상태를 지원한다.

## 2. 사용자 업무 흐름

### 2.1 권장 일일 흐름

1. `DiscoveryRun`이 허용된 source에서 username·profile URL 후보를 수집한다.
2. 기존 `Candidate`, `SocialProfile`, `InteractionHistory`와 대조해 동일 profile과 동일 인물을 제거한다.
3. source evidence를 정규화하고 필요한 경우 Business Discovery로 공개 Professional Account 정보를 보강한다.
4. eligibility engine이 hard exclude dimension을 먼저 평가한다.
5. `INELIGIBLE`은 이유와 함께 제외 보관하고, `REVIEW_REQUIRED`는 운영자 확인 queue로 보낸다.
6. 모든 필수 gate를 통과한 `ELIGIBLE` 후보만 일일 후보 pool에 올린다.
7. 최근 콘텐츠 중 근거가 충분하고 접근 가능한 항목을 선택해 content analysis를 만든다.
8. 댓글·DM draft를 생성한 뒤 groundedness, 중복, 광고성, 과장, 금칙어 검사를 수행한다.
9. 운영자는 profile, evidence, 최근 콘텐츠, 과거 interaction, cooldown을 한 화면에서 확인한다.
10. 운영자는 초안을 수정한 뒤 approve, reject, skip한다.
11. 승인된 액션은 현재 MVP에서 `READY_FOR_MANUAL_EXECUTION`이 된다.
12. 운영자는 Instagram 앱이나 웹을 직접 열어 실행하고 성공·실패·보류를 시스템에 기록한다.
13. 후속 반응과 관계 지속 여부를 기록해 다음 discovery와 generation에 반영한다.

### 2.2 상태 흐름

```text
DISCOVERED
  -> EVIDENCE_PENDING
      -> INELIGIBLE
      -> REVIEW_REQUIRED -> ELIGIBLE | INELIGIBLE
      -> ELIGIBLE

ELIGIBLE
  -> CONTENT_READY
  -> DRAFT_READY
  -> AWAITING_APPROVAL
      -> REJECTED
      -> SKIPPED
      -> APPROVED
          -> READY_FOR_MANUAL_EXECUTION
          -> EXECUTED_MANUAL | FAILED_MANUAL | EXECUTION_UNKNOWN
```

Eligibility 상태와 outreach 상태는 별도 필드로 둔다. 예를 들어 후보는 `ELIGIBLE`이지만 오늘은 `SKIPPED`일 수 있다.

### 2.3 예외 흐름

- follower evidence가 오래되었거나 10,000 경계에 가까우면 승인 직전에 재확인한다.
- username이 바뀌면 외부 platform ID와 identity evidence로 후보를 병합하고 자동 새 후보로 취급하지 않는다.
- 콘텐츠가 삭제·비공개 전환되거나 내용 확인이 불충분하면 기존 댓글 draft를 만료시킨다.
- 승인 후 draft가 수정되면 승인을 자동 무효화하고 새 revision 승인을 요구한다.
- Instagram에서 실행 결과가 불명확하면 성공으로 추정하지 않고 `EXECUTION_UNKNOWN`으로 둔다.
- 모발이식 관련 새 근거가 발견되면 모든 미실행 액션을 취소하고 재검토한다.

## 3. Instagram 공식 API 기능 매트릭스

### 3.1 조사 범위와 신뢰 수준

- Meta 개발자 문서와 Meta 공식 Instagram Postman workspace를 2026-08-17에 확인했다. 공식 Postman workspace는 2026년에도 갱신 중인 Meta 명의 자료이다. [S1]
- 공식 문서는 로그인 방식, 앱 access level, Graph API version, 계정 설정에 따라 달라진다. 구현 시 고정한 API version으로 read-only contract test를 다시 수행해야 한다.
- 문서에 없는 mutation을 “엔드포인트가 있을 것”이라고 추정하지 않았다.

### 3.2 기능별 판정

| 쟁점 | 판정 | 공식 지원 범위와 제한 | 본 시스템 영향 |
| --- | --- | --- | --- |
| 다른 Professional Account를 조건 검색 | `NOT_SUPPORTED` | 직업, 전문 분야, 지역, follower 범위로 account를 검색하는 일반 API가 없다. Facebook Login 구성은 다른 Business/Creator의 기본 metadata를 가져올 수 있다고 명시하지만 이미 username을 알아야 한다. [S2][S4] | 공식 API 단독 discovery는 불가능하다. |
| Business Discovery | `PARTIALLY_SUPPORTED` | 정확한 username으로 공개 Business/Creator의 기본 profile metadata·metrics와 media를 조회한다. Personal Account는 대상이 아니다. 설계상 Facebook Login + 연결된 Page 구성을 전제로 한다. [S2][S4] | 발견이 아니라 검증 adapter로 사용한다. |
| 공개 hashtag media 탐색 | `PARTIALLY_SUPPORTED` | Facebook Login API는 hashtagged media 탐색을 지원한다고 명시한다. 다만 이는 계정 조건 검색이 아니며, 현재 버전에서 반환 media로부터 target username·owner를 후보화할 수 있는지는 확인하지 못했다. [S2] | 계정 discovery 채널로 확정하지 않는다. 반환 필드·quota를 read-only spike로 확인하기 전에는 후보 공급량에 포함하지 않는다. |
| 다른 계정 follower 수 | `PARTIALLY_SUPPORTED` | Business Discovery 대상 Business/Creator에 `followers_count`를 요청할 수 있다. Personal, 접근 제한 계정, 실패 응답에는 적용할 수 없다. | API 관찰값이면 임계치 판정에 사용하고 source와 `observed_at`을 저장한다. 없으면 review한다. |
| 다른 계정 최근 media | `PARTIALLY_SUPPORTED` | Business Discovery field expansion으로 대상 Professional Account의 공개 media metadata를 요청할 수 있다. caption, timestamp, permalink, media type 등 실제 필드 가용성은 API version과 media 특성에 좌우된다. [S4] | 최근 활동·콘텐츠 근거 보강에 사용한다. 전량 수집을 전제로 하지 않는다. |
| 다른 계정 Reels 내용 | `PARTIALLY_SUPPORTED` | media metadata에서 Reel을 구분할 수 있으나 원본 영상·오디오 URL이나 모든 콘텐츠 요소가 항상 제공된다고 보장할 수 없다. 저작권·가용성 제한이 있을 수 있다. | caption·permalink·운영자 확인을 우선한다. 영상/오디오를 임의 추출하지 않는다. |
| Personal Account profile/media | `NOT_SUPPORTED` | Facebook Login 기반 Instagram API는 consumer account에 접근할 수 없다고 명시한다. [S2] | 공개 개인 의료인 계정은 API 검증이 불가능할 수 있어 수동 evidence 경로가 필요하다. |
| 자사 media 댓글 읽기·관리·답글 | `OFFICIAL_API_SUPPORTED` | 앱 사용자의 Professional Account가 소유한 media의 댓글 관리와 답글이 지원된다. [S1][S5] | 인바운드 커뮤니티 관리에는 쓸 수 있으나 이번 cold outreach 댓글과 다르다. |
| 타인 media에 새 댓글 등록 | `NOT_SUPPORTED` | 공식 capability는 앱 사용자가 소유한 media의 댓글 관리·reply로 기술된다. 타인의 Business Discovery media에 top-level comment를 쓰는 공식 기능은 문서화되어 있지 않다. [S1][S5] | 댓글은 운영자가 Instagram UI에서 수동 작성한다. |
| 타인 게시물 좋아요 | `NOT_SUPPORTED` | 공식 Instagram API capability와 공식 collection에 feed/reel post를 like하는 mutation이 없다. 메시지의 heart sticker·reaction은 게시물 좋아요와 다른 기능이다. [S1] | like는 수동 실행만 가능하다. |
| 타 계정 follow | `NOT_SUPPORTED` | 계정 follow/unfollow mutation이 공식 Instagram API capability에 없다. [S1] | follow는 수동 실행만 가능하다. |
| 선제 cold DM | `NOT_SUPPORTED` | Send API는 상대 Instagram 사용자가 앱 사용자의 Professional Account에 먼저 메시지를 보냈어야 하며, conversation도 상대의 선행 메시지로 시작한다고 명시한다. [S6] | 이 요구의 소개 DM은 공식 API로 보낼 수 없다. |
| 기존 inbound conversation 응답 | `PARTIALLY_SUPPORTED` | 상대가 먼저 메시지를 보낸 이후 Professional Account가 응답할 수 있다. standard messaging window와 permission을 지켜야 한다. [S6][S16] | 향후 인바운드 응답 지원과 cold outreach를 별도 use case로 둔다. |
| 자사 게시물 commenter에게 private reply | `PARTIALLY_SUPPORTED` | 자사 post/reel/story 등에 상대가 댓글을 남긴 경우 한 번의 private reply를 7일 안에 보낼 수 있다. 후속 메시지는 상대가 응답한 뒤 24시간 안에 가능하다. [S7] | 이번 후보에게 먼저 보내는 DM의 우회 수단으로 사용할 수 없다. |
| 24-hour window·Human Agent | `PARTIALLY_SUPPORTED` | 일반 응답은 24시간 window가 핵심이다. `HUMAN_AGENT`는 상대 메시지 후 7일 안의 실제 사람 지원 용도이며 자동 메시지와 문의 무관 콘텐츠는 금지된다. [S8] | 네트워킹 cold DM이나 자동 follow-up 예외로 사용하면 안 된다. |
| Instagram 웹·UI의 비공식 automated data collection | `NOT_SUPPORTED` | Instagram 약관은 명시적 허가 없는 자동 접근·수집을 금지한다. Meta Automated Data Collection Terms도 별도 명시적 서면 허가가 선행되어야 하며 약관 수락만으로 허가가 되지 않는다고 한다. 공식 API가 허용한 범위의 수집과 구분해야 한다. [S10][S11] | Playwright/Selenium scraper, private endpoint 호출, 세션 기반 대량 수집을 사용하지 않는다. |
| 공식 API rate limit | `NEEDS_VERIFICATION` | Graph API는 Platform·Business Use Case 등 복수 rate bucket과 응답 usage header를 사용한다. endpoint, token, app, account usage에 따라 실제 quota가 달라진다. [S9] | live header 계측, cache, backoff, queue가 필요하다. 고정된 “안전 호출 수”를 코드에 가정하지 않는다. |
| Business·Creator·Personal 차이 | `PARTIALLY_SUPPORTED` | API 관리 대상은 Professional Account인 Business·Creator이다. Facebook Login은 연결 Page가 필요하고 Instagram Login은 Page 없이 가능하지만 ads·tagging 제한이 있다. Personal은 API 관리 대상이 아니다. [S2][S3][S15] | 석지웅 원장 계정 유형과 Page 연결 여부를 Phase 0에서 확인한다. |

### 3.3 Business Discovery의 정확한 역할

권장 요청 개념은 다음과 같다. 실제 API version과 permission은 구현 시 공식 문서로 다시 확인한다.

```text
GET /{OUR_IG_USER_ID}
  ?fields=business_discovery.username({TARGET_USERNAME}){
      id,username,name,biography,website,profile_picture_url,
      followers_count,follows_count,media_count,
      media.limit(N){id,caption,media_type,media_product_type,permalink,timestamp}
  }
```

다음 경계를 지켜야 한다.

- input은 이미 알고 있는 username이다. `전문의 + 서울 + follower < 10000` 같은 검색 query가 아니다.
- target은 Business 또는 Creator 계정이어야 한다.
- 반환 profile category만으로 실제 의사·약사 자격을 확정하지 않는다.
- follower는 관찰 시점 snapshot이다. 승인 시점에 바뀔 수 있다.
- 공개 metadata이지 타 계정의 private insights가 아니다.
- target media의 모든 본문·영상·오디오가 항상 제공된다고 가정하지 않는다.
- age/country restriction, username 변경, account type 변경, field 권한 오류를 정상적인 `DATA_UNAVAILABLE`로 처리한다.
- Instagram Login 구성에서 Business Discovery를 사용할 수 있다고 추정하지 않는다. 현재 공식 capability 설명상 Facebook Login 경로로 한정해 설계하고 spike에서 재확인한다.

### 3.4 계정과 인증 경로 비교

| 구분 | Instagram API with Facebook Login | Instagram API with Instagram Login | Personal Account |
| --- | --- | --- | --- |
| 운영 계정 | Business·Creator | Business·Creator | API 관리 불가 |
| Facebook Page 연결 | 필요 | 불필요 | 해당 없음 |
| 다른 Business·Creator metadata | 공식 설명에 포함 | 현재 공식 설명에서 확인되지 않음 | 해당 없음 |
| 자사 media·comment 관리 | 가능 범위 있음 | 가능 범위 있음 | 불가 |
| Messaging | permission·inbound 조건 아래 가능 | permission·inbound 조건 아래 가능 | app user로 사용 불가 |
| 이번 설계 적합성 | Business Discovery 때문에 우선 spike 대상 | 향후 자사 관리·인바운드 메시지 대안 | 검증 blind spot |

자체 계정만 관리하는 Standard Access와 타인이 소유한 Professional Account를 앱 사용자로 서비스하는 Advanced Access의 조건도 다르다. 이번 내부 도구가 석지웅 원장 소유 계정 하나만 인증한다면 필요한 access level이 낮을 수 있으나, 공개 target 조회에 필요한 permission, App Review, business verification의 실제 조합은 Meta App Dashboard에서 확인해야 한다.

### 3.5 rate limit 설계 원칙

- Graph API 일반 문서의 숫자 하나를 전체 Instagram quota로 사용하지 않는다.
- `X-App-Usage`, `X-Business-Use-Case-Usage` 등 실제 응답 header와 오류 code를 저장한다.
- `call_count`뿐 아니라 CPU time과 total time 사용률도 관찰한다.
- 비필수 refresh는 usage threshold 전에 지연한다.
- 동일 profile과 media는 TTL cache를 사용하고 한 실행에서 중복 조회하지 않는다.
- 429 또는 rate 관련 오류에는 즉시 반복 호출하지 않고 provider가 안내하는 회복 시간과 exponential backoff를 적용한다.
- 하루 15명은 rate limit과 별개이다. profile 하나에 중첩 media를 과도하게 요청하면 작은 후보 수라도 비용이 커질 수 있다.
- 공식 API rate limit은 Instagram UI의 anti-abuse limit과 무관하다. UI follow·comment·DM에 공개된 보장 가능한 “안전 수치”가 있다고 가정하지 않는다.

## 4. 후보 발굴 전략 비교

### 4.1 비교표

`정책 위험`은 해당 source의 약관·라이선스를 준수하고 Instagram UI를 직접 자동 수집하지 않는다는 전제의 상대 평가이다.

| 전략 | Precision | Recall | 운영 비용 | 자동화 가능성 | 정책 위험 | 권장 역할 |
| --- | --- | --- | --- | --- | --- | --- |
| 사용자가 제공하는 seed account | 높음 | 낮음 | 낮음 | 중간 | 낮음 | 초기 고품질 pool과 연관 후보의 출발점 |
| 병원·학회·전문가 공개 디렉터리 | 직업 precision 높음 | 중간 | 중간 | 중간 | 낮음~중간 | 의사·약사 자격·소속 evidence. source 이용조건 확인 필요 |
| 검색엔진 기반 공개 웹 검색 | 중간 | 중간~높음 | 중간 | 중간~높음 | 낮음~중간 | `site:instagram.com` 결과와 병원 profile을 이용한 username 발견. 검색 provider 약관 준수 |
| 공식 hashtag media 탐색 | 판단 보류 | 판단 보류 | 중간 | 중간 | 낮음 | 공개 media 탐색 가능성만 확인되었다. target username 반환 여부·quota를 Phase 0에서 검증하기 전에는 계정 discovery 수단으로 사용하지 않음 |
| Business Discovery | target type·follower precision 높음 | Personal 누락으로 낮음 | 낮음 | 높음 | 낮음 | 정확한 username 검증과 최근 media 보강. discovery 자체가 아님 |
| 운영자 수동 후보 입력 | 높음 | 낮음 | 높음 | 낮음 | 낮음 | API blind spot과 특수 사례 처리 |
| Instagram 웹 UI browser scraping | 불안정 | 겉보기에는 높음 | 장기적으로 높음 | 높음 | 매우 높음 | 사용하지 않음 |

Meta는 조건을 충족하는 성인의 공개 Professional Account가 올린 public post·reel이 Google·Bing 같은 검색엔진에 색인될 수 있다고 안내한다. 계정 소유자가 색인을 끄거나 계정 유형을 바꿀 수 있고 de-index에도 시간이 걸리므로 검색 결과는 발견 signal이지 최신 사실이 아니다. [S14]

### 4.2 추천 discovery funnel

```text
허용된 search result / seed / directory / manual input
  -> username·URL 정규화
  -> 동일 profile·동일 인물·과거 interaction 중복 제거
  -> 직업·소속의 공개 evidence 수집
  -> Business Discovery로 Professional Account·follower·media 보강
  -> hard exclude triage
  -> 운영자 review
  -> 최대 15명 일일 후보
```

### 4.3 source별 운영 규칙

- 검색엔진은 공식 검색 API나 정상적인 사람이 보는 검색 결과를 사용한다. Instagram 페이지 자체를 search result에서 연쇄 crawl하지 않는다.
- 디렉터리는 공개라는 사실만으로 자동 수집 권리가 생긴다고 보지 않는다. 이용약관, robots, 재사용·저장 허용 범위를 source registry에 기록한다.
- seed에서 추천 계정을 따라가는 과정도 Instagram UI 자동 탐색으로 구현하지 않는다. 운영자가 username을 입력하거나 허용된 검색 결과를 사용한다.
- source마다 `source_type`, `source_url`, `query`, `license_review_status`, `observed_at`, `collector`를 남긴다.
- 같은 사실을 복사한 여러 검색 결과를 독립 evidence로 중복 계산하지 않는다.

### 4.4 precision 우선 전략

- 초기에는 recall보다 precision을 우선한다.
- “의료인처럼 보이는 bio”만으로 통과시키지 않고 소속 기관 profile 등 강한 근거를 요구한다.
- 모발이식 false negative 비용이 크므로 hair-transplant screen은 high-recall로 운영한다.
- 계정이 적게 나오는 날은 15명을 채우지 않는다.
- 운영자 판정 결과를 source·query별 precision에 연결해 다음 discovery 우선순위를 조정한다.

## 5. 추천 아키텍처

### 5.1 설계 원칙

- MVP는 배포·운영이 단순한 modular monolith를 권장한다.
- 관계형 데이터베이스를 system of record로 사용한다. 구체 제품과 애플리케이션 기술 스택은 아직 정하지 않는다.
- AI는 evidence에서 signal과 문안 후보를 만들지만 hard gate의 미확정값을 자동 확정하지 않는다.
- 외부 액션은 generation transaction과 분리하고 별도 capability gate를 통과한다.
- provider가 지원하지 않는 액션은 실패가 아니라 명시적 `UNSUPPORTED` 결과로 처리한다.

### 5.2 논리 구성

```text
[Discovery Sources]
  search / seed / directory / manual / Business Discovery
          |
          v
[Discovery Orchestrator] -> [Identity & Dedupe]
          |                         |
          v                         v
      [Evidence Ledger] ------> [Eligibility Policy Engine]
                                      |        |
                          REVIEW_REQUIRED      ELIGIBLE
                                      |        |
                                      v        v
                              [Operator UI] [Content Intake & Analysis]
                                                   |
                                                   v
                                     [Comment/DM Generation + QA]
                                                   |
                                                   v
                                           [Approval Service]
                                                   |
                                                   v
                                          [Action Orchestrator]
                                           /        |        \
                                  Manual Provider  Meta Provider  Disabled Browser Provider
                                           |
                                           v
                                  [Interaction History & KPI]
```

### 5.3 주요 모듈

#### Discovery Orchestrator

- source adapter를 실행하고 `DiscoveryRun`을 만든다.
- source별 입력, query, cursor, 결과 수, 오류, 소요 시간, 정책 검토 상태를 기록한다.
- 하루 최대치에 도달해도 이미 시작한 evidence write의 일관성을 보장한다.

#### Identity & Dedupe

- username 소문자 정규화, URL canonicalization, 외부 platform ID를 사용한다.
- 이름·소속이 같은 다중 profile은 자동 병합하지 않고 merge suggestion을 만든다.
- username 변경과 계정 재사용을 구분하기 위해 evidence history를 유지한다.

#### Evidence Ledger

- 판정 근거를 append-only에 가깝게 저장한다.
- 관찰 사실과 AI inference를 분리한다.
- source reliability, freshness, polarity, excerpt, URL을 보존한다.
- 원문 전체 저장보다 최소 excerpt와 구조화된 assertion을 우선한다.

#### Eligibility Policy Engine

- versioned rule set으로 dimension별 `PASS`, `FAIL`, `UNKNOWN`, `CONFLICT`를 계산한다.
- hard gate 하나가 `FAIL`이면 전체 `INELIGIBLE`이다.
- hard gate에 `UNKNOWN` 또는 `CONFLICT`가 있고 `FAIL`은 없으면 `REVIEW_REQUIRED`이다.
- 모든 hard gate가 `PASS`일 때만 `ELIGIBLE`이다.
- ranking score는 최근 활동과 evidence 품질 순서를 정할 뿐 hard gate를 덮지 못한다.

#### Content Intake & Analysis

- 공식 API에서 허용된 metadata, 운영자가 선택한 permalink, 운영자 입력 요약을 입력으로 받는다.
- 주제, 구체적 포인트, 게시 목적, 어조, 질문 가능 지점, 피해야 할 민감 표현을 구조화한다.
- 콘텐츠를 실제로 확인하지 못하면 `INSUFFICIENT_CONTEXT`로 두고 댓글을 생성하지 않는다.
- 환자 식별 정보나 불필요한 건강 정보를 별도 추출·저장하지 않는다.

#### Generation & QA

- 댓글과 DM을 서로 다른 prompt·policy로 생성한다.
- 사용한 content item, evidence IDs, prompt version, model identifier, 생성 시각을 남긴다.
- 규칙 기반 검사와 AI critique를 결합하되 최종 승인은 사람에게 둔다.
- 최근 문안 corpus와 exact·n-gram·semantic similarity를 비교해 반복을 탐지한다.

#### Approval Service

- eligibility 승인과 outreach 문안 승인을 구분한다.
- edit마다 immutable revision을 만든다.
- `Approval`은 revision ID와 content hash에 결속한다.
- evidence나 콘텐츠가 만료되면 기존 승인을 만료시킨다.

#### Action Orchestrator

- action type, target, provider, capability, approval, cooldown, idempotency를 검사한다.
- 승인만으로 실행 가능하다고 보지 않는다.
- 현재 MVP에서는 manual execution checklist와 deep link를 제공하고 결과 입력을 받는다.

#### Audit & Metrics

- 상태 변경, 정책 version, operator, 수정 내용, 실행 결과를 이벤트로 남긴다.
- 일일 funnel과 품질 KPI를 산출한다.
- 모발이식 false negative는 별도 incident와 원인 evidence를 기록한다.

### 5.4 eligibility dimension 설계

| Dimension | `PASS` 기준 예시 | `FAIL` 기준 예시 | `UNKNOWN/CONFLICT` 예시 |
| --- | --- | --- | --- |
| 개인 운영 계정 | 이름·얼굴·소속이 특정 개인 의료인과 일치 | 기관 브랜드만 있고 개인 특정 불가 | 동일 이름, 공동 운영, identity 불명확 |
| 의사·약사 | 신뢰 가능한 기관 profile 또는 복수 공개 근거 | 비의료 직업으로 확인 | bio만 “doctor”이고 외부 근거 없음 |
| 한의 계열 아님 | 의사·약사 자격·기관 유형이 명확 | 한의사·한의원·한방병원 | 표현이 혼재하거나 번역 불명확 |
| 모발이식 아님 | 다른 전문 분야와 서비스가 일관되며 hair 관련 strong signal 없음 | 모발이식 서비스·반복 콘텐츠·소속이 확인 | hair 키워드가 있으나 맥락 불명확, 서비스 확인 불가 |
| follower < 10,000 | 신선한 exact count가 10,000 미만 | count가 10,000 이상 | count 없음, stale, 반올림 표기, 경계 변경 가능 |
| 다른 의료 섹터 | 전문 분야가 모발이식과 구분됨 | 모발이식이 주 업무 | 일반의·성형외과 등만 확인되고 실제 서비스 불명확 |
| 최근 활동 | 정책 기간 내 실제 post/reel timestamp | 비활성 cutoff를 hard gate로 정한 경우 초과 | timestamp를 확인할 수 없음 |

최근 활동은 사용자 문구상 “우선” 조건이므로 현재 설계에서는 ranking dimension이다. 비활성 계정을 `INELIGIBLE`로 만들지는 사용자 확인 전 확정하지 않는다.

### 5.5 댓글 생성 구조

댓글 생성 입력은 다음 최소 구조를 사용한다.

```text
sender_context
target_name_and_specialty
selected_content_permalink
content_topic
one_or_two_grounding_points
tone_constraints
forbidden_claims
recent_comment_similarity_candidates
```

생성 후 다음을 검사한다.

- 실제 콘텐츠에서 확인 가능한 구체적 anchor가 최소 하나 있다.
- 근거 없는 의학적 동의나 효과 보장을 하지 않는다.
- “최고”, “완벽”, “무조건” 같은 과장 칭찬을 피한다.
- 광고, 상담 유도, 병원 홍보, 링크 유도를 포함하지 않는다.
- 상대와 이미 친하다는 인상을 주지 않는다.
- 최근 사용한 문장과 지나치게 유사하지 않다.
- 게시물과 무관한 범용 문장만 남으면 재생성하거나 사람 작성으로 전환한다.
- 공개 환자 사례의 민감한 세부 내용을 댓글에서 반복하지 않는다.

### 5.6 DM 생성 구조

DM은 고정 인사말의 단어 치환이 아니라 다음 semantic slot을 사용한다.

- 발신자 소개: 압구정에서 모발이식병원을 운영하는 석지웅 원장
- 발견 맥락: 과장하지 않는 일반적 표현
- 개인화 anchor: 전문 분야 또는 실제 최근 콘텐츠에서 확인한 한 가지
- 교류 의도: 다른 분야 의료인으로서 배우고 자연스럽게 소통하고 싶다는 내용
- 마무리: 짧고 부담 없는 감사

금지 규칙은 다음과 같다.

- 실제로 읽지 않은 콘텐츠를 읽었다고 단정하지 않는다.
- 상대의 사생활, 가족, 환자 정보를 개인화 소재로 쓰지 않는다.
- 답변, 예약, 소개, 공동 마케팅을 압박하지 않는다.
- 같은 후보에게 응답 없이 동일·유사 DM을 반복하지 않는다.
- API로 보낼 수 있다는 가정으로 `sendable=true`를 설정하지 않는다.

### 5.7 최소 운영 화면

#### 오늘의 후보 queue

- 날짜, discovery run 상태, raw·deduped·eligible·review·ineligible 수를 보인다.
- `ELIGIBLE` 후보를 최대 15명까지 우선순위순으로 보인다.
- 각 행에 이름, username, 직업·전문 분야, 소속, follower snapshot, 최근 활동일, confidence, eligibility, cooldown을 표시한다.
- `REVIEW_REQUIRED`와 모발이식 signal은 일반 후보와 시각적으로 구분한다.
- 숫자를 채우기 위한 일괄 적합 처리 기능은 두지 않는다.

#### 후보 상세와 evidence panel

- profile URL을 새 창에서 연다.
- 의사·약사, 한의 계열, 모발이식, follower, 다른 섹터, 최근 활동 dimension을 각각 펼쳐 본다.
- 각 dimension에서 source URL, 짧은 excerpt·관찰값, 관찰일, freshness, 수집 방법을 확인한다.
- 상충 evidence와 `UNKNOWN`을 숨기지 않는다.
- 운영자가 evidence를 추가하고 `REVIEW_REQUIRED`를 해소할 수 있게 하되 변경 이유를 필수로 남긴다.

#### 최근 콘텐츠와 문안 panel

- recent post·reel의 permalink, 게시 시각, 유형, caption excerpt, content analysis를 보인다.
- 운영자가 실제 댓글 대상 content item을 명시적으로 선택한다.
- 댓글 초안과 DM 초안을 분리해 표시하고 inline edit, regenerate, reject, skip을 지원한다.
- 사용한 grounding point와 중복·광고성·과장 검사 결과를 문안 옆에서 확인한다.
- content context가 부족하면 생성 버튼 대신 필요한 추가 확인을 안내한다.

#### 승인과 실행 panel

- `APPROVE`, `EDIT_AND_APPROVE`, `REJECT`, `SKIP`을 제공한다.
- 승인 revision, 승인자, expiry, 현재 capability를 보인다.
- 공식 실행이 불가능한 action에는 API 실행 버튼을 표시하지 않는다.
- manual action은 profile·post 열기, 승인 문안 복사, 실행 완료·실패·불명확 기록 순서의 checklist로 제공한다.
- action별 실행 여부와 마지막 확인 시각을 표시하고, 운영자가 Instagram에서 확인한 결과만 기록한다.
- 실행 완료 표시 전 target username과 draft hash를 다시 보여 오대상 실행을 줄인다.

#### interaction history

- 과거 follow·comment·DM, 응답, 거절, block, skip을 시간순으로 보인다.
- action별 cooldown과 다음 접근 가능 시각을 표시한다.
- 같은 사람으로 추정되는 다른 profile이 있으면 경고한다.
- follow-back·DM response·관계 지속 결과를 운영자가 추가할 수 있다.

초기 UI는 속도보다 오류 방지를 우선한다. batch approve는 후보별 evidence와 content를 확인하지 못하게 만들 수 있으므로 MVP에서 제공하지 않는 편이 안전하다.

## 6. 데이터 모델

### 6.1 핵심 entity

| Entity | 역할 | 주요 필드 |
| --- | --- | --- |
| `Candidate` | 실제 개인 후보의 canonical record | `id`, `display_name`, `profession`, `specialty`, `organization`, `eligibility_status`, `current_assessment_id`, `confidence`, `first_discovered_at`, `last_verified_at`, `archived_at` |
| `CandidateEvidence` | 판정 dimension별 관찰·추론 근거 | `id`, `candidate_id`, `dimension`, `assertion`, `polarity`, `evidence_kind`, `source_type`, `source_url`, `excerpt`, `observed_value_json`, `observed_at`, `expires_at`, `reliability`, `collection_method`, `created_by` |
| `SocialProfile` | platform account와 snapshot | `id`, `candidate_id`, `platform`, `normalized_username`, `profile_url`, `platform_user_id`, `account_type`, `bio_excerpt`, `follower_count`, `follower_observed_at`, `is_public`, `last_checked_at` |
| `ContentItem` | 댓글 grounding에 쓰는 post·reel | `id`, `social_profile_id`, `external_media_id`, `content_type`, `permalink`, `caption_excerpt`, `published_at`, `retrieval_method`, `availability_status`, `content_hash`, `last_checked_at` |
| `GeneratedComment` | content-specific 댓글 draft와 revision | `id`, `candidate_id`, `content_item_id`, `revision`, `text`, `input_evidence_ids`, `analysis_id`, `prompt_version`, `model_id`, `quality_checks_json`, `status`, `created_at` |
| `GeneratedDM` | 후보별 DM draft와 revision | `id`, `candidate_id`, `anchor_content_item_id`, `revision`, `text`, `input_evidence_ids`, `prompt_version`, `model_id`, `quality_checks_json`, `status`, `created_at` |
| `OutreachAction` | 실행 의도와 lifecycle | `id`, `candidate_id`, `social_profile_id`, `action_type`, `draft_type`, `draft_id`, `draft_revision`, `approved_hash`, `provider`, `capability_status`, `status`, `idempotency_key`, `scheduled_at`, `executed_at`, `external_reference`, `failure_code` |
| `Approval` | 사람의 판정과 승인 근거 | `id`, `action_id`, `reviewer_id`, `decision`, `approved_revision`, `approved_hash`, `reason_code`, `note`, `created_at`, `expires_at` |
| `InteractionHistory` | outbound·inbound·manual 결과 이력 | `id`, `candidate_id`, `social_profile_id`, `action_id`, `direction`, `interaction_type`, `occurred_at`, `outcome`, `source`, `external_event_id`, `recorded_by`, `note` |
| `DiscoveryRun` | 발굴 batch의 provenance와 funnel | `id`, `source_adapter`, `policy_version`, `input_json`, `started_at`, `completed_at`, `raw_count`, `deduped_count`, `eligible_count`, `review_count`, `ineligible_count`, `error_summary` |

### 6.2 권장 보조 entity

| Entity | 필요 이유 |
| --- | --- |
| `EligibilityAssessment` | 전체 판정과 dimension별 결과, rule version, 설명을 snapshot으로 보존한다. |
| `ContentAnalysis` | 원문과 생성 문안 사이의 주제·anchor·민감도 분석을 독립 보존한다. |
| `SourceRegistry` | source 이용조건, 자동화 허용 범위, reliability, 검토일을 관리한다. |
| `PolicyVersion` | hard exclude, freshness, cooldown, 문안 정책의 변경 이력을 남긴다. |
| `Operator` 또는 기존 인증 사용자 | 승인 권한과 감사 주체를 식별한다. |

### 6.3 관계와 무결성

- `Candidate 1:N SocialProfile`
- `Candidate 1:N CandidateEvidence`
- `SocialProfile 1:N ContentItem`
- `Candidate 1:N EligibilityAssessment`
- `ContentItem 1:N ContentAnalysis`
- `Candidate/ContentItem 1:N GeneratedComment|GeneratedDM`
- `OutreachAction N:1 draft revision`
- `OutreachAction 1:N Approval`, 단 현재 유효 승인 하나만 허용
- `Candidate 1:N InteractionHistory`

권장 unique constraint는 다음과 같다.

- `SocialProfile(platform, normalized_username)`
- `ContentItem(platform, external_media_id)` 또는 external ID가 없을 때 `permalink_hash`
- `OutreachAction(idempotency_key)`
- `InteractionHistory(source, external_event_id)`에서 external ID가 존재하는 경우

### 6.4 evidence와 confidence

- `evidence_kind`: `OBSERVATION`, `SOURCE_ASSERTION`, `AI_INFERENCE`, `OPERATOR_CONFIRMATION`
- `polarity`: `SUPPORTS`, `CONTRADICTS`, `NEUTRAL`
- `dimension`: `IDENTITY`, `PROFESSION`, `HAN_MEDICINE`, `HAIR_TRANSPLANT`, `FOLLOWER_THRESHOLD`, `SECTOR`, `RECENT_ACTIVITY`
- confidence는 evidence 품질 표시이며 eligibility 결정을 대체하지 않는다.
- AI inference만으로 의사·약사 또는 non-hair를 `PASS`로 만들지 않는다.
- 오래된 evidence를 삭제해 과거 결정을 설명할 수 없게 하지 않고 만료 상태로 보존한다.

### 6.5 cooldown

`InteractionHistory`에서 다음 값을 계산한다.

- `last_any_outbound_at`
- `last_comment_at`
- `last_dm_at`
- `last_response_at`
- `last_rejection_or_block_at`
- `next_allowed_at_by_action`

정책 값은 아직 확정하지 않는다. 보수적 pilot 후보안은 다음과 같다.

- 동일 후보에게 하루에 하나의 신규 outbound action만 허용한다.
- cold DM은 응답이 없으면 재발송하지 않는다.
- 댓글은 동일 post에 한 번만 작성하고 후보 단위 cooldown을 둔다.
- reject, block, 부정적 응답은 영구 suppression 또는 명시적 해제 전 suppression으로 처리한다.
- 다른 username이더라도 같은 인물로 확인되면 candidate-level cooldown을 공유한다.

## 7. Approval workflow

### 7.1 모드 비교

| 모드 | generation | 사람 승인 | 외부 실행 | 현재 적합성 |
| --- | --- | --- | --- | --- |
| `MANUAL` | 후보·초안 생성 | 선택적 | 운영자가 전부 직접 실행 | 가장 단순한 pilot에 적합 |
| `APPROVAL_REQUIRED` | 후보·초안 생성 | 필수, edit/reject/skip 포함 | 지원 provider만 실행. 현재 목표 액션은 수동 provider | 추천 초기 모드 |
| `FULL_AUTO` | 자동 | 사전 정책만 | 공식 capability가 있는 액션만 | 현재 목표 outbound에는 사용 불가, 비활성화 |

`APPROVAL_REQUIRED`와 manual execution은 모순이 아니다. 전자는 내부 의사결정 gate이고 후자는 Instagram에서 실제 행동하는 방식이다.

### 7.2 승인 단계

1. eligibility gate: 후보가 `ELIGIBLE`인지 확인한다.
2. freshness gate: follower, profile, selected content, cooldown을 재확인한다.
3. generation gate: 콘텐츠 anchor와 문안 QA를 통과한다.
4. operator review: approve, edit-and-approve, reject, skip 중 하나를 선택한다.
5. revision binding: 승인 hash와 현재 draft hash가 일치하는지 확인한다.
6. capability gate: provider가 action과 context를 현재 지원하는지 확인한다.
7. execution: manual checklist를 열거나 향후 공식 provider job을 enqueue한다.
8. reconciliation: 성공·실패·unknown을 기록하고 interaction history를 만든다.

### 7.3 결정 의미

- `APPROVE`: 현재 revision과 target content를 승인한다.
- `EDIT_AND_APPROVE`: 새 revision을 만든 뒤 그 revision을 승인한다.
- `REJECT`: 후보 자체, 콘텐츠 선택, 문안 중 무엇을 거절했는지 reason code를 남긴다.
- `SKIP`: 부적합 확정이 아니라 오늘 실행하지 않는 상태이다. 재노출 시각과 이유를 둔다.
- `OVERRIDE_ELIGIBILITY`: 일반 승인과 분리하고 별도 권한·근거를 요구한다. hard exclude를 override하는 기능은 MVP에서 두지 않는 편이 안전하다.

### 7.4 stale approval 방지

- profile 또는 content가 바뀌거나 삭제됨
- follower count가 임계치를 넘음
- 새 모발이식 signal이 발견됨
- cooldown 위반 interaction이 추가됨
- 승인 expiry가 지남
- draft text 또는 target이 수정됨

위 조건이면 `APPROVED`를 `EXPIRED`로 바꾸고 재검토한다. 승인 TTL의 구체 시간은 사용자 확인이 필요하다.

## 8. Instagram action abstraction

### 8.1 interface 개념

애플리케이션 구현 시 다음 의미의 인터페이스를 둔다. 아래는 코드가 아니라 contract 초안이다.

```text
InstagramActionProvider
  capabilities(context) -> CapabilityDecision[]
  prepare(action) -> PreparedAction
  execute(preparedAction, idempotencyKey) -> ExecutionReceipt
  reconcile(receipt) -> ExecutionStatus

CapabilityDecision
  actionType
  supportStatus
  reasonCode
  officialSource
  verifiedAt
  accountPrerequisites
```

### 8.2 provider 구성

#### `ManualInstagramProvider`

- profile·content deep link와 승인된 최종 문안을 제공한다.
- programmatic execution을 수행하지 않는다.
- 운영자가 실행 결과와 시각을 기록한다.
- 성공을 자동 추정하지 않는다.

#### `MetaOfficialInstagramProvider`

- OAuth token, permission, API version, usage header를 관리한다.
- Business Discovery 같은 허용된 read 기능을 제공한다.
- 향후 자사 media 댓글 관리와 inbound messaging처럼 공식 지원되는 별도 use case를 제공할 수 있다.
- 타인 media 댓글, cold DM, post like, follow 요청에는 항상 `UNSUPPORTED_FOR_CONTEXT`를 반환한다.

#### `BrowserAutomationProvider`

- 인터페이스 검토상 이름만 둘 수 있으나 구현·등록·활성화하지 않는다.
- provider fallback으로 자동 선택하지 않는다.
- 공식 provider 오류를 browser automation으로 우회하지 않는다.

### 8.3 action별 provider routing

| Action | Meta official provider | Manual provider | MVP routing |
| --- | --- | --- | --- |
| target profile 검증 | Professional Account 일부 지원 | 운영자 확인 | 공식 read + 수동 fallback |
| target 최근 media 확인 | Professional Account 일부 지원 | permalink 열기 | 공식 read + 수동 보강 |
| 타인 post/reel 댓글 | 미지원 | 가능 | manual only |
| cold DM | 미지원 | Instagram UI에서는 가능 | manual only |
| follow | 미지원 | 가능 | manual only |
| post like | 미지원 | 가능 | manual only |
| 상대 선행 DM에 응답 | 조건부 지원 | 가능 | 별도 inbound use case, MVP 범위 밖 |

### 8.4 실행 안전장치

- `approved_hash == current_draft_hash`
- eligibility가 실행 시점에도 `ELIGIBLE`
- `next_allowed_at <= now`
- provider capability가 `OFFICIAL_API_SUPPORTED` 또는 명시된 manual route
- action당 안정적인 idempotency key
- token과 session secret을 일반 로그에 기록하지 않음
- 외부 응답의 request ID, API version, status, usage header를 보존
- timeout 후 무조건 재시도하지 않고 먼저 reconcile

## 9. 정책 및 운영 위험

### 9.1 위험과 통제

| 위험 | 심각도 | 통제 |
| --- | --- | --- |
| 모발이식 계열 false negative | 매우 높음 | high-recall screen, 다중 evidence, ambiguous review, 승인 직전 재검증, 샘플 이중 감사 |
| 비의료인·한의사 오판 | 높음 | authoritative source 우선, 기관 identity cross-check, AI 단독 확정 금지 |
| follower threshold stale | 높음 | 관찰 시각·source 저장, TTL, 경계 계정 재확인 |
| 반복·스팸성 접촉 | 높음 | candidate cooldown, DM 무응답 재발송 금지, 문안 similarity 검사, action budget |
| Instagram 약관 위반 | 매우 높음 | official API/수동 경로만 사용, browser scraping 금지, source registry |
| 계정 제한·정지 | 매우 높음 | 수동 승인, 낮은 빈도만으로 안전을 보장하지 않음, 계정 상태 모니터링, 즉시 kill switch |
| AI hallucination·가짜 개인화 | 높음 | content evidence ID 강제, insufficient context 시 생성 금지, 사람 승인 |
| 개인정보·환자 정보 과수집 | 높음 | 공개 전문 정보 최소화, 민감 콘텐츠 저장 금지, retention·접근통제·AI vendor 검토 |
| 저작권 콘텐츠 복제 | 중간~높음 | 전체 영상·이미지 저장 회피, permalink와 최소 excerpt 중심, 원문 재배포 금지 |
| 잘못된 identity merge | 중간 | 자동 merge 금지, platform ID·소속 evidence, operator confirmation |
| 수동 실행 기록 오류 | 중간 | 실행 체크리스트, evidence attachment 선택, `UNKNOWN` 상태, 정기 reconciliation |
| Meta API·permission 변경 | 높음 | version pin, capability registry, 공식 문서 재검증일, contract test, kill switch |

Instagram Community Guidelines는 반복 댓글·콘텐츠와 동의 없는 반복적 상업 연락을 피하라고 명시한다. 이번 업무가 관계 형성을 목적으로 해도 반복 cold outreach가 상대에게 상업 연락이나 spam으로 인식될 수 있으므로 빈도·개인화·중단 정책이 필요하다. [S12]

### 9.2 browser automation 대안 분석

#### 기술적으로 가능한 범위

Playwright·Selenium은 로그인된 Instagram 웹 UI를 열고 profile 탐색, visible follower 확인, post/reel 열기, follow, like, comment, DM 입력 같은 동작을 기술적으로 모사할 수 있다. 가능하다는 사실은 공식 지원이나 정책 허용을 뜻하지 않는다.

#### 약관과 계정 제한 위험

- Instagram 약관은 명시적 허가 없는 자동 접근·정보 수집을 금지한다. 로그인 상태인지 여부와 무관하다. [S10]
- Meta Automated Data Collection Terms는 별도 서면 허가를 요구한다. [S11]
- Instagram은 자동 접근·수집 또는 비인가 앱 상호작용이 탐지되면 계정을 scraping 의심으로 제한할 수 있다고 안내한다. [S13]
- UI 자동 액션은 official API permission·rate control 밖에서 동작하므로 “사람처럼 느리게” 설정해도 정책상 안전해지지 않는다.

#### 유지보수 비용과 UI 취약성

- DOM 구조, accessible label, modal, experiment, locale, 반응형 layout이 자주 바뀔 수 있다.
- 로그인 challenge, consent dialog, 추천 화면, rate warning이 정상 흐름을 깨뜨린다.
- 성공 버튼 클릭과 실제 server-side action 성공을 구분하기 어렵다.
- 회귀 테스트에 실제 계정을 쓰면 그 자체가 외부 action과 계정 위험을 만든다.
- UI 변경 때마다 selector·state machine·reconciliation을 수정해야 한다.

#### 로그인·2FA·session 관리

- password, cookie, session storage, recovery code를 다뤄야 해 credential 위험이 커진다.
- 새 device·IP·headless 환경은 2FA나 checkpoint를 유발할 수 있다.
- session 만료, 동시 로그인, 운영자 직접 사용과 bot 사용이 충돌한다.
- 직원 퇴사·권한 변경·공용 계정 사용 시 추적성과 책임 분리가 어렵다.

#### rate limit·anti-abuse

- 공식적으로 보장된 UI action quota가 없다.
- 계정 연령, 과거 행동, IP·device, action mix, 상대 반응 등 비공개 신호로 제한이 달라질 수 있다.
- retry가 오히려 의심 행동을 늘리거나 duplicate comment·DM을 만들 수 있다.
- anti-abuse 회피를 설계 목표로 삼아서는 안 된다.

#### 결론

browser automation은 MVP와 권장 roadmap에서 제외한다. 운영자에게 profile·post deep link와 복사 가능한 승인 문안을 제공하는 operator-assisted manual 방식이 위험과 유지보수 비용이 훨씬 낮다.

### 9.3 개인정보와 법률 검토

- 공개 정보도 저장·분석·프로파일링 목적, 보유 기간, 제3자 AI 전송에 관한 검토가 필요하다.
- 환자 사례 content에는 건강 정보나 식별 가능한 이미지가 포함될 수 있다. 시스템은 해당 정보를 후보 판정이나 개인화에 필요하지 않은 데이터로 취급한다.
- 국내 개인정보·의료광고·전자적 연락 관련 법률 판단은 이 기술 보고서에서 확정하지 않는다.
- 구현 전 병원 내부 개인정보 처리 기준, 이용할 AI provider의 학습 사용 여부·보유 정책, 삭제 요청 처리, 접근권한을 법무·개인정보 담당자가 확인해야 한다.

## 10. 측정 지표

### 10.1 품질 KPI

| KPI | 권장 정의 | 주의점 |
| --- | --- | --- |
| candidate precision | 감사 표본에서 실제 적합으로 확인된 `ELIGIBLE` / 감사한 `ELIGIBLE` | 운영자 approve를 ground truth와 동일시하지 않는다. |
| 모발이식 false negative rate | 실제 hair-transplant 계열인데 `ELIGIBLE`로 통과한 수 / 감사에서 확인한 실제 hair-transplant 계열 수 | 분모가 작으면 비율과 절대 건수를 함께 본다. 목표는 0건이다. |
| 의료인 판정 accuracy | 직업 ground truth와 일치한 판정 / 감사한 판정 | `REVIEW_REQUIRED`를 오답으로 볼지 별도 coverage와 함께 정의한다. |
| review-required rate | `REVIEW_REQUIRED` / deduped candidates | 너무 낮으면 과신, 너무 높으면 evidence source 부족일 수 있다. |
| evidence freshness | 실행 시 유효 TTL 안인 필수 evidence 비율 | follower와 content에 서로 다른 TTL이 필요하다. |

### 10.2 운영·문안 KPI

| KPI | 권장 정의 |
| --- | --- |
| approval rate | 승인된 draft / 사람이 검토한 draft |
| comment edit rate | 승인 전 실질 수정된 comment / 승인 comment |
| DM edit rate | 승인 전 실질 수정된 DM / 승인 DM |
| duplicate outreach rate | dedupe·cooldown 정책을 위반한 실행 action / 전체 실행 action |
| execution reconciliation rate | 성공·실패가 확인된 action / manual 실행 대상으로 전환된 action |
| daily funnel yield | raw discovered → deduped → assessed → eligible → approved → executed 단계별 수와 전환율 |

### 10.3 관계 KPI

| KPI | 권장 정의 | 데이터 취득 |
| --- | --- | --- |
| follow-back rate | 확인된 follow-back / 실행한 follow | 공식 broad 조회를 가정하지 않고 운영자 확인 |
| DM response rate | 응답한 후보 / 실행 확인된 첫 DM | 운영자 기록 또는 합법적으로 연결된 inbound data |
| interaction continuation rate | 정한 기간 안에 2회 이상 상호 교류한 후보 / 첫 상호 응답 후보 | 기간 정의 필요 |
| opt-out·negative rate | 거절·차단·부정 반응 후보 / 실행 대상 후보 | 즉시 suppression signal로 사용 |

단순히 하루 15명을 채우는 수치는 품질 KPI가 아니다. 미달 사유가 source 부족인지 hard exclude 증가인지도 함께 본다.

## 11. MVP 범위

### 11.1 추천 MVP

- 수동 seed·profile URL 입력
- 허용된 검색 결과·디렉터리 결과의 반수동 import
- username normalization과 candidate dedupe
- evidence ledger와 dimension별 eligibility 화면
- `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED` 계산
- 모발이식 hard-exclude 전용 review queue
- recent content permalink 선택과 운영자 보강 입력
- content analysis, comment draft, DM draft
- 반복·광고성·groundedness QA
- edit·approve·reject·skip과 immutable revision
- `APPROVAL_REQUIRED + ManualInstagramProvider`
- interaction history, cooldown, daily dashboard, KPI 기본 집계
- 모든 외부 액션은 운영자가 직접 실행

### 11.2 조건부 MVP 확장

Phase 0 spike가 성공하고 석지웅 원장 계정이 필요한 Professional Account·Page 조건을 충족하면 다음 read-only 기능을 포함할 수 있다.

- Business Discovery profile snapshot
- exact follower count snapshot
- 공개 recent media metadata
- API usage header와 token health 모니터링

### 11.3 MVP 제외

- Instagram password·cookie 보관
- Playwright·Selenium Instagram 조작
- Instagram 전체 또는 profile 연쇄 scraping
- 타인 게시물 comment API 실행
- cold DM API 실행
- follow·post like API 실행
- `FULL_AUTO`
- 허가되지 않은 private endpoint·모바일 API reverse engineering
- 환자 이미지·영상 archive와 대규모 원문 저장
- 기술 스택에 불필요한 microservice 분리

## 12. 단계별 개발 로드맵

### Phase 0. 정책 결정과 공식 API read-only spike

목표는 코드 본개발 전에 capability 불확실성을 제거하는 것이다.

- 석지웅 원장 Instagram 계정의 Business·Creator·Personal 유형을 확인한다.
- Facebook Page 연결, Meta app, token, permission, App Review·business verification 필요성을 확인한다.
- 테스트용 공개 Professional Account 몇 개로 Business Discovery field와 error case를 검증한다.
- follower, recent feed, Reel metadata, unavailable media, Personal target 실패를 확인한다.
- 공식 hashtag media 응답에 후보화 가능한 target username·owner가 실제로 포함되는지와 quota를 확인한다.
- 실제 response의 API version과 usage header를 기록한다.
- 외부 follow·like·comment·DM은 실행하지 않는다.

완료 기준:

- capability matrix의 `NEEDS_VERIFICATION` 항목별 실제 결과가 남는다.
- 공식 문서 URL, API version, permission, response schema, 오류가 재현 가능하게 기록된다.
- 계정 prerequisites가 충족되지 않으면 read adapter 없이 수동 MVP로 가는 결정이 가능하다.

### Phase 1. 데이터 기반과 수동 discovery

- 핵심 entity와 audit schema를 만든다.
- manual·seed·directory import와 dedupe를 구현한다.
- evidence CRUD와 source registry를 구현한다.
- versioned eligibility engine과 hard exclude review를 구현한다.

검증:

- 명확한 의사·약사, 한의사, hair-transplant, follower 경계, 근거 부족 fixture를 모두 판정한다.
- invalid input, duplicate username, stale evidence를 확인한다.

### Phase 2. content·generation·approval

- content selection과 analysis를 구현한다.
- comment·DM 생성과 QA를 구현한다.
- revision·approval·expiry·reject·skip을 구현한다.
- 오늘의 후보 15명 화면과 evidence drawer를 구현한다.

검증:

- 콘텐츠와 무관한 문안, 반복 문안, 과장·광고 문안이 차단되는지 평가한다.
- 승인 후 수정 시 승인이 무효화되는지 확인한다.

### Phase 3. manual execution과 interaction

- manual provider, deep link, copy action, 실행 체크리스트를 구현한다.
- interaction history와 cooldown을 구현한다.
- unknown execution과 reconciliation queue를 구현한다.

검증:

- 실제 Instagram action 없이 dry-run으로 상태 transition과 duplicate 방지를 검증한다.
- 하나의 승인 action이 두 번 실행 완료 처리되지 않는지 확인한다.

### Phase 4. 조건부 공식 read adapter

- Phase 0 결과가 허용할 때만 Business Discovery adapter를 연결한다.
- token refresh, cache, rate header, error taxonomy, circuit breaker를 구현한다.
- API 데이터가 없을 때 manual evidence로 안전하게 fallback한다.

검증:

- sandbox·test account 또는 허용된 read request로 contract test를 수행한다.
- Personal·비공개·제한·username 변경·rate limit error가 `ELIGIBLE`로 fail-open하지 않는지 확인한다.

### Phase 5. 측정과 정책 개선

- source·query별 precision, edit rate, response, continuation을 분석한다.
- false negative incident review와 policy version 비교를 추가한다.
- cooldown·활동 기준은 데이터와 사용자 결정으로 조정한다.

### 장래 실행 자동화 gate

Meta가 향후 공식 capability를 추가하더라도 다음을 모두 충족하기 전에는 자동 실행하지 않는다.

- 해당 use case를 직접 허용하는 최신 공식 문서
- 필요한 permission과 App Review 승인
- 계정 prerequisites와 rate contract의 실측
- 법무·개인정보·운영 승인
- idempotency, kill switch, dry-run, audit, rollback 또는 reconciliation
- 제한된 canary와 명시적 KPI·중단 기준

## 13. 아직 결정되지 않은 사항

- 하루 최대 15명이 raw discovery인지, `ELIGIBLE`인지, draft까지 준비된 승인 대기 후보인지
- 대상 지역, 언어, 우선 의료 섹터와 sector diversity 기준
- 의사·약사 신원을 통과시키는 최소 evidence 조합
- 최근 활동의 기준 일수와 비활성 계정 처리 방식
- follower snapshot TTL과 10,000 경계 재확인 범위
- follow·comment·DM의 권장 순서와 후보별 일일 action budget
- comment·DM cooldown, 무응답 재접촉, 과거 지인 예외 정책
- 석지웅 원장 계정 유형, Facebook Page 연결, Meta app 준비 상태
- `MANUAL` pilot과 `APPROVAL_REQUIRED + manual execution` 중 첫 release 형태
- 승인 TTL, override 권한, operator가 여러 명일 때 역할 분리
- 콘텐츠·evidence·interaction 보유 기간과 삭제 정책
- 외부 AI provider에 전달 가능한 데이터 범위와 provider 보안 조건
- follow-back·DM response·continuation을 누가 어떤 주기로 기록할지
- 검색 API·디렉터리의 구체 provider와 이용조건
- 애플리케이션 기술 스택과 배포 환경

이 항목들은 현재 분석을 막는 P0 blocker는 아니지만 구현 정책을 확정하기 전 답이 필요하다.

## 14. 사용자 확인이 필요한 질문

별도 질문지에 빠른 답변 형식으로 정리했다.

- `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`

질문 답변 전에도 공식 API read-only spike 설계와 수동 MVP의 논리 설계는 진행할 수 있다. 다만 action 순서, cooldown, 개인정보 보유, 계정 연동은 답변 없이 제품 결정으로 확정하면 안 된다.

## 15. `PROJECT_CONTEXT.md` 반영 제안

### 장기 맥락으로 반영할 내용

- 프로젝트 목적, 발신자, 운영자 역할, 자연스러운 의료인 네트워킹이라는 업무 배경
- 하루 최대 15명의 적합 신규 후보라는 목표
- 의사·약사만 대상이며 한의·모발이식·비의료인·10,000 이상·개인 미특정 기관 계정은 hard exclude라는 요구
- 모발이식 관련 불명확성은 `REVIEW_REQUIRED`로 보낸다는 안전 원칙
- 공식 API·허용된 공개 source 우선, 무단 Instagram scraping 금지
- generation과 execution 분리, 외부 액션은 capability gate와 승인 이력 필요
- 2026-08-17 기준 공식 API의 핵심 제약과 구현 전 재검증 의무
- 본 보고서와 clarification request 경로

### 확정 결정으로 반영하지 않을 내용

- `APPROVAL_REQUIRED`를 최종 운영 모드로 확정하는 것
- cooldown 숫자, 최근 활동 일수, 승인 TTL
- 특정 기술 스택·database·AI provider
- `FULL_AUTO` 도입 일정
- Meta app·Page prerequisite가 충족되었다는 가정

이번 작업 종료 시 위 구분에 따라 `PROJECT_CONTEXT.md`를 갱신한다.

## 공식 출처

| ID | 출처 |
| --- | --- |
| `S1` | [Meta 공식 Instagram API Postman workspace][S1] |
| `S2` | [Meta 공식 Instagram API with Facebook Login 컬렉션][S2] |
| `S3` | [Meta 공식 Instagram API with Instagram Login 컬렉션][S3] |
| `S4` | [Meta Business Discovery 개발자 문서][S4] |
| `S5` | [Meta 공식 Reply to a comment API 자료][S5] |
| `S6` | [Meta 공식 Instagram Send API 자료][S6] |
| `S7` | [Meta 공식 Instagram Private Replies 자료][S7] |
| `S8` | [Meta 공식 Instagram HUMAN_AGENT 자료][S8] |
| `S9` | [Meta Graph API Rate Limits][S9] |
| `S10` | [Instagram Terms of Use][S10] |
| `S11` | [Meta Automated Data Collection Terms][S11] |
| `S12` | [Instagram Community Guidelines][S12] |
| `S13` | [Instagram scraping 제한 안내][S13] |
| `S14` | [Instagram 공개 Professional Account의 검색엔진 색인 안내][S14] |
| `S15` | [Instagram Professional Account 안내][S15] |
| `S16` | [Meta 공식 Instagram Conversations API 자료][S16] |

## 조사 한계와 재검증 조건

- Meta 문서는 지속적으로 갱신되며 API version별 차이가 있다.
- 이 작업에서는 실제 Meta app, token, 석지웅 원장 계정 유형을 확인하거나 API call을 실행하지 않았다.
- Business Discovery의 실제 필드, permission, rate bucket, unavailable case는 Phase 0 read-only spike에서 재검증해야 한다.
- 공식 문서가 명시하지 않은 기능은 가능하다고 확정하지 않았다.
- 본 보고서는 법률 자문이 아니다. 국내 개인정보·의료광고·전자적 연락 규정은 별도 검토가 필요하다.

[S1]: https://www.postman.com/meta/instagram/overview
[S2]: https://www.postman.com/meta/instagram/folder/23987686-3a75357f-e106-47ef-a8d9-af1aadf85365
[S3]: https://www.postman.com/meta/instagram/folder/6raa77c/instagram-api-with-instagram-login
[S4]: https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login/business-discovery/
[S5]: https://www.postman.com/meta/instagram/request/23987686-59e5000b-326c-42a1-8545-b984c7fd0e40
[S6]: https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api?entity=request-23987686-1ef992b2-1a7f-463d-9004-3c020c6a294c
[S7]: https://www.postman.com/meta/instagram/request/23987686-189d7215-22b3-403f-b2f5-a46c7e66a514
[S8]: https://www.postman.com/meta/instagram/request/23987686-3f06ebc8-c5ad-4b8a-be9f-81acdc79245c
[S9]: https://developers.facebook.com/docs/graph-api/overview/rate-limiting/
[S10]: https://www.facebook.com/help/instagram/581066165581870
[S11]: https://www.facebook.com/legal/automated_data_collection_terms
[S12]: https://www.facebook.com/help/instagram/477434105621119/
[S13]: https://www.facebook.com/help/instagram/740480200552298
[S14]: https://www.facebook.com/help/147542625391305
[S15]: https://www.facebook.com/help/instagram/138925576505882
[S16]: https://www.postman.com/meta/instagram/folder/23987686-6a91368f-1fa8-4614-9ed6-7d1e08c21e62
