# Flows

## 용도

- 이 디렉토리는 기능, 업무, 운영 흐름별 상세 문서를 저장한다.
- `AGENTS.md`, `PROJECT_CONTEXT.md`, `HANDOFF.md`에 담기에는 길고 특정 흐름에만 필요한 상세 절차, 단계, 예시를 여기에 둔다.
- 상시 필요한 규칙이나 장기 맥락은 여기 두지 않고 `AGENTS.md`와 `PROJECT_CONTEXT.md`에 둔다.

## 페이지 인덱스

- [템플릿 전파](sync_from_template.md) — 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 흐름이다.
- [멀티 유저 워크플로우](multi_user_workflow.md) — 여러 개발자가 브랜치에서 하네스 문서를 갱신할 때의 병합 규칙이다.

## 인덱스 갱신 규칙

- flows 문서를 추가, 제거, rename하면 위 `페이지 인덱스`를 함께 갱신한다.
- 각 인덱스 항목은 `- [제목](파일명) — 한 줄 설명` 형식으로 작성한다.

## 페이지 간 링크 관례

- flows 디렉토리 안의 문서끼리는 상대 경로로 링크한다(예: `[다른 흐름](other_flow.md)`).
- 하네스 상위 문서를 가리킬 때는 `docs/harness/` 기준 경로를 사용한다(예: `docs/harness/PROJECT_CONTEXT.md`).
