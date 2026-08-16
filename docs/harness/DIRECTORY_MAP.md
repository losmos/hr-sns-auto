# Directory Map

이 문서는 프로젝트 개념 구조가 아니라 저장소의 주요 디렉토리와 파일 역할을 설명하는 문서이다.

## Root structure

```text
.
├── AGENTS.md
├── CLAUDE.md
├── GEMINI.md
├── agent_outputs/
│   ├── .gitkeep
│   ├── clarification_requests/
│   │   └── .gitkeep
│   ├── llm_context/
│   │   └── .gitkeep
│   ├── reports/
│   │   └── .gitkeep
│   └── run_logs/
│       └── .gitkeep
├── docs/
│   └── harness/
│       ├── README.md
│       ├── QUICKSTART.md
│       ├── PROJECT_CONTEXT.md
│       ├── HANDOFF.md
│       ├── DIRECTORY_MAP.md
│       ├── CLARIFICATION_FORMAT.md
│       ├── archive/
│       │   └── .gitkeep
│       └── flows/
│           ├── .gitkeep
│           ├── README.md
│           ├── multi_user_workflow.md
│           └── sync_from_template.md
└── prompts/
    ├── .gitkeep
    ├── harness/
    │   ├── README.md
    │   ├── audit_doc_drift.md
    │   ├── generate_chat_llm_context.md
    │   ├── reset_for_new_project.md
    │   ├── sync_from_template.md
    │   └── verify_task_result.md
    └── tasks/
        └── .gitkeep
```

## Root files

- `AGENTS.md`: AI Agent가 따라야 하는 공통 작업 규칙이다. 작업 시작, 모호성 처리, 문서 갱신, 검증 원칙을 정의한다.
- `CLAUDE.md`: Claude Code가 자동으로 읽는 진입점이며, 공통 작업 규칙이 `AGENTS.md`임을 명시하는 포인터 파일이다.
- `GEMINI.md`: Gemini CLI가 자동으로 읽는 진입점이며, 공통 작업 규칙이 `AGENTS.md`임을 명시하는 포인터 파일이다.
- `.gitignore`: git에 포함하지 않을 실행 산출물과 임시 파일 규칙을 정의한다.

## docs/harness/

- `docs/harness/README.md`: 하네스의 목적, 구성, 기본 작업 흐름을 설명하는 문서이다.
- `docs/harness/QUICKSTART.md`: reset, 문서 드리프트 감사, context snapshot 생성, 일반 작업 실행, 작업 결과 검증, clarification request 확인 흐름을 빠르게 안내하는 문서이다.
- `docs/harness/PROJECT_CONTEXT.md`: 프로젝트 목적, 배경, 목표, 제약사항, 확정된 사실, 결정 사항, 미확정 질문, 참고 산출물, 다음 작업 기준을 담는 장기 프로젝트 맥락 source of truth이다.
- `docs/harness/HANDOFF.md`: 중단기 작업 기억과 직전 작업 기억을 담는 인수인계 문서이다.
- `docs/harness/DIRECTORY_MAP.md`: 저장소의 디렉토리와 파일 역할을 설명하는 문서이다.
- `docs/harness/CLARIFICATION_FORMAT.md`: clarification request의 파일 형식 명세와 처리 흐름을 정의하는 참조 문서이다.
- `docs/harness/flows/`: 기능, 업무, 운영 흐름별 상세 문서를 저장하는 디렉토리이다.
- `docs/harness/flows/README.md`: flows 디렉토리의 용도, 페이지 인덱스, 인덱스 갱신 규칙, 페이지 간 링크 관례를 정의하는 인덱스 문서이다.
- `docs/harness/flows/multi_user_workflow.md`: 여러 개발자가 브랜치에서 하네스 문서를 갱신할 때의 병합 규칙을 설명하는 가이드 문서이다.
- `docs/harness/flows/sync_from_template.md`: 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 흐름을 설명하는 가이드 문서이다.
- `docs/harness/archive/`: 오래된 컨텍스트, 인수인계, 결정 기록을 보관할 수 있는 디렉토리이다.

## prompts/

- `prompts/harness/`: 하네스 운영용 재사용 프롬프트를 저장하는 디렉토리이다.
- `prompts/harness/README.md`: 디렉토리 용도와 각 프롬프트 파일의 역할을 안내하는 로컬 인덱스이다.
- `prompts/harness/generate_chat_llm_context.md`: 새 대화형 LLM에게 전달할 context snapshot 생성 프롬프트이다.
- `prompts/harness/reset_for_new_project.md`: 하네스를 새 개발 프로젝트에 복사한 뒤 작업 맥락과 임시 산출물을 초기화하는 프롬프트이다.
- `prompts/harness/sync_from_template.md`: 하네스 템플릿의 변경을 프로젝트 복사본에 전파하는 프롬프트이다.
- `prompts/harness/verify_task_result.md`: 이미 실행한 작업 결과를 새 컨텍스트에서 읽기 전용으로 다시 채점하는 검증 프롬프트이다.
- `prompts/harness/audit_doc_drift.md`: 하네스 문서와 실제 저장소 상태의 드리프트를 감사하는 프롬프트이다.
- `prompts/tasks/`: AI Agent에게 줄 일반 작업 단위 프롬프트를 저장하는 디렉토리이다.

## agent_outputs/

- `agent_outputs/`: AI Agent 실행 산출물의 상위 디렉토리이다. 루트에는 일반 분석 Markdown 산출물을 직접 쌓지 않고 목적별 하위 디렉토리에 저장한다.
- `agent_outputs/reports/`: 긴 분석, 계획, 리뷰, 감사 보고서를 저장하는 디렉토리이다.
- `agent_outputs/clarification_requests/`: 작업 중단 질문과 분석 결과에서 나온 사용자 확인 질문지를 저장하는 디렉토리이다.
- `agent_outputs/run_logs/`: 실행 로그가 필요한 작업의 로그를 저장하는 디렉토리이다.
- `agent_outputs/llm_context/`: 새 대화형 LLM에게 전달할 context snapshot을 저장하는 디렉토리이다.

## 갱신 기준

- 저장소의 주요 디렉토리나 하네스 운영 파일이 추가, 제거, rename되면 이 문서를 갱신한다.
- 이 문서에는 파일과 디렉토리의 역할을 적고, 장기 프로젝트 판단이나 결정 사항은 `PROJECT_CONTEXT.md`에 기록한다.
