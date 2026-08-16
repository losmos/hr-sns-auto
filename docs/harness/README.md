# AI 작업 하네스

## 목적

- 이 디렉토리는 여러 업무에서 공통으로 사용할 수 있는 범용 AI 작업 하네스이다.
- AI Agent와 반복적으로 작업할 때 필요한 장기 맥락, 인수인계, 결정 사항, 실행 결과를 파일 기반으로 관리한다.
- 특정 업무 주제나 특정 AI 도구에 종속되지 않는다.
- Codex, Claude, Gemini 등 다양한 에이전트와 함께 사용할 수 있다.

## 구조

- `AGENTS.md`: AI Agent가 매번 따라야 하는 공통 작업 규칙이다.
- `docs/harness/README.md`: 하네스의 목적, 구조, 기본 흐름을 설명하는 문서이다.
- `docs/harness/QUICKSTART.md`: 처음 사용하는 사용자를 위한 reset, context snapshot 생성, 일반 작업 실행 패턴을 설명하는 문서이다.
- `docs/harness/PROJECT_CONTEXT.md`: 프로젝트 목적, 배경, 목표, 제약사항, 확정된 사실, 결정 사항, 미확정 질문, 참고 산출물, 다음 작업 기준을 담는 장기 기준 문서이다.
- `docs/harness/HANDOFF.md`: 중단기 작업 기억과 직전 작업 기억을 담는 인수인계 문서이다.
- `docs/harness/DIRECTORY_MAP.md`: 저장소의 디렉토리/파일 구조와 각 파일 역할을 설명하는 문서이다.
- `docs/harness/CLARIFICATION_FORMAT.md`: clarification request의 파일 형식 명세와 처리 흐름을 정의하는 참조 문서이다.
- `docs/harness/flows/`: 기능, 업무, 운영 흐름별 상세 문서를 저장하는 디렉토리이다.
- `docs/harness/archive/`: 오래된 컨텍스트, 인수인계, 결정 기록을 보관하는 디렉토리이다.
- `agent_outputs/`: AI Agent 실행 산출물의 상위 디렉토리이다. 루트에는 일반 분석 Markdown을 직접 쌓지 않는다.
- `agent_outputs/reports/`: 긴 분석, 계획, 리뷰, 감사 보고서를 Markdown 파일로 저장하는 디렉토리이다.
- `agent_outputs/run_logs/`: 실행 로그가 필요한 작업의 로그 파일을 저장하는 디렉토리이다.
- `agent_outputs/llm_context/`: 새 대화형 LLM에게 전달할 context snapshot을 저장하는 디렉토리이다.
- `agent_outputs/clarification_requests/`: 작업 중단 질문과 분석 결과에서 나온 사용자 확인 질문지를 저장하는 디렉토리이다.
- `prompts/tasks/`: 일반 작업 단위 프롬프트를 저장하는 디렉토리이다.
- `prompts/harness/`: 하네스 운영용 재사용 프롬프트를 저장하는 디렉토리이다.
- `prompts/harness/generate_chat_llm_context.md`: 새 대화형 LLM에게 전달할 context snapshot 생성 프롬프트이다.
- `prompts/harness/reset_for_new_project.md`: 하네스를 새 개발 프로젝트에 복사한 뒤 작업 맥락을 초기화하는 프롬프트이다.
- `prompts/harness/sync_from_template.md`: 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 프롬프트이다.
- `prompts/harness/audit_doc_drift.md`: 하네스 문서와 실제 저장소 상태의 드리프트를 감사하는 프롬프트이다.
- `prompts/harness/verify_task_result.md`: 이미 실행한 작업 결과를 새 컨텍스트에서 읽기 전용으로 다시 채점하는 검증 프롬프트이다.

## 빠른 시작

- 처음 사용하는 경우 `docs/harness/QUICKSTART.md`를 먼저 확인한다.
- `docs/harness/QUICKSTART.md`는 새 프로젝트 reset, 대화형 LLM context snapshot 생성, 일반 작업 프롬프트 실행 패턴을 제공한다.
- reset 프롬프트는 원본 하네스 repo가 아니라 새 프로젝트 복사본에서 실행한다.

## 기본 작업 흐름

