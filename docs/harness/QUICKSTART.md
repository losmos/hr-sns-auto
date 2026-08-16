# Quickstart

이 문서는 하네스를 처음 받은 사용자가 빠르게 시작하기 위한 문서이다.

reset 프롬프트는 원본 하네스 repo가 아니라 새 프로젝트 복사본에서 실행한다. reset 실행 로그는 reset 중 정리될 수 있는 repo 내부 `agent_outputs/run_logs/`가 아니라 `/tmp`에 남기는 방식을 사용한다.

## AI Agent 실행 공통 패턴

이 문서의 실행 명령은 특정 도구에 종속되지 않는다. codex, Claude Code, Gemini CLI 중 사용하는 AI Agent의 비대화형 실행 명령으로 프롬프트 파일을 읽어 실행하고, 출력을 run log로 tee로 남기는 형태를 공통 패턴으로 사용한다.

```bash
<AI Agent 비대화형 실행 명령> "$(cat <프롬프트 파일>)" 2>&1 | tee "<run log 경로>"
```

도구별 비대화형 실행 명령 예시는 다음과 같다.

| 도구 | 비대화형 실행 명령 예시 |
| --- | --- |
| codex | `codex exec --sandbox workspace-write "$(cat <프롬프트 파일>)"` |
| Claude Code | `claude -p --permission-mode acceptEdits "$(cat <프롬프트 파일>)"` |
| Gemini CLI | `gemini -p "$(cat <프롬프트 파일>)"` |

파일 생성·수정이 필요한 작업은 도구별 비대화형 쓰기 권한 옵션이 필요하다. codex는 `--sandbox workspace-write`, Claude Code는 `--permission-mode acceptEdits`가 그 역할을 한다. Gemini CLI의 해당 옵션은 미실측이므로 사용 전 도구 help로 확인한다. verify_task_result 같은 읽기 전용 검증도 판정 보고서 1개를 생성하므로 쓰기 권한 옵션이 필요하다. 어떤 파일도 만들지 않는 실행만 쓰기 권한 없이 가능하다.

- 위 명령의 플래그는 예시이며 도구 버전에 따라 달라질 수 있다. 정확한 플래그는 각 도구의 `--help`로 확인한다.
- 각 도구의 파일 쓰기 승인 방식(codex 샌드박스, Claude Code permission, Gemini CLI 승인 등)은 도구마다 다르므로 해당 도구 문서를 따른다.

아래 흐름의 실행 명령은 모두 이 공통 패턴을 따른다. `<AI Agent 실행 명령(쓰기 권한 옵션 포함)>` 자리는 위 대응표의 도구별 명령을 쓰기 권한 옵션까지 포함해 그대로 넣는다.

### 진행 상황 실시간 표시(선택)

- 기본은 조용한 최소형이다. 비대화형 실행은 작업 종료 시 결과가 한 번에 출력되며, 파이프로 연결된 `tee` 출력이 버퍼링되어 run log가 실시간으로 보이지 않을 수 있다.
- 긴 작업이나 처음 실행하는 프롬프트는 중간에 멈추거나 잘못된 방향으로 가는지 확인할 수 있도록 진행 표시를 켜는 것을 권장한다.
- 도구별 진행 표시 방법은 다음과 같다.
  - Claude Code: `--output-format stream-json --verbose` 옵션을 추가하거나, `script` 명령으로 실행을 감싼다.
  - codex: 기본으로 진행 출력을 보여준다.
  - Gemini CLI: `--help`로 출력 옵션을 확인한다.
- 출력량이 과하면 옵션을 빼고 조용한 최소형으로 돌아온다.

### 작업 유형별 권장 모델 티어

작업을 실행할 모델은 아래 표를 참고해 티어 기준으로 고른다. 이 표는 권장이지 강제가 아니며, 사용자의 명시적 모델 지정이 항상 우선한다.

