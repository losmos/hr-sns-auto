# Handoff

## 마지막 갱신일

- 2026-08-17 01:10:04

# 중단기 작업 기억

## 이번 범위

- 사용자 Q1~Q8과 추가 운영 결정을 장기 source of truth에 반영했다.
- 초기 아키텍처를 discovery, eligibility, ranking, content, generation, approval, cooldown, module, data model, UI, roadmap 수준으로 상세화했다.
- Option A~C 기술 스택을 비교하고 Spring Boot 서버 렌더링 modular monolith를 추천했다.
- 애플리케이션 코드와 project skeleton은 생성하지 않았다.

## 현재 상태

- MVP 상세 설계 보고서가 완료된 상태이다.
- 첫 release mode는 `APPROVAL_REQUIRED + MANUAL_EXECUTION`으로 확정됐다.
- 일일 목표는 운영자 검토가 가능한 신규 `ELIGIBLE` 후보 최대 15명이며 quota가 아니다.
- 모발이식 관련 불확실 후보는 `REVIEW_REQUIRED`이다.
- browser action automation과 Meta 필수 연동은 MVP 범위에서 제외됐다.
- 추천 기술 스택은 Spring Boot + Spring MVC/Thymeleaf + PostgreSQL + Docker Compose이지만 아직 사용자 확정 Decision은 아니다.
- 테스트·빌드·정적 검사·실행 명령은 애플리케이션이 없어 `TODO` 상태이다.

## 확정 운영 정책 요약

- 초기 대상은 대한민국 한국어 계정의 의사·약사이며 진료과 quota는 두지 않는다.
- bio/category만으로 profession을 통과시키지 않고 강한 공개 근거와 Instagram identity 연결을 요구한다.
- 최근 30일 활동을 우선하며 활동 기간만으로 hard exclude하지 않는다.
- 후보당 하루 신규 outbound action은 하나이며 content interaction을 먼저 검토하고 DM은 별도 시점·별도 승인으로 다룬다.
- cold DM 무응답 재발송 금지, 동일 post comment 1회, candidate cooldown 30일, 거절·연락 중단·차단 permanent suppression을 적용한다.
- 공개 데이터와 외부 AI 전달은 permalink·구조화 사실·최소 excerpt 중심으로 최소화한다.
- `prompts/tasks/*.md`는 민감 정보를 제외하고 기본 Git commit 대상이다.

## 구현 권장안

- 배포 단위 하나의 Spring Boot modular monolith를 사용한다.
- 서버 렌더링 UI로 Daily Workbench, Candidate Workspace, Discovery & Policy Admin 세 화면을 구성한다.
- PostgreSQL을 system of record로 사용하고 policy·assessment·draft·approval·interaction을 versioned·auditable하게 저장한다.
- Phase 1은 manual seed, evidence, deterministic eligibility, review queue부터 시작한다.
- Search API와 Meta Business Discovery는 core dependency가 아닌 optional read adapter로 둔다.

## 남은 결정과 blocker

- Phase 1 코드 착수의 `P0 Blocker`는 기술 스택과 operator 인증 방식 확정이다.
- follower evidence TTL, approval TTL, 90일 초과 activity 처리, retention·AI provider gate는 후속 결정이 필요하다.
- 특정 Search API provider 선정과 Meta prerequisites 조사는 manual-first MVP blocker가 아니다.
- 이번 문서 작업 완료에는 blocker가 없다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다. 기존 Q1~Q8 미확정 항목을 해소하고 장기 정책에 Decision ID를 부여했다.
- Meta account 상태와 기술 스택·TTL·보유 기간 등 미확정 사실은 Decision으로 추측하지 않았다.

## 직전 작업 delta

- `agent_outputs/reports/mvp_implementation_plan.md`: 구현 직전 수준의 MVP 상세 설계와 Phase별 계획을 추가했다.
- `agent_outputs/clarification_requests/20260817_005758_mvp_implementation_decisions.md`: 구현 전 남은 P0/P1/P2 질문을 추가했다.
- `docs/harness/PROJECT_CONTEXT.md`: 사용자 확정 정책, 최신 미확정 질문, 새 보고서 링크를 반영했다.
- `docs/harness/HANDOFF.md`: 이번 결과, 추천 스택, 다음 단계와 blocker로 교체했다.

## 마지막 작업 요약

- manual-first MVP가 특정 Search·Meta provider 없이도 후보 발굴부터 승인·수동 실행 기록까지 동작하도록 범위를 확정하고, 구현 순서를 Phase 1~5로 구체화했다.

## 변경 파일

- `agent_outputs/reports/mvp_implementation_plan.md`
- `agent_outputs/clarification_requests/20260817_005758_mvp_implementation_decisions.md`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 생성 산출물

- `agent_outputs/reports/mvp_implementation_plan.md`
- `agent_outputs/clarification_requests/20260817_005758_mvp_implementation_decisions.md`

## 다음 추천 작업 상세

1. 새 clarification request의 Q1·Q2에 답해 기술 스택과 인증 방식을 확정한다.
2. 답변을 반영한 Phase 1 구현 task prompt를 `prompts/tasks/`에 작성한다.
3. Phase 1에서 eligibility golden fixture와 DB constraint·audit 기준을 먼저 구현한다.
4. Phase 2 전 Search API가 필요하면 공식 문서·계약·동일 query fixture 기반 provider spike를 수행한다.
5. Meta account prerequisites가 확인될 때만 외부 action 없는 read-only spike를 별도 수행한다.

## 이전 추천 작업과의 관계

- 이전 Handoff는 Q1~Q8 답변과 eligibility·cooldown·approval 구체화를 추천했다. 최신 사용자 요청이 해당 답변을 제공해 이번 작업에서 수행했다.
- 이전에 권장된 Meta read-only spike는 Q7 상태가 미확인이고 Meta가 MVP blocker가 아니라는 최신 결정에 따라 수행하지 않았다. optional investigation으로 유지한다.

## 검증 상태

- 문서 heading, Phase별 필수 항목, clarification 형식, 핵심 Decision과 미확정 구분을 `rg`로 확인했다.
- `git diff --check`가 통과했고 `git diff`·`git status`로 변경 범위를 확인했다.
- 애플리케이션이 없어 코드 테스트, build, 정적 검사는 실행할 수 없다.

## 주의할 점

- 구체 Spring·PostgreSQL version은 skeleton 생성 시 공식 지원 상태를 다시 확인하고 pin한다.
- 특정 Search API의 가격·이용 가능성·품질은 공식 문서와 실제 계약 확인 없이 단정하지 않는다.
- 외부 AI production 호출은 retention·학습 사용·보유·subprocessor 검토 전 활성화하지 않는다.
- 작업 시작 전 존재한 `prompts/tasks/define_mvp_and_implementation_plan.md` 미추적 파일은 수정하지 않았다.
