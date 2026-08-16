# Project Context

## 프로젝트 목적

- `hr-sns-auto`는 압구정에서 모발이식병원을 운영하는 석지웅 원장의 개인 브랜딩과 의료계 네트워킹 업무를 지원한다.
- Instagram에서 다른 의료 섹터의 의사·약사와 자연스럽게 교류하고 장기적인 관계를 형성하도록 후보 발굴, 적합성 검증, 콘텐츠 분석, 댓글·DM 초안, 승인 업무를 지원한다.
- 무작위 광고 발송이나 대량 spam 자동화를 목적으로 하지 않는다.

## 배경

- 발신자는 석지웅 원장이다.
- 주 사용자는 병원 직원인 운영자이며 최종 승인자 역할을 한다.
- 시스템이 매일 적합한 신규 후보를 제시하고, 운영자가 근거와 문안을 검토한 뒤 외부 행동 여부를 결정하는 흐름이다.
- 2026-08-16에 범용 하네스를 새 프로젝트용으로 초기화했고, 2026-08-17에 첫 요구사항 분석과 공식 Instagram API 가능성 조사를 수행했다.

## 목표

- 매일 최대 15명의 적합한 신규 Instagram 후보를 제시한다.
- 실제 의사·약사 여부, 한의 계열 여부, 모발이식 관련 여부, follower 10,000 미만 여부, 최근 활동 여부를 독립적으로 판정하고 사람이 확인할 근거를 남긴다.
- 후보의 실제 최근 콘텐츠에 grounded된 댓글과 DM 초안을 만든다.
- 중복·과잉 접근을 interaction history와 cooldown으로 방지한다.
- 후보 품질, 문안 승인·수정, 관계 형성 성과를 측정할 수 있게 한다.
- 모발이식 계열 false negative는 매우 심각한 오류로 취급하고 0건을 목표로 한다.

## 제약사항

- 저장소 작업은 루트 `AGENTS.md`의 규칙을 따른다.
- Instagram 전체 또는 웹 UI의 무단 scraping을 전제로 설계하지 않는다.
- 공식 Meta/Instagram API, 허용된 공개 웹 검색, 사용자 seed, 이용조건을 확인한 공개 디렉터리, 수동 입력을 우선한다.
- 공식 문서로 확인하지 못한 기능은 구현 가능하다고 확정하지 않는다.
- 의사·약사만 대상이며 한의사·한의원·한방병원, 비의료인, 개인을 특정할 수 없는 기관 계정, follower 10,000 이상은 hard exclude이다.
- 모발이식, 탈모수술, 헤어라인 교정, hair transplant, hair restoration surgery, FUE·FUT 중심 계정과 실제 서비스에서 모발이식을 주요 업무로 하는 계정은 hard exclude이다.
- 분석 보고서는 모발이식 여부가 불명확하거나 필수 evidence가 부족하면 `REVIEW_REQUIRED`로 보내는 안전안을 권장한다. 사용자 최종 확인 전 운영 정책으로 확정하지 않는다.
- generation과 external execution을 분리하고, execution은 provider capability와 사람 승인 상태를 별도로 검사한다.
- API capability, permission, rate limit은 구현 전에 고정 API version과 실제 계정 조건으로 재검증한다.
- 애플리케이션 기술 스택과 실행 명령은 아직 확정되지 않았다.

## 확정된 사실

