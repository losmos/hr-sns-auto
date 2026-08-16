SYNC_FROM_TEMPLATE: NO
TEMPLATE_REPO_PATH: TODO

# Sync From Template

AGENTS.md, docs/harness/PROJECT_CONTEXT.md, docs/harness/HANDOFF.md를 먼저 읽어라.

## 목적

- 이 프롬프트는 하네스 템플릿 저장소의 변경을 프로젝트 복사본에 전파하기 위한 하네스 운영용 프롬프트이다.
- 이 프롬프트는 템플릿 저장소(`ai-workbench` 원본)에서 실행하는 것이 아니라, 하네스를 복사해 사용하는 프로젝트 복사본에서 실행한다.
- 하네스 공용 파일만 템플릿 기준으로 갱신하고, 프로젝트 고유 맥락과 산출물은 수정하지 않는다.

## 안전 설정

- `SYNC_FROM_TEMPLATE` 값이 `YES`가 아니면 어떤 파일도 수정하지 않는다.
- `TEMPLATE_REPO_PATH` 값이 `TODO`이거나 비어 있으면 어떤 파일도 수정하지 않는다.
- `TEMPLATE_REPO_PATH` 경로가 실제로 존재하지 않으면 어떤 파일도 수정하지 않는다.
- 안전 조건을 만족하지 않으면 필요한 설정값과 실행 방법 안내만 출력하고 종료한다.
- 전파가 성공적으로 끝나면 이 파일 상단의 설정값을 기본값으로 되돌린다. reset 프롬프트의 self-lock과 같은 방식이다.
- 기본값은 `SYNC_FROM_TEMPLATE: NO`, `TEMPLATE_REPO_PATH: TODO`이다.
- 안전 조건이 실패한 경우에는 전파를 수행하지 않았으므로 기존 설정값을 임의로 변경하지 않는다.

## 실행 전 확인

다음 두 값을 먼저 수정한 뒤 프로젝트 복사본의 repo root에서 실행한다.

- `SYNC_FROM_TEMPLATE: YES`
- `TEMPLATE_REPO_PATH: 템플릿 저장소의 로컬 경로`

추가 확인:

- 현재 작업 디렉토리가 하네스를 복사한 프로젝트 복사본인지 확인한다.
- 프로젝트 복사본의 작업 중 변경이 커밋되어 있는지 확인한다. 커밋되지 않은 변경이 섞여 있으면 전파 diff 검토가 어려워진다.
- 전파는 추측으로 진행하지 않는다. 보존 대상인지 갱신 대상인지 판단이 어려운 충돌은 수정하지 않는다.

## 파일 구분

### 공용 파일 (템플릿 기준으로 갱신)

- `prompts/harness/`의 프롬프트 파일
- `CLAUDE.md`
- `GEMINI.md`
- `docs/harness/CLARIFICATION_FORMAT.md`
- `docs/harness/README.md`
- `docs/harness/QUICKSTART.md`

### 프로젝트 고유 파일 (수정 금지)

- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `prompts/tasks/`
- `agent_outputs/`
- `docs/harness/archive/`

### 혼합 파일 (부분 병합)

- `AGENTS.md`: 규칙 본문은 템플릿을 따르되, `## Commands` 섹션의 프로젝트별 채움 내용은 보존한다.
- `docs/harness/DIRECTORY_MAP.md`: 하네스 공용 구조 설명은 템플릿 기준으로 갱신하되, 프로젝트 고유 디렉토리와 파일 설명은 보존한다.
- `.gitignore`: 하네스 기본 제외 규칙(`agent_outputs/run_logs/`, `agent_outputs/llm_context/` 등)은 템플릿 기준으로 갱신하되, 프로젝트가 추가한 규칙(예: `prompts/tasks/`, 프로젝트별 빌드 산출물)은 보존한다.
- `docs/harness/flows/README.md`: 템플릿 유래 가이드 항목은 템플릿 기준으로 갱신하되, 프로젝트가 추가한 페이지 항목은 보존한다.

