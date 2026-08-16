RESET_FOR_NEW_PROJECT: NO
NEW_PROJECT_NAME: TODO
NEW_PROJECT_SLUG: TODO

# Reset For New Project

AGENTS.md, docs/harness/PROJECT_CONTEXT.md, docs/harness/HANDOFF.md를 먼저 읽어라.

## 목적

- 이 프롬프트는 하네스를 실제 개발 프로젝트에 복사한 뒤 새 프로젝트 맥락으로 초기화하기 위한 하네스 운영용 프롬프트이다.
- 하네스 규칙과 구조는 유지하고, 하네스 개발 과정에서 생긴 작업 맥락과 임시 산출물만 초기화한다.
- 현재 `ai-workbench` 원본 repo에서 실수로 실행해도 리셋이 일어나지 않도록 상단 설정값으로 명시적 확인을 요구한다.

## 안전 설정

- `RESET_FOR_NEW_PROJECT` 값이 `YES`가 아니면 리셋 대상 파일이나 디렉토리를 수정하지 않는다.
- `NEW_PROJECT_NAME` 값이 `TODO`이거나 비어 있으면 리셋 대상 파일이나 디렉토리를 수정하지 않는다.
- `NEW_PROJECT_SLUG` 값이 `TODO`이거나 비어 있으면 리셋 대상 파일이나 디렉토리를 수정하지 않는다.
- 안전 조건을 만족하지 않으면 `agent_outputs/clarification_requests/` 아래에 clarification request Markdown 파일만 생성하고 종료한다.
- 안전 조건 실패는 `P0 Blocker`로 다루며, 답변이나 설정값 보완 없이는 reset 구현 작업을 진행하지 않는다.
- clarification request에는 빠른 답변표, 필요한 설정값, 실행 방법, 리셋을 수행하지 않은 이유, 질문별 `Priority`, 임의로 확정하지 않은 가정을 적는다.
- 리셋이 성공적으로 끝나면 이 파일 상단의 안전 설정값을 다시 기본값으로 되돌린다.
- 기본값은 `RESET_FOR_NEW_PROJECT: NO`, `NEW_PROJECT_NAME: TODO`, `NEW_PROJECT_SLUG: TODO`이다.
- 안전 조건이 실패한 경우에는 리셋을 수행하지 않았으므로 기존 설정값을 임의로 변경하지 않는다.
- 리셋 도중 실패한 경우에는 가능한 경우 마지막 출력에 안전 설정값 기본값 복구 여부를 기록한다.

## 실행 전 확인

다음 세 값을 먼저 수정한 뒤 repo root에서 실행한다.

- `RESET_FOR_NEW_PROJECT: YES`
- `NEW_PROJECT_NAME: 새 프로젝트 이름`
- `NEW_PROJECT_SLUG: 새 프로젝트 slug`

추가 확인:

- 현재 작업 디렉토리가 하네스를 복사한 새 개발 프로젝트인지 확인한다.
- 현재 `ai-workbench` 원본 repo에서는 이 프롬프트를 실제 리셋 목적으로 실행하지 않는다.
- 리셋은 추측으로 진행하지 않는다.
- reset은 `.git/` 디렉토리, git remote, branch, commit history를 수정하지 않는다.
- 기존 git 기록 제거, `git init`, remote 설정은 사용자가 reset 전후에 직접 수행한다.
- `.gitignore`는 하네스 산출물 제외 정책이므로 유지한다.

## 리셋 대상

다음 파일과 디렉토리만 초기화한다.

- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `prompts/tasks/*.md`
- `agent_outputs/reports/*`
- `agent_outputs/run_logs/*`
- `agent_outputs/llm_context/*`
- `agent_outputs/clarification_requests/*`

reset 대상에는 `.git/` 디렉토리, git remote, branch, commit history가 포함되지 않는다.

## 유지 대상

다음 파일과 디렉토리 유지용 파일은 삭제하거나 초기화하지 않는다.

- `AGENTS.md` (규칙과 구조는 유지하고 `## Commands` 섹션만 새 프로젝트 명령으로 채운다)
- `.gitignore`
- `docs/harness/README.md`
- `docs/harness/QUICKSTART.md`
- `docs/harness/DIRECTORY_MAP.md`
- `docs/harness/CLARIFICATION_FORMAT.md`
- `docs/harness/flows/` (`README.md`, `multi_user_workflow.md`, `sync_from_template.md` 포함)
- `docs/harness/archive/`
- `prompts/harness/generate_chat_llm_context.md`
- `prompts/harness/reset_for_new_project.md`
- `prompts/harness/sync_from_template.md`
- `prompts/harness/audit_doc_drift.md`
- `prompts/harness/verify_task_result.md`
- 디렉토리 유지를 위한 `.gitkeep` 파일

## 리셋 작업

### 안전 조건 실패 시

1. 리셋 대상 파일이나 디렉토리를 수정하지 않는다.
2. `agent_outputs/clarification_requests/` 디렉토리를 생성한다.
3. `agent_outputs/clarification_requests/YYYYMMDD_HHMMSS_reset_for_new_project.md` 파일을 생성한다.
4. 파일에는 다음 내용을 포함한다.
   - 빠른 답변표
   - 중단 이유
   - 필요한 설정값
   - 실행 방법
   - 질문별 `Priority`
   - 임의로 확정하지 않은 가정
   - 관련 파일
5. clarification request 파일을 생성한 뒤 종료한다.

### 안전 조건 충족 시

