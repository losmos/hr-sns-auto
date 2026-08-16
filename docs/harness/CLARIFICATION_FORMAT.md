# Clarification Request Format

이 문서는 clarification request의 파일 형식 명세와 처리 흐름을 정의한다. clarification request의 행동 원칙(모호하면 중단하고 질문, `P0/P1/P2` 우선순위, 비대화형 실행 규칙 등)은 `AGENTS.md`의 `Ask when ambiguous`를 따른다.

## 파일명

- clarification request 파일은 `agent_outputs/clarification_requests/YYYYMMDD_HHMMSS_<task>.md` 형식으로 저장한다.

## 빠른 답변표

- clarification request 파일 상단에는 `빠른 답변표`를 먼저 둔다.
- `빠른 답변표`는 질문 전체를 훑는 인덱스 역할만 한다.
- `빠른 답변표`의 표준 컬럼은 `질문 ID`, `Priority`, `한 줄 질문`, `답변 방식`, `권장 다음 행동`으로 제한한다.
- `빠른 답변표`에는 긴 선택지, 긴 근거, 관련 파일 목록, 추천 기본값을 넣지 않는다.
- 선택지는 각 질문 본문에 둔다.

## 질문 ID

- 질문 ID는 `Q1`, `Q2`처럼 1부터 시작하며 `Q0`은 사용하지 않는다.

## 객관식 작성 규칙

- 질문은 가능하면 객관식으로 작성한다.
- 모든 객관식 질문에는 `모르겠다 / 추가 조사 필요` 선택지를 포함한다.
- 선택지에 없는 답변을 위해 `기타: ____` 선택지를 둔다.
- 객관식으로 표현하기 어려운 질문만 주관식으로 작성한다.
- 주관식 질문에는 사용자가 어떤 정보를 적어야 하는지 구체적으로 안내한다.

## 질문 본문 구조

- 질문 본문은 짧고 읽기 쉽게 작성한다.
- 질문별 본문은 `## Q1`, `### Priority`, `### 질문`, `### 답변 선택지`, `### 답변하지 않으면`, `### 근거 요약` 구조를 따른다.

## 노출 금지 필드

- `Current evidence`, `Blocks`, `Can proceed without answer`, `Default if unanswered`, `Why it matters`, `Related files` 같은 긴 필드는 기본 질문 본문에 그대로 노출하지 않는다.
- 긴 코드 근거, 관련 파일 목록, 상세 분석은 질문 본문에 길게 넣지 않는다.
- 필요한 경우 긴 근거와 관련 파일은 `근거 요약`으로 짧게 요약하고, 상세 내용은 `상세 근거`, `부록`, 또는 `agent_outputs/reports/`의 분석 보고서로 분리한다.

## 질문 수 제한

- `P0 Blocker` 질문이 많으면 한 파일에 모두 넣지 않고 5~7개 이하의 핵심 질문만 먼저 묻는다.
- 나머지 질문은 `후속 질문` 섹션이나 별도 clarification request로 분리한다.

## 처리 흐름

- 분석 보고서에는 질문 전문을 길게 반복하지 않고 관련 clarification request 파일 경로를 남긴다.
- clarification request가 생성되면 사용자는 해당 파일을 읽고 질문에 답변한다.
- 사용자의 답변과 clarification request를 대화형 LLM에 전달해 다음 `prompts/tasks/*.md` 작업 프롬프트를 작성한다.
- 질문에 대한 답이 장기적으로 유효한 내용이면 `docs/harness/PROJECT_CONTEXT.md`에 승격한다.
- 직전 작업 delta나 바로 이어갈 세부 작업이면 `docs/harness/HANDOFF.md`에 반영한다.
- clarification request 자체를 source of truth로 취급하지 않고, 질문과 blocker를 구조화한 중간 산출물로 취급한다.
- `docs/harness/HANDOFF.md`에는 모든 질문을 자동으로 남기지 않는다. blocker로 남길 필요가 있거나 사용자가 요청한 경우에만 질문을 반영한다.