### flows 파일 처리 (유래 기준)

- `docs/harness/flows/` 하위 파일 중 템플릿 저장소에도 존재하는 파일은 공용으로 취급해 템플릿 기준으로 갱신한다.
- 프로젝트에만 존재하는 flows 파일은 고유로 취급해 수정하지 않는다.
- 템플릿 유래 flows 파일에 프로젝트 수정이 있는 충돌은 수정하지 않고 `agent_outputs/clarification_requests/`에 질문지로 분리한다.
- 단, `docs/harness/flows/README.md`는 위 혼합 파일 기준으로 부분 병합한다.

### 기본값 규칙 (분류표에 없는 파일)

- 위 공용/고유/혼합 목록에 명시되지 않은 파일은 유래 기준을 기본값으로 적용한다: 템플릿 저장소에 존재하면 공용으로 갱신하고, 프로젝트에만 존재하면 고유로 보존하며, 템플릿 유래인데 프로젝트 수정이 있어 판단이 어려우면 수정하지 않고 clarification request로 분리한다.
- 위 분류표는 기본값으로 처리할 수 없는 예외(혼합 파일과 명시적 고유 지정)를 관리하는 목록이다.

## 전파 작업

### 안전 조건 실패 시

1. 어떤 파일도 수정하지 않는다.
2. 어떤 안전 조건이 실패했는지, 필요한 설정값이 무엇인지, 어떻게 실행하는지 안내만 출력하고 종료한다.

### 안전 조건 충족 시

1. 템플릿 저장소와 현재 저장소의 공용 파일을 비교한다.

       diff -ru "<TEMPLATE_REPO_PATH>/prompts/harness" prompts/harness
       diff -u "<TEMPLATE_REPO_PATH>/CLAUDE.md" CLAUDE.md
       diff -u "<TEMPLATE_REPO_PATH>/docs/harness/QUICKSTART.md" docs/harness/QUICKSTART.md

2. 공용 파일의 템플릿 변경을 현재 저장소에 반영한다. 템플릿에 새로 추가된 공용 파일은 같은 경로로 복사한다.
3. 혼합 파일은 부분 병합한다. `AGENTS.md`의 `## Commands` 채움 내용과 `DIRECTORY_MAP.md`의 프로젝트 고유 설명이 병합 후에도 남아 있는지 확인한다.
4. 프로젝트 고유 파일은 어떤 경우에도 수정하지 않는다.
5. 보존 대상인지 갱신 대상인지 판단이 어려운 충돌은 해당 파일이나 구간을 수정하지 않고 `agent_outputs/clarification_requests/`에 질문지로 분리한다.
6. 전파 결과 요약을 `agent_outputs/reports/YYYYMMDD_HHMMSS_sync_from_template.md`에 남긴다. 갱신한 파일, 보존한 파일, 질문으로 분리한 항목을 구분해서 적는다.
7. 이 파일 상단의 설정값을 기본값(`SYNC_FROM_TEMPLATE: NO`, `TEMPLATE_REPO_PATH: TODO`)으로 되돌린다.
8. `docs/harness/HANDOFF.md`를 이번 전파 작업 기준으로 갱신한다. 이 갱신은 현재 저장소(프로젝트 복사본)의 `HANDOFF.md`에 대한 것이며, 본문 내용을 템플릿 저장소의 것으로 덮지 않는다.

## 작업 후 확인

다음 명령을 실행한다.

    git status --short
    git diff --stat
    rg -n "SYNC_FROM_TEMPLATE|TEMPLATE_REPO_PATH" prompts/harness/sync_from_template.md
    ls -t agent_outputs/reports/*_sync_from_template.md | head -n 1

## 마지막 출력

1. 안전 조건 확인 결과
2. 갱신한 공용 파일 목록
3. 보존한 프로젝트 고유 파일 목록
4. 혼합 파일 병합 결과
5. clarification request로 분리한 항목
6. 전파 결과 보고서 경로
7. 설정값 기본값 복구 여부
