ALLOW_DIRTY_SNAPSHOT: NO

AGENTS.md, docs/harness/PROJECT_CONTEXT.md, docs/harness/HANDOFF.md를 먼저 읽어라.

목표:
현재 하네스 문서와 작업 상태를 읽고, 새 대화형 LLM 세션에 그대로 붙여넣을 수 있는 맥락 snapshot 파일을 생성한다.

작업 순서 0단계, 생성 전 git 상태 확인(최우선, 다른 어떤 단계보다 먼저 수행):
1. `git status --short`를 실행한다. tracked 변경과 untracked 파일 모두 이 gate의 확인 대상이다.
2. `.gitignore`로 제외되는 산출물(`agent_outputs/llm_context/`, `agent_outputs/run_logs/` 등)은 `git status`에 나타나지 않으므로 자연히 gate 대상이 아니다. 이 프롬프트가 만드는 snapshot 산출물 자체를 미커밋 변경으로 오인하지 않는다.
3. 출력이 비어 있으면 `ALLOW_DIRTY_SNAPSHOT` 값과 무관하게 아래 `읽을 문서`부터 정상 진행한다.
4. 출력이 있고 `ALLOW_DIRTY_SNAPSHOT`이 `NO`이면 snapshot 파일을 포함해 어떤 파일도 생성하거나 수정하지 않는다. 미커밋 파일 목록과 함께 "커밋 또는 정리 후 재실행하거나, 의도적으로 미커밋 상태를 담으려면 `ALLOW_DIRTY_SNAPSHOT: YES`로 설정하라"는 안내를 출력하고 즉시 종료한다. 이후의 문서 읽기, 요약 작성 등 본 작업은 진행하지 않는다.
5. 출력이 있고 `ALLOW_DIRTY_SNAPSHOT`이 `YES`이면 미커밋 상태에서도 생성을 진행하되, 생성할 Markdown의 `Current git state` 절에 미커밋 상태임과 그 파일 목록을 명시한다.
6. `ALLOW_DIRTY_SNAPSHOT: YES`로 생성에 성공한 경우, 이 파일 상단의 안전 설정값을 `ALLOW_DIRTY_SNAPSHOT: NO` 기본값으로 되돌린다(reset, sync와 같은 self-lock 방식). `NO`로 정상 생성한 경우에는 설정값 변경이 없으므로 self-lock 동작이 불필요하다.

핵심 원칙:
1. 대화형 LLM을 위한 별도 고정 맥락 문서는 만들지 않는다.
2. 기존 하네스 문서를 역할별 기준 문서로 사용한다.
   - `AGENTS.md`는 AI Agent 행동 규칙의 기준 문서이다.
   - `docs/harness/PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이다.
   - `docs/harness/HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억 문서이다.
   - `docs/harness/DIRECTORY_MAP.md`는 디렉토리/파일 구조 설명 문서이다.
3. 생성 결과는 특정 시점의 snapshot으로만 취급한다.
4. 생성 결과는 `agent_outputs/llm_context/` 아래에 저장한다.
5. 원문 문서를 그대로 길게 덤프하지 않는다.
6. 대화형 LLM이 이어서 조언하는 데 필요한 핵심만 요약한다.
7. 핵심 내용은 생략하지 않는다.
8. 생성된 파일은 사람이 별도 안내문을 덧붙이지 않아도 그대로 새 대화형 LLM에 붙여넣을 수 있어야 한다.
9. 사람이 복사해야 하는 범위를 `--- BEGIN CHAT LLM CONTEXT ---`부터 `--- END CHAT LLM CONTEXT ---`까지로 명확히 안내한다.

읽을 문서:
- `AGENTS.md`
- `docs/harness/README.md`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `docs/harness/DIRECTORY_MAP.md`
- `docs/harness/QUICKSTART.md`

추가로 확인할 정보:
- `git status --short`
- `git diff --stat`
- 최근 `prompts/tasks/*.md` 파일 목록
- 최근 `prompts/harness/*.md` 파일 목록
- 최근 `agent_outputs/run_logs/*.log` 파일 목록
- 최근 `agent_outputs/reports/*.md` 파일 목록
- 최근 `agent_outputs/clarification_requests/*.md` 파일 목록

생성할 디렉토리:
- `agent_outputs/llm_context/`

생성할 파일:
- `agent_outputs/llm_context/YYYYMMDD_HHMMSS_chat_llm_context.md`

