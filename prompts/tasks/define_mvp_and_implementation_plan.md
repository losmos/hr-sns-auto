# Instagram 의료인 네트워킹 MVP 상세 설계 및 구현 계획 수립

## 목표

`hr-sns-auto`의 사용자 결정 사항을 source of truth에 반영하고,
현재 초기 아키텍처를 실제 구현 가능한 MVP 수준으로 구체화한다.

이번 작업에서는 애플리케이션 코드를 구현하지 않는다.

작업 시작 시 `AGENTS.md`의 읽기 순서를 따른다.

특히 다음을 반드시 먼저 확인한다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

필요한 경우에만 다음 산출물을 읽는다.

- `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`
- `prompts/tasks/design_instagram_medical_outreach_system.md`

## 사용자 확정 답변

기존 clarification request의 Q1~Q8에 대해 사용자가 다음과 같이 확정했다.

- Q1=A
- Q2=A
- Q3=A
- Q4=A
- Q5=A
- Q6=A
- Q7=D
- Q8=A

이를 다음 의미로 해석한다.

### Q1

하루 최대 15명은 raw discovery 후보 수가 아니다.

모든 필수 eligibility 검증을 통과한 `ELIGIBLE` 상태이며
운영자가 실제 검토할 수 있는 신규 후보를 최대 15명 제시하는 것이 목표이다.

15명을 반드시 채우는 quota는 아니다.

hard exclude나 evidence 품질보다 숫자를 우선하지 않는다.

### Q2

초기 대상은 대한민국의 한국어 계정이다.

모발이식 분야를 제외한 의사·약사를 폭넓게 대상으로 한다.

초기에는 특정 진료과 quota를 두지 않는다.

### Q3

Instagram bio/category만으로 의사·약사 여부를 확정하지 않는다.

기본 원칙은 다음과 같다.

- 소속 기관, 학회, 공식 의료기관 소개 등 강한 공개 근거 1개
- Instagram profile과 해당 실제 인물의 identity가 일치한다는 근거

를 요구한다.

강한 단일 근거가 부족한 경우 독립적인 공개 source 2개 이상을 검토한다.

근거가 충분하지 않으면 `REVIEW_REQUIRED`이다.

### Q4

최근 30일 내 활동한 계정을 우선한다.

30일을 넘었다는 이유만으로 즉시 제외하지 않는다.

90일 초과 비활성 계정은 낮은 우선순위 또는 `REVIEW_REQUIRED` 대상으로 취급할 수 있다.

최근 활동 여부는 기본적으로 ranking 요소이며 hard exclude가 아니다.

### Q5

한 후보에게 하루 신규 outbound action을 여러 개 몰아서 수행하지 않는다.

초기 흐름은 콘텐츠에 대한 자연스러운 interaction을 먼저 검토하고
DM은 다른 시점의 별도 action으로 다룬다.

comment와 DM은 각각 독립 approval 대상이다.

MVP에서는 실제 Instagram action을 자동 실행하지 않는다.

### Q6

cold DM에 응답이 없으면 동일 목적으로 재발송하지 않는다.

댓글은 동일 게시물에 한 번만 작성하는 것을 기본으로 한다.

후보 단위 기본 cooldown은 30일로 설계한다.

거절, 명시적 연락 중단 요청, 차단 등은 permanent suppression으로 처리한다.

숫자는 향후 `PolicyVersion` 등의 설정으로 변경 가능하게 설계하되
현재 MVP 기본값은 위 정책이다.

### Q7

석지웅 원장의 Instagram account type,
Facebook Page 연결 여부,
Meta App 준비 상태를 현재 알지 못한다.

따라서 Meta Business Discovery 연동은 현재 MVP blocker가 아니다.

Meta API integration을 MVP 필수 기능으로 두지 않는다.

향후 별도의 read-only investigation/spike 대상으로 유지한다.

### Q8

공개 profile 및 콘텐츠 데이터는 최소한만 저장한다.