- 프로젝트 이름과 slug는 모두 `hr-sns-auto`이다.
- 운영자는 병원 직원이고 최종 승인자이다.
- 후보 분류 상태는 `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED`를 사용하도록 설계한다.
- 후보 판정마다 source URL, 관찰값, 관찰일 등 사람이 확인할 evidence가 필요하다.
- 실제 Instagram 로그인, follow, like, comment, DM 전송은 2026-08-17 분석 작업 범위에서 수행하지 않았다.
- 애플리케이션 코드는 아직 구현하지 않았다.
- 2026-08-17 기준 Business Discovery는 이미 알고 있는 username의 공개 Business·Creator metadata와 일부 media를 조회하는 검증 기능이며 조건 기반 account search가 아니다.
- 2026-08-17 기준 공식 API는 hashtagged media 탐색을 지원하지만, target username·owner 반환 여부는 확인하지 못해 후보 계정 discovery 수단으로 확정하지 않았다.
- 2026-08-17 기준 공식 API는 타 계정 게시물에 새 댓글 작성, 게시물 좋아요, 계정 follow, 선제 cold DM을 지원하지 않는다.
- 2026-08-17 기준 Messaging API는 상대의 선행 메시지가 필요하며, commenter private reply도 자사 media에 상대가 댓글을 남긴 경우에 한정된다.
- 위 API 사실은 시간에 따라 변경될 수 있으므로 구현 또는 외부 실행 범위 변경 전에 공식 문서를 다시 확인해야 한다.
- `docs/harness/PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이고 `docs/harness/HANDOFF.md`는 중단기·직전 작업 기억을 관리한다.

## 결정 사항

- `DEC-20260817-no-unauthorized-instagram-collection`: Instagram 웹 UI 무단 scraping과 private endpoint를 후보 데이터 수집에 사용하지 않는다.
- `DEC-20260817-separate-generation-execution`: 후보 발굴·판정·콘텐츠 분석·문안 생성 영역과 실제 Instagram action 실행 영역을 분리한다.
- provider capability gate, browser action automation 제외, 초기 운영 모드, cooldown 숫자, 활동 기간, 기술 스택은 보고서의 설계 제안이며 아직 결정 사항으로 확정하지 않는다.

## 미확정 질문

- `P1 Investigation`: 하루 최대 15명이 raw 후보, `ELIGIBLE` 후보, draft까지 준비된 승인 대기 후보 중 무엇인지 확인해야 한다.
- `P1 Investigation`: 대상 지역·언어·우선 의료 섹터를 확인해야 한다.
- `P1 Investigation`: 의사·약사 신원을 통과시키는 최소 evidence 조합을 정해야 한다.
- `P1 Investigation`: 최근 활동 기준과 follower evidence TTL을 정해야 한다.
- `P1 Investigation`: follow·comment·DM 순서, 일일 action budget, cooldown, 무응답 재접촉 정책을 정해야 한다.
- `P1 Investigation`: 석지웅 원장 계정 유형, Facebook Page 연결, Meta app 준비 상태를 확인해야 한다.
- `P1 Investigation`: 공개 profile·콘텐츠의 보유 기간과 외부 AI provider 전달 기준을 정해야 한다.
- `P2 Non-blocking`: 모발이식 관련 evidence가 불명확할 때 항상 `REVIEW_REQUIRED`로 보내는 안전안을 최종 승인해야 한다.
- `P2 Non-blocking`: browser action automation을 roadmap에서 제외할지 최종 결정해야 한다.
- `P2 Non-blocking`: `MANUAL` pilot과 `APPROVAL_REQUIRED + manual execution` 중 첫 release 형태를 최종 선택해야 한다.
- `P2 Non-blocking`: `prompts/tasks/`를 전부 커밋할지 선별 커밋할지 결정하지 않았다.
- 현재 요구사항 분석 완료를 막는 `P0 Blocker`는 없다.

## 참고 산출물

- 요구사항·기술 가능성·초기 아키텍처 보고서: `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- 사용자 확인 질문지: `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`
- 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`에 저장한다.
- 실행 로그는 `agent_outputs/run_logs/`, 사용자 답변이 필요한 질문지는 `agent_outputs/clarification_requests/`에 저장한다.

## 다음 작업 기준

- clarification request 답변 중 장기적으로 유효한 내용을 이 문서에 승격한다.
- 구현 전에 공식 API read-only spike로 계정 prerequisites, Business Discovery field, permission, rate header, Personal target 실패를 확인한다.
- 외부 action을 수행하지 않는 수동 MVP부터 검토한다.
- 모발이식 hard-exclude, evidence freshness, duplicate·cooldown을 먼저 검증 가능한 정책으로 만든 뒤 generation을 연결한다.
- 기술 스택이 확정되면 `AGENTS.md`의 테스트, 빌드, 정적 검사, 실행 명령을 실제 값으로 갱신한다.
- 긴 조사 전문은 이 문서에 누적하지 않고 관련 `agent_outputs/` 경로를 연결한다.
- 작업 종료 시 `docs/harness/HANDOFF.md`를 갱신한다.
