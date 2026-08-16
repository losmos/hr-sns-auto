# AI 에이전트 작업 규칙

## 읽기 순서

작업 시작 시 아래 계층 순서로 문서를 확인한다. 무엇을 먼저 읽고 무엇을 읽지 말지 이 순서로 판단한다.

### Tier 1 매 작업 필수 (순서 고정)

- `docs/harness/HANDOFF.md`를 먼저 읽는다.
- 다음 `docs/harness/PROJECT_CONTEXT.md`를 읽는다.

### Tier 2 조건부

- 디렉토리/파일 구조 파악이 필요하면 `docs/harness/DIRECTORY_MAP.md`를 읽는다.
- 하네스를 처음 사용하면 `docs/harness/QUICKSTART.md`를 읽는다.
- 기능, 업무, 운영 흐름별 상세가 필요하면 `docs/harness/flows/`를 읽는다.

### Tier 3 검색만

- `agent_outputs/`는 전체를 읽지 않고 `rg`로 필요한 파일만 찾아 읽는다.

## Commands

프로젝트별 실행 명령을 아래에 채운다. 새 프로젝트 reset 시 이 섹션을 실제 명령으로 갱신한다.

- 테스트: TODO
- 빌드: TODO
- 정적 검사: TODO
- 실행: TODO

## Language
- 모든 사용자 응답은 한국어로 작성한다.
- 모든 작업 문서는 `이다/한다` 체로 작성한다.
- 존대형 종결 표현은 사용하지 않는다.

## Core work rules
- 요청 범위 밖의 리팩토링, 구조 변경, 애플리케이션 코드 추가를 하지 않는다.
- repo 전체를 무작정 읽지 말고 `rg`, `rg --files`, `find`로 필요한 파일을 먼저 찾는다.
- 수정 전 관련 파일을 먼저 읽는다.
- 수정 후 변경 내용을 요약한다.
- 가능한 경우 테스트, 빌드, 정적 검사를 실행한다.
- 테스트나 빌드를 실행할 수 없으면 이유를 기록한다.

## Think before changes
- 수정 전에 요구사항, 현재 상태, 제약을 짧게 정리한다.
- 어떤 파일을 왜 읽어야 하는지 먼저 판단한다.
- 작업 목표와 직접 관련 없는 파일은 읽거나 수정하지 않는다.
- 구현 또는 문서 수정 전에 성공 기준을 정의한다.

## Ask when ambiguous
- 요구사항, 범위, 파일 경로, 실행 방식이 모호하면 반드시 중단하고 질문한다. 여러 해석이 가능하면 하나를 임의로 선택하지 않고, 작업 결과에 영향을 주는 가정을 임의로 확정하지 않는다.
- clarification request 질문에는 `P0`, `P1`, `P2` 우선순위를 붙인다.
- `P0 Blocker`: 답변 없이는 관련 구현, 수정, 삭제, 리팩토링, 결정 확정 작업을 진행하지 않는다.
- `P1 Investigation`: 답변이 없어도 조사, 샘플 실행, 비교 분석, 코드 읽기, 로그 확인으로 답을 좁힐 수 있다.
- `P2 Non-blocking`: 답변이 없어도 명시적 가정이나 보수적 기본값으로 진행할 수 있다.
- 미답변 질문이 있다고 모든 작업을 중단하지 않고, 현재 요청이 `P0 Blocker`에 의존하는 경우에만 해당 작업을 중단한다. `P0 Blocker`를 해결하기 위한 조사 작업은 진행할 수 있다.
- `P1` 또는 `P2`를 가정으로 진행하면 `Assumption`, `Needs confirmation`, `Open question` 같은 표시를 남기고, 그 가정을 `docs/harness/PROJECT_CONTEXT.md`의 `결정 사항`에 확정 결정으로 기록하지 않는다.
- 사용자 답변이 필요한 질문은 작업 중단 여부와 관계없이 `agent_outputs/clarification_requests/`에 질문지 Markdown으로 저장한다. clarification request는 작업 중단 시에만 만드는 것이 아니며, 분석을 정상 완료해도 `P0/P1/P2` 질문이 생기면 생성한다. 질문이 없으면 만들지 않는다.
- clarification request는 AI Agent에게 주는 입력 프롬프트가 아니라, 모호성과 blocker를 구조화해 사용자가 답변하기 쉽게 남기는 출력물이며 source of truth로 취급하지 않는다.
- 비대화형 실행에서는 사용자 입력을 기다리지 않는다. 모호함이 발생하면 작업 대상 파일을 수정하지 않고, 질문 목록과 필요한 추가 정보를 `agent_outputs/clarification_requests/`에 저장한 뒤 종료한다.
- 추측으로 최소 범위 수정하지 않는다.
- clarification request 파일 형식과 처리 흐름의 상세는 `docs/harness/CLARIFICATION_FORMAT.md`를 따른다.