| 작업 유형 | 권장 티어 |
| --- | --- |
| 설계, 계획, 리뷰 | 상위 티어 |
| 구현, 문서 대량 수정 | 중위 티어, 난이도가 높으면 상위 티어 |
| 잡무, snapshot 생성, 드리프트 감사의 기계적 수정 | 하위 티어 |
| 작업 결과 검증 | 작업에 사용한 모델과 다른 모델, 가능하면 상위 티어 |

- 검증에 다른 모델을 권장하는 이유는 모델이 자기 출력을 더 후하게 평가하는 자기 출력 선호 편향을 줄이기 위해서이다.
- 티어는 상위/중위/하위 개념으로 판단한다. 특정 모델명(예: 상위 Claude Opus, 중위 Claude Sonnet, 하위 Claude Haiku)은 예시일 뿐이며 시간이 지나면 바뀐다.
- 사용한 모델은 run log 파일명 또는 `HANDOFF.md`의 직전 작업 기억에 한 줄로 기록한다.

## 1. 새 프로젝트 git 설정

### 템플릿 복사

템플릿을 새 프로젝트 디렉토리로 복사할 때는 아래 표준 복사 명령을 사용한다. 원본 경로 끝의 `/.`가 `.gitignore` 같은 dotfile까지 포함해 복사하는 핵심이다.

```bash
cp -a /path/to/ai-workbench/. /path/to/new-project/
```

`cp -r <템플릿>/* <복사본>/` 형식은 dotfile이 복사에서 빠져 `.gitignore`가 누락되므로 사용하지 않는다. 위 표준 명령은 템플릿의 `.git/`도 함께 복사하므로, 독립된 새 repo로 시작하는 경우 아래 안내대로 `rm -rf .git` 후 `git init`을 수행한다.

### git 초기화와 identity 설정

새 프로젝트를 시작할 때는 기존 하네스 repo의 `.git/`을 유지할지, 제거하고 독립된 새 repo로 시작할지 선택한다.

- 기존 repo 기록을 이어서 사용할 경우 `.git/`을 유지한다.
- 독립된 새 개발 repo로 시작할 경우 기존 `.git/`을 제거하고 새로 초기화한다.
- `.gitignore`는 `agent_outputs/run_logs/`와 `agent_outputs/llm_context/` 같은 하네스 산출물 제외 정책이므로 유지한다.
- `agent_outputs/reports/`와 `agent_outputs/clarification_requests/`의 필요한 Markdown 산출물은 git 추적 가능하게 둔다.

독립된 새 개발 repo로 시작하는 예시는 다음과 같다.

```bash
rm -rf .git
git init
git branch -M main
git remote -v
git remote add origin <NEW_PROJECT_REMOTE_URL>
```

이미 `origin`이 있으면 새로 추가하지 않고 URL만 바꿀 수 있다.

```bash
git remote set-url origin <NEW_PROJECT_REMOTE_URL>
git remote -v
```

git identity를 확인한다.

```bash
git config user.name
git config user.email
```

값이 비어 있거나 이 프로젝트에 맞지 않으면 설정한다. 아래는 저장소 로컬 설정 예시이고, 모든 저장소 공통으로 쓰려면 `--global`을 붙인다. `rm -rf .git` 후 `git init`으로 새로 시작한 경우 저장소 로컬 identity 설정이 사라지므로 재확인이 필요하다.

```bash
git config user.name "<이름>"
git config user.email "<이메일>"
```

### .gitignore 확인

`.gitignore`가 복사본에 존재하는지 확인한다.

```bash
ls -la .gitignore
cat .gitignore
```

없으면 템플릿 저장소의 `.gitignore`를 복사해 복원한다. 복원 또는 확인 후 `git status --short`에서 `agent_outputs/run_logs/`와 `agent_outputs/llm_context/` 산출물이 나타나지 않는지 확인한다.

첫 커밋과 push는 reset 결과를 검토한 뒤 수행하며, 절차는 2절의 `reset 후 첫 커밋` 소절을 따른다.

## 2. 새 프로젝트로 초기화

새 프로젝트 복사본에서 reset 안전 설정값을 먼저 편집한다.

