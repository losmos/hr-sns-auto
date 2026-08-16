# Project Context

## 프로젝트 목적

- `hr-sns-auto`는 압구정에서 모발이식병원을 운영하는 석지웅 원장의 개인 브랜딩과 의료계 네트워킹 업무를 지원한다.
- Instagram에서 다른 의료 섹터의 의사·약사와 자연스럽게 교류하고 장기적인 관계를 형성하도록 후보 발굴, 적합성 검증, 콘텐츠 분석, 댓글·DM 초안, 승인 업무를 지원한다.
- 무작위 광고 발송이나 대량 spam 자동화를 목적으로 하지 않는다.

## 배경

- 발신자는 석지웅 원장이다.
- 주 사용자는 병원 직원인 운영자이며 최종 승인자 역할을 한다.
- 시스템이 매일 적합한 신규 후보를 제시하고, 운영자가 근거와 문안을 검토한 뒤 외부 행동 여부를 결정하는 흐름이다.
- 첫 release에서 시스템은 외부 Instagram action을 실행하지 않고 운영자의 manual execution을 지원한다.
- 2026-08-16에 범용 하네스를 새 프로젝트용으로 초기화했고, 2026-08-17에 첫 요구사항 분석·공식 Instagram API 가능성 조사와 사용자 Q1~Q8 결정 반영을 수행했다.

## 목표

- 매일 모든 필수 eligibility 검증을 통과한 `ELIGIBLE` 신규 Instagram 후보를 운영자가 검토할 수 있도록 최대 15명 제시한다. 15명은 quota가 아니다.
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
- 모발이식 여부가 불명확하거나 필수 evidence가 부족하면 `ELIGIBLE`로 통과시키지 않고 `REVIEW_REQUIRED`로 보낸다.
- generation과 external execution을 분리하고, execution은 provider capability와 사람 승인 상태를 별도로 검사한다.
- MVP에는 Playwright·Selenium 등 Instagram browser action automation을 구현하지 않는다.
- Instagram 원본 media를 기본 저장하지 않고 공개 전문 정보·permalink·구조화 사실·최소 excerpt·관찰 시점을 중심으로 저장한다.
- API capability, permission, rate limit은 구현 전에 고정 API version과 실제 계정 조건으로 재검증한다.
- Meta Business Discovery와 특정 Search API는 MVP 필수 dependency로 두지 않는다.
- 애플리케이션 기술 스택, 운영자 인증 방식과 실행 명령은 아직 확정되지 않았다.

## 확정된 사실

- 프로젝트 이름과 slug는 모두 `hr-sns-auto`이다.
- 운영자는 병원 직원이고 최종 승인자이다.
- 후보 분류 상태는 `ELIGIBLE`, `INELIGIBLE`, `REVIEW_REQUIRED`를 사용하도록 설계한다.
- 후보 판정마다 source URL, 관찰값, 관찰일 등 사람이 확인할 evidence가 필요하다.
- 실제 Instagram 로그인, follow, like, comment, DM 전송은 2026-08-17 분석 작업 범위에서 수행하지 않았다.
- 애플리케이션 코드는 아직 구현하지 않았다.
- 석지웅 원장 Instagram account type, Facebook Page 연결 여부, Meta App 준비 상태는 현재 알 수 없다.
- 2026-08-17 기준 Business Discovery는 이미 알고 있는 username의 공개 Business·Creator metadata와 일부 media를 조회하는 검증 기능이며 조건 기반 account search가 아니다.
- 2026-08-17 기준 공식 API는 hashtagged media 탐색을 지원하지만, target username·owner 반환 여부는 확인하지 못해 후보 계정 discovery 수단으로 확정하지 않았다.
- 2026-08-17 기준 공식 API는 타 계정 게시물에 새 댓글 작성, 게시물 좋아요, 계정 follow, 선제 cold DM을 지원하지 않는다.
- 2026-08-17 기준 Messaging API는 상대의 선행 메시지가 필요하며, commenter private reply도 자사 media에 상대가 댓글을 남긴 경우에 한정된다.
- 위 API 사실은 시간에 따라 변경될 수 있으므로 구현 또는 외부 실행 범위 변경 전에 공식 문서를 다시 확인해야 한다.
- `docs/harness/PROJECT_CONTEXT.md`는 장기 프로젝트 맥락의 source of truth이고 `docs/harness/HANDOFF.md`는 중단기·직전 작업 기억을 관리한다.

## 결정 사항