파일명 규칙:
- 현재 시간을 사용해 `YYYYMMDD_HHMMSS_chat_llm_context.md` 형식으로 만든다.
- 같은 이름 파일을 덮어쓰지 않는다.

생성할 Markdown 구조:

# Chat LLM Context

## Human guide

이 섹션은 사람이 읽는 안내다.

포함할 내용:
- 이 파일은 새 대화형 LLM 세션에 그대로 붙여넣기 위한 파일이라고 설명한다.
- 새 대화형 LLM에는 파일 전체를 붙여넣어도 되지만, 권장 복사 범위는 `--- BEGIN CHAT LLM CONTEXT ---` 줄부터 `--- END CHAT LLM CONTEXT ---` 줄까지라고 설명한다.
- `Human guide` 섹션은 사람이 읽는 안내이며, 새 대화형 LLM에게 꼭 전달할 필요는 없다고 설명한다.
- `--- END CHAT LLM CONTEXT ---` 아래의 터미널 prompt, shell 출력, 추가 로그는 복사하지 않는다고 설명한다.
- 사람이 별도 설명을 덧붙일 필요가 없다고 설명한다.
- 이 파일은 특정 시점의 snapshot이라고 설명한다.
- 기준 문서 역할을 계층화해서 설명한다.
  - `AGENTS.md`는 AI Agent 행동 규칙의 기준 문서이다.
  - `PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이다.
  - `HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억 문서이다.
  - `DIRECTORY_MAP.md`는 디렉토리/파일 구조 설명 문서이다.
- 최신 사용자 입력과 실제 파일 내용, 명령 결과가 snapshot보다 우선한다고 설명한다.
- 새 작업을 시작하기 전에는 필요하면 이 snapshot을 다시 생성하는 것이 좋다고 설명한다.

## Copy range

포함할 내용:
- 아래 `--- BEGIN CHAT LLM CONTEXT ---` 줄부터 `--- END CHAT LLM CONTEXT ---` 줄까지 복사하라고 설명한다.
- 복사 시작 줄과 끝 줄을 모두 포함하라고 설명한다.
- `--- END CHAT LLM CONTEXT ---` 이후의 터미널 prompt는 복사하지 말라고 설명한다.

## Copy from here

반드시 다음 시작 구분선을 넣는다.

`--- BEGIN CHAT LLM CONTEXT ---`

그 아래부터 새 대화형 LLM에게 직접 전달할 문맥과 지시를 작성한다.

## Instruction for chat LLM

포함할 내용:
- 아래 내용은 현재 작업 맥락 snapshot이라고 설명한다.
- 이 메시지는 즉시 처리할 작업 요청이 아니라고 설명한다.
- 이 메시지는 앞으로 AI Agent에게 줄 프롬프트를 작성할 때 사용할 운영 지침과 현재 작업 맥락이라고 설명한다.
- 대화형 LLM은 AI Agent를 직접 실행하는 주체가 아니라고 설명한다.
- 대화형 LLM은 사용자가 AI Agent에게 줄 프롬프트 파일과 실행 명령을 만들도록 돕는 조언자라고 설명한다.
- 이 메시지를 받은 직후에는 코드, 명령어, 분석, 다음 작업 제안을 작성하지 않는다고 설명한다.
- 첫 응답은 짧은 수신 확인만 한다고 설명한다.
- 권장 첫 응답은 `맥락 수신 완료. 다음 질문, 실행 결과, git diff, 로그, 파일 내용을 보내면 이 맥락을 기준으로 이어서 돕겠다.` 라고 설명한다.
- 답변은 한국어로 작성한다고 명시한다.
- 문서와 프롬프트는 "이다/한다" 체로 작성한다고 명시한다.
- `입니다`, `합니다`, `됩니다`, `습니다` 같은 존대 표현은 사용하지 않는다고 명시한다.
- 일반 작업 프롬프트는 `prompts/tasks/*.md`, 하네스 운영용 프롬프트는 `prompts/harness/*.md` 파일로 생성하는 방식으로 제안한다고 명시한다.
- AI Agent용 작업 프롬프트를 제안할 때 프롬프트 파일 생성 명령만 주지 않고, AI Agent 실행 명령, 실행 결과 확인 명령, git 변경 확인 명령, 추천 커밋 메시지, git 반영 명령, 최종 확인 명령을 함께 제공한다고 명시한다.
- 커밋과 push는 사용자가 diff를 확인한 뒤 수행하는 단계로 안내한다고 명시한다.
- AI Agent용 프롬프트 파일 생성 명령은 Markdown fence safety 규칙에 따라 물결표 4개 바깥 블록과 heredoc-safe 형식으로 작성한다고 명시한다.
- heredoc 내부 프롬프트에는 백틱 3개짜리 Markdown 코드블록을 넣지 않고, 명령 예시는 들여쓰기된 plain text로 작성한다고 명시한다.
- heredoc delimiter는 `HARNESS_PROMPT_EOF`, `TASK_PROMPT_EOF`처럼 충분히 고유한 문자열을 사용하고, 종료 문자열이 프롬프트 본문 중간에 등장하지 않게 한다고 명시한다.
- AI Agent용 프롬프트 파일 생성 명령은 복사해서 바로 실행 가능한 단일 bash 블록으로 제공하는 것을 우선한다고 명시한다.

