# Instagram 의료인 네트워킹 MVP 상세 설계 및 구현 계획

## 문서 정보

- 작성일: 2026-08-17
- 대상 프로젝트: `hr-sns-auto`
- 문서 상태: 구현 전 상세 설계
- source of truth: `docs/harness/PROJECT_CONTEXT.md`
- 기반 문서: `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- 범위 밖: 애플리케이션 코드, 프로젝트 skeleton, Instagram 외부 action 실행

## 문서에서 사용하는 구분

- **확정 정책**: 이번 사용자 답변으로 장기 운영 원칙이 확정된 내용이다.
- **구현 권장안**: 구현 가능한 수준으로 좁힌 설계이지만 아직 사용자 Decision은 아니다.
- **미확정**: 구현 단계 전에 사용자 답변 또는 read-only investigation이 필요한 내용이다.

## 핵심 결론

- 첫 release는 `APPROVAL_REQUIRED + MANUAL_EXECUTION`이다.
- 시스템은 후보 발굴부터 문안 승인과 실제 결과 기록까지 담당하고 Instagram의 follow·comment·DM은 운영자가 직접 수행한다.
- 일일 목표는 모든 필수 eligibility gate를 통과한 신규 `ELIGIBLE` 후보를 최대 15명 제시하는 것이다. 15명은 quota가 아니다.
- 모발이식 관련성을 충분히 배제할 수 없는 후보는 `ELIGIBLE`로 통과시키지 않고 `REVIEW_REQUIRED`로 보낸다.
- Instagram 웹 UI scraping과 Playwright·Selenium 기반 browser action automation은 MVP 구현 범위에서 제외한다.
- Meta Business Discovery는 향후 exact username 검증·보강에 사용할 수 있는 선택 기능이다. 발신 계정과 Meta App 상태가 불명확하므로 MVP blocker나 필수 기능이 아니다.
- 권장 기술 방향은 Spring Boot 서버 렌더링 modular monolith와 PostgreSQL, Docker Compose 조합이다. 이 선택은 추천이며 사용자 확정 전에는 `PROJECT_CONTEXT.md`의 Decision으로 기록하지 않는다.
- 첫 구현 단계는 manual seed, evidence ledger, deterministic eligibility와 최소 운영 UI를 갖춘 데이터 기반을 만드는 Phase 1이다.

## 1. 확정된 사용자 결정

### 1.1 일일 후보의 정의

- 일일 제시 수는 raw discovery 결과 수가 아니라 현재 정책의 모든 필수 gate를 통과한 `ELIGIBLE` 신규 후보 수이다.
- 상한은 15명이다.
- 당일 유효 후보가 15명보다 적으면 적은 수만 제시한다.
- 숫자를 채우려고 hard exclude, evidence freshness, identity confidence를 낮추지 않는다.
- 후보가 `ELIGIBLE`이라는 사실과 comment 또는 DM draft가 준비되었다는 사실은 별도 상태로 관리한다.
- 이 보고서에서 “신규 제시 후보”는 canonical candidate가 기존 일일 queue에 제시된 적이 없고 과거 outbound interaction도 없는 후보를 뜻한다. 과거 raw discovery 이력만 있고 운영자에게 제시되지 않은 후보는 재검증 후 신규 제시가 가능하다.

### 1.2 초기 대상

- 대한민국의 한국어 계정을 대상으로 한다.
- 의사·약사를 폭넓게 대상으로 하며 초기 진료과 quota는 두지 않는다.
- 한의사·한의원·한방병원, 모발이식 관련 의료인, 비의료인, 개인을 특정할 수 없는 기관 계정은 제외한다.
- follower 10,000 이상은 제외한다.
- 한국 대상 여부는 공개 계정의 언어·소속·활동 맥락으로 판단하며 국적이나 민감한 개인 속성을 추론하지 않는다.

### 1.3 profession과 identity 근거

- Instagram bio나 category만으로 의사·약사를 확정하지 않는다.
- 원칙적으로 소속 기관, 학회, 공식 의료기관 소개 등 강한 공개 근거 1개와 Instagram profile이 같은 실제 인물이라는 identity 일치 근거를 모두 요구한다.
- 강한 단일 근거가 없으면 동일 원문을 재배포한 결과가 아닌 독립적인 공개 source 2개 이상을 검토한다.
- 근거 부족이나 상충은 `REVIEW_REQUIRED`이다.

### 1.4 최근 활동

- 최근 30일 내 실제 게시 활동을 우선한다.
- 30일을 넘겼다는 이유만으로 `INELIGIBLE`로 만들지 않는다.
- 31~90일은 ranking을 낮춘다.
- 90일 초과는 낮은 우선순위 또는 `REVIEW_REQUIRED`로 표현할 수 있으며 hard exclude가 아니다.
- 90일 초과를 두 방식 중 어느 것으로 활성화할지는 아직 미확정이다. 구현은 `PolicyVersion`으로 두 방식을 지원하고, 본 보고서는 안전 기본안으로 `REVIEW_REQUIRED`를 추천한다.

### 1.5 outreach 순서와 실행

- 한 후보에게 같은 날 여러 신규 outbound action을 몰아서 제안하지 않는다.
- 실제 콘텐츠에 대한 자연스러운 interaction을 먼저 검토하고 DM은 다른 시점의 별도 action으로 다룬다.
- comment와 DM은 각각 별도 draft revision과 별도 approval을 가진다.
- 실제 follow·comment·DM은 운영자가 Instagram에서 직접 수행한다.

### 1.6 cooldown과 suppression

- cold DM에 응답이 없으면 같은 목적으로 다시 보내지 않는다.
- 댓글은 동일 게시물에 한 번만 작성한다.
- 후보 단위 기본 cooldown은 외부 outbound action 실행 또는 실행 불명확 시점부터 30일이다.
- 거절, 명시적 연락 중단 요청, 차단은 candidate-level permanent suppression으로 처리한다.
- 위 숫자와 규칙은 활성 `PolicyVersion`에 기록하며 향후 새 policy version으로만 바꾼다.

### 1.7 데이터 최소화

- 공개 profile·콘텐츠 데이터는 username, permalink, 구조화 사실, 판정 evidence, 필요한 최소 excerpt, 관찰 시점을 중심으로 저장한다.
- Instagram 원본 이미지·영상은 기본 저장하지 않는다.
- 외부 AI provider에는 생성 목적에 필요한 최소 정보만 전달한다.
- 구체 보유 기간, 삭제 주기, 실제 AI provider는 아직 확정하지 않는다.

### 1.8 기타 운영 결정

- Meta account type, Facebook Page 연결, Meta App 준비 상태는 현재 알 수 없다.
- Meta integration 없이 MVP를 구현할 수 있어야 한다.
- browser automation은 MVP에서 제외하지만 영구 금지로 확정하지 않는다. 향후 정책·공식 API·운영 필요성이 바뀌면 별도 조사한다.
- `prompts/tasks/*.md`는 secret이나 민감 정보를 제외하고 기본적으로 Git에 커밋한다.

## 2. MVP scope / out of scope

### 2.1 MVP scope

- manual seed와 profile URL 입력
- 사람이 제공한 공개 검색 결과의 반수동 등록
- 이용조건 검토를 통과한 의료기관·학회·전문가 공개 디렉터리 source 등록과 제한된 수집
- 공개 연관 정보에서 후보 이름·소속·Instagram URL을 발견하는 provenance 기록
- username·profile·identity 중복 탐지
- 공개 evidence 수집, 최소 excerpt 저장, freshness 관리
- profession, identity, Korean target, 한의 계열, 모발이식, follower, 최근 활동, duplicate의 독립 판정
- `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED` 집계
- `ELIGIBLE` 후보 deterministic ranking과 일일 최대 15명 제시
- permalink 중심 recent content 입력·분석과 민감도 gate
- grounded comment와 DM draft 생성, QA, revision 관리
- comment·DM의 독립 approve, edit, reject, skip
- `READY_FOR_MANUAL_EXECUTION` queue와 manual result 기록
- interaction history, 동일 post 중복 방지, 30일 cooldown, suppression
- 운영자·policy version·상태 변경 audit
- source·discovery run·daily funnel 기본 지표

### 2.2 MVP out of scope

- Instagram 전체 웹 UI 또는 profile 연쇄 scraping
- Instagram password, cookie, session, 2FA credential 저장
- Playwright·Selenium 기반 Instagram 탐색이나 action 자동화
- private endpoint 또는 모바일 API reverse engineering
- 공식 API를 통한 타인 게시물 comment, follow, like, cold DM 실행
- `FULL_AUTO` execution mode
- Meta Business Discovery 필수 연동
- 환자 이미지·영상·전체 caption archive
- 환자 개인 식별 정보나 불필요한 건강 정보 분석
- 진료과별 quota와 복잡한 ML ranking
- microservice, message broker, Kubernetes, 별도 data warehouse
- 검색 API 또는 LLM provider를 검증 없이 특정 vendor로 고정하는 일

## 3. 일일 운영 workflow

```text
정해진 시각에 daily run 생성
  -> 허용된 source 실행 또는 운영자 입력 수신
  -> profile·identity dedupe
  -> 최소 evidence 수집과 freshness 확인
  -> dimension별 eligibility 평가
       -> INELIGIBLE: 이유를 보존하고 queue 제외
       -> REVIEW_REQUIRED: 전용 queue에서 운영자 확인
       -> ELIGIBLE: ranking pool 편입
  -> 아직 제시되지 않은 ELIGIBLE을 설명 가능한 점수로 정렬
  -> 최대 15명을 오늘의 후보로 고정
  -> 안전하고 실제 확인 가능한 recent content 선택
  -> content analysis와 comment draft 생성
  -> 필요 시 별도 시점의 DM draft 생성
  -> 운영자 edit·approve·reject·skip
  -> 승인·cooldown·최신 eligibility를 재검사
  -> READY_FOR_MANUAL_EXECUTION
  -> 운영자가 Instagram에서 직접 실행
  -> EXECUTED / FAILED / EXECUTION_UNKNOWN 기록
  -> interaction history·nextAllowedAt·suppression 갱신
```

### 3.1 일일 run 원칙

- 기준 timezone은 `Asia/Seoul`로 명시한다. DB timestamp는 UTC로 저장하고 UI에서 KST로 표시한다.
- 같은 날짜·source·query·policy version 조합에는 안정적인 run key를 사용한다.
- scheduler가 중복 실행되어도 하나의 active run만 처리하도록 DB uniqueness 또는 lock으로 막는다.
- source 하나의 실패가 다른 source 결과를 롤백하지 않는다. source별 run 상태와 오류를 별도로 남긴다.
- daily presentation은 `presentedAt`을 기록한 순간 카운트한다. 이미 15명을 제시한 뒤 reject된 후보를 숫자 보충 목적으로 자동 대체하지 않는다.
- eligibility가 바뀌면 아직 실행하지 않은 draft와 approval을 무효화한다.

### 3.2 운영자 시작점

- 운영자는 `오늘의 후보`, `REVIEW_REQUIRED`, `수동 실행 대기`, `실행 불명확` 네 queue를 우선 확인한다.
- 모발이식 signal이 있는 review 건은 일반 review보다 먼저 표시한다.
- batch approve는 MVP에서 제공하지 않는다.

## 4. Candidate Discovery 상세 전략

### 4.1 공통 discovery contract

각 adapter 또는 수동 입력은 최소한 다음 값을 반환한다.

- `discoverySourceId`
- `sourceType`
- `inputReference`: seed ID, query, directory page 또는 operator submission ID
- `candidateName`과 알려진 소속
- `instagramUsername` 또는 `profileUrl` 중 확인 가능한 값
- `sourceUrl`
- `minimalExcerpt` 또는 구조화 관찰값
- `observedAt`
- `collectionMethod`: `MANUAL`, `API`, `APPROVED_IMPORT`
- `sourceRecordFingerprint`

발견 결과는 자격 판정이 아니다. discovery observation은 evidence 후보로 들어가며 eligibility gate가 별도로 평가한다.

### 4.2 source별 설계

아래 precision은 제품 성능 보장이 아니라 초기 운영 우선순위를 위한 정성 가설이다. 실제 precision은 운영자 판정 결과로 source별 측정한다.

| Source type | 입력 | 출력 | provenance | 이용조건 확인 | 자동화 수준 | expected precision | 주요 failure mode와 처리 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `MANUAL_SEED` | 운영자가 입력한 username, profile URL, 이름·소속 | canonical profile 후보와 입력 메모 | operator ID, 입력 시각, 원문 값 | 보통 낮지만 공개 정보 사용 범위는 동일 적용 | 수동 입력, 정규화·dedupe만 자동 | 높음 | 오타, username 변경, 동명이인이다. identity를 자동 통과시키지 않는다. |
| `HUMAN_SEARCH_RESULT` | 사람이 검색엔진에서 확인해 제공한 result URL·snippet·query | Instagram URL 또는 이름·소속 후보 | query, result URL, 제공자, 관찰 시각 | 검색 결과 재사용·저장 범위를 확인한다 | 수동 발견, 반자동 import | 중간~높음 | snippet이 stale하거나 원문과 다르다. 원문 확인 전 strong evidence로 쓰지 않는다. |
| `SEARCH_API` | 승인된 query template, 지역·직업·site filter, cursor | 공개 result URL·title·snippet | provider, request ID, query hash, page, 수집 시각 | **필수**. API 약관·저장·표시·quota·비용을 source별 검토한다 | 조건부 자동 | 중간 | low precision, 중복, 색인 지연, 정책 변경, quota 오류이다. adapter 실패는 빈 결과로 기록하며 기준을 낮추지 않는다. |
| `PUBLIC_DIRECTORY` | 검토된 의료기관 staff page, 학회·전문가 directory 범위 | 이름, 직업, 전문 분야, 소속, 공개 profile 링크 | directory ID, record URL, observation, parser version | **필수**. 공개 여부만으로 자동 수집 권리를 가정하지 않는다 | manual 또는 허용 범위의 제한 자동화 | profession은 높음, Instagram identity는 중간 | 과거 소속, 페이지 구조 변경, Instagram link 부재이다. stale 처리하거나 identity review로 보낸다. |
| `RELATED_PUBLIC_INFO` | 기존 후보의 공식 기관 page, 학술 행사·인터뷰·공식 협업 page | 공개된 동료 이름·소속·profile URL | origin candidate, relation page, 관찰 시각 | **필수**. Instagram 추천 UI 자동 순회는 금지한다 | 수동 또는 허용 source adapter | 중간~높음 | 관계만 있고 직업·identity가 불명확하다. 독립 evidence를 추가로 요구한다. |
| `META_BUSINESS_DISCOVERY` | 이미 알고 있는 exact username과 충족된 Meta prerequisites | 지원되는 Professional Account의 profile snapshot·일부 media | API version, target username, request ID, observedAt | Meta 공식 문서·permission·App Review 조건 재검증 | 향후 조건부 자동 | known target 검증은 높고 coverage는 제한적 | 발신 계정 prerequisite 미충족, Personal/private target, permission·version 변경이다. `REVIEW_REQUIRED` 또는 manual evidence로 fallback한다. |

### 4.3 source registry gate

`DiscoverySource`는 다음 상태를 가진다.

- `NOT_REVIEWED`: 자동·대량 사용 금지, 운영자 단건 링크 입력만 허용한다.
- `ALLOWED_MANUAL`: 사람이 본 결과의 단건 등록만 허용한다.
- `ALLOWED_AUTOMATED`: 검토된 범위·빈도·필드 안에서 adapter 실행을 허용한다.
- `RESTRICTED`: 저장·표시·수집 방식 제한을 adapter가 강제한다.
- `DISABLED`: 신규 discovery에 사용하지 않는다.

각 source에는 약관 URL, 검토일, 검토자, 허용 collection method, 저장 가능한 필드, 재검토 예정일을 둔다. 검토 만료 시 adapter는 fail closed한다.

### 4.4 MVP discovery 순서

1. Phase 1에서는 `MANUAL_SEED`와 `HUMAN_SEARCH_RESULT`만으로 end-to-end 판정을 검증한다.
2. 이용조건을 확인한 소수 `PUBLIC_DIRECTORY`를 등록하고 profession evidence precision을 측정한다.
3. `RELATED_PUBLIC_INFO`는 운영자가 원문 링크를 제공하는 방식부터 시작한다.
4. 검색 자동화가 실제 병목일 때 provider-neutral `SEARCH_API` adapter를 연결한다.
5. Meta prerequisites가 확인된 경우에만 Business Discovery를 read-only validation/enrichment로 추가한다.

특정 Search API vendor는 이 보고서에서 추천하지 않는다. 2026년 현재의 가격, 신규 사용 가능성, result quality, 저장 정책을 확인하지 않은 상태에서 vendor를 고정하지 않기 위함이다. provider 선택 spike는 동일한 query set으로 coverage·precision·비용·약관·운영 안정성을 비교하고 공식 문서와 계약 조건을 기록해야 한다.

### 4.5 dedupe 순서

1. `platform + normalizedUsername` exact match를 찾는다.
2. platform user ID가 있으면 exact match를 찾는다.
3. canonical profile URL과 과거 username alias를 비교한다.
4. 이름·소속·전문 분야 조합이 유사하면 자동 병합하지 않고 merge suggestion을 만든다.
5. exact duplicate는 새 Candidate를 만들지 않고 기존 Candidate에 discovery provenance만 추가한다.
6. 동일 인물 여부가 불명확하면 `REVIEW_REQUIRED`로 보내며 둘 모두 일일 신규 queue에 자동 제시하지 않는다.

## 5. Eligibility policy

### 5.1 공통 판정 모델

각 dimension은 `PASS`, `FAIL`, `UNKNOWN`, `CONFLICT` 중 하나를 산출한다. `UNKNOWN`과 `CONFLICT`는 최종 집계에서 `REVIEW_REQUIRED`를 만든다.

- `OBSERVATION`: source에서 직접 관찰한 구조화 사실이다.
- `SOURCE_ASSERTION`: 기관·학회 등 source가 명시한 사실이다.
- `AI_INFERENCE`: 모델이 evidence에서 추론한 signal이다. 단독 hard-gate 통과 근거가 될 수 없다.
- `OPERATOR_CONFIRMATION`: 운영자가 원문을 보고 남긴 확인이다. 원문 URL과 이유가 필요하다.

강한 근거는 authoritative하다는 이름만으로 영구 유효하지 않다. source가 사라졌거나 역할·소속이 바뀌었거나 `PolicyVersion`의 freshness를 넘으면 stale이다. exact TTL은 아직 미확정이며 entity와 rule은 `observedAt`, `expiresAt`, `lastVerifiedAt`으로 표현한다.

### 5.2 dimension별 evidence와 판정

| Dimension | required evidence | strong evidence | weak evidence | stale evidence | `PASS` | `FAIL` | review 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PROFESSION` | 의사 또는 약사라는 외부 근거와 identity 연결 | 현재 소속 공식 staff 소개, 공식 의료기관 소개, 학회·전문 단체의 개인 소개 등 | Instagram bio/category, 자기소개 post, 검색 snippet, 출처 불명 기사 | 소속·직함 변경 가능성이 크거나 source가 사라지고 재확인하지 못한 근거 | strong 1개 또는 원출처가 다른 공개 source 2개 이상이 직업을 지지하고 `IDENTITY=PASS` | 신뢰 가능한 근거가 비의료인 또는 대상 외 직업으로 확인 | bio만 존재, source 간 직업 충돌, 약한 근거 하나뿐임 |
| `IDENTITY` | Instagram profile과 evidence 속 실제 인물이 같다는 연결 | 공식 개인·기관 page가 해당 Instagram을 직접 연결하거나 이름·소속·얼굴·전문 분야가 일관된 다중 연결 | 같은 이름, 유사 사진, bio의 소속 표기, 검색 snippet | username·소속·사진 변경 후 재확인하지 못한 연결 | 동일 인물임을 강하게 연결하거나 독립 signal 조합이 충돌 없이 일치 | 다른 사람, 기관 전용 계정, impersonation으로 확인 | 동명이인, 공동 운영, 개인 특정 불가, 연결 signal 충돌 |
| `KOREAN_TARGET` | 대한민국 활동 맥락과 한국어 소통 가능성 | 국내 공식 소속과 한국어 profile·최근 content의 일관된 조합 | 한글 username/bio 하나, 위치 tag 하나 | 오래된 국내 소속만 있고 현재 활동이 불명확 | 국내 활동과 한국어 사용이 공개 사실로 확인 | 명백히 대상 시장 밖이며 한국어 계정이 아님 | 다국어·해외 활동이 혼재하고 한국 대상 여부 불명확 |
| `TRADITIONAL_KOREAN_MEDICINE_EXCLUSION` | 의사·약사 자격·기관 유형 확인 | conventional hospital·clinic·pharmacy의 공식 역할, 의사·약사 직함 | 단순 `doctor`, 번역된 title, Instagram category | 기관 유형·직함이 바뀌었거나 오래된 profile | 의사·약사이며 한의 계열이 아님이 확인 | 한의사, 한의원, 한방병원 소속 또는 운영이 확인 | `doctor`만 있고 자격 체계 불명확, 양·한방 표현 충돌 |
| `HAIR_TRANSPLANT_EXCLUSION` | 소속기관 서비스, profile, 확인 가능한 최근 활동에서 hair-transplant signal을 별도 검사 | 모발이식·탈모수술·헤어라인 교정·hair restoration·FUE·FUT 서비스나 반복 content, 모발이식 기관 핵심 의료인이라는 공식 근거 | 단일 hair 키워드, 일회성 교육·협진·뉴스 언급 | 현재 서비스와 최근 활동을 확인할 수 없는 과거 non-hair 근거 | 다른 전문 분야·서비스가 신뢰 가능한 근거로 일관되고, 정한 확인 범위에서 hair signal이 없음을 검토 완료 | strong positive signal 하나 이상으로 실제 주요 업무 관련성이 확인 | hair 표현의 맥락 불명확, 기관 service 확인 불가, positive·negative evidence 충돌, 확인 범위 부족 |
| `FOLLOWER_THRESHOLD` | 공개 follower 관찰값, source, 관찰 시점 | 허용된 공식 API exact count 또는 운영자가 profile에서 확인한 명확한 현재값 | 검색 snippet, 제3자 통계, 반올림 표기 | 정책 TTL 초과 또는 승인 전 profile 재확인 실패 | 신선하고 임계값과 혼동되지 않는 값이 10,000 미만 | 신선한 값이 10,000 이상 | 값 없음, stale, `10K`처럼 경계가 모호함, 정책의 재확인 band 안임 |
| `RECENT_ACTIVITY` | 확인 가능한 post/reel permalink와 `publishedAt` | 실제 원문 timestamp 또는 허용된 공식 API timestamp | 검색 snippet 날짜, bio의 “활동 중” 표현 | content 삭제·비공개 전환 또는 마지막 확인 이후 장기간 미검증 | 30일 이내면 우선, 31~90일이면 낮은 ranking으로 통과 | 활동 기간만으로는 `FAIL`을 만들지 않음 | timestamp 불명확 또는 90일 초과를 review로 설정한 활성 policy |
| `DUPLICATE_IDENTITY_PROFILE` | canonical profile과 기존 candidate·interaction 비교 | platform ID exact match, normalized username·profile URL match, 운영자 확인 merge | 이름·소속 유사도, 사진 유사성 | 과거 username만 있고 현재 연결을 확인하지 못함 | 기존 canonical record와 일치하지 않음 | exact duplicate이면 신규 discovered record를 기존 Candidate에 병합하고 신규 제시 대상에서 제외 | 같은 사람일 가능성은 높지만 자동 병합 근거가 부족 |

### 5.3 모발이식 safety gate

- hair dimension은 recall 우선으로 signal을 찾되 최종 `FAIL`은 실제 업무 관련성이 있는 근거를 요구한다.
- `PASS`는 단순 키워드 부재가 아니라 확인 범위가 충족되었다는 기록이 있어야 한다.
- `UNKNOWN`이나 `CONFLICT`를 confidence score로 상쇄할 수 없다.
- 새 hair signal이 발견되면 현재 assessment를 만료시키고 모든 미실행 outreach action을 `CANCELLED_POLICY_CHANGE`로 바꾼다.
- 운영자가 hair review를 해소할 때 evidence URL, 판단 이유, 확인 시점이 필수이다.
- 표본 감사에서 false negative가 발견되면 incident로 기록하고 해당 policy version의 유사 후보를 재평가한다.

### 5.4 최종 집계

```text
exact duplicate
  -> 신규 record를 병합하고 신규 queue에서 제외

hard dimension 중 하나라도 FAIL
  -> INELIGIBLE

hard dimension에 UNKNOWN 또는 CONFLICT가 하나라도 존재
  -> REVIEW_REQUIRED

HAIR_TRANSPLANT_EXCLUSION이 UNKNOWN 또는 CONFLICT
  -> 항상 REVIEW_REQUIRED

모든 hard dimension PASS
  -> ELIGIBLE

RECENT_ACTIVITY
  -> 기본적으로 ranking만 조정
  -> 활성 policy가 90일 초과 review를 선택한 경우 REVIEW_REQUIRED
```

hard dimension은 profession, identity, Korean target, 한의 계열 제외, 모발이식 제외, follower threshold이다. duplicate는 intake gate로 먼저 처리한다. recent activity는 hard exclude가 아니다.

### 5.5 evidence freshness와 재검사

- source별·dimension별 TTL을 `PolicyVersion`에 둔다.
- TTL이 정해지기 전 production policy를 임의 활성화하지 않는다.
- 실행 준비 시 profession·identity·hair·follower·target content·cooldown을 다시 검사한다.
- source URL이 사라져도 과거 assessment 설명을 위해 evidence record를 삭제하지 않고 `UNAVAILABLE` 또는 `EXPIRED`로 표시한다.
- retention에 따른 물리 삭제와 audit 보존 범위는 개인정보 검토 후 별도 정책으로 정한다.

## 6. Ranking policy

### 6.1 적용 범위

- 현재 assessment가 `ELIGIBLE`인 후보에만 적용한다.
- suppression, cooldown, duplicate, stale hard evidence를 ranking score로 상쇄하지 않는다.
- 후보가 15명 이하이면 score와 관계없이 유효 후보를 모두 제시할 수 있다.
- score는 추천 순서를 설명하는 값이며 자동 outreach 판단값이 아니다.

### 6.2 권장 100점 규칙

| 항목 | 배점 | 계산 예시 |
| --- | ---: | --- |
| evidence confidence | 25 | strong·fresh·독립 evidence가 모두 충족되면 25, 일부가 약하지만 gate는 통과하면 단계적으로 감점한다. |
| 최근 활동 | 25 | 7일 이내 25, 8~30일 20, 31~60일 10, 61~90일 5, 90일 초과 0이다. |
| commentable content | 20 | 실제 확인 가능한 최근 content와 구체 anchor가 있고 민감도 gate를 통과하면 높게 준다. |
| 개인 브랜딩 활동 | 15 | 특정 개인이 전문 지식·관점을 지속적으로 공유하는 공개 계정이면 높게 준다. 기관 공지 위주이면 낮춘다. |
| 의료 분야 차별성 | 10 | 모발이식과 명확히 다른 전문 분야이고 전문성이 구체적일수록 높게 준다. 진료과 quota로 사용하지 않는다. |
| interaction readiness | 5 | 과거 outbound가 없고 suppression·cooldown risk가 없으면 5이다. |

### 6.3 계산과 설명

- 각 component의 원점수, 이유 code, 사용 evidence ID, policy version을 ranking snapshot에 남긴다.
- 동점이면 `recentActivityAt DESC`, `evidenceConfidence DESC`, `firstDiscoveredAt ASC`, `candidateId ASC` 순서로 안정적으로 정렬한다.
- 운영자 edit·reject 결과를 다음 source precision 분석에 사용하지만 MVP에서 ML 학습은 하지 않는다.
- ranking weight 변경은 새 `PolicyVersion`과 회귀 fixture 검증을 거친다.
- hair 관련 signal은 음수 점수가 아니라 eligibility review gate로 처리한다.

## 7. Content analysis 정책

### 7.1 입력과 저장 범위

content별 최소 record는 다음과 같다.

- `permalink`
- `publishedAt`
- `contentType`: `POST`, `REEL`, `CAROUSEL`, `OTHER`
- `captionExcerpt`: 생성에 필요한 최소 구간만 저장한다.
- `observedAt`과 `retrievalMethod`
- `availabilityStatus`
- `analysisSummary`
- `groundingPoints`
- `commentable`
- `sensitivityLevel`
- `externalAiAllowed`
- `draftGenerationStatus`

원본 이미지·영상과 전체 caption을 기본 저장하지 않는다. 운영자가 permalink를 열어 실제 내용을 확인할 수 있어야 하며, 시스템에는 판정과 생성에 필요한 최소 excerpt와 구조화 요약만 남긴다.

### 7.2 분석 단계

1. permalink, 게시 시각, content type, 접근 가능 여부를 확인한다.
2. 운영자 입력 또는 허용된 read source에서 최소 excerpt를 만든다.
3. 환자 사례·건강 정보·식별 가능한 정보가 포함될 가능성을 규칙으로 먼저 탐지한다.
4. 외부 AI 전송 허용 여부를 판정한다.
5. content 주제, 전달 목적, 확인 가능한 구체 포인트, 피해야 할 표현을 구조화한다.
6. commentable 여부와 comment draft 생성 가능 여부를 별도로 정한다.
7. 분석 input hash와 version을 남겨 content가 바뀌면 이전 분석과 draft를 만료시킨다.

### 7.3 민감 콘텐츠 gate

| `sensitivityLevel` | 예시 | 외부 AI 전달 | comment draft |
| --- | --- | --- | --- |
| `NONE` | 일반적인 전문 지식, 행사, 공개 교육 내용 | 최소 excerpt만 가능 | 가능 |
| `LOW` | 일반적 임상 교육이나 비식별 통계 | 필요성 검토 후 최소 구조화 요약만 가능 | 가능하나 환자 outcome 단정 금지 |
| `POSSIBLE_PATIENT_DATA` | 환자 사례, 전후 사진, 진단·시술 맥락, 식별 가능성이 불명확 | 원본·전체 caption 전달 금지 | 운영자가 비식별 일반 주제 anchor를 만들 수 있을 때만 가능 |
| `SENSITIVE_OR_IDENTIFIABLE` | 얼굴·이름·날짜·상세 건강정보 등 식별 가능 정보 | 금지 | 기본 `DRAFT_NOT_ALLOWED` |

- 민감도 판정은 “공개 게시물”이라는 이유만으로 완화하지 않는다.
- 외부 AI가 필요하면 환자 세부를 제거한 `redactedGroundingSummary`만 전달한다.
- 비식별화가 확실하지 않으면 외부 AI를 호출하지 않는다.
- 댓글에서 환자 사례 세부나 결과를 재언급하지 않는다.
- 운영자가 직접 쓴 안전한 일반 댓글도 별도 approval을 거친다.

### 7.4 commentable과 draft 가능 상태

- `COMMENTABLE`: 실제 내용과 구체 anchor를 확인했고 민감도 정책을 통과했다.
- `NOT_COMMENTABLE`: 홍보·채용 공지처럼 자연스러운 전문 교류 anchor가 없거나 민감도가 높다.
- `INSUFFICIENT_CONTEXT`: permalink는 있으나 내용을 확인하지 못했다.
- `CONTENT_UNAVAILABLE`: 삭제·비공개·접근 불가이다.
- `DRAFT_ALLOWED`: 외부 AI 또는 규칙 기반 보조 생성이 가능하다.
- `DRAFT_REQUIRES_OPERATOR_SUMMARY`: 운영자의 비식별 요약이 있어야 한다.
- `DRAFT_NOT_ALLOWED`: 현재 정보로 생성하지 않는다.

## 8. Comment/DM generation 정책

### 8.1 공통 grounding bundle

모든 생성 요청은 다음 구조의 snapshot에 결속한다.

```text
GroundingBundle
  candidateId
  profileId
  senderContextVersion
  professionAndSpecialtyFacts[]
  selectedContentId? 
  groundingPoints[]
  evidenceIds[]
  prohibitedInferences[]
  tonePolicyVersion
  recentDraftSimilarityReferences[]
  sensitivityDecision
  inputHash
```

- 생성 결과에는 사용한 `groundingPointId`와 `evidenceId`를 역참조한다.
- 자연어 문장 중 개인화된 주장마다 source로 추적 가능한 anchor가 있어야 한다.
- 입력이 바뀌면 같은 revision을 덮어쓰지 않고 새 revision을 만든다.
- model identifier, provider, prompt version, 생성 시각, QA 결과를 저장한다.
- provider log에 secret, 전체 profile, 전체 caption을 남기지 않는다.

### 8.2 Comment generation

목표 말투는 다음과 같다.

- 같은 의료인에 대한 예의를 지킨다.
- 실제 콘텐츠를 읽은 흔적이 되는 구체 포인트 하나를 담는다.
- 과장된 칭찬, 영업, 상담 유도, 자기 병원 홍보를 넣지 않는다.
- 지나치게 친한 척하거나 의학적 효과에 동의·보증하지 않는다.
- 짧고 자연스럽게 작성한다.
- 최근 사용 문구와 반복을 최소화한다.

단순 범용 문장 template의 synonym random substitution은 사용하지 않는다. 생성 흐름은 `content facts -> grounding point 선택 -> tone-constrained draft -> groundedness/안전/중복 QA` 순서이다.

필수 QA는 다음과 같다.

- 선택한 content permalink와 연결된 anchor가 최소 1개 존재한다.
- anchor가 excerpt 또는 운영자 확인 사실을 벗어나지 않는다.
- “최고”, “완벽”, “무조건” 등 과장·효과 보장 표현이 없다.
- 환자·가족·사생활의 세부를 반복하지 않는다.
- 광고, 예약, 링크, 협업 제안이 없다.
- 최근 승인·실행 comment와 exact·n-gram 유사도가 임계값을 넘지 않는다.
- QA 실패 원인을 사람이 볼 수 있고 재생성 또는 직접 edit가 가능하다.

grounding이 부족하면 자연스러운 범용 댓글을 만들어 채우지 않고 `INSUFFICIENT_GROUNDING`으로 종료한다.

### 8.3 DM generation

발신자는 압구정에서 모발이식병원을 운영하는 석지웅 원장이다. 기존 예시의 정중하고 부담 없는 어조를 유지하되 다음 semantic slot을 사용한다.

- 상대 이름 또는 안전한 호칭
- 확인된 전문 분야
- 실제로 확인한 최근 content의 한 가지 포인트 또는 공개 전문 활동
- 석지웅 원장의 짧은 소개
- 다른 의료 분야 종사자로서 배우고 자연스럽게 교류하고 싶다는 의도
- 답변을 압박하지 않는 마무리

다음은 금지한다.

- 사생활, 가족, 환자, 성격에 관한 inference
- 읽지 않은 content를 읽었다는 주장
- 실제 interaction history에 없는 follow·comment·만남을 했다는 주장
- 예약, 소개, 공동 마케팅, 답변을 압박하는 표현
- 무응답 후보에게 같은 목적의 동일·유사 DM 재생성
- 이름·전문 분야만 치환한 고정 template 반복

DM은 comment와 다른 `OutreachAction`, draft series, revision, approval을 가진다. comment 승인이나 실행이 DM 승인을 의미하지 않는다. comment 이후 candidate cooldown이 끝나고 별도 검토 시점이 되었을 때 최신 evidence로 DM을 생성하는 방식을 우선한다.

### 8.4 생성 실패와 fallback

- LLM timeout·quota·provider 오류는 draft 없음으로 기록한다. 허위 fallback 문장을 자동 승인 대기 상태로 만들지 않는다.
- 규칙 기반으로 안전성을 판단할 수 없는 결과는 `QA_FAILED`이다.
- operator는 AI 없이 직접 draft를 작성할 수 있지만 동일 grounding·revision·approval 규칙을 적용한다.
- 생성 provider를 바꾸어도 evidence·prompt·result audit contract는 유지한다.

## 9. Approval 및 manual execution workflow

### 9.1 상태 분리

eligibility와 outreach lifecycle은 서로 다른 상태로 저장한다.

```text
EligibilityStatus
  ELIGIBLE | INELIGIBLE | REVIEW_REQUIRED

DraftStatus
  DRAFTING
  -> QA_FAILED | READY_FOR_REVIEW
  -> SUPERSEDED | INVALIDATED

OutreachStatus
  PLANNED
  -> DRAFT_READY
  -> AWAITING_APPROVAL
      -> REJECTED
      -> SKIPPED
      -> APPROVED
          -> READY_FOR_MANUAL_EXECUTION
              -> EXECUTED_MANUAL
              -> FAILED_MANUAL
              -> EXECUTION_UNKNOWN
      -> APPROVAL_INVALIDATED -> AWAITING_APPROVAL
  -> CANCELLED
  -> SUPPRESSED
```

후보는 `ELIGIBLE`이면서 오늘 outreach가 `SKIPPED`일 수 있다. 반대로 과거 승인된 action이 있어도 현재 eligibility가 review로 바뀌면 execution 준비가 될 수 없다.

### 9.2 command와 transition

| Command | 허용 전 상태 | 결과 | 필수 audit |
| --- | --- | --- | --- |
| `GENERATE` | `PLANNED`, `QA_FAILED` | 새 draft revision, QA 통과 시 `DRAFT_READY` | input hash, evidence, prompt/model, 생성자 |
| `EDIT` | 실행 완료 전 모든 draft 상태 | immutable 새 revision, `AWAITING_APPROVAL` | 이전 revision, diff 또는 이전 text hash, editor, 이유 |
| `SUBMIT_FOR_APPROVAL` | `DRAFT_READY` | `AWAITING_APPROVAL` | 제출 revision·hash |
| `APPROVE` | `AWAITING_APPROVAL` | `APPROVED`, gate 통과 시 `READY_FOR_MANUAL_EXECUTION` | reviewer, approved revision·hash, 시각 |
| `REJECT` | `AWAITING_APPROVAL` | 해당 action `REJECTED` | reason code, note |
| `SKIP` | `AWAITING_APPROVAL` 또는 오늘의 queue | `SKIPPED` | 이유, 재노출 가능 시각 또는 무기한 여부 |
| `RECORD_EXECUTED` | `READY_FOR_MANUAL_EXECUTION` | `EXECUTED_MANUAL` | 실제 target 재확인, executedAt, operator, result |
| `RECORD_FAILED` | `READY_FOR_MANUAL_EXECUTION` | `FAILED_MANUAL` | 실패 이유, 외부 action 미발생 확실성 |
| `RECORD_UNKNOWN` | `READY_FOR_MANUAL_EXECUTION` | `EXECUTION_UNKNOWN` | attemptedAt, 불명확 이유, 확인 예정 |
| `RECONCILE` | `EXECUTION_UNKNOWN` | executed 또는 failed 확정 | 확인 source, confirmer, confirmedAt |

`REJECT`는 내부 draft 거절이며 상대방의 연락 거절과 다르다. 외부 거절·중단 요청은 `InteractionHistory`에 기록하고 permanent suppression을 만든다.

### 9.3 approval binding과 무효화

`Approval`은 action ID, draft row ID, revision number, normalized text hash, target profile, target content에 결속한다. 다음 중 하나가 바뀌면 승인을 자동 무효화한다.

- draft text 또는 target content
- target username 또는 canonical profile
- selected grounding evidence
- profession·identity·hair·follower eligibility 결과
- content availability 또는 sensitivity
- cooldown·suppression 상태
- approval expiry

승인 이후 edit는 새 revision을 만들고 `APPROVAL_INVALIDATED -> AWAITING_APPROVAL`로 이동한다. 운영자가 수정한 문장을 곧바로 실행 가능 상태로 만들지 않는다.

### 9.4 `READY_FOR_MANUAL_EXECUTION` gate

다음을 모두 만족해야 한다.

- 현재 candidate eligibility가 `ELIGIBLE`이다.
- permanent 또는 action-specific suppression이 없다.
- `nextAllowedAt <= now`이다.
- 같은 후보에게 당일 다른 신규 outbound action이 ready 또는 executed 상태가 아니다.
- 승인 revision·hash와 현재 draft가 일치한다.
- comment이면 target content가 여전히 접근 가능하고 동일 post 중복이 없다.
- 운영자가 target username, action type, approved text를 마지막으로 확인할 수 있다.

MVP에는 Instagram API 실행 버튼을 두지 않는다. 제공 기능은 profile/content deep link, 승인 text 복사, 수동 체크리스트, 결과 입력뿐이다.

### 9.5 실행 결과 의미

- `EXECUTED_MANUAL`: 운영자가 실제 action 성공을 직접 확인했다.
- `FAILED_MANUAL`: action이 발생하지 않았음을 운영자가 확인했다. 재시도에는 새 실행 확인이 필요하지만 text가 바뀌지 않으면 기존 승인 재사용 여부는 approval TTL 정책을 따른다.
- `EXECUTION_UNKNOWN`: 클릭·전송 결과를 확신할 수 없다. 성공으로 추정하지 않으며 중복 방지를 위해 보수적으로 cooldown을 적용한다.
- 완료된 action record는 수정하지 않고 correction event를 추가한다.

## 10. Cooldown / suppression 정책

### 10.1 candidate-level 규칙

- 확인된 outbound 실행 후 `nextAllowedAt = executedAt + 30일`이다.
- `EXECUTION_UNKNOWN`이면 `attemptedAt + 30일`을 임시 적용한다. 이후 미실행으로 확정되면 새 correction event로 다시 계산한다.
- 같은 candidate의 다른 username·profile에도 candidate-level cooldown을 공유한다.
- 하루 신규 outbound action은 최대 1개이다. 30일 cooldown보다 짧은 당일 중복을 별도 transaction gate로 막는다.

### 10.2 action별 규칙

| Action | 중복 방지 key | 재접촉 규칙 |
| --- | --- | --- |
| `COMMENT` | candidate/profile + content item + action type | 동일 post에는 한 번만 실행한다. unknown도 해소 전 재시도하지 않는다. |
| `DM` | candidate + purpose code + cold-outreach sequence | cold DM이 실행되고 무응답으로 닫히면 같은 목적의 재발송을 영구 차단한다. 상대가 응답한 이후 대화는 별도 inbound/relationship use case이다. |
| `FOLLOW` | candidate/profile + follow lifecycle | MVP는 수동 결과만 기록한다. 반복 follow/unfollow를 outreach 전략으로 사용하지 않는다. |

### 10.3 suppression scope

- `CANDIDATE_PERMANENT`: 거절, 명시적 연락 중단, 차단, 잘못된 대상 등이다. 모든 신규 outreach를 막는다.
- `ACTION_PURPOSE_PERMANENT`: cold DM 무응답처럼 특정 action·purpose의 반복만 막는다.
- `TEMPORARY`: candidate cooldown 또는 운영자 skip-until이다.
- `POLICY`: hair signal, eligibility 변경, 법무·privacy hold 등이다.

suppression에는 `reasonCode`, `scope`, `sourceInteractionId`, `effectiveAt`, `expiresAt` nullable, `createdBy`, `note`를 둔다. permanent suppression 해제는 일반 approve가 아니라 별도 권한과 사유가 필요한 audit action으로 설계한다.

### 10.4 concurrency와 계산

- `nextAllowedAt`은 조회 편의를 위한 snapshot이며 source of truth는 immutable interaction·suppression event와 적용 policy version이다.
- manual execution 완료 transaction은 OutreachAction 상태 변경, InteractionHistory 추가, cooldown snapshot 갱신, idempotency 확인을 함께 커밋한다.
- 두 운영자가 같은 candidate를 열어도 optimistic lock과 unique action key로 이중 완료를 막는다.

## 11. 기술 스택 비교

### 11.1 비교 전제

- 소수 운영자가 쓰는 내부 업무 도구이다.
- 핵심 난도는 대규모 트래픽이 아니라 관계형 무결성, 상태 전이, 설명 가능한 규칙, audit이다.
- 첫 release UI는 rich client보다 한 화면의 빠른 검토와 오류 방지가 중요하다.
- 배포 단위와 운영 구성요소를 최소화한다.

### 11.2 옵션 비교

| 기준 | Option A: Spring Boot modular monolith + server-side UI | Option B: Spring Boot REST + 별도 SPA | Option C: Python lightweight web app |
| --- | --- | --- | --- |
| MVP 구현 복잡도 | 낮음~중간. 한 codebase·runtime·배포 단위이다. | 높음. backend·frontend·API contract·두 build를 관리한다. | 낮음~중간. API와 LLM 연결은 빠르지만 admin UI·ORM·migration·scheduler 조합을 선정해야 한다. |
| 장기 유지보수성 | 높음. 명시적 domain/service/repository 경계와 정적 타입이 상태 workflow에 유리하다. | 기능이 커질 때 높지만 현재는 양쪽 변경 비용이 크다. | 팀의 Python 숙련도에 따라 높을 수 있으나 convention을 프로젝트가 더 많이 정해야 한다. |
| 데이터 모델·transaction | 매우 적합. Spring transaction과 JPA 또는 JDBC 선택지가 있다. | backend는 A와 동일하게 매우 적합하다. | SQLAlchemy 등으로 충분하지만 transaction·session 규칙을 일관되게 정해야 한다. |
| scheduler | Spring scheduling으로 단일 instance daily trigger가 단순하다. run idempotency는 DB가 담당한다. | backend는 A와 동일하다. | 별도 scheduler 또는 process 운영 결정을 더 일찍 해야 한다. request 후 background task만으로 durable daily job을 대신하지 않는다. |
| LLM/Search API integration | 충분하다. provider port와 HTTP client adapter로 격리한다. | backend는 A와 동일하다. | 가장 빠른 편이며 AI SDK 선택 폭이 넓다. |
| UI 구현 비용 | 낮다. Spring MVC + Thymeleaf와 최소 JS로 queue/detail/drawer를 만든다. | 가장 높다. 상호작용은 풍부하지만 별도 React/Next.js 개발이 필요하다. | Jinja2 등 server rendering이면 낮고, 별도 admin framework 선택 시 제약을 검토해야 한다. |
| 테스트 용이성 | 높다. rule unit test, MVC test, PostgreSQL container integration test를 한 build에서 수행한다. | backend·frontend·contract·E2E 층을 모두 관리해야 한다. | pytest 기반 단위·통합 테스트가 빠르지만 UI와 scheduler integration fixture를 별도 구성한다. |
| 배포·운영 | 낮다. app + PostgreSQL 두 service로 시작할 수 있다. | 높다. backend, frontend, DB와 reverse proxy·CORS·auth 경계를 운영한다. | 낮음~중간. app + DB로 가능하지만 worker를 추가하면 service가 늘어난다. |
| 향후 확장 | 내부 workflow, read adapter, 정책 versioning에 충분하다. UI가 매우 복잡해지면 REST/SPA를 나중에 추가할 수 있다. | 복잡한 실시간 UI와 독립 frontend 팀에 가장 유리하다. | 데이터·AI 실험이 많아질 때 유리하다. 대형 transactional domain으로 커질 때 discipline이 중요하다. |
| 불필요한 복잡성 | 현재 요구에 가장 적다. | 현재 MVP에는 가장 크다. | 낮지만 라이브러리 조합과 background job 운영 선택이 숨은 복잡성이 될 수 있다. |

### 11.3 2026-08-17 공식 문서 확인

- Spring Boot 공식 system requirements는 4.1.0을 stable로 표시하고 최소 Java 17을 요구한다. 구체 application version은 구현 시점에 다시 확인한다.
- Spring Boot 공식 문서는 Spring Data JPA, MVC template engine, Testcontainers integration을 제공한다.
- Spring Framework 공식 문서는 `TaskScheduler`, cron, `@Scheduled`, Quartz integration을 제공한다. MVP 단일 instance는 단순 scheduler와 DB idempotency로 충분하다.
- Next.js 공식 self-hosting 문서는 Node.js server 또는 Docker 운영을 설명한다. Option B는 기술적으로 가능하지만 별도 runtime·deployment boundary가 생긴다.
- FastAPI 공식 문서는 작은 in-process background task와 여러 process·server가 필요한 무거운 작업을 구분한다. Option C에서 daily job durability는 별도 설계해야 한다.
- PostgreSQL 공식 문서는 18을 current, 19를 beta로 표시한다. 구현 시점의 지원 stable version을 pin하고 beta version을 선택하지 않는다.

공식 참고:

- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot SQL/JPA: https://docs.spring.io/spring-boot/reference/data/sql.html
- Spring MVC template engines: https://docs.spring.io/spring-boot/reference/web/servlet.html
- Spring scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Boot Testcontainers: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Next.js self-hosting: https://nextjs.org/docs/app/guides/self-hosting
- FastAPI background task caveat: https://fastapi.tiangolo.com/tutorial/background-tasks/#caveat
- PostgreSQL documentation: https://www.postgresql.org/docs/
- Docker Compose documentation: https://docs.docker.com/compose/

## 12. 추천 기술 스택

### 12.1 추천안

**Option A: Spring Boot 기반 서버 렌더링 modular monolith**를 추천한다.

- Java LTS. 구체 버전은 skeleton 생성일의 Spring Boot 호환성과 조직 표준을 확인해 pin한다.
- 현재 supported stable Spring Boot release
- Spring MVC + Thymeleaf + 최소한의 progressive JavaScript
- Spring Data JPA를 기본 persistence layer로 사용하되 복잡한 조회는 명시적 query로 작성한다.
- PostgreSQL stable release
- Flyway database migration
- Spring Security 기반 named operator authentication과 role authorization
- Spring scheduling + DB-backed `DiscoveryRun` idempotency
- provider port + adapter 구조의 Search/LLM/Meta integration
- JUnit + rule unit tests + Spring integration tests + Testcontainers PostgreSQL
- Docker image + Docker Compose의 app/PostgreSQL 구성

### 12.2 추천 이유

- approval, revision, execution, cooldown을 하나의 transaction boundary 안에서 안전하게 다루기 쉽다.
- 관계형 제약, audit, versioned policy가 핵심인 업무 특성과 잘 맞는다.
- 서버 렌더링으로 queue와 candidate workspace를 만들면 별도 SPA 없이 첫 release UI를 완성할 수 있다.
- scheduler, web, persistence, security, integration test를 한 deployable에서 관리할 수 있다.
- provider adapter를 domain 밖에 두면 Meta 또는 Search API를 나중에 추가해도 핵심 정책을 바꾸지 않는다.

### 12.3 포기하는 장점

- Option B의 풍부한 client-side interaction과 frontend/backend 독립 개발 속도를 포기한다.
- Option C의 빠른 AI 실험, Python 중심 SDK 사용 편의, 짧은 초기 코드량을 포기한다.
- server-side UI가 복잡한 drag-and-drop이나 실시간 협업 화면으로 커지면 별도 frontend가 유리할 수 있다. 현재 요구에는 그 비용을 선투자하지 않는다.

### 12.4 확정 상태와 version 원칙

- 이 기술 스택은 보고서 추천안이며 확정 Decision이 아니다.
- 구현 전 사용자 확인이 필요하다.
- 구체 minor/patch version은 skeleton 생성 시 공식 지원 상태를 다시 확인하고 lock file/build file에 고정한다.
- 검색·LLM·Meta provider는 core dependency가 아니라 runtime-disabled adapter로 둔다.

## 13. 논리 모듈 구조

### 13.1 구성 원칙

- 하나의 Spring Boot application과 하나의 database를 사용하는 modular monolith이다.
- 초기에는 build-level multi-module로 나누지 않고 top-level package boundary와 package visibility test로 경계를 유지한다.
- 각 package는 `domain`, `application`, `port`, `adapter`를 필요한 만큼만 둔다. 모든 계층을 기계적으로 반복하지 않는다.
- generation과 external execution 책임은 분리한다.
- provider SDK type이 domain entity나 application use case에 노출되지 않게 한다.

### 13.2 module 책임

| Module | 책임 | 직접 사용할 수 있는 module |
| --- | --- | --- |
| `candidate` | Candidate·SocialProfile·Evidence, username 정규화, identity merge suggestion, suppression snapshot | `policy`의 value snapshot만 사용한다. 다른 업무 module에 의존하지 않는 기반이다. |
| `discovery` | DiscoverySource·DiscoveryRun, source 실행, raw observation 정규화, candidate intake와 provenance | `candidate`, `eligibility` application port, provider discovery port, `policy` |
| `eligibility` | dimension rule, assessment snapshot, final aggregation, hair safety gate, ranking input fact 계산 | `candidate` read model, `policy` |
| `content` | permalink intake, 최소 excerpt, availability·sensitivity, ContentAnalysis, grounding point | `candidate`, provider read port, `policy` |
| `generation` | comment·DM generation, immutable revision, prompt/model metadata, groundedness·중복·안전 QA | `candidate`, `content`, LLM port, `policy`; `outreach`나 execution provider에는 의존하지 않음 |
| `outreach` | action planning, approval state machine, revision binding, ready gate, manual execution checklist | `candidate`, `eligibility` read port, `generation`, `interaction` policy check port, `policy` |
| `interaction` | outbound/inbound result event, cooldown 계산, duplicate comment·DM 재발송 방지, suppression | `candidate`, `policy`; completed action reference는 ID로 받음 |
| `policy` | immutable PolicyVersion, active policy 조회, threshold·weight·cooldown 설정 | 다른 업무 module에 의존하지 않음 |
| `provider` | Search·directory import·LLM·향후 Meta adapter와 capability registry | 각 core module이 정의한 port를 구현한다. core package를 호출할 수 있지만 core가 provider 구현을 import하지 않음 |
| `admin` | MVC controller, server-side view, form validation, operator command 호출, dashboard query | 각 application facade만 사용한다. repository·provider SDK 직접 호출 금지 |

### 13.3 의존 방향

```text
admin
  -> discovery / eligibility / content / generation / outreach / interaction facades

discovery -> candidate -> policy
eligibility -> candidate + policy
content -> candidate + policy
generation -> candidate + content + policy + LLM port
outreach -> candidate + eligibility read + generation + interaction check + policy
interaction -> candidate + policy

provider adapters -> core가 소유한 ports
core modules -X-> provider SDK / admin / Instagram execution implementation
```

- `admin`은 orchestration 진입점일 뿐 business rule을 갖지 않는다.
- `outreach`는 승인된 text를 준비하지만 Instagram에 전송하지 않는다.
- MVP의 manual provider는 deep link와 copy payload를 만들고 operator result command를 받을 뿐이다.
- 향후 공식 API adapter가 생겨도 capability 확인과 execution은 별도 application service에서만 수행한다.

### 13.4 transaction boundary

- discovery observation 수신과 candidate/evidence 생성은 하나의 intake transaction으로 처리한다.
- eligibility assessment는 사용한 evidence IDs와 policy version을 한 snapshot으로 커밋한다.
- draft edit는 새 revision 생성과 이전 revision supersede를 한 transaction으로 처리한다.
- approval은 approved revision·hash 기록과 action 상태 변경을 한 transaction으로 처리한다.
- manual result 기록은 action 상태, InteractionHistory, cooldown, suppression 변경을 한 transaction으로 처리한다.

## 14. 데이터 모델

### 14.1 공통 규칙

- 내부 식별자는 UUID 같은 비순차 opaque ID를 사용한다.
- 모든 business table에 `createdAt`, `updatedAt`, optimistic `version`을 둔다. immutable record는 `updatedAt` 대신 correction relation을 사용할 수 있다.
- 시간은 UTC로 저장한다.
- 상태 변경 주체는 named operator 또는 `SYSTEM`으로 식별한다.
- 삭제보다 `archivedAt`, `invalidatedAt`, `expiresAt`을 우선해 과거 판단을 설명할 수 있게 한다. 물리 삭제는 확정될 retention 정책을 따른다.
- 자유 text보다 enum reason code와 짧은 note를 함께 사용한다.
- active policy, approved revision, executed result처럼 중요한 연결은 ID와 hash를 모두 남긴다.

### 14.2 `Candidate`

- **책임**: 실제 개인의 canonical record와 현재 업무 상태를 나타낸다.
- **식별자**: `candidateId`.
- **주요 필드**: `displayName`, `profession`, `specialty`, `organization`, `targetCountry`, `primaryLanguage`, `currentEligibilityStatus`, `currentAssessmentId`, `firstDiscoveredAt`, `firstPresentedAt`, `lastVerifiedAt`, `suppressionScope`, `suppressionReason`, `suppressedAt`, `archivedAt`.
- **상태값**: `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED`; suppression은 별도 필드이다.
- **관계**: SocialProfile·Evidence·Assessment·OutreachAction·InteractionHistory와 1:N이다.
- **unique**: 사람 이름·소속만으로 unique를 만들지 않는다. 확정된 내부 identity key가 있으면 nullable unique로 사용할 수 있으나 자동 생성하지 않는다.
- **index**: `(currentEligibilityStatus, suppressedAt, firstPresentedAt)`, `lastVerifiedAt`, normalized name+organization 검색 보조 index.
- **audit**: merge, split, suppression, current assessment 변경을 전부 기록한다.

### 14.3 `SocialProfile`

- **책임**: Candidate와 Instagram account의 연결 및 최신 공개 snapshot을 관리한다.
- **식별자**: `socialProfileId`.
- **주요 필드**: `candidateId`, `platform`, `normalizedUsername`, `displayUsername`, `profileUrl`, `platformUserId`, `accountType`, `bioExcerpt`, `followerCount`, `followerObservedAt`, `isPublic`, `lastCheckedAt`, `profileStatus`.
- **상태값**: `ACTIVE`, `RENAMED`, `UNAVAILABLE`, `PRIVATE`, `CLOSED`, `UNKNOWN`.
- **관계**: Candidate N:1, ContentItem 1:N이다.
- **unique**: `(platform, normalizedUsername)`, platform user ID가 있을 때 `(platform, platformUserId)`이다. username 변경 이력은 audit에서 보존한다.
- **index**: `candidateId`, `followerObservedAt`, `(platform, profileStatus)`.
- **audit**: username, account type, follower, visibility 변경의 관찰 시점과 source를 기록한다.

### 14.4 `CandidateEvidence`

- **책임**: dimension 판정에 사용한 관찰과 source assertion을 append-oriented ledger로 보존한다.
- **식별자**: `evidenceId`.
- **주요 필드**: `candidateId`, `socialProfileId?`, `discoverySourceId`, `dimension`, `assertionCode`, `polarity`, `evidenceKind`, `sourceUrl`, `sourceRecordLocator`, `minimalExcerpt`, `observedValue`, `observedAt`, `expiresAt`, `availability`, `reliabilityClass`, `collectionMethod`, `independenceGroup`, `evidenceFingerprint`, `createdBy`.
- **상태값**: `ACTIVE`, `EXPIRED`, `CONTRADICTED`, `UNAVAILABLE`, `RETRACTED`.
- **관계**: Candidate N:1, EligibilityAssessment N:M 논리 참조이다.
- **unique**: `evidenceFingerprint`로 동일 source·assertion·observation의 중복 insert를 막는다.
- **index**: `(candidateId, dimension, availability)`, `expiresAt`, `discoverySourceId`, `evidenceFingerprint`.
- **audit**: 원문을 덮어쓰지 않고 새 evidence 또는 상태 event를 추가한다. 운영자 confirmation에는 이유가 필수이다.

### 14.5 `ContentItem`

- **책임**: comment grounding 대상인 공개 post·reel의 최소 metadata를 관리한다.
- **식별자**: `contentItemId`.
- **주요 필드**: `socialProfileId`, `externalMediaId?`, `contentType`, `permalink`, `permalinkHash`, `captionExcerpt`, `publishedAt`, `observedAt`, `retrievalMethod`, `availabilityStatus`, `contentFingerprint`, `sensitivityLevel`, `externalAiAllowed`, `lastCheckedAt`.
- **상태값**: `AVAILABLE`, `UNAVAILABLE`, `DELETED`, `PRIVATE`, `UNKNOWN`; 민감도 상태는 별도이다.
- **관계**: SocialProfile N:1, ContentAnalysis·GeneratedComment와 1:N이다.
- **unique**: `(platform, externalMediaId)`가 있으면 이를 사용하고, 없으면 `permalinkHash`를 사용한다.
- **index**: `(socialProfileId, publishedAt DESC)`, `availabilityStatus`, `lastCheckedAt`.
- **audit**: excerpt·availability·sensitivity 변경과 수집 방법을 기록한다. media blob은 기본 보관하지 않는다.

### 14.6 `ContentAnalysis`

- **책임**: ContentItem과 generated draft 사이의 구조화 분석 snapshot을 보존한다.
- **식별자**: `contentAnalysisId`.
- **주요 필드**: `contentItemId`, `analysisVersion`, `inputHash`, `summary`, `groundingPoints`, `commentableStatus`, `sensitivityDecision`, `redactionSummary`, `draftGenerationStatus`, `modelId?`, `createdBy`.
- **상태값**: `READY`, `INSUFFICIENT_CONTEXT`, `NOT_COMMENTABLE`, `BLOCKED_SENSITIVE`, `INVALIDATED`.
- **관계**: ContentItem N:1, GeneratedComment 1:N 참조이다.
- **unique**: `(contentItemId, analysisVersion, inputHash)`.
- **index**: `(contentItemId, createdAt DESC)`, `commentableStatus`.
- **audit**: 사용 input과 redaction decision을 immutable하게 남긴다.

### 14.7 `EligibilityAssessment`

- **책임**: 특정 시점과 PolicyVersion에서 계산한 전체 eligibility snapshot이다.
- **식별자**: `assessmentId`.
- **주요 필드**: `candidateId`, `policyVersionId`, 각 dimension의 status, `dimensionDetails`, `evidenceIds`, `finalStatus`, `reasonCodes`, `rankingInputs`, `assessedAt`, `assessedBy`, `validUntil`, `supersededAt`.
- **상태값**: 최종 `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED`; 각 dimension은 `PASS`, `FAIL`, `UNKNOWN`, `CONFLICT`이다.
- **관계**: Candidate N:1, PolicyVersion N:1이다.
- **unique**: 동일 `candidateId + policyVersionId + evidenceSetHash` 재계산은 중복 저장하지 않는다.
- **index**: `(finalStatus, assessedAt DESC)`, `candidateId`, `validUntil`, hair dimension status.
- **audit**: 과거 assessment를 수정하지 않고 supersede한다. operator override는 hard exclude에 제공하지 않는다.

### 14.8 `GeneratedComment`

- **책임**: 특정 ContentItem에 grounded된 comment의 immutable revision이다.
- **식별자**: `generatedCommentId`; 동일 draft series를 묶는 `draftSeriesId`를 별도로 둔다.
- **주요 필드**: `candidateId`, `contentItemId`, `contentAnalysisId`, `draftSeriesId`, `revision`, `previousRevisionId`, `text`, `normalizedTextHash`, `groundingPointIds`, `evidenceIds`, `promptVersion`, `modelProvider`, `modelId`, `qualityChecks`, `generationSource`, `createdBy`.
- **상태값**: `DRAFTING`, `QA_FAILED`, `READY_FOR_REVIEW`, `SUPERSEDED`, `INVALIDATED`.
- **관계**: Candidate·ContentItem N:1, OutreachAction에서 정확한 revision row를 참조한다.
- **unique**: `(draftSeriesId, revision)`, `normalizedTextHash`는 similarity 조회 index로 사용한다.
- **index**: `(candidateId, createdAt DESC)`, `contentItemId`, `status`, text similarity 지원 index는 필요가 확인될 때만 추가한다.
- **audit**: edit는 update가 아니라 새 revision이다. prompt·model·input refs를 남긴다.

### 14.9 `GeneratedDM`

- **책임**: Candidate별 DM의 immutable revision과 개인화 anchor를 보존한다.
- **식별자**: `generatedDmId`, `draftSeriesId`.
- **주요 필드**: `candidateId`, `anchorContentItemId?`, `draftSeriesId`, `revision`, `previousRevisionId`, `purposeCode`, `text`, `normalizedTextHash`, `evidenceIds`, `groundingPointIds`, `promptVersion`, `modelProvider`, `modelId`, `qualityChecks`, `generationSource`, `createdBy`.
- **상태값**: comment와 같은 draft 상태를 사용한다.
- **관계**: Candidate N:1, ContentItem은 optional N:1, OutreachAction이 exact revision을 참조한다.
- **unique**: `(draftSeriesId, revision)`.
- **index**: `(candidateId, purposeCode, createdAt DESC)`, `status`, `normalizedTextHash`.
- **audit**: comment와 별도로 revision·approval 이력을 유지한다. 무응답 purpose 재생성 차단 결과를 기록한다.

### 14.10 `Approval`

- **책임**: 특정 action과 정확한 draft revision에 대한 사람의 review decision이다.
- **식별자**: `approvalId`.
- **주요 필드**: `outreachActionId`, `decision`, `reviewerId`, `draftType`, `draftRevisionId`, `approvedRevision`, `approvedTextHash`, `targetProfileId`, `targetContentId?`, `reasonCode`, `note`, `decidedAt`, `expiresAt`, `invalidatedAt`, `invalidationReason`.
- **상태값**: `APPROVED`, `REJECTED`, `SKIPPED`, `INVALIDATED`, `EXPIRED`.
- **관계**: OutreachAction N:1이다.
- **unique**: action마다 현재 유효한 approval은 하나만 허용한다. DB partial unique가 없으면 active approval key로 강제한다.
- **index**: `(outreachActionId, decidedAt DESC)`, `(reviewerId, decidedAt DESC)`, `expiresAt`.
- **audit**: decision을 수정하지 않고 invalidation과 새 decision을 추가한다.

### 14.11 `OutreachAction`

- **책임**: follow·comment·DM 한 건의 계획부터 manual execution 결과까지 lifecycle을 관리한다.
- **식별자**: `outreachActionId`.
- **주요 필드**: `candidateId`, `socialProfileId`, `actionType`, `purposeCode`, `targetContentItemId?`, `generatedCommentId?`, `generatedDmId?`, `currentDraftRevision`, `approvedRevision`, `approvedTextHash`, `policyVersionId`, `status`, `idempotencyKey`, `scheduledFor`, `readyAt`, `manualAttemptedAt`, `executedAt`, `result`, `failureCode`, `nextAllowedAtSnapshot`, `createdBy`.
- **상태값**: 9장의 OutreachStatus 전체를 사용한다.
- **관계**: Candidate·SocialProfile·PolicyVersion N:1, Approval·InteractionHistory 1:N이다.
- **unique**: `idempotencyKey`; comment에는 candidate/profile/content/action을 포함한 unique action key를 둔다. comment와 DM draft FK는 action type에 맞게 하나만 존재하도록 check한다.
- **index**: `(status, scheduledFor)`, `(candidateId, createdAt DESC)`, `(socialProfileId, actionType)`, `nextAllowedAtSnapshot`.
- **audit**: 모든 transition에 actor, from/to, command, reason, version을 남긴다.

### 14.12 `InteractionHistory`

- **책임**: 실제 outbound·inbound·operator observation을 immutable event로 남기고 cooldown·suppression의 근거가 된다.
- **식별자**: `interactionHistoryId`.
- **주요 필드**: `candidateId`, `socialProfileId`, `outreachActionId?`, `direction`, `actionType`, `purposeCode?`, `targetContentItemId?`, `draftRevision`, `approvedRevision`, `occurredAt`, `executedAt?`, `result`, `nextAllowedAt`, `suppressionScope?`, `suppressionReason?`, `source`, `externalEventId?`, `recordedBy`, `note`, `correctionOfId?`.
- **상태값**: `EXECUTED`, `FAILED`, `UNKNOWN`, `RESPONSE`, `NO_RESPONSE_CLOSED`, `REJECTED_BY_TARGET`, `OPT_OUT`, `BLOCKED`, `FOLLOW_BACK`, `CORRECTION`.
- **관계**: Candidate·SocialProfile N:1, OutreachAction optional N:1, self correction relation이다.
- **unique**: external event ID가 있으면 `(source, externalEventId)`; manual event는 action+event type+idempotency key로 중복을 막는다.
- **index**: `(candidateId, occurredAt DESC)`, `(candidateId, actionType, occurredAt DESC)`, `nextAllowedAt`, `suppressionReason`.
- **audit**: event를 update하지 않고 correction event를 연결한다.

### 14.13 `DiscoverySource`

- **책임**: discovery source의 provenance, 이용조건, 허용 자동화 범위를 관리한다.
- **식별자**: `discoverySourceId`.
- **주요 필드**: `sourceType`, `name`, `baseUrl`, `adapterKey`, `termsUrl`, `termsReviewStatus`, `termsReviewedAt`, `termsReviewedBy`, `allowedCollectionMethods`, `allowedFields`, `retentionConstraints`, `recheckAt`, `enabled`.
- **상태값**: `NOT_REVIEWED`, `ALLOWED_MANUAL`, `ALLOWED_AUTOMATED`, `RESTRICTED`, `DISABLED`.
- **관계**: DiscoveryRun·CandidateEvidence와 1:N이다.
- **unique**: `adapterKey + source configuration identity`.
- **index**: `(enabled, termsReviewStatus)`, `recheckAt`.
- **audit**: 이용조건·허용 범위 변경과 승인자를 기록한다.

### 14.14 `DiscoveryRun`

- **책임**: source 실행 단위의 input, 결과, funnel, 오류를 추적한다.
- **식별자**: `discoveryRunId`; 같은 날 여러 source를 묶는 `runGroupId`를 둔다.
- **주요 필드**: `runGroupId`, `discoverySourceId`, `businessDate`, `runKey`, `triggerType`, `inputSummary`, `inputHash`, `policyVersionId`, `status`, `startedAt`, `completedAt`, `rawCount`, `dedupedCount`, `eligibleCount`, `reviewCount`, `ineligibleCount`, `presentedCount`, `errorCode`, `errorSummary`.
- **상태값**: `QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `CANCELLED`.
- **관계**: DiscoverySource·PolicyVersion N:1이며 발견 evidence에 run ID를 남긴다.
- **unique**: `runKey`.
- **index**: `(businessDate, status)`, `runGroupId`, `(discoverySourceId, startedAt DESC)`.
- **audit**: scheduler와 manual rerun의 actor, input, adapter version, 오류를 기록한다.

### 14.15 `PolicyVersion`

- **책임**: eligibility, freshness, ranking, generation QA, cooldown·suppression rule의 immutable version이다.
- **식별자**: `policyVersionId`와 사람이 읽는 `versionCode`.
- **주요 필드**: `versionCode`, `status`, `schemaVersion`, `eligibilityRules`, `freshnessRules`, `rankingWeights`, `generationRules`, `cooldownRules`, `checksum`, `effectiveFrom`, `effectiveTo`, `createdBy`, `approvedBy`, `changeReason`.
- **상태값**: `DRAFT`, `ACTIVE`, `RETIRED`.
- **관계**: Assessment·DiscoveryRun·OutreachAction과 1:N이다.
- **unique**: `versionCode`, 동시에 active인 policy 하나를 보장하는 active key.
- **index**: `(status, effectiveFrom DESC)`.
- **audit**: active row를 수정하지 않고 새 version을 승인·활성화한다. 미확정 값을 active policy에 임의 입력하지 않는다.

### 14.16 지원 entity와 합치지 않는 이유

- `OperatorAccount`: named operator와 `REVIEWER`, `ADMIN` 권한을 식별한다. 실제 인증 방식은 미확정이다.
- `AuditEvent`: aggregate type·ID, actor, command, before/after state, reason, occurredAt, correlation ID를 append-only로 저장한다.
- `ContentAnalysis`는 원문 최소화·민감도 gate와 생성 input을 명확히 연결해야 하므로 ContentItem에 덮어쓰지 않는다.
- `GeneratedComment`와 `GeneratedDM`은 서로 다른 grounding, QA, cooldown, approval을 가져 분리한다.
- `OutreachAction`은 현재 lifecycle이고 `InteractionHistory`는 실제·관찰 event이다. inbound response와 correction을 표현하기 위해 합치지 않는다.
- `DiscoverySource`는 이용조건과 adapter capability의 장기 설정이고 `DiscoveryRun`은 일회 실행이므로 분리한다.

## 15. 운영 UI

열 개 기능을 열 개 페이지로 만들지 않고 세 개 top-level 화면과 drawer/modal로 구성한다.

### 15.1 화면 A: Daily Workbench

상단 summary:

- business date와 active policy version
- source run 상태
- raw, deduped, assessed, eligible, review, ineligible, presented 수
- 수동 실행 대기·unknown·suppression 신규 건수

queue tab:

- `오늘의 후보`
- `REVIEW_REQUIRED`
- `수동 실행 대기`
- `실행 불명확`

오늘의 후보 row:

- 이름, username, profession·specialty, organization
- follower 값과 관찰 시점
- 최근 활동일
- ranking 총점과 component 설명
- eligibility·content·draft·cooldown 상태
- hair warning과 suppression 경고

행을 선택하면 화면 이동 없이 Candidate Quick View drawer를 열 수 있다. batch approve와 quota 채우기 기능은 제공하지 않는다.

### 15.2 화면 B: Candidate Workspace

한 화면을 다음 panel로 나눈다.

1. **Profile header**: 이름, profile deep link, 직업·소속, follower, current status, cooldown, suppression이다.
2. **Eligibility & Evidence**: 여덟 dimension별 status, reason, source URL, excerpt, observedAt, expiry, conflict이다.
3. **Recent Content**: permalink, publishedAt, type, 최소 excerpt, sensitivity, commentable과 grounding point이다.
4. **Drafts**: comment와 DM을 별도 tab으로 두고 revision history, edit, regenerate, QA 결과, grounding refs를 표시한다.
5. **Approval & Manual Execution**: approve·reject·skip, approved hash, target 재확인, copy, deep link, executed·failed·unknown 입력이다.
6. **Interaction Timeline**: 과거 action, result, response, nextAllowedAt, suppression, correction이다.

review 해소:

- 운영자는 evidence를 추가하거나 기존 evidence를 `UNAVAILABLE`로 확인할 수 있다.
- dimension 상태를 직접 덮어쓰지 않고 재assessment command를 실행한다.
- hair review 해소에는 source URL과 판단 이유가 필수이다.
- `INELIGIBLE` hard gate override 기능은 MVP에서 제공하지 않는다.

### 15.3 화면 C: Discovery & Policy Admin

- DiscoverySource 목록, 이용조건 검토 상태, enabled, 다음 재검토일
- DiscoveryRun 이력, input hash, funnel, 오류, 수동 rerun
- manual seed·human search result import form
- active PolicyVersion 요약과 이전 version diff
- source·policy 변경 audit

MVP에서는 active policy를 UI에서 자유 form으로 즉시 편집하지 않는다. draft policy 작성과 테스트 후 명시적 활성화 command를 사용한다.

### 15.4 오류 방지 UX

- `REVIEW_REQUIRED`와 `ELIGIBLE`을 색상뿐 아니라 text·icon으로 구분한다.
- hair signal, follower stale, target mismatch는 승인 버튼 근처에 다시 표시한다.
- 승인 후 edit 시 “승인이 무효화됨”을 즉시 보여준다.
- manual execution 전에 target username, content permalink, action type, approved text hash를 확인한다.
- `EXECUTION_UNKNOWN`은 성공·실패 button과 다른 별도 선택지로 둔다.
- permanent suppression 해제는 일반 화면에서 즉시 할 수 없게 한다.

## 16. Phase별 구현 로드맵

### Phase 1. 데이터 기반과 manual eligibility workbench

**목표**

- 외부 API와 LLM 없이도 후보·profile·evidence를 저장하고 안전한 eligibility 판정을 재현한다.

**구현 범위**

- 사용자 확정 후 application skeleton과 Docker Compose
- PostgreSQL migration과 core entity
- named operator 인증·최소 role
- `Candidate`, `SocialProfile`, `CandidateEvidence`, `EligibilityAssessment`, `DiscoverySource`, `PolicyVersion`, `AuditEvent`
- manual seed와 human search result 입력
- username normalization, exact dedupe, merge suggestion
- deterministic eligibility engine과 hair review queue
- Daily Workbench·Candidate Workspace의 evidence 부분

**제외 범위**

- scheduled discovery, Search API, Meta, LLM, draft, approval, 외부 실행

**완료 조건**

- 명확한 의사, 약사, 한의사, 모발이식, 10,000 경계, identity 부족, duplicate fixture의 expected 결과가 재현된다.
- bio만 있는 후보와 hair 불명확 후보가 `ELIGIBLE`이 되지 않는다.
- operator가 source URL·관찰 시점과 함께 evidence를 추가하고 assessment 이력을 볼 수 있다.
- application과 PostgreSQL이 Docker Compose로 기동된다.

**검증 방법**

- rule unit test: 각 dimension의 valid·invalid·unknown·conflict table test
- database integration test: unique, FK, optimistic lock, immutable assessment
- MVC test: manual seed validation, evidence input, review queue
- Testcontainers PostgreSQL integration test
- production-like build와 Docker Compose smoke test

### Phase 2. discovery run, dedupe, ranking, daily queue

**목표**

- 검토된 source에서 repeatable discovery를 실행하고 신규 `ELIGIBLE`을 최대 15명만 설명 가능한 순서로 제시한다.

**구현 범위**

- provider-neutral discovery port와 source registry gate
- approved directory import와 related public info input
- `DiscoveryRun`, run group, scheduler, retry·partial failure 기록
- exact dedupe와 operator merge suggestion
- 100점 deterministic ranking snapshot
- `firstPresentedAt`과 일일 15명 cap
- source별 precision·funnel 기본 집계
- Search API adapter는 vendor가 선택되고 terms review를 통과한 경우에만 활성화

**제외 범위**

- Instagram UI crawl, Meta 필수 연동, ML ranking, quota 강제 충족

**완료 조건**

- 중복 scheduler trigger가 같은 run을 두 번 처리하지 않는다.
- 20명의 유효 fixture 중 deterministic top 15만 제시되고 score explanation이 보인다.
- 10명만 유효하면 10명만 제시된다.
- disabled·terms-expired source는 자동 실행되지 않는다.
- exact duplicate는 새 Candidate와 신규 presentation을 만들지 않는다.

**검증 방법**

- adapter contract test와 source failure simulation
- concurrent run/idempotency integration test
- ranking golden fixture와 tie-break regression test
- daily cap·no-refill behavior test
- source terms state별 authorization test

### Phase 3. content analysis와 grounded generation

**목표**

- 실제 확인 가능한 안전한 content에서만 comment·DM draft를 만들고 근거와 결과를 연결한다.

**구현 범위**

- `ContentItem`, `ContentAnalysis`, sensitivity·availability gate
- operator permalink·최소 excerpt·redacted summary 입력
- LLM provider port와 disabled/fake adapter
- comment·DM 별도 prompt, revision, QA
- groundedness, 금칙 표현, 광고성, 유사도 검사
- 생성 input/output metadata와 최소화된 provider payload audit

**제외 범위**

- 원본 media archive, 식별 가능한 환자 데이터 전송, auto approval, 외부 action

**완료 조건**

- permalink·anchor가 없는 comment는 생성되지 않는다.
- 민감 환자 content는 외부 AI에 전달되지 않고 정책에 따라 차단된다.
- comment와 DM이 독립 draft series를 가진다.
- 생성 문장의 개인화 claim을 evidence 또는 grounding point로 추적할 수 있다.
- 반복·영업·과장 fixture가 QA에서 차단된다.

**검증 방법**

- redacted provider payload snapshot test
- prompt contract와 structured output validation test
- sensitive/insufficient/unavailable content negative test
- golden set human evaluation: groundedness, 자연스러움, 과장·영업성, 반복성
- provider timeout·invalid output·quota failure test

### Phase 4. approval, manual execution, interaction과 cooldown

**목표**

- `APPROVAL_REQUIRED + MANUAL_EXECUTION`의 전체 상태 전이를 안전하고 감사 가능하게 완성한다.

**구현 범위**

- `Approval`, `OutreachAction`, `InteractionHistory`
- edit·approve·reject·skip과 immutable revision binding
- approval invalidation과 expiry 설정
- `READY_FOR_MANUAL_EXECUTION` gate
- deep link, copy, target 확인, executed·failed·unknown 기록
- 하루 한 action, 30일 candidate cooldown
- 동일 post comment unique, cold DM no-retry, permanent suppression
- interaction timeline과 unknown reconciliation queue

**제외 범위**

- Instagram programmatic execution, browser automation, inbound API automation

**완료 조건**

- 승인 후 한 글자라도 수정하면 ready 상태가 해제된다.
- comment와 DM 승인·revision이 서로 영향을 주지 않는다.
- 같은 candidate의 action 두 개가 같은 날 ready 또는 executed가 될 수 없다.
- 동일 post comment와 무응답 cold DM 재발송이 차단된다.
- unknown 실행을 성공으로 추정하지 않고 cooldown과 reconciliation을 적용한다.
- 외부 거절·중단 요청·차단이 모든 신규 outreach를 영구 차단한다.

**검증 방법**

- 전체 state transition table test와 금지 transition test
- 동시 approval·execution optimistic locking test
- draft hash mismatch와 stale eligibility negative test
- 30일 경계 시각, KST/UTC, correction event test
- 실제 Instagram action 없이 manual-provider dry run과 UI acceptance test

### Phase 5. optional enrichment와 운영 개선

**목표**

- manual MVP의 실제 병목과 품질 지표를 근거로 선택적 read adapter와 운영 개선을 추가한다.

**구현 범위**

- source·query별 precision, review rate, edit rate, execution reconciliation 지표
- policy version 비교와 hair false-negative incident review
- Search API provider spike 및 승인된 경우 adapter
- 발신 계정 prerequisites가 확인된 경우 Meta Business Discovery read-only spike와 adapter
- evidence refresh queue, retention·deletion job, operator 업무 개선

**제외 범위**

- 검증되지 않은 외부 execution, browser action automation, microservice 전환

**완료 조건**

- 각 optional adapter가 official docs, terms, permission, field availability, error taxonomy, 비용·quota 실측을 갖춘다.
- adapter failure가 eligibility를 fail open하지 않는다.
- Meta prerequisites가 없으면 기능을 꺼도 전체 MVP가 정상 동작한다.
- 정책 변경 전후 golden fixture 결과와 운영 KPI를 비교할 수 있다.

**검증 방법**

- sandbox 또는 허용된 read-only contract test
- rate limit·permission·Personal/private/unavailable error test
- disabled adapter fallback test
- metric definition reconciliation과 표본 audit

### 16.1 release 경계

- Phase 1은 가장 먼저 구현한다.
- Phase 1~2 완료 시 안전한 후보 발굴·검토 도구가 된다.
- Phase 3 완료 시 grounded draft 지원 도구가 된다.
- Phase 4 완료 시 첫 release mode인 `APPROVAL_REQUIRED + MANUAL_EXECUTION`이 완성된다.
- Phase 5는 첫 release blocker가 아니다.

## 17. MVP acceptance criteria

1. 일일 queue는 current `ELIGIBLE`이며 신규인 후보만 최대 15명 제시한다.
2. 15명을 채우지 못해도 review·ineligible 후보를 자동 승격하지 않는다.
3. 대한민국 한국어 의사·약사 대상과 hard exclude가 active policy에 명시된다.
4. profession은 Instagram bio/category만으로 통과하지 않는다.
5. profession과 Instagram identity 근거가 각각 추적 가능하다.
6. 한의 계열, follower 10,000 이상, 개인 미특정 기관 계정은 `INELIGIBLE`이다.
7. 모발이식 관련 불확실성은 항상 `REVIEW_REQUIRED`이며 ranking이 이를 덮지 못한다.
8. 최근 30일 활동이 우선되고 활동 기간만으로 즉시 hard exclude하지 않는다.
9. discovery source마다 provenance, 이용조건 상태, collection method, failure가 기록된다.
10. Instagram UI scraping과 browser action automation 코드·credential 저장이 없다.
11. Meta integration을 끈 상태로 전체 first-release workflow가 동작한다.
12. comment는 실제 ContentItem과 grounding point가 없으면 생성되지 않는다.
13. 민감하거나 식별 가능한 환자 정보가 외부 AI에 전달되지 않는다.
14. comment와 DM은 별도 revision과 approval을 가진다.
15. 승인 후 draft·target·핵심 evidence가 변경되면 승인이 무효화된다.
16. `READY_FOR_MANUAL_EXECUTION`은 current eligibility, approval hash, cooldown, suppression, target availability를 모두 검사한다.
17. Instagram 외부 action은 운영자만 수행하고 시스템에는 executed·failed·unknown을 구분해 기록한다.
18. 동일 post comment, 같은 목적의 무응답 cold DM 재발송, 30일 내 candidate outbound가 차단된다.
19. 거절·중단 요청·차단은 permanent suppression으로 모든 신규 outreach를 막는다.
20. 정책·evidence·draft·approval·action·interaction 상태 변경에 actor, time, reason, version이 남는다.
21. source·provider 장애가 `ELIGIBLE` 또는 실행 성공으로 fail open하지 않는다.
22. Docker Compose에서 application과 PostgreSQL을 기동하고 real PostgreSQL integration test를 통과한다.

## 18. 남은 질문 및 investigation

### 18.1 사용자 확인이 필요한 결정

별도 clarification request에 정리한다.

- `agent_outputs/clarification_requests/20260817_005758_mvp_implementation_decisions.md`

핵심 항목은 다음과 같다.

- 추천 기술 스택 확정
- operator 인증 방식과 최소 role
- follower evidence freshness·경계 재확인 값
- approval TTL
- 90일 초과 비활성 후보의 ranking-only 대 review 처리
- production retention과 외부 AI provider 사용 전 gate

### 18.2 read-only investigation

- Search API는 특정 vendor를 선택하기 전에 official terms, 신규 사용 가능성, 가격, query coverage·precision, 저장 제한을 동일 fixture로 비교한다.
- 공개 directory마다 자동화 허용 범위, 저장 가능 필드, freshness와 원출처 독립성을 확인한다.
- 석지웅 원장 account type, Facebook Page 연결, Meta App 상태는 현재 알 수 없다. 준비가 확인된 경우에만 Business Discovery read-only spike를 수행한다.
- Meta spike는 exact username profile field, follower, recent media, Personal/private failure, permission, API version, rate header만 확인하며 외부 action은 하지 않는다.
- 병원 내부 개인정보·보안 담당자가 retention, 삭제 요청, operator access, AI provider의 학습 사용·보유·subprocessor 조건을 검토한다.

### 18.3 blocker 판정

- 이번 문서 작업 완료에는 blocker가 없다.
- Phase 1 코드 착수에는 기술 스택과 인증 방식 확정이 blocker이다.
- Search API와 Meta 상태는 Phase 1~4의 manual-first MVP blocker가 아니다.
- production LLM 사용에는 데이터 최소화·retention·provider 계약 검토가 선행되어야 한다.