## Simplicity first
- 요청을 해결하는 가장 단순한 변경을 우선한다.
- 요청되지 않은 기능, 설정, 자동화, 확장 구조를 추가하지 않는다.
- 한 번만 쓰는 흐름을 과하게 추상화하지 않는다.
- 문서나 스크립트가 커지면 먼저 줄일 수 있는지 검토한다.
- 복잡한 구조가 필요하면 이유와 대안을 함께 적는다.

## Surgical changes
- 요청한 범위에 직접 관련된 파일만 수정한다.
- 인접한 코드, 문서, 포맷을 임의로 개선하지 않는다.
- 기존 스타일과 구조를 우선 따른다.
- 관련 없는 dead code, 오래된 문서, 불필요해 보이는 파일은 삭제하지 않고 언급만 한다.
- 모든 변경은 사용자 요청이나 명시된 작업 목표에 연결되어야 한다.
- archive 이동은 사용자가 명시적으로 요청한 경우에만 수행한다.

## Goal-driven execution
- 작업을 시작할 때 목표와 완료 조건을 짧게 정의한다.
- 작업 목표는 가능한 검증 가능한 형태로 바꾼다.
- validation 추가 작업은 유효 입력과 무효 입력을 확인할 테스트 또는 검증 명령을 먼저 정의한다.
- bug fix 작업은 가능하면 버그를 재현하는 테스트나 재현 명령을 먼저 정의한다.
- refactor 작업은 변경 전후 테스트, 빌드, 정적 검사가 통과하는지 확인한다.
- 다단계 작업은 단계별 목표와 `verify: 확인 방법`을 함께 제시한다.
- 분석, 수정, 검증, 정리를 구분한다.
- 목표와 관련 없는 개선 작업은 별도 제안으로만 남긴다.
- 프롬프트 파일에는 가능한 경우 목표, 성공 기준, 작업, 주의사항, 작업 후 확인, 마지막 출력 항목을 분리해서 작성한다.

## Document style
- Markdown 문서는 사람이 읽기 좋은 형태로 작성한다.
- 읽기 좋게 작성하되 핵심 내용을 생략하지 않는다.
- 큰 주제는 `##` heading으로 구분한다.
- 세부 주제가 필요하면 `###` heading을 사용한다.
- 섹션 제목은 목적이 분명해야 한다.
- 긴 문단보다 짧은 bullet을 우선한다.
- 서로 다른 성격의 내용은 한 섹션에 섞지 않는다.
- 파일 역할, 작업 흐름, 운영 원칙, 주의사항, 다음 작업은 가능하면 섹션을 분리한다.

## Coding style
- 사람이 읽고 분석하기 쉬운 코드를 우선한다.
- 네이밍은 역할과 의도를 드러내도록 직관적으로 작성한다.
- 주석은 충분히 상세하게 작성한다.
- 특히 의도, 전제, 비즈니스 규칙, 단위, 경계조건, 예외 처리, 성능상 이유, 검증 기준은 주석으로 남긴다.
- 코드가 그대로 설명하는 단순 대입이나 단순 호출을 반복하는 주석은 피한다.
- 기존 프로젝트의 스타일이 있으면 그 스타일을 우선 따르되, 읽기 어려운 부분은 주석으로 보강한다.