## Required first response

포함할 내용:
- 첫 응답은 짧은 수신 확인만 작성한다.
- 권장 첫 응답 문구는 `맥락 수신 완료. 다음 질문, 실행 결과, git diff, 로그, 파일 내용을 보내면 이 맥락을 기준으로 이어서 돕겠다.` 이다.
- context snapshot을 받은 직후에는 실질 분석, 코드, 명령어, 다음 작업 제안을 작성하지 않는다.
- 실질 작업은 다음 사용자 메시지부터 시작한다.
- 사용자가 다음 질문, 실행 결과, `git diff`, 로그, 파일 내용을 붙여넣으면 그 최신 입력을 우선해서 답한다.

## Prompt writing rules for chat LLM

포함할 내용:
- AI Agent용 프롬프트는 얇게 작성한다.
- `AGENTS.md`에 이미 있는 전역 규칙을 매번 반복하지 않는다.
- 프롬프트에는 이번 작업에만 필요한 목표, 성공 기준, 작업, 주의사항, 작업 후 확인을 작성한다.
- 성공 기준은 전역 문서 스타일 규칙이 아니라 이번 작업 결과를 판정할 수 있는 조건으로 작성한다.
- 한국어, `이다/한다` 체, 문서 스타일, 요청 범위 밖 변경 금지 같은 공통 규칙은 `AGENTS.md`를 따른다고만 적는다.
- 단, 해당 작업에서 반드시 강조해야 하는 전역 규칙은 짧게 재언급할 수 있다.
- 다음 task prompt를 만들 때 `PROJECT_CONTEXT.md`와 `HANDOFF.md`를 모두 고려한다고 명시한다.
- 다음 task prompt를 만들 때 사용자의 최신 요청과 `PROJECT_CONTEXT.md`의 제약사항, 결정 사항, `P0 Blocker`를 우선 확인한다고 명시한다.
- `HANDOFF.md`의 중단기 작업 기억에 있는 `다음 추천 작업`은 이어가기 방향이고, 직전 작업 기억에 있는 `다음 추천 작업 상세`는 바로 이어갈 세부 추천 액션이라고 명시한다.
- 사용자의 최신 요청이 `HANDOFF.md`의 추천 작업과 다르면 최신 요청을 우선하되, 작업 종료 시 `HANDOFF.md`에 그 delta를 남기도록 안내한다.
- AI Agent용 작업 프롬프트를 제안할 때는 기본적으로 아래 블록을 함께 제공한다고 명시한다.
  1. 작업 프롬프트 파일 생성 명령
  2. AI Agent 실행 명령
  3. 실행 결과 확인 명령
  4. git 변경 확인 명령
  5. 추천 커밋 메시지
  6. git 반영 명령
  7. 최종 확인 명령