```bash
vi prompts/harness/reset_for_new_project.md
```

프롬프트 안의 값을 명시적으로 설정한다.

```text
RESET_FOR_NEW_PROJECT: YES
NEW_PROJECT_NAME: My Project
NEW_PROJECT_SLUG: my-project
```

reset을 실행하고 결과를 확인한다.

```bash
<AI Agent 실행 명령(쓰기 권한 옵션 포함)> "$(cat prompts/harness/reset_for_new_project.md)" 2>&1 | tee "/tmp/$(date +%Y%m%d_%H%M%S)_reset_for_new_project.log"
git status --short
git diff --stat
git diff
```

Claude Code 기준 구체 예시는 다음과 같다.

```bash
claude -p --permission-mode acceptEdits "$(cat prompts/harness/reset_for_new_project.md)" 2>&1 | tee "/tmp/$(date +%Y%m%d_%H%M%S)_reset_for_new_project.log"
```

reset이 성공하면 `prompts/harness/reset_for_new_project.md` 상단 안전 설정값은 자동으로 `NO/TODO/TODO` 기본값으로 복구된다. 이 self-lock 동작은 reset 프롬프트를 다음 실행 전까지 다시 안전 기본 상태로 두기 위한 것이다.

```bash
rg -n "RESET_FOR_NEW_PROJECT|NEW_PROJECT_NAME|NEW_PROJECT_SLUG" prompts/harness/reset_for_new_project.md
```

자동 복구가 되지 않았다면 다음 기본값으로 수동 복구한다.

```text
RESET_FOR_NEW_PROJECT: NO
NEW_PROJECT_NAME: TODO
NEW_PROJECT_SLUG: TODO
```

reset 이후에는 다음 문서를 확인한다.

- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `docs/harness/DIRECTORY_MAP.md`

### reset 후 첫 커밋

snapshot 생성(5절)의 dirty snapshot gate는 미커밋 변경이 있으면 생성을 막으므로, reset 결과 diff를 검토한 뒤 여기서 첫 커밋을 만든다. `git init` 직후에는 커밋이 0개라 이 단계를 건너뛰면 snapshot 생성이 gate에 막힌다.

`git add -A` 전에 `git status --short`로 run log와 llm context 산출물이 추적 대상에 없는지 확인한다.

```bash
git status --short
git add -A
git commit -m "chore: initialize project from harness template"
git push -u origin main
```

remote를 아직 설정하지 않았으면 push는 생략할 수 있다.

## 3. 작업 시작 문서 확인

일반 작업을 시작할 때는 먼저 장기 맥락과 인수인계 상태를 모두 확인한다.

```bash
sed -n '1,240p' docs/harness/PROJECT_CONTEXT.md
sed -n '1,240p' docs/harness/HANDOFF.md
```

- `PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이다.
- `HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억을 담는다.
- `HANDOFF.md`의 중단기 작업 기억에 있는 `다음 추천 작업`은 이어가기 방향이고, 직전 작업 기억에 있는 `다음 추천 작업 상세`는 바로 이어갈 세부 추천 액션이다.
- 두 추천 항목과 다른 일을 하더라도 사용자의 최신 요청이 우선한다.
- 단, 사용자의 최신 요청이 `PROJECT_CONTEXT.md`의 제약사항, 결정 사항, `P0 Blocker`와 충돌하면 중단하고 질문한다.

디렉토리/파일 구조가 필요하면 구조 설명 문서를 확인한다.

```bash
sed -n '1,240p' docs/harness/DIRECTORY_MAP.md
```

## 4. 문서 드리프트 감사

context snapshot을 생성하기 전에 하네스 문서와 실제 저장소 상태의 드리프트를 감사한다. 감사 후 snapshot 순서로 진행해 최신 문서 상태가 snapshot에 반영되게 한다.

```bash
<AI Agent 실행 명령(쓰기 권한 옵션 포함)> "$(cat prompts/harness/audit_doc_drift.md)" 2>&1 | tee "agent_outputs/run_logs/$(date +%Y%m%d_%H%M%S)_audit_doc_drift.log"
latest_audit="$(ls -t agent_outputs/reports/*_doc_drift_audit.md | head -n 1)"
echo "$latest_audit"
cat "$latest_audit"
```