가능하면 다음을 중심으로 저장한다.

- permalink
- username
- 구조화된 사실
- 판정 evidence
- 필요한 최소 excerpt
- 관찰 시점

Instagram 원본 media를 무조건 저장하는 구조를 만들지 않는다.

외부 AI provider에 전달하는 데이터도 생성 목적에 필요한 최소 범위로 제한한다.

구체 보유 기간이나 법적 정책이 아직 확정되지 않은 부분은 임의로 결정 사항으로 확정하지 않는다.

## 추가 사용자 결정

다음 사항도 확정된 운영 정책으로 반영한다.

### 모발이식 관련 불확실성

모발이식 관련 여부가 충분히 확인되지 않은 후보는 `ELIGIBLE`로 자동 통과시키지 않는다.

`REVIEW_REQUIRED`로 분류한다.

이는 중요한 안전 정책이다.

### Browser action automation

Playwright, Selenium 등을 이용한 Instagram browser action automation은 MVP 범위에서 제외한다.

영구적으로 금지한다고 확정하는 것은 아니다.

향후 정책, API, 운영 필요성이 바뀌면 별도 조사 후 재검토할 수 있다.

현재 구현 계획에는 포함하지 않는다.

### 첫 release mode

첫 release는 다음으로 확정한다.

`APPROVAL_REQUIRED + MANUAL_EXECUTION`

시스템은 다음까지 담당한다.

- 후보 발굴
- evidence 수집 및 정리
- eligibility 판정
- 최근 콘텐츠 분석
- comment draft 생성
- DM draft 생성
- 운영자 approval/edit/reject/skip
- 실제 interaction 결과 기록

실제 Instagram에서의 follow/comment/DM 등 외부 action은 운영자가 직접 수행한다.

### task prompt Git 정책

`prompts/tasks/*.md`는 기본적으로 Git에 커밋한다.

작업 의도와 재현성을 유지하는 프로젝트 기록으로 취급한다.

secret이나 민감 정보는 task prompt에 직접 저장하지 않는다.

## 이번 작업 1: source of truth 갱신

위 사용자 결정 중 장기적으로 유효한 내용을
`docs/harness/PROJECT_CONTEXT.md`에 반영한다.

필요하다면 기존 미확정 질문을 제거하거나 상태를 변경한다.

장기 결정에는 기존 형식을 따라 적절한 Decision ID를 사용할 수 있다.

예:

- MVP execution mode
- ambiguous hair-transplant classification policy
- browser automation MVP exclusion
- task prompt tracking policy
- initial target market
- daily candidate definition
- cooldown 기본 정책

단, Q7에서 알 수 없다고 답한 Meta account 상태는 확정 사실로 추측하지 않는다.

`docs/harness/HANDOFF.md`도 이번 작업 결과에 맞게 갱신한다.

clarification request는 source of truth가 아니다.
과거 질문 기록을 보존할 필요가 있다면 그대로 둘 수 있다.
불필요하게 원문을 다시 작성하지 않는다.

## 이번 작업 2: MVP 범위 상세화

초기 설계 보고서를 기반으로 MVP의 기능 범위를 구체화한다.

최소한 다음 영역을 포함한다.

### Candidate Discovery

Instagram 전체 웹 UI scraping을 전제로 하지 않는다.

현실적인 MVP discovery source를 구체적으로 설계한다.

검토 후보:

- 운영자 manual seed
- 검색엔진의 공식 또는 허용된 Search API
- 검색엔진 공개 검색 결과를 사람이 제공하는 방식
- 의료기관/학회/전문가 공개 디렉터리
- 이미 발견된 후보의 공개 연관 정보
- Instagram Business Discovery가 향후 가능할 경우 validation/enrichment 용도

각 source마다 다음을 정의한다.

- source type
- 입력
- 출력
- provenance
- 이용조건 확인 필요 여부
- 자동화 수준
- expected precision
- failure mode