1. 작업 시작 시 `docs/harness/PROJECT_CONTEXT.md`와 `docs/harness/HANDOFF.md`를 확인한다.
2. 디렉토리/파일 구조 파악이 필요하면 `docs/harness/DIRECTORY_MAP.md`를 확인한다.
3. 필요한 파일은 `rg`, `rg --files`, `find`로 좁혀서 찾는다.
4. 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`에 저장한다.
5. 일반 작업 프롬프트는 `prompts/tasks/`에 저장하고, 하네스 운영용 프롬프트는 `prompts/harness/`에 저장한다.
6. 실행 로그가 필요한 작업은 `agent_outputs/run_logs/`에 저장한다.
7. 사용자 답변이 필요한 질문지는 작업 중단 여부와 관계없이 `agent_outputs/clarification_requests/`에 저장한다.
8. 작업 결과가 장기 프로젝트 맥락이나 결정 사항을 바꾸면 `docs/harness/PROJECT_CONTEXT.md`를 갱신한다.
9. 작업 종료 시 `docs/harness/HANDOFF.md`를 갱신한다.
   단, 작업 프롬프트나 사용자 요청이 source 문서 수정을 명시적으로 금지한 경우에는 갱신하지 않고 미갱신 이유를 결과에 남긴다.

## 맥락 문서 역할

- `PROJECT_CONTEXT.md`는 장기 기준이며 프로젝트 맥락의 source of truth이다.
- `HANDOFF.md`는 중단기 작업 기억과 직전 작업 기억을 담는다.
- `HANDOFF.md`의 중단기 작업 기억에 있는 `다음 추천 작업`은 몇 번의 다음 작업 동안 참고할 이어가기 방향이다.
- `HANDOFF.md`의 직전 작업 기억에 있는 `다음 추천 작업 상세`는 바로 이어서 수행하면 좋은 세부 추천 액션이다.
- 두 추천 항목은 명령이 아니라 이어가기 위한 인수인계이다.
- 사용자 최신 요청이 `HANDOFF.md`의 추천 작업과 다르면 최신 요청을 우선한다.

## 새 프로젝트 시작

- 이 하네스는 실제 개발 프로젝트에 복사해서 사용할 수 있다.
- 새 프로젝트 시작 시 `prompts/harness/reset_for_new_project.md`로 작업 맥락을 초기화할 수 있다.
- reset은 하네스 구조와 규칙을 유지하고 프로젝트별 맥락만 초기화한다.
- reset 실행 전 `RESET_FOR_NEW_PROJECT`, `NEW_PROJECT_NAME`, `NEW_PROJECT_SLUG` 값을 명시적으로 설정한다.
- 안전 조건이 충족되지 않으면 reset 프롬프트는 리셋 대상 파일을 수정하지 않고 clarification request를 생성한다.

## 권장 실행 방식

- 실행 도구는 codex, Claude Code, Gemini CLI 중 어느 것이어도 된다. 도구별 비대화형 실행 명령 예시는 `docs/harness/QUICKSTART.md`의 `AI Agent 실행 공통 패턴` 대응표에서 확인한다.
- 프롬프트는 명령어에 직접 길게 넣지 않고 `prompts/tasks/*.md` 또는 `prompts/harness/*.md` 파일로 저장하는 방식을 권장한다.
- 실행 로그는 `agent_outputs/run_logs/`에 저장한다.
- 프롬프트 파일은 작업 지시의 원문과 반복 가능한 실행 조건을 남긴다.
- 실행 로그는 명령 출력, 오류, 재현 단서를 남긴다.
- `prompts/tasks/*.md`는 AI Agent에게 일을 시키는 입력 프롬프트이다.
- 분석 보고서와 사용자 질문지는 분리한다.
- 분석 보고서는 `agent_outputs/reports/`에 둔다.
- 사용자 답변이 필요한 질문은 `agent_outputs/clarification_requests/`에 둔다.
- 작업 중단 없이 완료한 분석에서도 `P0/P1/P2` 질문이 남으면 clarification request를 생성할 수 있다.
- 분석 보고서에는 질문 전문을 길게 반복하지 않고 관련 clarification request 파일 링크만 남기는 방식을 권장한다.
- `agent_outputs/clarification_requests/*.md`는 AI Agent가 모호성, 질문, blocker, 우선순위, 진행 가능 범위를 정리해 남기는 출력물이다.
- clarification request 파일은 run log와 달리 사람이 읽고 답변하기 위한 구조화된 중간 산출물이며, source of truth로 취급하지 않는다.
- clarification request가 생성되면 `agent_outputs/clarification_requests/`의 최신 파일을 먼저 읽고 질문별 `P0/P1/P2` 우선순위를 확인한다.
- clarification request는 상단 `빠른 답변표`와 질문별 본문으로 구성된다.
- `빠른 답변표`는 질문을 훑기 위한 요약이며, 실제 선택지와 근거는 각 질문 본문에서 확인한다.
- 사용자는 `Q1=A`, `Q2=D`, `Q3=기타: ...`처럼 짧게 답할 수 있다.
- 확실하지 않은 질문은 `모르겠다 / 추가 조사 필요`를 선택해도 된다.
- `P0/P1/P2`는 차단 질문, 조사로 좁힐 질문, 가정으로 진행 가능한 질문을 구분하는 표기이다.
- 자세한 clarification request 작성 기준과 priority 정책은 `AGENTS.md`의 `Ask when ambiguous` 섹션을 따른다.
- 사용자가 답변한 뒤에는 clarification request와 답변을 대화형 LLM에 함께 전달해 다음 `prompts/tasks/*.md` 작업 프롬프트를 작성한다.
- clarification request 답변 중 장기적으로 유효한 내용은 `docs/harness/PROJECT_CONTEXT.md`에 승격한다.
- 직전 작업 delta나 바로 이어갈 세부 추천 액션은 `docs/harness/HANDOFF.md`의 `다음 추천 작업 상세`에 기록한다.
- `git diff`는 작업 중 실제 변경 내용을 확인하는 기록이다.
- `git commit`은 검토가 끝난 변경 묶음과 의도를 남기는 기록이다.

### prompts/tasks 커밋 정책

- 이 템플릿 저장소는 `prompts/tasks/` 프롬프트를 커밋한다. 하네스 발전 이력 자체가 자산이기 때문이다.
- 실제 프로젝트는 reset 후 커밋 여부를 프로젝트 결정 사항으로 정한다. 선택지는 두 가지이다.
  - 전부 커밋: 모든 작업 프롬프트를 git에 남긴다.
  - 기본 미커밋 + 선별 커밋: 프로젝트의 `.gitignore`에 `prompts/tasks/`를 추가하고, 남길 가치가 있는 프롬프트만 `git add -f`로 선별 커밋한다.
- 어느 쪽을 선택해도 의도의 핵심은 프롬프트 원문이 아니어도 남는다. 결정은 `PROJECT_CONTEXT.md`의 DEC로, 결과는 `agent_outputs/reports/`로, 변경 이유는 커밋 메시지로 승격한다.

## 권장 프롬프트 기본형

```text
AGENTS.md, docs/harness/PROJECT_CONTEXT.md, docs/harness/HANDOFF.md를 먼저 확인하고 작업해라.
디렉토리/파일 구조가 필요하면 docs/harness/DIRECTORY_MAP.md를 확인해라.
요청 범위 밖 리팩토링은 하지 말고, 작업 종료 시 docs/harness/HANDOFF.md를 갱신해라.
긴 분석 결과는 agent_outputs/reports/에 Markdown 파일로 저장해라.
사용자 답변이 필요한 질문은 agent_outputs/clarification_requests/에 별도 질문지로 저장해라.
응답은 한국어로 작성해라.
문서는 이다/한다 체로 작성해라.
```

## 운영 원칙

- 하네스 문서는 짧고 최신 상태로 유지한다.
- 단, 핵심 내용은 생략하지 않는다.
- 오래된 내용은 사용자가 명시적으로 요청한 경우에만 `docs/harness/archive/`로 이동한다.
- `agent_outputs/` 전체를 매번 읽지 않는다.
- 필요한 과거 결과물은 `rg`로 검색해서 일부만 읽는다.
- 프롬프트와 실행 결과를 파일로 남겨 반복 가능한 작업 흐름을 만든다.