- 기계적 불일치(깨진 경로, DIRECTORY_MAP 누락/유령 항목, 존재하지 않는 파일 참조)는 감사 중 바로 수정된다.
- 판단 필요 불일치(확정 사실의 유효성, 문서 간 상충 서술)는 수정되지 않고 `agent_outputs/clarification_requests/`에 질문지로 분리된다.
- 감사가 끝나면 이어서 context snapshot을 생성한다.

## 5. 대화형 LLM context snapshot 생성

새 대화형 LLM에게 전달할 context snapshot을 생성한다.

snapshot 생성 전 미커밋 변경을 커밋하거나 정리한다. 미커밋 변경이 있으면 생성 프롬프트가 파일을 만들지 않고 안내만 출력하고 종료한다. 의도적으로 미커밋 상태를 담으려면 프롬프트 상단의 `ALLOW_DIRTY_SNAPSHOT: YES`를 설정한다.

`ALLOW_DIRTY_SNAPSHOT: YES`로 실행했다가 실행이 파일 생성 전에 실패하면 self-lock이 동작하지 않아 `YES`가 프롬프트 파일에 잔류한다. `YES` 실행 후에는 성공 여부와 무관하게 프롬프트 1행이 `NO`인지 확인하고, 아니면 수동으로 `NO`로 복구한다.

```bash
<AI Agent 실행 명령(쓰기 권한 옵션 포함)> "$(cat prompts/harness/generate_chat_llm_context.md)" 2>&1 | tee "agent_outputs/run_logs/$(date +%Y%m%d_%H%M%S)_generate_chat_llm_context.log"
latest_context="$(ls -t agent_outputs/llm_context/*_chat_llm_context.md | head -n 1)"
echo "$latest_context"
cat "$latest_context"
```

생성된 context snapshot은 새 대화형 LLM에게 그대로 붙여넣는다. 권장 복사 범위는 snapshot 안의 `--- BEGIN CHAT LLM CONTEXT ---` 줄부터 `--- END CHAT LLM CONTEXT ---` 줄까지이다.

붙여넣은 직후 새 대화형 LLM은 실질 작업을 시작하지 않고 짧은 수신 확인만 하는 것이 정상이다. 이후 사용자가 질문, 실행 결과, `git diff`, 로그, 파일 내용을 이어서 붙여넣으면 그때부터 해당 맥락을 기준으로 작업을 진행한다.

새 대화형 LLM에게 작업 프롬프트 생성을 요청하면 프롬프트 파일 생성 명령과 AI Agent 실행 명령뿐 아니라 실행 결과 확인, `git status --short`, `git diff --stat`, `git diff` 확인, 추천 커밋 메시지, commit/push 명령까지 함께 받는 것을 기대한다. 커밋과 push는 사용자가 diff를 확인한 뒤 수행하는 단계로 다룬다.

AI Agent용 프롬프트 파일 생성 명령은 Markdown fence 중첩 방지를 위해 바깥 명령 블록을 백틱 3개가 아니라 물결표 4개로 감싼 형식을 기대한다. heredoc 내부에는 백틱 3개짜리 Markdown 코드블록을 넣지 않고, 프롬프트 안에 명령 예시가 필요하면 들여쓰기된 plain text로 작성한다. heredoc 종료 문자열이 본문 중간에 등장하지 않도록 작업마다 고유한 delimiter를 사용한다.

이 규칙은 새 대화형 LLM이 사용자에게 보여주는 응답 포맷 지침이다. `AGENTS.md`의 AI Agent 작업 규칙과 역할이 다르며, AI Agent의 repo 작업 규칙을 대체하지 않는다.

## 6. 일반 작업 프롬프트 실행

일반 작업은 `prompts/tasks/`에 작업 프롬프트를 저장한 뒤 실행한다.