- 실행 결과 확인 명령에는 작업 성격에 맞는 `rg`, 테스트, 빌드, 정적 검사, 산출물 확인 명령을 포함한다고 명시한다.
- git 변경 확인 명령에는 기본적으로 `git status --short`, `git diff --stat`, `git diff`를 포함한다고 명시한다.
- git 반영 명령에는 기본적으로 `git add -A` 또는 변경 파일을 명시한 `git add ...`, `git commit -m "<recommended message>"`, `git push`를 포함한다고 명시한다.
- 커밋과 push는 사용자가 `git diff`를 확인한 뒤 수행하는 단계로 안내한다고 명시한다.
- 커밋 전 `git status --short`로 `agent_outputs/run_logs/`의 run log와 `agent_outputs/llm_context/`의 llm context snapshot이 git에 들어가지 않는지 확인하게 한다고 명시한다.
- 변경 범위가 좁거나 git 제외 산출물이 섞일 수 있으면 `git add -A` 대신 변경 파일을 명시한 `git add`를 제안한다고 명시한다.
- 작업 성격상 커밋이 필요 없으면 이유를 짧게 설명하고 커밋 명령을 생략할 수 있다고 명시한다.
- AI Agent 실행 명령을 제안할 때 작업 유형별 권장 모델 티어(설계·계획·리뷰는 상위, 구현·문서 대량 수정은 중위, 잡무는 하위, 검증은 작업에 사용한 모델과 다른 모델)를 함께 제안하고, 상세 기준은 `docs/harness/QUICKSTART.md`의 `작업 유형별 권장 모델 티어` 표를 따른다고 명시한다.
- 사용한 모델을 run log 파일명 또는 `docs/harness/HANDOFF.md`의 직전 작업 기억에 기록하는 관례를 실행 명령 제안에 반영한다고 명시한다.
- 실행 명령에 진행 상황 실시간 표시를 선택적으로 켜고 끌 수 있다고 안내하고, 상세는 `docs/harness/QUICKSTART.md`의 `진행 상황 실시간 표시(선택)` 절을 따른다고 명시한다.
- 실패 비용이 큰 작업(문서 대량 수정, 구조 변경, 코드 변경)의 프롬프트를 제안한 경우, 커밋 이후 단계로 `prompts/harness/verify_task_result.md` 실행 블록을 선택 단계로 함께 제안한다고 명시한다.
- AI Agent용 프롬프트 파일 생성 명령을 줄 때는 Markdown fence safety 형식으로 작성한다고 명시한다.
- 바깥 쉘 명령 코드블록은 백틱 3개 대신 물결표 4개를 사용한다고 명시한다.
- heredoc 내부 프롬프트에는 백틱 3개짜리 Markdown 코드블록을 넣지 않는다고 명시한다.
- 프롬프트 내부에 명령 예시가 필요하면 fenced code block을 쓰지 않고 들여쓰기된 plain text로 작성한다고 명시한다.
- 코드블록 중첩이 필요해 보이는 경우에도 heredoc 내부에서는 fenced code block 대신 plain text를 사용한다고 명시한다.
- heredoc delimiter는 `HARNESS_PROMPT_EOF`, `TASK_PROMPT_EOF`처럼 작업마다 충분히 고유한 이름을 사용한다고 명시한다.
- heredoc 종료 문자열이 프롬프트 본문 중간에 등장하지 않게 한다고 명시한다.
- AI Agent용 프롬프트 파일 생성 명령은 복사해서 바로 실행 가능한 단일 bash 블록으로 제공하는 것을 우선한다고 명시한다.
- 명령 블록 밖에는 짧은 설명만 둔다고 명시한다.
- 사용자가 원하면 실행 명령, 확인 명령, 커밋 명령을 별도 블록으로 나눌 수 있지만, 프롬프트 파일 생성 명령 자체는 깨지지 않는 하나의 bash 블록으로 제공한다고 명시한다.

## Ambiguity handling

