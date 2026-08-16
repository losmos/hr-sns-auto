# Handoff

## 마지막 갱신일

- 2026-08-16 23:56:51

# 중단기 작업 기억

## 이번 범위

- 범용 하네스의 기존 개발 맥락과 임시 산출물을 `hr-sns-auto` 새 프로젝트용으로 초기화했다.
- `PROJECT_CONTEXT.md`와 `HANDOFF.md`를 새 프로젝트용 템플릿으로 다시 작성했다.
- 지정된 작업 프롬프트와 산출물 디렉토리를 비우고 `.gitkeep`만 유지했다.

## 이번 작업에서 제외할 범위

- 애플리케이션 코드, 프로젝트 구조, 기능 요구사항은 추가하거나 확정하지 않았다.
- `.git/`, Git remote, branch, commit history는 수정하지 않았다.
- `prompts/tasks/` 커밋 정책은 reset에서 결정하지 않았다.

## 현재 상태

- 프로젝트 이름과 slug는 모두 `hr-sns-auto`이다.
- 하네스 맥락 문서와 임시 산출물 초기화가 완료된 상태이다.
- 애플리케이션 매니페스트를 찾지 못해 테스트, 빌드, 정적 검사, 실행 명령은 모두 `TODO` 상태이다.
- reset 프롬프트의 안전 설정값은 `NO / TODO / TODO` 기본값으로 복구했다.

## 주요 결론

- 안전 설정값, 작업 디렉토리, Git 원격 저장소 이름이 `hr-sns-auto` 새 프로젝트와 일치했다.
- 프로젝트 목적과 기술 스택은 제공된 정보만으로 확정할 수 없어 추측하지 않았다.

## 다음 추천 작업

- 프로젝트 목적, 대상 사용자, 핵심 범위와 성공 기준을 정리하는 작업을 우선한다.
- `prompts/tasks/` 커밋 정책을 프로젝트 결정 사항으로 선택한다.

## 남은 확인 사항

- 제품 및 업무 목적, 대상 사용자, 핵심 기능 범위를 확인해야 한다.
- 기술 스택이 정해지면 `AGENTS.md`의 실제 실행 명령을 채워야 한다.
- `prompts/tasks/`의 전부 커밋 또는 기본 미커밋 후 선별 커밋 정책을 선택해야 한다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다. 프로젝트 이름과 slug, 아직 확정되지 않은 프로젝트 목적·기술 스택·커밋 정책을 새 프로젝트 장기 맥락에 기록했다.

## 직전 작업 delta

- `docs/harness/PROJECT_CONTEXT.md`: 기존 하네스 개발 맥락을 제거하고 `hr-sns-auto`용 초기 템플릿으로 교체했다.
- `docs/harness/HANDOFF.md`: reset 결과와 후속 확인 사항을 기록한 초기 인수인계 문서로 교체했다.
- `prompts/tasks/`: 기존 작업 프롬프트를 제거하고 누락되어 있던 `.gitkeep`을 추가했다.
- `agent_outputs/reports/`, `agent_outputs/run_logs/`, `agent_outputs/llm_context/`, `agent_outputs/clarification_requests/`: 기존 산출물을 제거하고 각 `.gitkeep`만 유지했다.
- `prompts/harness/reset_for_new_project.md`: 안전 설정값을 기본값으로 복구했다.

## 마지막 작업 요약

- 안전 조건을 확인한 뒤 지정된 범위만 새 프로젝트 상태로 초기화하고 파일 존재 및 변경 범위를 검증했다.

## 변경 파일

- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `prompts/harness/reset_for_new_project.md`
- `prompts/tasks/.gitkeep` 추가
- `prompts/tasks/*.md` 삭제
- `agent_outputs/reports/*.md` 삭제
- `agent_outputs/clarification_requests/*.md` 삭제

## 생성 산출물

- 새 실행 산출물은 생성하지 않았다.
- 대상 디렉토리의 `.gitkeep` 파일을 유지했으며, `prompts/tasks/.gitkeep`은 새로 추가했다.

## 다음 추천 작업 상세

- 프로젝트 요구사항을 제공받아 목적, 대상 사용자, 핵심 범위, 성공 기준을 `PROJECT_CONTEXT.md`에 반영한다.
- 기술 스택이나 초기 코드가 정해지면 테스트, 빌드, 정적 검사, 실행 명령을 확인해 `AGENTS.md`의 `Commands` 섹션을 갱신한다.
- reset 결과 diff를 검토한 뒤 프로젝트의 첫 커밋 여부를 판단한다.

## 주의할 점

- 기존 HANDOFF의 템플릿 sync 실측 추천보다 사용자의 최신 reset 요청을 우선했으며, 해당 추천은 새 프로젝트 맥락에 승격하지 않았다.
- 현재 저장소 파일이 모두 Git 미추적 상태이므로 첫 커밋 전에 `git status --short`와 diff 대체 검토를 수행해야 한다.
- reset 범위 밖의 기존 Vim swap 파일 `docs/harness/.QUICKSTART.md.swp`는 삭제하지 않았다. 첫 커밋 전에 필요 여부를 검토해야 한다.
- 애플리케이션 구성 파일이 없어 테스트, 빌드, 정적 검사, 실행 검증은 수행할 수 없다.