```bash
<AI Agent 실행 명령(쓰기 권한 옵션 포함)> "$(cat prompts/tasks/task_name.md)" 2>&1 | tee "agent_outputs/run_logs/$(date +%Y%m%d_%H%M%S)_task_name.log"
git status --short
git diff --stat
git diff
```

Claude Code 기준 구체 예시는 다음과 같다.

```bash
claude -p --permission-mode acceptEdits "$(cat prompts/tasks/task_name.md)" 2>&1 | tee "agent_outputs/run_logs/$(date +%Y%m%d_%H%M%S)_task_name.log"
```

## 7. 작업 결과 확인

작업 후 변경 파일과 문서 반영 상태를 확인한다.

```bash
git status --short
git diff --stat
git diff
rg -n "TODO|FIXME|clarification|blocker" docs prompts AGENTS.md agent_outputs || true
```

작업 결과가 장기 프로젝트 맥락이나 결정 사항을 바꾸면 `PROJECT_CONTEXT.md`를 갱신한다. 직전 작업 delta와 바로 실행 가능한 세부 추천 액션은 `HANDOFF.md`의 `다음 추천 작업 상세`에 기록한다.

작업 산출물은 목적별 디렉토리에서 확인한다.

- 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`를 확인한다.
- 사용자 답변이 필요한 질문지는 `agent_outputs/clarification_requests/`를 확인한다.
- 분석 보고서만 읽지 말고 `agent_outputs/clarification_requests/`의 최신 파일이 있는지 함께 확인한다.

## 8. 작업 결과 검증

실패 비용이 큰 작업(문서 대량 수정, 구조 변경, 코드 변경) 후에는 선택적으로 작업 결과를 새 컨텍스트에서 다시 채점한다. 매 작업 필수 단계가 아니며, 되돌리기 비용이 큰 작업에만 적용한다.

먼저 검증 대상 작업 프롬프트 경로를 설정값에 지정한다.

```bash
vi prompts/harness/verify_task_result.md
```

프롬프트 상단 값을 검증 대상 경로로 바꾼다.

```text
VERIFY_TARGET_PROMPT: prompts/tasks/task_name.md
```

검증을 실행하고 판정 보고서를 확인한다.

```bash
<AI Agent 실행 명령(쓰기 권한 옵션 포함)> "$(cat prompts/harness/verify_task_result.md)" 2>&1 | tee "agent_outputs/run_logs/$(date +%Y%m%d_%H%M%S)_verify_task_result.log"
latest_verify="$(ls -t agent_outputs/reports/*_verify_*.md | head -n 1)"
echo "$latest_verify"
cat "$latest_verify"
```

- 검증 에이전트는 읽기 전용이며 판정 보고서 1개 외에는 어떤 파일도 수정하거나 생성하지 않는다.
- 판정은 성공 기준별로 충족, 미충족, 검증 불가(이유)로 나뉘고 최종 종합 판정이 함께 남는다.
- 검증 중 발견한 문제는 이 단계에서 고치지 않고 후속 작업으로 분리한다.
- `VERIFY_TARGET_PROMPT`가 `TODO`이면 검증 에이전트는 파일을 수정하지 않고 대상 지정이 필요하다는 안내만 출력하고 종료한다.

## 9. Clarification Request 확인

clarification request가 생성되면 `agent_outputs/clarification_requests/`의 최신 파일을 먼저 읽고 질문별 `Priority`를 확인한다. clarification request는 AI Agent에게 다시 실행시킬 입력 프롬프트가 아니라, AI Agent가 모호성, 질문, blocker, 우선순위, 진행 가능 범위를 정리한 출력물이다. 작업 중단 없이 완료한 분석에서도 사용자 확인 질문이 남으면 clarification request가 생성될 수 있다.

- 먼저 파일 상단의 `빠른 답변표`를 확인한다.
- `빠른 답변표`는 질문을 훑기 위한 요약이므로, 실제 선택지는 각 `Q` 섹션의 `답변 선택지`에서 확인한다.
- 각 `Q` 섹션의 `답변 선택지`에서 하나를 고른다.
- 답변은 `Q1=A`, `Q2=D`, `Q3=기타: ...` 같은 짧은 형식으로 작성할 수 있다.
- 확실하지 않은 질문은 `모르겠다 / 추가 조사 필요`를 선택해도 된다.
- 선택지에 없으면 `기타: ...`로 답한다.
- 주관식 질문은 질문에 안내된 항목만 짧게 적는다.

- `P0 Blocker`: 답변 없이는 관련 구현이나 결정 확정 작업을 진행하지 않는다.
- `P1 Investigation`: 답변이 없어도 조사로 답을 좁힐 수 있다.
- `P2 Non-blocking`: 답변이 없어도 명시적 가정이나 보수적 기본값으로 진행할 수 있다.

자세한 형식과 priority 정책은 `AGENTS.md`의 clarification request 정책을 따른다. 사용자가 질문에 답변한 뒤에는 clarification request와 사용자 답변을 대화형 LLM에 함께 전달해 다음 task prompt를 만들게 한다. 대화형 LLM은 답변을 바탕으로 다음 `prompts/tasks/*.md` 작업 프롬프트를 작성하고, 장기적으로 유효한 내용은 `docs/harness/PROJECT_CONTEXT.md`에 반영할지 판단한다. 직전 작업 delta나 다음 세부 추천 액션은 `docs/harness/HANDOFF.md`의 `다음 추천 작업 상세`에 기록할지 판단한다.

## 10. 커밋과 push

검토가 끝난 변경만 커밋하고 push한다.

`git add -A` 전에 `git status --short`를 확인해 run log와 llm context snapshot이 git 추적 대상에 들어가지 않는지 확인한다. `agent_outputs/run_logs/`와 `agent_outputs/llm_context/` 산출물은 일반적으로 `.gitignore`에 의해 제외되어야 한다. 필요한 `agent_outputs/reports/`와 `agent_outputs/clarification_requests/` Markdown 산출물만 포함되는지도 함께 확인한다.

```bash
git status --short
git add -A
git commit -m "Describe the change"
git push -u origin main
```

## 11. 템플릿 전파

하네스 템플릿에 구조적 개선이 커밋된 뒤, 프로젝트 복사본이 안정된 시점에 템플릿 변경을 프로젝트 복사본으로 전파한다. 전파는 템플릿 저장소가 아니라 프로젝트 복사본에서 실행한다.

템플릿 저장소가 다른 서버에 있으면 먼저 [템플릿 전파 가이드](flows/sync_from_template.md)의 `다른 서버에서의 전파 준비` 절을 따라 템플릿 사본을 대상 서버로 가져온다.

전파 설정값을 먼저 편집한다.

```bash
vi prompts/harness/sync_from_template.md
```

프롬프트 상단 값을 명시적으로 설정한다.

```text
SYNC_FROM_TEMPLATE: YES
TEMPLATE_REPO_PATH: /path/to/ai-workbench
```

전파를 실행하고 보고서와 diff를 확인한다.

```bash
<AI Agent 실행 명령(쓰기 권한 옵션 포함)> "$(cat prompts/harness/sync_from_template.md)" 2>&1 | tee "agent_outputs/run_logs/$(date +%Y%m%d_%H%M%S)_sync_from_template.log"
latest_sync="$(ls -t agent_outputs/reports/*_sync_from_template.md | head -n 1)"
echo "$latest_sync"
cat "$latest_sync"
git status --short
git diff
```

- 공용 파일은 템플릿 기준으로 갱신되고, `PROJECT_CONTEXT.md`, `HANDOFF.md` 같은 프로젝트 고유 파일은 수정되지 않는다.
- 전파가 성공하면 프롬프트 상단 설정값은 `NO`/`TODO` 기본값으로 자동 복구된다.
- 언제 전파하는지, 사전 준비, 파일 구분 기준, 검토 포인트의 상세는 [템플릿 전파 가이드](flows/sync_from_template.md)를 따른다.