특정 외부 검색 API를 추천하려면 2026년 현재 공식 문서를 확인하고
가격, 이용 가능성, 검색 품질, 정책을 추측하지 않는다.

### Eligibility

다음 dimension을 독립적으로 평가하는 정책을 설계한다.

- profession
- identity
- korean target
- traditional Korean medicine exclusion
- hair transplant exclusion
- follower threshold
- recent activity
- duplicate identity/profile

각 dimension마다 다음을 정의한다.

- required evidence
- strong evidence
- weak evidence
- stale evidence
- pass
- fail
- review 조건

최종 결과:

- `ELIGIBLE`
- `INELIGIBLE`
- `REVIEW_REQUIRED`

모발이식 관련 false negative를 일반 오류보다 훨씬 심각하게 취급한다.

### Ranking

`ELIGIBLE` 후보가 15명보다 많을 때 사용할 ranking 기준을 설계한다.

예:

- 최근 활동
- evidence confidence
- 콘텐츠 품질
- 실제 개인 브랜딩 활동 여부
- 원장과 다른 의료 분야
- 최근 interaction 없음
- 콘텐츠가 comment generation에 적합한가

복잡한 ML scoring은 MVP에 도입하지 않는다.

설명 가능한 deterministic 또는 weighted scoring을 우선 검토한다.

### Content Analysis

댓글 생성에 사용할 콘텐츠는 실제 확인 가능한 최근 콘텐츠에 grounded되어야 한다.

콘텐츠별로 최소한 다음을 고려한다.

- permalink
- publishedAt
- content type
- caption 또는 필요한 최소 excerpt
- 분석 요약
- commentable 여부
- 민감한 환자 정보 포함 가능성
- draft 생성 가능 여부

환자 사례나 민감한 의료정보가 포함된 콘텐츠는
불필요하게 외부 AI provider로 전달하지 않는 방안을 포함한다.

### Comment Generation

기존 사용자 제공 말투를 참고한다.

목표:

- 같은 의료인에 대한 예의
- 실제 콘텐츠를 읽은 흔적
- 과장된 칭찬 없음
- 영업성 없음
- 반복 문구 최소화
- 짧고 자연스러움

범용 댓글 템플릿 random substitution만으로 구현하지 않는다.

grounding evidence와 생성 결과를 연결하는 방식을 설계한다.

### DM Generation

발신자는 석지웅 원장이다.

기존 설계와 사용자 제공 DM 예시의 어조를 유지한다.

DM 생성은 다음을 활용할 수 있다.

- 상대 이름
- 전문 분야
- 최근 콘텐츠
- 실제 인상 깊었던 포인트

과도한 personal inference는 하지 않는다.

DM draft는 comment draft와 별도 revision/approval을 가진다.

### Approval Workflow

최소 상태와 전이를 실제 구현 가능한 수준으로 확정한다.

다음을 반드시 고려한다.

- draft 생성
- edit
- approve
- reject
- skip
- 승인 후 draft 수정 시 approval 무효화
- READY_FOR_MANUAL_EXECUTION
- manual execution 완료 기록
- 실패
- 실행 여부 불명확

eligibility 상태와 outreach 상태는 분리한다.

### Interaction History / Cooldown

다음을 데이터 모델과 정책에 반영한다.

- candidate
- action type
- target content
- draft revision
- approved revision
- executedAt
- result
- nextAllowedAt
- suppression reason

cold DM 무응답 재전송 방지,
동일 post 댓글 중복 방지,
30일 후보 cooldown,
permanent suppression을 표현할 수 있어야 한다.

## 이번 작업 3: 기술 스택 비교 및 추천

현재 애플리케이션 기술 스택은 확정되지 않았다.

이 MVP는 대규모 consumer service가 아니라
소수 운영자가 사용하는 내부 업무 도구라는 점을 고려한다.

필요 역량은 다음과 같다.

- Web admin UI
- relational persistence
- scheduled daily discovery
- 외부 Search/API adapter
- LLM provider adapter
- deterministic eligibility rules
- approval workflow
- audit history
- Docker 기반 실행
- 향후 Meta API adapter 가능성

