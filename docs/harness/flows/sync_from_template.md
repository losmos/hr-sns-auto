# 템플릿 전파 (Sync From Template)

이 문서는 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 흐름을 설명한다. 전파 프롬프트는 `prompts/harness/sync_from_template.md`이며, 템플릿 저장소(`ai-workbench` 원본)가 아니라 하네스를 복사해 사용하는 프로젝트 복사본에서 실행한다.

## 언제 전파하는가

- 템플릿 저장소에 규칙, 프롬프트, 가이드 문서 같은 구조적 개선이 커밋된 뒤에 전파한다.
- 프로젝트 복사본이 안정된 시점에 전파한다. 진행 중인 작업이 많거나 커밋되지 않은 변경이 쌓여 있으면 전파를 미룬다.
- 템플릿 변경이 생길 때마다 즉시 전파할 필요는 없고, 여러 개선을 모아 한 번에 전파해도 된다.

## 사전 준비

- 프로젝트 복사본의 작업 중 변경을 먼저 커밋한다. 전파 결과를 git diff로 깨끗하게 검토하기 위해서이다.
- 템플릿 저장소의 로컬 경로를 확인한다. 이 경로를 프롬프트의 `TEMPLATE_REPO_PATH`에 넣는다.
- 템플릿 저장소가 전파하려는 개선이 커밋된 최신 상태인지 확인한다.

## 다른 서버에서의 전파 준비

템플릿 저장소가 프로젝트 복사본과 같은 서버에 없으면, 먼저 템플릿 사본을 대상 서버의 로컬 경로로 가져온다. 경로는 세 가지 중 상황에 맞는 것을 쓴다.

- git clone: 대상 서버에서 템플릿 원격 저장소에 접근할 수 있으면 clone으로 사본을 만든다.

      git clone <템플릿 원격 URL> /path/to/ai-workbench

- rsync 또는 scp: 서버 간 직접 접속이 가능하면 템플릿 디렉토리를 그대로 복사한다.

      rsync -a user@source:/path/to/ai-workbench/ /path/to/ai-workbench/

- tar 아카이브 전달: 직접 접속이 없으면 아카이브를 만들고, 복사하고, 해제한다.

      tar -czf ai-workbench_<커밋해시>.tar.gz -C /path/to ai-workbench
      # 아카이브 파일을 대상 서버로 복사한 뒤
      tar -xzf ai-workbench_<커밋해시>.tar.gz -C /path/to

어느 경로든 결과는 대상 서버의 로컬 경로에 템플릿 사본이 존재하는 것이고, 그 경로를 `TEMPLATE_REPO_PATH`에 지정하면 sync 동작은 동일하다. sync는 git이 아니라 파일 비교 기반이라 전달 수단에 무관하다.

- copy나 아카이브 기반 전달은 템플릿의 어느 시점인지 기록이 남지 않는다. 위 예시처럼 아카이브 파일명이나 사본 내 메모에 템플릿 커밋 해시를 남기는 관례를 권장한다.
- 새 프로젝트를 처음 시작하는 경우는 사본 확보 후 reset을 실행하고, 기존 프로젝트 갱신은 사본 확보 후 이 문서의 sync 흐름을 따른다.

## 실행 순서

1. `prompts/harness/sync_from_template.md` 상단 설정값을 편집한다.

       SYNC_FROM_TEMPLATE: YES
       TEMPLATE_REPO_PATH: /path/to/ai-workbench

2. 프로젝트 복사본의 repo root에서 전파 프롬프트를 실행한다. 실행 명령은 `docs/harness/QUICKSTART.md`의 AI Agent 실행 공통 패턴을 따른다.
3. `agent_outputs/reports/`의 최신 `*_sync_from_template.md` 보고서에서 갱신 파일, 보존 파일, 질문 분리 항목을 확인한다.
4. `git diff`로 전파된 변경을 검토한다. 아래 검토 포인트를 확인한다.
5. 검토가 끝나면 커밋한다.

## 공용/고유/혼합 파일 구분 기준

| 구분 | 대상 | 처리 |
| --- | --- | --- |
| 공용 | `prompts/harness/` 프롬프트, `CLAUDE.md`, `GEMINI.md`, `docs/harness/CLARIFICATION_FORMAT.md`, `docs/harness/README.md`, `docs/harness/QUICKSTART.md`, `docs/harness/flows/` 중 템플릿 저장소에도 존재하는 파일 | 템플릿 기준으로 갱신한다 |
| 고유 | `docs/harness/PROJECT_CONTEXT.md`, `docs/harness/HANDOFF.md`, `prompts/tasks/`, `agent_outputs/`, `docs/harness/archive/`, 프로젝트에만 존재하는 flows 파일 | 수정하지 않는다 |
| 혼합 | `AGENTS.md`, `docs/harness/DIRECTORY_MAP.md`, `.gitignore`, `docs/harness/flows/README.md` | 부분 병합한다 |

표에 명시되지 않은 파일은 유래 기준을 기본값으로 적용한다: 템플릿 저장소에 존재하면 공용으로 갱신하고, 프로젝트에만 존재하면 고유로 보존하며, 템플릿 유래인데 프로젝트 수정이 있어 판단이 어려우면 수정하지 않고 clarification request로 분리한다. 위 표는 기본값으로 처리할 수 없는 예외(혼합 파일과 명시적 고유 지정)를 관리하는 목록이다. flows 하위 파일도 같은 유래 기준으로 처리하며, 템플릿 유래 flows 파일에 프로젝트 수정이 있는 충돌은 clarification request로 분리한다.

혼합 파일의 병합 기준:

- `AGENTS.md`는 규칙 본문은 템플릿을 따르되 `## Commands` 섹션의 프로젝트별 채움 내용을 보존한다.
- `docs/harness/DIRECTORY_MAP.md`는 하네스 공용 구조 설명은 갱신하되 프로젝트 고유 디렉토리 설명을 보존한다.
- `.gitignore`는 하네스 기본 제외 규칙(`agent_outputs/run_logs/`, `agent_outputs/llm_context/` 등)은 템플릿 기준으로 갱신하되, 프로젝트가 추가한 규칙(예: `prompts/tasks/`, 프로젝트별 빌드 산출물)은 보존한다.
- `docs/harness/flows/README.md`는 템플릿 유래 가이드 항목은 템플릿 기준으로 갱신하되, 프로젝트가 추가한 페이지 항목은 보존한다.
- 보존 대상인지 갱신 대상인지 판단이 어려운 충돌은 수정되지 않고 `agent_outputs/clarification_requests/`에 질문지로 분리된다.

## 검토 포인트

- `docs/harness/PROJECT_CONTEXT.md`와 `docs/harness/HANDOFF.md`가 전파로 변경되지 않았는지 확인한다. HANDOFF는 전파 작업 기록으로만 갱신되며 본문이 템플릿 것으로 덮이면 안 된다.
- `AGENTS.md`의 `## Commands` 섹션에 프로젝트별 채움 내용이 그대로 남아 있는지 확인한다.
- `DIRECTORY_MAP.md`의 프로젝트 고유 디렉토리 설명이 사라지지 않았는지 확인한다.
- `prompts/tasks/`와 `agent_outputs/`가 변경되지 않았는지 확인한다.
- `prompts/harness/sync_from_template.md` 상단 설정값이 `NO`/`TODO` 기본값으로 복구되었는지 확인한다.
- clarification request로 분리된 항목이 있으면 답변 후 후속 작업으로 처리한다.