포함할 내용:
- AI Agent용 프롬프트를 만들 때 작업 범위가 모호하면 먼저 사용자에게 질문한다.
- 비대화형 AI Agent 실행에서 모호함이 발생하면 파일을 수정하지 않고 질문 목록을 출력한 뒤 종료하도록 지시한다.
- 모호함으로 중단된 질문 목록은 `agent_outputs/clarification_requests/`에 구조화된 Markdown 파일로 남기도록 지시한다.
- 작업 중단 없이 완료한 분석에서도 `P0/P1/P2` 사용자 확인 질문이 남으면 `agent_outputs/clarification_requests/`에 별도 질문지를 만들도록 지시한다.
- clarification request는 AI Agent에게 주는 작업 지시가 아니라 AI Agent가 생성한 질문 출력물로 해석한다고 명시한다.
- clarification request를 만들거나 기존 질문지를 재정리할 때는 `AGENTS.md`의 표준 질문지 형식을 따른다고 명시한다.
- clarification request는 `빠른 답변표`와 질문별 본문으로 구성한다고 명시한다.
- `빠른 답변표`에는 선택지를 넣지 않고 `질문 ID`, `Priority`, `한 줄 질문`, `답변 방식`, `권장 다음 행동` 요약만 둔다고 명시한다.
- 선택지와 짧은 근거는 질문별 본문에 둔다고 명시한다.
- 사용자가 복잡한 질문지를 제공하면 `빠른 답변표`와 질문별 본문 형식으로 재구성할 수 있다고 명시한다.
- 답변을 받을 때는 `Q1=A`, `Q2=D`, `Q3=기타: ...` 형식을 허용한다고 명시한다.
- `P0`는 미답변 시 관련 구현이나 결정 확정을 막는 질문, `P1`은 조사로 답을 좁힐 수 있는 질문, `P2`는 명시적 가정이나 보수적 기본값으로 진행 가능한 질문이라고 요약한다.
- 다음 task prompt를 만들 때 clarification request의 전체 작성 기준과 `P0/P1/P2` 의미는 `AGENTS.md`의 `Ask when ambiguous` 섹션을 기준으로 삼고, snapshot에는 필요한 요약만 둔다고 명시한다.
- 사용자가 `모르겠다 / 추가 조사 필요`를 선택한 질문은 `P0/P1/P2` 의미에 따라 다음 작업을 나눈다고 명시한다.
- 사용자가 clarification request와 답변을 함께 제공하면, 답변을 바탕으로 다음 `prompts/tasks/*.md` 생성 명령을 제안한다고 명시한다.
- 분석 보고서와 clarification request가 함께 제공되면 질문 파일을 우선 읽고, 사용자 답변을 구조화해 다음 `prompts/tasks/*.md` 작업 프롬프트를 만든다고 명시한다.
- 분석 보고서에 질문이 묻혀 있는 경우 다음 task prompt에서 질문을 `agent_outputs/clarification_requests/`로 분리하도록 제안할 수 있다고 명시한다.
- 답변 중 장기적으로 유효한 내용은 `docs/harness/PROJECT_CONTEXT.md`에 반영하도록 안내한다.
- 직전 작업 delta나 바로 이어갈 세부 추천 액션은 `docs/harness/HANDOFF.md`의 `다음 추천 작업 상세`에 반영하도록 안내한다.
- 결정 사항은 `docs/harness/PROJECT_CONTEXT.md`의 `결정 사항` 섹션을 따른다고 설명한다.
- 사용자 답변, 샘플 검증, 코드 분석으로 장기 기준이 확정되면 `docs/harness/PROJECT_CONTEXT.md`에 반영해야 한다고 안내한다.
- clarification request의 `P0/P1/P2` 우선순위를 유지해서 다음 작업 범위를 정한다고 명시한다.

## Purpose

포함할 내용:
- 새 대화형 LLM 세션에 붙여넣기 위한 현재 작업 맥락 snapshot이다.
- 별도 고정 맥락 문서가 아니다.
- `AGENTS.md`는 AI Agent 행동 규칙의 기준 문서이다.
- `docs/harness/PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이다.
- `docs/harness/HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억을 담는 문서이다.
- `docs/harness/DIRECTORY_MAP.md`는 디렉토리/파일 구조 설명 문서이다.
- snapshot은 특정 시점의 참고 자료이며, 최신 사용자 입력과 실제 파일 내용, 명령 결과가 우선한다.
- 원문 문서를 그대로 덤프하지 않고, 이어서 조언하는 데 필요한 핵심만 담는다.

## How to use

포함할 내용:
- 이 파일 내용을 먼저 읽는다.
- 사용자가 이어서 붙여넣는 질문, 실행 결과, `git diff`, 로그 요약을 최신 정보로 우선한다.
- snapshot보다 최신 사용자의 입력이 우선한다.
- 확실하지 않은 내용은 사용자가 붙여넣은 실제 파일 내용이나 명령 결과를 기준으로 판단한다.

## Role of chat LLM

