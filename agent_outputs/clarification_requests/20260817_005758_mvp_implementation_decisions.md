# MVP 구현 착수 전 결정 질문지

## 빠른 답변표

| 질문 ID | Priority | 한 줄 질문 | 답변 방식 | 권장 다음 행동 |
| --- | --- | --- | --- | --- |
| Q1 | P0 Blocker | 첫 구현 기술 스택을 무엇으로 확정하는가 | 객관식 | A 확정 |
| Q2 | P0 Blocker | operator 인증과 최소 role을 어떻게 구성하는가 | 객관식 | A 확정 |
| Q3 | P1 Investigation | follower evidence freshness를 어떻게 정하는가 | 객관식 | A 검토 |
| Q4 | P1 Investigation | outreach approval TTL을 어떻게 정하는가 | 객관식 | A 검토 |
| Q5 | P2 Non-blocking | 90일 초과 비활성 후보를 어떻게 처리하는가 | 객관식 | A 검토 |
| Q6 | P1 Investigation | retention·AI provider 미확정 상태에서 Phase 3를 어떻게 gate하는가 | 객관식 | A 확정 |
| Q7 | P2 Non-blocking | Search API provider 선택을 언제 진행하는가 | 객관식 | A 진행 |

빠른 답변 예시는 `Q1=A, Q2=A, Q3=A, Q4=A, Q5=A, Q6=A, Q7=A`이다.

## Q1

### Priority

`P0 Blocker`

### 질문

첫 application skeleton의 기술 스택을 무엇으로 확정하는가?

### 답변 선택지

- A. Spring Boot modular monolith + Spring MVC/Thymeleaf + PostgreSQL + Docker Compose로 확정한다. 추천한다.
- B. Spring Boot REST backend + React/Next.js frontend + PostgreSQL로 확정한다.
- C. FastAPI 기반 server-rendered lightweight application + PostgreSQL로 확정한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 언어, framework, UI, database 기준을 적는다: ____

### 답변하지 않으면

설계 추천은 A로 유지하지만 project skeleton과 application code를 생성하지 않는다.

### 근거 요약

현재 핵심은 transaction, 상태 전이, audit이며 별도 SPA의 복잡성이 필요하지 않다. 팀의 주력 언어가 다르면 추천이 바뀔 수 있다.

## Q2

### Priority

`P0 Blocker`

### 질문

내부 운영자의 인증 방식과 첫 release role을 어떻게 구성하는가?

### 답변 선택지

- A. named local account를 사용하고 `OPERATOR`, `ADMIN` 두 role로 시작한다. 비밀번호 저장은 검증된 password hashing과 secret 분리를 적용한다. 추천한다.
- B. 병원의 기존 SSO를 사용한다. 사용할 identity provider와 protocol 정보를 제공한다.
- C. 배포망 접근통제를 전제로 단일 named admin account로 pilot한 뒤 role을 분리한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 사용자 수, 인증 방식, 필요한 role을 적는다: ____

### 답변하지 않으면

인증·감사 주체가 불명확하므로 Phase 1 skeleton 구현을 시작하지 않는다.

### 근거 요약

approve, edit, suppression 해제, policy 활성화의 책임자를 식별하려면 shared anonymous access를 피해야 한다.

## Q3

### Priority

`P1 Investigation`

### 질문

follower 10,000 미만 판정을 위한 evidence freshness와 경계 재확인 범위를 어떻게 정하는가?

### 답변 선택지

- A. 일반 assessment TTL은 7일로 두고, 실행 준비 시 값이 9,000~9,999이거나 관찰 후 24시간이 지났으면 운영자가 다시 확인한다. 추천한다.
- B. 모든 follower evidence를 실행 전 24시간 이내 값으로 요구한다.
- C. assessment TTL을 30일로 두고 `10K`처럼 표시가 모호할 때만 다시 확인한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: TTL과 재확인 범위를 적는다: ____

### 답변하지 않으면

필드와 configurable rule은 구현하되 production `PolicyVersion`의 TTL을 임의 활성화하지 않는다.

### 근거 요약