과도한 microservice 구조는 피한다.

최소 다음 세 가지 방향을 비교한다.

### Option A

Java/Spring Boot 기반 modular monolith

예:

- Spring Boot
- Spring MVC
- Spring Data JPA 또는 적절한 persistence layer
- PostgreSQL
- server-side UI 또는 매우 얇은 frontend
- Docker Compose

### Option B

Java/Spring Boot backend + 별도 SPA frontend

예:

- Spring Boot REST API
- React/Next.js 등의 frontend
- PostgreSQL
- Docker Compose

### Option C

Python 기반 lightweight web application

예:

- FastAPI 등의 backend
- 적절한 admin UI
- PostgreSQL
- Docker Compose

다음 기준으로 비교한다.

- MVP 구현 복잡도
- 장기 유지보수성
- 데이터 모델 및 transaction 적합성
- scheduler 구현
- LLM/Search API integration
- UI 구현 비용
- 테스트 용이성
- 배포/운영 복잡도
- 향후 기능 확장
- 불필요한 기술 복잡성

최종적으로 하나의 추천안을 제시한다.

추천 이유와 포기하는 장점도 함께 설명한다.

아직 사용자 확인이 필요한 결정이라면
확정 Decision으로 기록하지 말고 clarification request로 분리한다.

## 이번 작업 4: 논리 모듈 구조

추천 기술 스택을 전제로 하되 애플리케이션 코드는 만들지 않는다.

MVP의 logical module 또는 package boundary를 제안한다.

예시 역할:

- discovery
- candidate
- eligibility
- evidence
- content
- generation
- outreach
- approval
- interaction
- policy
- provider
- admin

과도하게 잘게 나누지 않는다.

각 모듈의 책임과 의존 방향을 정의한다.

generation과 execution의 분리는 유지한다.

## 이번 작업 5: 데이터 모델 상세화

기존 초기 entity를 실제 DB 설계 직전 수준으로 좁힌다.

최소 검토 대상:

- Candidate
- SocialProfile
- CandidateEvidence
- ContentItem
- EligibilityAssessment
- GeneratedComment
- GeneratedDM
- Approval
- OutreachAction
- InteractionHistory
- DiscoveryRun
- DiscoverySource
- PolicyVersion

각 entity에 대해 최소한 다음을 제안한다.

- 책임
- 주요 필드
- 식별자
- 상태값
- 주요 관계
- unique constraint
- 필요한 index
- 생성/변경 시각
- audit 요구

DB vendor-specific DDL까지 작성할 필요는 없다.

MVP에 불필요한 entity는 합칠 수 있다.
합치는 경우 이유를 설명한다.

## 이번 작업 6: MVP 운영 화면

복잡한 디자인 시스템은 필요 없다.

운영자가 매일 빠르게 사용할 수 있는 최소 화면을 설계한다.

최소 검토:

1. Dashboard / 오늘의 후보
2. Candidate 상세
3. Evidence 확인
4. 최근 콘텐츠
5. comment draft
6. DM draft
7. approve/edit/reject/skip
8. manual execution 기록
9. interaction history
10. REVIEW_REQUIRED queue

가능하면 화면 수를 줄이고 한 화면에서 처리할 수 있는 흐름을 우선한다.

## 이번 작업 7: 구현 로드맵

기능을 한 번에 전부 구현하지 않는다.

실제 개발 가능한 단계로 나눈다.

예:

### Phase 1

- 프로젝트 skeleton
- DB
- Candidate
- Evidence
- eligibility policy
- manual seed 입력
- admin UI 최소 기능

### Phase 2

- discovery source adapter
- daily DiscoveryRun
- ranking
- deduplication

### Phase 3

- content input/analysis
- LLM abstraction
- comment/DM generation
- generation validation

### Phase 4

- approval
- interaction history
- cooldown
- manual execution tracking

### Phase 5

