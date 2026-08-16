# prompts/harness

이 디렉토리는 하네스 운영용 재사용 프롬프트를 저장한다.

## 파일 역할

- `reset_for_new_project.md`: 하네스를 새 개발 프로젝트에 복사한 뒤 작업 맥락과 임시 산출물을 초기화한다.
- `sync_from_template.md`: 하네스 템플릿의 변경을 프로젝트 복사본에 전파한다. 프로젝트 복사본에서 실행한다.
- `audit_doc_drift.md`: 하네스 문서와 실제 저장소 상태의 드리프트를 감사한다.
- `generate_chat_llm_context.md`: 새 대화형 LLM에게 전달할 context snapshot을 생성한다.
- `verify_task_result.md`: 이미 실행한 작업 결과를 새 컨텍스트에서 읽기 전용으로 다시 채점한다.

## 상세 용법

각 프롬프트의 실행 방법과 흐름은 `docs/harness/QUICKSTART.md`의 해당 절을 따른다. reset은 2절, 문서 드리프트 감사는 4절, context snapshot 생성은 5절, 작업 결과 검증은 8절, 템플릿 전파는 11절이다.

## 인덱스 갱신 규칙

- 이 디렉토리에 프롬프트를 추가, 제거, rename하면 이 인덱스를 함께 갱신한다.