- `DEC-20260817-no-unauthorized-instagram-collection`: Instagram 웹 UI 무단 scraping과 private endpoint를 후보 데이터 수집에 사용하지 않는다.
- `DEC-20260817-separate-generation-execution`: 후보 발굴·판정·콘텐츠 분석·문안 생성 영역과 실제 Instagram action 실행 영역을 분리한다.
- `DEC-20260817-daily-eligible-candidate-cap`: 일일 목표는 필수 검증을 모두 통과한 운영자 검토 가능 신규 `ELIGIBLE` 후보 최대 15명이다. 15명 미달을 허용하며 숫자를 위해 기준을 낮추지 않는다.
- `DEC-20260817-initial-target-market`: 초기 대상은 대한민국의 한국어 Instagram 계정이며 모발이식 분야를 제외한 의사·약사를 폭넓게 다룬다. 초기 진료과 quota는 두지 않는다.
- `DEC-20260817-profession-identity-evidence`: Instagram bio·category만으로 의사·약사를 확정하지 않는다. 강한 공개 근거 1개와 Instagram identity 일치 근거를 요구하고, 강한 단일 근거가 없으면 독립적인 공개 source 2개 이상을 검토한다. 부족·상충 근거는 `REVIEW_REQUIRED`이다.
- `DEC-20260817-hair-ambiguity-review`: 모발이식 관련성을 충분히 확인할 수 없는 후보는 `ELIGIBLE`로 자동 통과시키지 않고 `REVIEW_REQUIRED`로 분류한다.
- `DEC-20260817-recent-activity-ranking`: 최근 30일 활동을 우선하고 30일 초과만으로 제외하지 않는다. 90일 초과는 낮은 우선순위 또는 `REVIEW_REQUIRED`로 취급할 수 있으며 최근 활동은 기본적으로 ranking 요소이지 hard exclude가 아니다.
- `DEC-20260817-first-release-mode`: 첫 release는 `APPROVAL_REQUIRED + MANUAL_EXECUTION`이다. 시스템은 후보·evidence·eligibility·content 분석·comment/DM draft·독립 승인·결과 기록을 담당하고 운영자가 Instagram action을 직접 수행한다.
- `DEC-20260817-outreach-sequencing`: 후보당 하루 신규 outbound action은 최대 하나이다. 실제 content interaction을 먼저 검토하고 DM은 다른 시점의 별도 action·별도 approval로 다룬다.
- `DEC-20260817-cooldown-suppression`: cold DM 무응답이면 같은 목적으로 재발송하지 않고 동일 post comment는 한 번만 허용한다. 후보 단위 cooldown 기본값은 30일이며 거절·연락 중단 요청·차단은 permanent suppression이다. 변경은 새 `PolicyVersion`으로 관리한다.
- `DEC-20260817-browser-automation-mvp-exclusion`: Playwright·Selenium 기반 Instagram browser action automation은 MVP와 현재 구현 계획에서 제외한다. 영구 금지는 아니며 향후 별도 조사 없이 추가하지 않는다.
- `DEC-20260817-meta-read-integration-optional`: 발신 계정·Page·Meta App 상태를 알 수 없으므로 Meta Business Discovery는 MVP blocker나 필수 기능이 아니다. 향후 prerequisites 확인 후 read-only validation/enrichment spike로만 검토한다.
- `DEC-20260817-public-data-minimization`: username, permalink, 구조화 사실, 판정 evidence, 필요한 최소 excerpt, 관찰 시점을 중심으로 저장하고 Instagram 원본 media를 기본 보관하지 않는다. 외부 AI provider 전달도 생성 목적의 최소 범위로 제한한다.
- `DEC-20260817-task-prompts-versioned`: secret·민감 정보를 제외한 `prompts/tasks/*.md`는 작업 의도와 재현성을 위한 프로젝트 기록으로 기본 Git commit 대상이다.
- 기술 스택 추천, 구체 evidence TTL, approval TTL, 90일 초과 후보의 두 허용 처리 중 하나, 보유 기간과 실제 AI·Search provider는 아직 확정 Decision이 아니다.

## 미확정 질문

- `P0 Blocker`: Phase 1 코드 착수 전에 추천 Option A를 포함한 애플리케이션 기술 스택을 확정해야 한다.
- `P0 Blocker`: named operator 인증 방식과 최소 role을 확정해야 한다.
- `P1 Investigation`: follower evidence TTL·임계값 인접 재확인 범위와 approval TTL을 정해야 한다.
- `P1 Investigation`: 공개 profile·content의 구체 보유·삭제 기간과 실제 AI provider의 학습·보유·subprocessor 조건을 정해야 한다.
- `P1 Investigation`: 특정 Search API를 사용하려면 공식 이용조건, 가격, 신규 이용 가능성, query quality와 저장 제한을 비교해야 한다.
- `P2 Non-blocking`: 90일 초과 비활성 후보를 낮은 ranking의 `ELIGIBLE`로 유지할지 `REVIEW_REQUIRED`로 보낼지 선택해야 한다.
- 석지웅 원장 account type, Facebook Page 연결, Meta App 준비 상태는 미확인이다. 이는 manual-first MVP blocker가 아니며 optional read-only Meta spike의 선행 조사이다.
- 현재 상세 설계 문서 완료를 막는 blocker는 없다.

## 참고 산출물

- 요구사항·기술 가능성·초기 아키텍처 보고서: `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- MVP 상세 설계·구현 계획: `agent_outputs/reports/mvp_implementation_plan.md`
- 답변 완료된 과거 질문 기록: `agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md`
- 구현 전 남은 결정 질문지: `agent_outputs/clarification_requests/20260817_005758_mvp_implementation_decisions.md`
- 긴 분석, 계획, 리뷰, 감사 보고서는 `agent_outputs/reports/`에 저장한다.
- 실행 로그는 `agent_outputs/run_logs/`, 사용자 답변이 필요한 질문지는 `agent_outputs/clarification_requests/`에 저장한다.

## 다음 작업 기준

- 구현 전 남은 clarification request 답변 중 장기적으로 유효한 내용을 이 문서에 승격한다.
- Phase 1은 manual seed, evidence, deterministic eligibility, review queue부터 구현한다.
- Meta read-only spike는 계정 prerequisites가 확인될 때만 수행하며 Phase 1~4의 선행조건으로 두지 않는다.
- 모발이식 hard-exclude와 ambiguous review, evidence freshness, duplicate·cooldown을 fixture와 boundary test로 먼저 검증한 뒤 generation을 연결한다.
- 기술 스택이 확정되면 `AGENTS.md`의 테스트, 빌드, 정적 검사, 실행 명령을 실제 값으로 갱신한다.
- 긴 조사 전문은 이 문서에 누적하지 않고 관련 `agent_outputs/` 경로를 연결한다.
- 작업 종료 시 `docs/harness/HANDOFF.md`를 갱신한다.