- optional external API enrichment
- metrics
- 운영 개선

실제 분석 결과에 따라 더 적절한 단계를 제안할 수 있다.

각 Phase마다 다음을 작성한다.

- 목표
- 구현 범위
- 제외 범위
- 완료 조건
- 검증 방법

## 성공 기준

작업 완료 시 다음이 만족되어야 한다.

1. 사용자 Q1~Q8 결정이 정확히 source of truth에 반영되어 있다.
2. 미확정 상태였던 관련 PROJECT_CONTEXT 질문이 최신 상태로 정리되어 있다.
3. 첫 release가 `APPROVAL_REQUIRED + MANUAL_EXECUTION`으로 명확하다.
4. Browser automation이 MVP 구현 범위에서 빠져 있다.
5. 모발이식 불확실 후보가 `REVIEW_REQUIRED`로 처리된다.
6. 하루 15명의 정확한 의미가 명확하다.
7. 실제 구현 가능한 discovery strategy가 구체화되어 있다.
8. eligibility evidence 정책이 구현 가능한 수준이다.
9. 기술 스택 후보가 비교되고 하나의 추천안이 제시되어 있다.
10. 모듈 구조, 데이터 모델, 운영 UI가 구현 직전 수준으로 좁혀져 있다.
11. Phase별 구현 로드맵과 완료 조건이 있다.
12. 확정되지 않은 사실을 추측해 Decision으로 기록하지 않는다.
13. 애플리케이션 코드는 생성하지 않는다.

## 산출물

다음 보고서를 생성한다.

    agent_outputs/reports/mvp_implementation_plan.md

보고서에는 최소한 다음을 포함한다.

1. 확정된 사용자 결정
2. MVP scope / out of scope
3. 일일 운영 workflow
4. Candidate Discovery 상세 전략
5. Eligibility policy
6. Ranking policy
7. Content analysis 정책
8. Comment/DM generation 정책
9. Approval 및 manual execution workflow
10. Cooldown / suppression 정책
11. 기술 스택 비교
12. 추천 기술 스택
13. 논리 모듈 구조
14. 데이터 모델
15. 운영 UI
16. Phase별 구현 로드맵
17. MVP acceptance criteria
18. 남은 질문 및 investigation

사용자 확인이 필요한 새로운 질문이 발생하면
`AGENTS.md` 표준 형식으로 별도 clarification request를 생성한다.

예:

    agent_outputs/clarification_requests/mvp_implementation_decisions.md

질문이 없으면 만들지 않는다.

## source 문서 갱신

이번 작업은 source 문서 갱신을 허용하고 요구한다.

필요한 장기 결정은:

    docs/harness/PROJECT_CONTEXT.md

에 반영한다.

작업 종료 시:

    docs/harness/HANDOFF.md

를 반드시 최신 상태로 갱신한다.

긴 설계 전문은 PROJECT_CONTEXT에 복사하지 않고
보고서 경로만 연결한다.

## 주의사항

- 애플리케이션 코드를 구현하지 않는다.
- 프로젝트 skeleton도 아직 생성하지 않는다.
- Meta/Instagram 계정 상태를 추측하지 않는다.
- Q7=D를 다른 값으로 추측하지 않는다.
- Instagram browser scraping 또는 browser action automation을 MVP에 다시 넣지 않는다.
- 하루 15명을 채우기 위해 eligibility 기준을 낮추지 않는다.
- 모발이식 관련 false negative를 허용하는 방향으로 최적화하지 않는다.
- 기존 source document 역할을 유지한다.
- 요청 범위 밖의 문서나 파일을 정리하지 않는다.
- secret, token, 계정 비밀번호를 파일에 기록하지 않는다.

## 마지막 출력

작업 완료 후 콘솔에 다음을 짧게 출력한다.

- 변경한 파일
- 생성한 보고서
- 새 clarification request 생성 여부
- 추천 기술 스택 한 줄 요약
- 가장 먼저 구현할 Phase
- 남은 blocker 여부
