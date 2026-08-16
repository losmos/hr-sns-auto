# Audit Doc Drift

AGENTS.md, docs/harness/PROJECT_CONTEXT.md, docs/harness/HANDOFF.md를 먼저 읽어라.

## 목적

- 이 프롬프트는 하네스 문서와 실제 저장소 상태의 드리프트를 주기적으로 감사하기 위한 하네스 운영용 재사용 프롬프트이다.
- 문서가 설명하는 구조, 경로, 상호 참조가 실제 파일 트리와 최근 git 이력, 다른 문서와 일치하는지 확인한다.
- 기계적으로 확정 가능한 불일치는 바로 고치고, 판단이 필요한 불일치는 고치지 않고 질문으로 분리한다.

## 대상

- `AGENTS.md`
- `docs/harness/` 하위 문서 전체

대조 기준은 다음 세 가지이다.

- 실제 파일 트리(`rg --files`, `find`로 확인한다).
- git 최근 이력(`git log`, `git status`로 확인한다).
- 문서 상호 간 서술(같은 사실을 서로 다르게 적었는지 확인한다).

## 불일치 분류

### 기계적 불일치

- 정의: 사실 판단 없이 저장소 상태만으로 옳고 그름을 확정할 수 있는 불일치이다.
- 예시:
  - 깨진 경로나 존재하지 않는 파일을 가리키는 참조.
  - `DIRECTORY_MAP.md`에서 실제 존재하는 파일이나 디렉토리가 누락된 항목.
  - `DIRECTORY_MAP.md`에서 실제로는 없는 파일이나 디렉토리를 적은 유령 항목.
- 처리: 바로 수정한다.

### 판단 필요 불일치

- 정의: 사실의 유효성 판단이나 사용자 의도 확인이 필요한 불일치이다.
- 예시:
  - `PROJECT_CONTEXT.md`의 확정 사실이나 결정 사항이 아직 유효한지에 대한 의문.
  - 두 문서가 같은 규칙을 서로 다르게 서술해 어느 쪽이 맞는지 확정할 수 없는 경우.
- 처리: 수정하지 않고 clarification request로 분리한다.

## 작업

1. `AGENTS.md`와 `docs/harness/` 하위 문서 전체를 읽는다.
2. 실제 파일 트리를 확인한다.
3. git 최근 이력과 현재 상태를 확인한다.
4. 문서가 가리키는 경로, 파일, 디렉토리, 상호 참조를 실제 상태와 대조한다.
5. 발견한 불일치를 기계적 불일치와 판단 필요 불일치로 나눈다.
6. 기계적 불일치는 바로 수정한다.
7. 판단 필요 불일치는 수정하지 않고 clarification request 파일로 분리한다.
   - `agent_outputs/clarification_requests/YYYYMMDD_HHMMSS_doc_drift_audit.md`에 저장한다.
   - `AGENTS.md`와 `CLARIFICATION_FORMAT.md`의 표준 질문지 형식을 따른다.
   - 질문마다 `P0`, `P1`, `P2` 우선순위를 붙인다.
8. 감사 결과를 `agent_outputs/reports/YYYYMMDD_HHMMSS_doc_drift_audit.md`에 남긴다.
   - 같은 이름 파일을 덮어쓰지 않는다.
9. 판단 필요 불일치가 없으면 clarification request는 만들지 않는다.

## 감사 보고서 요구사항

- 상단에 감사 시각과 대조에 사용한 명령을 적는다.
- 수정한 항목과 질문으로 분리한 항목을 구분해 기록한다.
- 수정한 항목에는 어떤 파일을 어떻게 고쳤는지 적는다.
- 질문으로 분리한 항목에는 왜 판단이 필요한지와 관련 clarification request 경로를 적는다.
- 불일치를 발견하지 못했으면 그 사실을 명시한다.

## 주의사항

- 이번 작업 범위는 감사와 기계적 불일치 수정, 판단 필요 불일치의 질문 분리로 제한한다.
- 요청 범위 밖의 문서 개선, 재구성, 표현 다듬기를 하지 않는다.
- 판단 필요 불일치는 확신이 없으면 수정하지 않고 질문으로만 남긴다.
- 확정 사실이나 결정 사항의 유효성 판단은 임의로 확정하지 않는다.
- 명령 예시가 필요하면 아래처럼 들여쓰기된 plain text로만 적는다.

    rg --files docs prompts
    rg -n "docs/harness" docs AGENTS.md
    git log --oneline -n 20

## 작업 후 확인

- 수정한 문서와 생성한 산출물을 확인한다.

    git status --short
    git diff --stat
    ls -t agent_outputs/reports/*_doc_drift_audit.md | head -n 1

- 판단 필요 불일치가 있었으면 clarification request 파일이 생성됐는지 확인한다.

    ls -t agent_outputs/clarification_requests/*_doc_drift_audit.md | head -n 1

- 작업 종료 시 `HANDOFF.md`를 이번 감사 기준으로 갱신한다.

## 마지막 출력

1. 대조에 사용한 명령
2. 수정한 기계적 불일치 목록
3. 질문으로 분리한 판단 필요 불일치 목록과 clarification request 경로
4. 생성한 감사 보고서 경로
5. `HANDOFF.md` 갱신 여부
6. 변경 파일 목록
