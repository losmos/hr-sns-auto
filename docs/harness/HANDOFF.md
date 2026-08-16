# Handoff

## 마지막 갱신일

- 2026-08-17 00:37:36

# 중단기 작업 기억

## 이번 범위

- Instagram 의료인 네트워킹 지원 시스템의 요구사항과 초기 논리 아키텍처를 분석했다.
- 2026년 기준 Meta 공식 개발 문서와 Meta 공식 Instagram Postman 컬렉션을 우선 확인했다.
- 후보 발굴, eligibility, content analysis, comment·DM generation, approval, action abstraction, 데이터 모델, UI, KPI, roadmap을 설계했다.
- 사용자 확인이 필요한 운영 정책을 별도 clarification request로 정리했다.
- 장기 프로젝트 목적, 제약, 날짜가 붙은 공식 API 확인 결과를 `PROJECT_CONTEXT.md`에 반영했다.

## 이번 작업에서 제외한 범위

- 애플리케이션 코드를 구현하지 않았다.
- Instagram 계정에 로그인하지 않았다.
- follow, like, comment, DM을 실행하지 않았다.
- Meta app·token·실계정으로 API call을 실행하지 않았다.
- Playwright·Selenium browser automation을 구현하거나 실행하지 않았다.
- 애플리케이션 기술 스택과 배포 환경을 정하지 않았다.

## 현재 상태

- 요구사항 분석 보고서와 clarification request 작성이 완료된 상태이다.
- 추천 초기 형태는 `APPROVAL_REQUIRED + MANUAL_EXECUTION`이지만 사용자 확정 결정은 아니다.
- 공식 API만으로 목표 outbound action을 실행할 수 없어 manual execution이 현실적인 MVP 경로이다.
- Business Discovery read adapter는 발신 계정 유형·Page 연결과 read-only spike 결과에 따라 조건부로 추가할 수 있다.
- 테스트, 빌드, 정적 검사, 실행 명령은 애플리케이션이 없어 여전히 `TODO`이다.

## 주요 결론

- Business Discovery는 account discovery 검색이 아니라 이미 아는 Business·Creator username의 검증 수단이다.
- 공식 hashtag 기능은 공개 media 탐색까지만 확인되었고 target username·owner 반환 여부가 확인되지 않아 후보 계정 discovery로 확정하지 않았다.
- follower와 recent media는 target이 지원되는 Professional Account일 때만 일부 조회할 수 있다.
- 타인 media top-level comment, post like, follow, cold DM은 현재 공식 API에서 지원되지 않는다.
- Messaging API는 상대가 먼저 시작한 대화가 전제이고 24-hour window 등 별도 제한이 있다.
- Instagram UI 무단 자동 수집과 browser action automation은 약관·계정 제한·유지보수 위험이 높아 권장 경로에서 제외했다.
- 모발이식 관련성이 불명확하면 `REVIEW_REQUIRED`로 보내고 일일 숫자를 채우기 위해 통과시키지 않는다.
- generation과 execution을 분리하고 provider capability를 명시적으로 검사하는 구조를 권장한다.

## 다음 추천 작업

- `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`의 Q1~Q8에 답한다.
- 발신 계정이 필요한 조건을 충족하면 외부 action 없는 Business Discovery read-only spike를 설계한다.
- 답변을 반영해 eligibility policy, source registry, cooldown, approval 상태 전이를 구현 가능한 수준으로 구체화한다.

## 남은 확인 사항

- 하루 15명의 pipeline 단계, 대상 범위, 신원 evidence 기준을 확인해야 한다.
- 최근 활동 기간, action 순서·budget, cooldown·무응답 정책을 정해야 한다.
- 발신 계정 유형, Facebook Page 연결, Meta app 준비 상태를 확인해야 한다.
- 데이터 보유와 외부 AI provider 전달 기준을 정해야 한다.
- `MANUAL`과 `APPROVAL_REQUIRED + manual execution` 중 첫 release 모드를 확정해야 한다.
- 기술 스택과 `prompts/tasks/` 커밋 정책은 아직 미확정이다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다. 프로젝트 목적, 사용자, 목표, hard exclude, 정책 제약, 날짜가 붙은 API 가능성, 결정·미확정 질문, 참고 산출물을 기록했다.

## 직전 작업 delta

- `agent_outputs/reports/instagram_medical_outreach_system_design.md`: 요구사항·API 기능 매트릭스·후보 발굴·아키텍처·데이터 모델·workflow·위험·MVP·roadmap 보고서를 추가했다.
- `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`: P1 질문 8개와 빠른 답변표를 추가했다.
- `docs/harness/PROJECT_CONTEXT.md`: 초기 빈 프로젝트 맥락을 Instagram 의료인 네트워킹 프로젝트 맥락으로 갱신했다.
- `docs/harness/HANDOFF.md`: reset 인수인계를 이번 분석 결과와 다음 작업 기준으로 교체했다.

## 마지막 작업 요약

- 공식 API가 가능한 범위와 불가능한 범위를 추정 없이 분리하고, 실행 자동화가 막혀도 후보·근거·문안·승인 업무에서 가치를 내는 수동 실행형 MVP를 설계했다.

## 변경 파일

- `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`

## 생성 산출물

- `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`

## 다음 추천 작업 상세

1. 질문지에 `Q1=A` 형식으로 답한다.
2. 답변을 장기 결정과 pilot 가정으로 구분해 `PROJECT_CONTEXT.md`에 반영한다.
3. 계정 조건이 준비되었다면 Phase 0 read-only spike용 task prompt를 작성한다.
4. spike는 Business Discovery profile·follower·recent media·Reels metadata·Personal target failure·rate header와 hashtag media의 target 식별 필드만 확인하고 외부 Instagram action을 수행하지 않는다.
5. spike가 불가능하거나 prerequisites가 없으면 Meta integration 없는 manual MVP 요구사항으로 바로 구체화한다.

## 주의할 점

- 공식 API 기능과 약관은 변경될 수 있으므로 2026-08-17 조사 결과를 구현 시점에 재검증한다.
- `FULL_AUTO`를 provider interface가 있다는 이유만으로 활성화하면 안 된다.
- 하루 15명 목표가 hard exclude와 evidence 기준보다 우선하면 안 된다.
- 작업 시작 전부터 존재한 `docs/harness/.QUICKSTART.md.swp` 변경과 `prompts/tasks/design_instagram_medical_outreach_system.md` 미추적 파일은 이번 작업에서 수정하지 않았다.
- 애플리케이션 구성 파일이 없어 코드 테스트·빌드·정적 검사는 수행할 수 없다.
