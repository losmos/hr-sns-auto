# Project Context

## 프로젝트 목적

- `hr-sns-auto` 프로젝트의 장기 맥락을 이 문서에서 관리한다.
- 제품 및 업무 목적은 아직 확정되지 않았다.

## 배경

- 범용 AI 작업 하네스를 새 개발 프로젝트 복사본에 적용했다.
- 기존 하네스 개발 맥락과 임시 산출물을 2026-08-16에 초기화했다.
- 대상 사용자, 해결할 문제, 운영 배경은 아직 확인되지 않았다.

## 목표

- 프로젝트 요구사항이 확정되면 검증 가능한 제품 목표와 성공 기준을 기록한다.
- 현재 확정된 제품 또는 개발 목표는 없다.

## 제약사항

- 저장소 작업은 루트 `AGENTS.md`의 규칙을 따른다.
- 프로젝트 고유 제약사항은 아직 확정되지 않았다.
- 확정되지 않은 요구사항, 기술 스택, 실행 명령을 추측하지 않는다.

## 확정된 사실

- 프로젝트 이름은 `hr-sns-auto`이다.
- 프로젝트 slug는 `hr-sns-auto`이다.
- 이 문서는 장기 프로젝트 맥락의 source of truth이다.
- `docs/harness/HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억을 관리한다.

## 결정 사항

- 현재 확정된 프로젝트 고유 결정은 없다.
- 새 결정에는 필요한 경우 `DEC-YYYYMMDD-<slug>` 형식의 ID를 사용한다.

## 미확정 질문

- 현재 reset 작업을 막는 `P0 Blocker`는 없다.
- `P1 Investigation`: 후속 기능 구현 전에 제품 목적, 대상 사용자, 핵심 범위를 확인해야 한다.
- `P1 Investigation`: 기술 스택과 프로젝트 실행 명령은 아직 확인되지 않았다.
- `P2 Non-blocking`: `prompts/tasks/`를 전부 커밋할지, 기본 미커밋 후 필요한 프롬프트만 선별 커밋할지 결정하지 않았다.
- 미확정 질문은 필요에 따라 `P0 Blocker`, `P1 Investigation`, `P2 Non-blocking`으로 분류한다.

## 참고 산출물

- reset 시점에 유지한 프로젝트 고유 산출물은 없다.
- 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`에 저장한다.
- 실행 로그는 `agent_outputs/run_logs/`, 대화형 LLM context snapshot은 `agent_outputs/llm_context/`에 저장한다.
- 사용자 답변이 필요한 질문지는 `agent_outputs/clarification_requests/`에 저장한다.

## 다음 작업 기준

- 애플리케이션 구현 전에 제품 목적, 범위, 성공 기준을 확인한다.
- 프로젝트 고유 결정은 이 문서의 `결정 사항`에 기록한다.
- 기술 스택이 확정되면 `AGENTS.md`의 `Commands` 섹션을 실제 명령으로 갱신한다.
- 긴 분석이나 실행 로그 전문은 이 문서에 복사하지 않고 목적별 `agent_outputs/` 경로를 연결한다.
- 작업 종료 시 `docs/harness/HANDOFF.md`를 갱신한다.