포함할 내용:
- AI Agent를 직접 실행하지 않는다.
- 사용자가 AI Agent에 줄 프롬프트 파일과 실행 명령을 만들도록 돕는다.
- 사용자가 붙여넣은 파일 내용, 실행 결과, `git diff`를 기준으로 다음 작업을 제안한다.
- 사용자가 clarification request를 붙여넣으면 작업 지시가 아니라 AI Agent의 질문 출력물로 해석하고, 사용자의 답변과 함께 다음 작업 프롬프트 작성에 사용한다.
- 사용자가 복잡한 clarification request를 붙여넣으면 `AGENTS.md`의 표준에 맞춰 `빠른 답변표`와 질문별 본문으로 재정리할 수 있다.
- clarification request 답변은 `Q1=A`, `Q2=D`, `Q3=기타: ...` 같은 짧은 형식을 허용한다.
- 답변은 복사해서 바로 실행 가능한 명령 중심으로 작성한다.
- 하네스 문서는 역할별 기준 문서로 보고, snapshot은 특정 시점의 참고 자료로만 취급한다.
- 최신 사용자 입력과 실제 파일 내용, 명령 결과를 snapshot보다 우선한다.
- 사용자의 최신 요청이 `HANDOFF.md`의 추천 작업과 다르면 최신 요청을 우선한다.
- 사용자의 최신 요청이 `PROJECT_CONTEXT.md`의 제약사항, 결정 사항, `P0 Blocker`와 충돌하면 중단하고 질문하도록 프롬프트를 설계한다.

## Response rules

포함할 내용:
- 한국어로 답한다.
- 문서와 프롬프트는 `이다/한다` 체로 작성한다.
- `입니다`, `합니다`, `됩니다`, `습니다` 같은 존대 표현은 사용하지 않는다.
- Markdown 문서는 사람이 읽기 좋은 형태로 작성한다.
- 읽기 좋게 작성하되 핵심 내용은 생략하지 않는다.
- 큰 주제는 `##`, 세부 주제는 `###` heading으로 구분한다.
- 긴 문단보다 짧은 bullet을 우선한다.
- 일반 작업 프롬프트는 `prompts/tasks/*.md`, 하네스 운영용 프롬프트는 `prompts/harness/*.md` 파일로 생성하는 방식으로 제공한다.
- AI Agent용 작업 프롬프트를 제안할 때 프롬프트 파일 생성 명령만 주지 않는다.
- AI Agent용 작업 프롬프트를 제안할 때 작업 프롬프트 파일 생성 명령, AI Agent 실행 명령, 실행 결과 확인 명령, git 변경 확인 명령, 추천 커밋 메시지, git 반영 명령, 최종 확인 명령을 함께 제공한다.
- 실행 결과 확인 명령에는 작업 성격에 맞는 `rg`, 테스트, 빌드, 정적 검사, 산출물 확인 명령을 포함한다.
- git 변경 확인 명령에는 기본적으로 `git status --short`, `git diff --stat`, `git diff`를 포함한다.
- 추천 커밋 메시지는 변경 목적이 드러나는 한 줄 명령형 또는 요약형 문구로 제공한다.
- git 반영 명령에는 기본적으로 `git add -A` 또는 변경 파일을 명시한 `git add ...`, `git commit -m "<recommended message>"`, `git push`를 포함한다.
- 커밋과 push는 사용자가 diff를 확인한 뒤 수행하는 단계로 안내한다.
- 커밋 전 `git status --short`로 `agent_outputs/run_logs/`의 run log와 `agent_outputs/llm_context/`의 llm context snapshot이 git에 들어가지 않는지 확인하게 한다.
- 필요한 경우 `git add -A` 대신 변경 파일을 명시한 `git add`를 제안한다.
- 작업 성격상 커밋이 필요 없으면 이유를 짧게 설명하고 커밋 명령을 생략할 수 있다.
- AI Agent용 프롬프트 파일 생성 명령을 줄 때는 Markdown fence safety 형식으로 작성한다.
- 바깥 쉘 명령 코드블록은 백틱 3개 대신 물결표 4개를 사용한다.
- heredoc 내부 프롬프트에는 백틱 3개짜리 Markdown 코드블록을 넣지 않는다.
- 프롬프트 내부에 명령 예시가 필요하면 fenced code block을 쓰지 않고 들여쓰기된 plain text로 작성한다.
- 코드블록 중첩이 필요해 보이는 경우에도 heredoc 내부에서는 fenced code block 대신 plain text를 사용한다.
- heredoc delimiter는 `HARNESS_PROMPT_EOF`, `TASK_PROMPT_EOF`처럼 작업마다 충분히 고유한 이름을 사용한다.
- heredoc 종료 문자열이 프롬프트 본문 중간에 등장하지 않게 한다.
- AI Agent용 프롬프트 파일 생성 명령은 복사해서 바로 실행 가능한 단일 bash 블록으로 제공하는 것을 우선한다.
- 명령 블록 밖에는 짧은 설명만 둔다.
- 사용자가 원하면 실행 명령, 확인 명령, 커밋 명령을 별도 블록으로 나눌 수 있지만, 프롬프트 파일 생성 명령 자체는 깨지지 않는 하나의 bash 블록으로 제공한다.