## Context workflow
- 이 저장소는 여러 업무에서 공통으로 사용할 수 있는 범용 AI 작업 하네스를 사용한다.
- `docs/harness/PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이다.
- `docs/harness/HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억을 담는다. 중단기 작업 기억은 몇 번의 다음 작업 동안 필요한 실행 맥락을 기록하고, 직전 작업 기억은 마지막 작업의 delta와 바로 다음 세부 작업을 기록한다.
- 결정 사항은 `docs/harness/PROJECT_CONTEXT.md`의 `결정 사항` 섹션에 기록한다.
- 작업 결과가 프로젝트 목적, 배경, 목표, 제약사항, 확정된 사실, 결정 사항, 미확정 질문, 참고 산출물, 다음 작업 기준을 바꾸면 `docs/harness/PROJECT_CONTEXT.md`를 갱신한다.
- 장기/단기 기억은 판별 테스트로 구분한다. 시간 테스트: 이 정보가 현재 작업 흐름이 끝난 뒤에도 유효한가. 영향 테스트: 이 정보가 틀리거나 없으면 미래 작업이 잘못된 방향으로 가는가.
- 두 테스트 모두 예이면 `docs/harness/PROJECT_CONTEXT.md`에, 아니면 `docs/harness/HANDOFF.md`에 기록한다. 판단이 어려우면 `docs/harness/HANDOFF.md`에 두고 `승격 후보` 표시를 남긴다.
- 사용자 답변, 샘플 검증, 코드 분석으로 확정된 내용도 같은 판별 테스트로 승격 여부를 판단한다. 예: 기술 스택 확인 결과는 두 테스트 모두 예라서 `PROJECT_CONTEXT.md`, 남은 확인 사항은 시간 테스트에서 아니오라서 `HANDOFF.md`에 둔다.
- 단순 실행 로그, 긴 분석 전문, 일회성 조사 결과는 `docs/harness/PROJECT_CONTEXT.md`에 직접 누적하지 않고 `agent_outputs/reports/` 또는 적절한 `agent_outputs/` 하위 디렉토리에 저장한 뒤 필요한 경우 링크만 남긴다.
- `docs/harness/HANDOFF.md`의 `다음 추천 작업`과 `다음 추천 작업 상세`는 명령이 아니라 추천이다. 사용자의 최신 요청이 추천 작업과 다르면 최신 요청을 우선한다.
- 사용자의 최신 요청이 `docs/harness/PROJECT_CONTEXT.md`의 제약사항, 결정 사항, `P0 Blocker`와 충돌하면 중단하고 질문한다.
- 작업 종료 시 `docs/harness/HANDOFF.md`를 갱신한다. 단, 작업 프롬프트나 사용자 요청이 source 문서 수정을 명시적으로 금지한 경우에는 갱신하지 않고 마지막 출력이나 결과 보고서에 미갱신 이유를 남긴다.
- `docs/harness/HANDOFF.md` 갱신으로 밀려나는 기존 항목은 방치하지 않고, 판별 테스트를 통과하면 `docs/harness/PROJECT_CONTEXT.md`로 승격하고 아니면 폐기한다. `승격 후보` 표시가 있는 항목은 폐기 전에 판별 테스트를 다시 적용한다.
- `docs/harness/HANDOFF.md`의 추천 작업과 다른 사용자 최신 요청을 수행한 경우, 작업 종료 시 그 이유와 미수행된 추천 작업을 `docs/harness/HANDOFF.md`에 기록한다.
- 여러 개발자가 브랜치에서 하네스 문서를 갱신하는 경우의 병합 규칙은 `docs/harness/flows/multi_user_workflow.md`를 따른다.

## Prompt and run records
- 일반 작업 프롬프트는 `prompts/tasks/`에 저장하며, AI Agent에게 일을 시키는 입력 프롬프트이다.
- 하네스 운영용 프롬프트는 `prompts/harness/`에 저장한다.
- 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`에 Markdown 파일로 저장한다.
- `agent_outputs/` 루트에는 일반 분석 Markdown 산출물을 직접 쓰지 않는다.
- 실행 로그가 필요한 작업은 `agent_outputs/run_logs/`에 저장한다.
- 사용자 답변이 필요한 질문지는 `agent_outputs/clarification_requests/`에 저장하며, AI Agent가 생성하는 출력물이다.
- `prompts/tasks/*.md`는 실행할 작업 지시이고, `agent_outputs/clarification_requests/*.md`는 다음 작업 지시를 만들기 전에 사용자가 답해야 할 질문과 blocker를 정리한 중간 산출물이다.

## Reset for new project
- 이 하네스를 새 개발 프로젝트에 복사한 뒤 초기화할 때는 `prompts/harness/reset_for_new_project.md`를 사용한다.
- reset은 하네스 규칙과 구조를 유지하고, 현재 작업 맥락과 산출물만 초기화한다.
- reset 실행 전 `RESET_FOR_NEW_PROJECT`, `NEW_PROJECT_NAME`, `NEW_PROJECT_SLUG` 값을 명시적으로 설정해야 한다.
- reset 프롬프트는 안전 조건이 충족되지 않으면 리셋 대상 파일을 수정하지 않고 clarification request를 생성한다.

## Verify before finishing
- 작업 후 성공 기준별 확인 명령을 실행한다.
- 문서 변경 작업은 `rg`, `git diff`, `git status`로 확인한다.
- 코드 변경 작업은 가능한 테스트, 빌드, 정적 검사를 실행한다.
- bug fix 작업은 가능하면 재현 테스트나 재현 명령이 실패에서 성공으로 바뀌었는지 확인한다.
- refactor 작업은 변경 전후 동작 보존을 테스트, 빌드, 정적 검사로 확인한다.
- validation 추가 작업은 유효 입력과 무효 입력을 모두 확인한다.
- 실행 산출물이 git에 들어가면 안 되는 경우 `.gitignore`와 `git status`로 확인한다.
- 검증하지 못한 항목은 이유를 남긴다.