1. `docs/harness/PROJECT_CONTEXT.md`를 새 프로젝트용 템플릿으로 초기화한다.
2. `docs/harness/HANDOFF.md`를 새 프로젝트용 템플릿으로 초기화한다.
3. `prompts/tasks/*.md`를 제거하고 `prompts/tasks/.gitkeep`만 남긴다.
4. `agent_outputs/reports/*`를 제거하고 `agent_outputs/reports/.gitkeep`만 남긴다.
5. `agent_outputs/run_logs/*`를 제거하고 `agent_outputs/run_logs/.gitkeep`만 남긴다.
6. `agent_outputs/llm_context/*`를 제거하고 `agent_outputs/llm_context/.gitkeep`만 남긴다.
7. `agent_outputs/clarification_requests/*`를 제거하고 `agent_outputs/clarification_requests/.gitkeep`만 남긴다.
8. `AGENTS.md`의 `## Commands` 섹션 TODO 항목을 새 프로젝트의 실제 테스트, 빌드, 정적 검사, 실행 명령으로 채운다. 아직 확정되지 않은 명령은 `TODO`로 남긴다.
9. `prompts/harness/reset_for_new_project.md` 상단 안전 설정값을 기본값으로 복구한다.
   - `RESET_FOR_NEW_PROJECT: NO`
   - `NEW_PROJECT_NAME: TODO`
   - `NEW_PROJECT_SLUG: TODO`

## 템플릿 요구사항

### PROJECT_CONTEXT.md

다음 구조를 따른다.

- `# Project Context`
- `## 프로젝트 목적`
- `## 배경`
- `## 목표`
- `## 제약사항`
- `## 확정된 사실`
- `## 결정 사항`
- `## 미확정 질문`
- `## 참고 산출물`
- `## 다음 작업 기준`

작성 기준:

- 새 프로젝트의 장기 프로젝트 맥락 source of truth로 작성한다.
- 결정 사항은 `결정 사항` 섹션에 기록한다.
- 결정 항목은 필요한 경우 `DEC-YYYYMMDD-<slug>` 형식의 ID(예: `DEC-20260711-template-sync-flow`)를 사용한다. 날짜와 slug 조합이라 여러 브랜치에서 동시에 결정을 추가해도 ID가 충돌하지 않는다.
- 미확정 질문에는 `P0 Blocker`, `P1 Investigation`, `P2 Non-blocking` 우선순위를 사용할 수 있음을 안내한다.
- 긴 분석 전문이나 실행 로그는 복붙하지 않고 `agent_outputs/reports/` 또는 적절한 `agent_outputs/` 하위 경로만 연결한다.

### HANDOFF.md

다음 구조를 따른다.

- `# Handoff`
- `## 마지막 갱신일`
- `# 중단기 작업 기억`
- `## 이번 범위`
- `## 이번 작업에서 제외할 범위`
- `## 현재 상태`
- `## 주요 결론`
- `## 다음 추천 작업`
- `## 남은 확인 사항`
- `# 직전 작업 기억`
- `## PROJECT_CONTEXT 반영 여부`
- `## 직전 작업 delta`
- `## 마지막 작업 요약`
- `## 변경 파일`
- `## 생성 산출물`
- `## 다음 추천 작업 상세`
- `## 주의할 점`

작성 기준:

- `마지막 갱신일`에는 `YYYY-MM-DD HH:MM:SS` 형식으로 시분초까지 포함한다.
- `PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth로 유지한다.
- 중단기 작업 기억은 `PROJECT_CONTEXT.md`의 전체 복사본이 아니라 몇 번의 다음 작업 동안 필요한 실행 맥락과 이어가면 좋은 방향을 기록한다.
- 중단기 작업 기억의 `다음 추천 작업`은 명령이 아니라 추천이다.
- 장기 기준, 확정 사실, 결정 사항은 `PROJECT_CONTEXT.md`에 반영한다.
- 직전 작업 기억에는 마지막 작업의 delta, 확인한 내용, 바로 이어서 수행하면 좋은 `다음 추천 작업 상세`를 기록한다.
- `다음 추천 작업 상세`는 명령이 아니라 직전 작업 기준의 세부 추천 액션이다.
- `PROJECT_CONTEXT 반영 여부`에는 직전 작업 결과가 `PROJECT_CONTEXT.md`에 반영됐는지, 반영하지 않았다면 이유가 무엇인지 기록한다.

## 작업 후 확인

다음 명령을 실행한다.

```bash
test -f docs/harness/PROJECT_CONTEXT.md && echo "OK: project context exists"
test -f docs/harness/HANDOFF.md && echo "OK: handoff exists"
test -f docs/harness/DIRECTORY_MAP.md && echo "OK: directory map exists"
test -f prompts/tasks/.gitkeep && echo "OK: tasks gitkeep exists"
test -f agent_outputs/reports/.gitkeep && echo "OK: reports gitkeep exists"
test -f agent_outputs/run_logs/.gitkeep && echo "OK: run logs gitkeep exists"
test -f agent_outputs/llm_context/.gitkeep && echo "OK: llm context gitkeep exists"
test -f agent_outputs/clarification_requests/.gitkeep && echo "OK: clarification requests gitkeep exists"
git status --short
```

## 마지막 출력

1. 안전 조건 확인 결과
2. 초기화한 파일과 디렉토리 목록
3. 유지한 파일과 디렉토리 목록
4. 실행한 확인 명령과 결과
5. 안전 설정값 기본값 복구 여부
6. `prompts/tasks/` 커밋 정책 결정 안내: 커밋 여부는 reset이 정하지 않으며, 사용자가 전부 커밋 또는 기본 미커밋 + 선별 커밋 중에서 프로젝트 결정 사항으로 정한다. 선택지 상세는 `docs/harness/README.md`의 `prompts/tasks 커밋 정책`을 안내한다.
7. 남은 확인 사항