## Example response skeleton

AI Agent용 작업 프롬프트를 제안할 때 아래 응답 골격을 포함한다.

포함할 내용:
- `## 1. 프롬프트 파일 생성`
- `## 2. AI Agent 실행`
- `## 3. 실행 결과 확인`
- `## 4. 커밋 전 확인`
- `## 5. 커밋`
- `## 6. Push`
- `## 7. 최종 확인`
- `## 8. 작업 결과 검증(선택)`
- `## 1. 프롬프트 파일 생성`에는 `prompts/tasks/*.md` 또는 `prompts/harness/*.md` 생성 명령을 둔다.
- `## 1. 프롬프트 파일 생성`의 바깥 쉘 명령 코드블록은 물결표 4개로 감싼 단일 bash 블록으로 제공한다.
- `## 1. 프롬프트 파일 생성`의 heredoc delimiter는 `HARNESS_PROMPT_EOF`, `TASK_PROMPT_EOF`처럼 작업마다 충분히 고유한 이름을 사용하고, 종료 문자열이 프롬프트 본문 중간에 등장하지 않게 한다.
- `## 1. 프롬프트 파일 생성`의 heredoc 내부 프롬프트에는 백틱 3개짜리 Markdown 코드블록을 넣지 않고, 명령 예시는 들여쓰기된 plain text로 작성한다.
- `## 2. AI Agent 실행`에는 사용자가 사용하는 AI Agent의 비대화형 실행 명령으로 프롬프트 파일을 읽어 실행하는 `<AI Agent 실행 명령> "$(cat <prompt-file>)"` 형태의 명령과 run log 저장 명령을 둔다.
- AI Agent 실행 명령은 특정 도구로 고정하지 않고 codex, Claude Code, Gemini CLI 중 사용자가 선택한 도구의 명령으로 제안하며, 정확한 도구별 명령 예시는 `docs/harness/QUICKSTART.md`의 `AI Agent 실행 공통 패턴` 대응표를 따른다고 안내한다.
- `## 3. 실행 결과 확인`에는 작업 성격에 맞는 `rg`, 테스트, 빌드, 정적 검사, 산출물 확인 명령을 둔다.
- `## 4. 커밋 전 확인`에는 `git status --short`, `git diff --stat`, `git diff`를 기본으로 두고, run log와 llm context snapshot이 git에 들어가지 않는지 확인하라고 안내한다.
- `## 5. 커밋`에는 추천 커밋 메시지와 `git add -A` 또는 변경 파일을 명시한 `git add ...`, `git commit -m "<recommended message>"`를 둔다.
- `## 6. Push`에는 `git push`를 둔다.
- `## 7. 최종 확인`에는 `git status --short`와 필요한 원격 반영 확인 명령을 둔다.
- `## 8. 작업 결과 검증(선택)`에는 실패 비용이 큰 작업(문서 대량 수정, 구조 변경, 코드 변경)일 때 `prompts/harness/verify_task_result.md` 실행 명령과 `agent_outputs/reports/`의 판정 보고서 확인을 안내하고, 그 외 작업에서는 생략할 수 있다고 안내한다.
- 커밋과 push는 사용자가 diff를 확인한 뒤 수행하는 단계라고 안내한다.
- 작업 성격상 커밋이 필요 없으면 `## 5. 커밋`과 `## 6. Push`에서 이유를 짧게 설명하고 명령을 생략할 수 있다.

## Current harness structure

현재 하네스 구조를 요약한다.

포함할 내용:
- `AGENTS.md`
- `docs/harness/`
- `docs/harness/README.md`
- `docs/harness/PROJECT_CONTEXT.md`: 장기 프로젝트 맥락의 source of truth이다.
- `docs/harness/HANDOFF.md`: 중단기 작업 기억과 직전 작업 기억을 담는 인수인계 문서이다.
- `docs/harness/DIRECTORY_MAP.md`: 디렉토리/파일 구조 설명 문서이다.
- `docs/harness/QUICKSTART.md`
- `docs/harness/flows/`
- `docs/harness/archive/`
- `prompts/`
- `prompts/tasks/`
- `prompts/harness/`
- `agent_outputs/`
- `agent_outputs/reports/`: 분석, 계획, 리뷰, 감사 산출물 디렉토리이다.
- `agent_outputs/run_logs/`
- `agent_outputs/llm_context/`
- `agent_outputs/clarification_requests/`: 사용자 답변이 필요한 질문지 디렉토리이다.