임계값 자체는 확정됐지만 stale·반올림 값을 어떻게 다룰지는 아직 정해지지 않았다.

## Q4

### Priority

`P1 Investigation`

### 질문

comment·DM approval은 시간 기준으로 언제 만료하는가?

### 답변 선택지

- A. 승인 후 24시간에 만료하고 draft·target·eligibility·content·cooldown 변경 시 즉시 무효화한다. 추천한다.
- B. 승인 후 72시간에 만료하고 동일한 변경 기반 무효화를 적용한다.
- C. 시간 만료는 두지 않고 변경 기반 무효화만 적용한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: action별 TTL과 예외를 적는다: ____

### 답변하지 않으면

approval expiry field와 invalidation rule은 구현하되 production TTL을 확정하지 않는다.

### 근거 요약

수동 실행이 늦어지면 follower, content, cooldown과 문안 맥락이 달라질 수 있다.

## Q5

### Priority

`P2 Non-blocking`

### 질문

마지막 확인 가능한 게시 활동이 90일을 넘은 후보를 어떤 상태로 둘 것인가?

### 답변 선택지

- A. `REVIEW_REQUIRED`로 보내 운영자가 현재 활동 가능성을 확인한다. 추천한다.
- B. `ELIGIBLE`을 유지하되 recent activity ranking 점수를 0점으로 둔다.
- C. 모르겠다 / 추가 조사 필요
- D. 기타: 처리 기준을 적는다: ____

### 답변하지 않으면

두 방식을 `PolicyVersion`으로 지원하고 안전 기본안 A를 설계 제안으로만 유지한다.

### 근거 요약

90일 초과는 hard exclude가 아니라 낮은 우선순위 또는 review라는 범위까지만 확정되어 있다.

## Q6

### Priority

`P1 Investigation`

### 질문

구체 retention과 AI provider가 미확정인 상태에서 Phase 3 production 기능을 어떻게 gate하는가?

### 답변 선택지

- A. 최소 데이터 구조와 삭제 가능성은 구현하되 외부 AI 호출은 privacy·보안 검토와 provider 승인 전까지 비활성화한다. 추천한다.
- B. 구현 전에 보유 기간과 사용할 enterprise AI provider를 먼저 확정한다.
- C. 외부 AI를 사용하지 않고 local model만 검토한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 보유 기간, provider 조건, 허용 데이터 범위를 적는다: ____

### 답변하지 않으면

fake adapter로 generation workflow만 검증하고 실제 공개 profile·content의 외부 AI 전달은 하지 않는다.

### 근거 요약

데이터 최소화 원칙은 확정됐지만 물리 삭제 기간과 실제 provider의 학습·보유·subprocessor 조건은 정해지지 않았다.

## Q7

### Priority

`P2 Non-blocking`

### 질문

Search API provider 선택을 언제 진행하는가?

### 답변 선택지

- A. Phase 1은 manual seed·사람이 제공한 검색 결과로 진행하고, Phase 2에서 공식 문서·계약·동일 query fixture로 provider spike를 수행한다. 추천한다.
- B. Phase 1 전에 사용할 Search API provider와 계약 조건을 사용자가 지정한다.
- C. MVP에서는 Search API를 제외하고 manual·directory source만 사용한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: provider, 계정·계약 상태, 선택 시점을 적는다: ____

### 답변하지 않으면

A를 사용해 Phase 1을 진행할 수 있으며 Search API adapter는 비활성 상태로 둔다.

### 근거 요약

특정 provider의 2026년 가격·신규 이용 가능성·검색 품질·저장 조건을 아직 검증하지 않았고 manual-first 경로가 MVP를 막지 않는다.

## 답변 후 처리

- Q1·Q2 답변 후 Phase 1 task prompt와 skeleton 범위를 확정한다.
- 장기적으로 유효한 답변은 `docs/harness/PROJECT_CONTEXT.md`의 Decision으로 승격한다.
- 숫자 정책은 `PolicyVersion` fixture와 boundary test에 반영한다.
- 이 질문지는 source of truth가 아니며 사용자 답변이 source가 된다.