## Current work context

`docs/harness/PROJECT_CONTEXT.md`와 `docs/harness/HANDOFF.md`를 바탕으로 현재 작업 목표와 진행 상태를 요약한다.

## Important decisions

`docs/harness/PROJECT_CONTEXT.md`의 `결정 사항` 섹션을 바탕으로 중요한 결정을 요약한다.

## Directory and file map

`docs/harness/DIRECTORY_MAP.md`를 바탕으로 주요 디렉토리와 파일 역할을 요약한다.

## Current git state

`git status --short`와 `git diff --stat` 결과를 요약한다. `ALLOW_DIRTY_SNAPSHOT: YES`로 미커밋 상태에서 생성한 경우에는 미커밋 상태임과 그 파일 목록을 이 절에 명시한다.

## Recent prompt files

최근 `prompts/tasks/*.md`와 `prompts/harness/*.md` 파일 목록을 요약한다.

## Recent outputs

최근 실행 로그와 산출물 목록을 요약한다.

포함할 내용:
- `agent_outputs/reports/`는 분석, 계획, 리뷰, 감사 산출물 저장소라고 설명한다.
- `agent_outputs/clarification_requests/`는 사용자 답변이 필요한 질문지 저장소라고 설명한다.
- clarification request는 `AGENTS.md`의 표준 질문지 형식을 따르며, 빠른 답변표에는 선택지를 넣지 않고 질문별 본문에 선택지를 둔다고 설명한다.
- 분석 보고서와 clarification request가 함께 제공되면 질문 파일을 우선 읽고 사용자 답변을 구조화해 다음 task prompt를 만든다고 설명한다.
- 분석 보고서에 질문이 묻혀 있는 경우 다음 task prompt에서 질문을 clarification request로 분리하도록 제안할 수 있다고 설명한다.

제한:
- 로그 본문은 포함하지 않는다.
- 과거 `agent_outputs/llm_context/` context 파일 본문은 포함하지 않는다.

## Suggested next user message

새 대화형 LLM에게 이 snapshot을 붙여넣은 뒤 사용자가 이어서 입력하면 좋은 예시를 작성한다.

예:
- "위 맥락을 기준으로 현재 git status 결과를 검토해줘."
- "위 맥락을 기준으로 다음 AI Agent 프롬프트 파일을 작성해줘."
- "위 맥락을 기준으로 아래 실행 결과를 해석하고 다음 작업을 제안해줘."

## End marker

파일 끝에는 다음 구분선을 넣는다.

`--- END CHAT LLM CONTEXT ---`

제한:
- 전체 문서는 너무 길게 만들지 않는다.
- 원문 파일 전체를 그대로 붙이지 않는다.
- 필요한 핵심을 요약한다.
- 단, 중요한 결정, 경로, 실행 방식은 생략하지 않는다.
- `agent_outputs/run_logs/` 로그 본문은 포함하지 않는다.
- `agent_outputs/llm_context/`의 과거 context 파일 본문은 포함하지 않는다.

문서 작성 규칙:
- 모든 문서는 한국어로 작성한다.
- 문서 말투는 "이다/한다" 체를 사용한다.
- "입니다/합니다/됩니다/습니다" 같은 존대 표현은 사용하지 않는다.
- Markdown 문서는 사람이 읽기 좋은 형태로 작성한다.
- 읽기 좋게 작성하되 핵심 내용을 생략하지 않는다.
- 큰 주제는 `##` heading으로 구분한다.
- 세부 주제가 필요하면 `###` heading을 사용한다.
- 긴 문단보다 짧은 bullet을 우선한다.
- 서로 다른 성격의 내용은 한 섹션에 섞지 않는다.

작업 후 확인:
1. 생성한 context 파일 경로를 출력한다.
2. `ls -t agent_outputs/llm_context/*_chat_llm_context.md | head -n 1` 결과를 출력한다.
3. `git status --short`를 출력한다.

주의:
- 이 작업은 context snapshot 생성 작업이다.
- 하네스 source 문서는 수정하지 않는다.
- `docs/harness/HANDOFF.md`도 갱신하지 않는다.
- 애플리케이션 코드는 만들지 않는다.
- archive 이동은 수행하지 않는다.
