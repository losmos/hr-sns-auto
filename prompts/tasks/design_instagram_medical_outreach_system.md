# Instagram 의료인 네트워킹 자동화 시스템 요구사항 분석 및 설계

## 목표

`hr-sns-auto` 프로젝트에서 Instagram을 이용한 의료인 네트워킹 업무를 지원하는 시스템의 요구사항과 기술적 구현 가능성을 분석하고 초기 아키텍처를 설계한다.

이번 작업에서는 애플리케이션 코드를 구현하지 않는다.

`AGENTS.md`, `docs/harness/PROJECT_CONTEXT.md`, `docs/harness/HANDOFF.md`, `docs/harness/QUICKSTART.md`를 먼저 읽고 현재 프로젝트 상태와 기존 결정 사항을 확인한다.

## 업무 배경

압구정에서 모발이식병원을 운영하는 원장의 개인 브랜딩과 의료계 네트워킹을 지원하기 위한 업무이다.

운영자는 병원 직원이며 최종 승인자 역할을 한다.

목표는 무작위 광고 발송이 아니라 다른 의료 섹터의 의사·약사 계정과 자연스럽게 교류하고 관계를 형성하는 것이다.

## 대상 계정 조건

매일 최대 15개의 적합한 Instagram 계정을 후보로 선정하는 것을 목표로 한다.

필수 조건:

- 의사 또는 약사이다.
- 한의사는 제외한다.
- Instagram follower가 10,000명 미만이다.
- 모발이식 관련 의료인은 반드시 제외한다.
- 다른 의료 섹터에 종사한다.
- 실제 개인 또는 의료전문가가 운영하는 것으로 충분한 근거가 있어야 한다.
- 최근 게시 활동이 있는 계정을 우선한다.

### 모발이식 계열 HARD EXCLUDE

다음과 같은 계정은 반드시 제외한다.

- 모발이식
- 탈모수술
- 헤어라인 교정
- 모발이식 전문 병원
- hair transplant
- hair restoration surgery
- FUE/FUT 등 모발이식 시술 중심 계정
- 이름상 다른 진료과라도 실제 콘텐츠나 병원 서비스에서 모발이식을 주요 업무로 하는 계정

모발이식 관련 여부가 불명확한 경우 자동으로 적합 판정을 하지 말고 `REVIEW_REQUIRED` 상태로 분류하는 방안을 검토한다.

### 기타 HARD EXCLUDE

- 한의사
- 한의원
- 한방병원
- 의료인이 아닌 계정
- 병원 공식 계정이지만 실제 개인 의료인을 특정할 수 없는 계정
- follower 10,000 이상

## 주요 기능 요구사항

### 1. Candidate Discovery

매일 최대 15명의 신규 후보를 발굴한다.

후보마다 가능한 범위에서 다음 정보를 관리하는 방안을 설계한다.

- Instagram username
- profile URL
- 이름
- 직업
- 전문 분야
- 병원 또는 약국
- follower 수
- 프로필 소개
- 최근 콘텐츠
- 후보 발견 경로
- 선정 근거
- 제외 조건 확인 결과
- 신뢰도
- 상태
- 최초 발견일
- 마지막 확인일

Instagram 전체를 무단 scraping하는 것을 전제로 설계하지 않는다.

공식 Instagram/Meta API, 공개 웹 검색, 수동 seed 입력 등 정책적으로 지속 가능한 후보 발굴 방법을 구분하여 검토한다.

### 2. Eligibility Validation

후보 계정을 다음 중 하나로 분류하는 방안을 설계한다.

- ELIGIBLE
- INELIGIBLE
- REVIEW_REQUIRED

특히 다음 조건은 별도로 검증한다.

- 실제 의사/약사 여부
- 한의사 여부
- 모발이식 관련 여부
- follower 10,000 미만 여부
- 최근 활동 여부

각 판정에는 사람이 확인할 수 있는 근거를 남긴다.

### 3. Content Analysis

후보가 최근 올린 게시물 또는 Reels의 내용을 분석하여 실제 콘텐츠와 관련 있는 댓글을 작성할 수 있도록 한다.

단순한 범용 댓글이나 동일 댓글 반복을 피하는 구조를 설계한다.

### 4. Comment Generation

후보의 실제 게시물 내용을 기반으로 자연스러운 리액션 댓글 초안을 생성한다.

말투는 다음 특성을 따른다.

- 같은 의료인에 대한 예의가 있다.
- 지나치게 친한 척하지 않는다.
- 상대 콘텐츠를 실제로 읽은 흔적이 있다.
- 과장된 칭찬을 하지 않는다.
- 짧지만 구체적이다.
- 광고나 영업처럼 느껴지지 않는다.
- 반복적인 문장을 피한다.

예시 분위기:

    좋은 내용 잘 보았습니다. 평소 생각해보지 못했던 부분인데 많이 배우고 갑니다.

    같은 의료인 입장에서 흥미롭게 보았습니다. 설명해주신 부분이 특히 인상적이네요.

실제 댓글은 대상 게시물의 내용에 맞게 개인화해야 한다.

### 5. DM Generation

후보별로 개인화된 DM 초안을 생성한다.

발신자는 압구정에서 모발이식병원을 운영하는 석지웅 원장이다.

기본적인 말투와 분위기는 다음 예시를 참고한다.

    안녕하세요 선생님. 압구정에서 모발이식병원을 운영하고 있는 석지웅이라고 합니다.
    우연히 선생님 인스타를 보게 되었는데 좋은 내용이 많아 많이 배우고 싶다는 생각에 팔로우 신청드립니다.
    앞으로 종종 소통하며 좋은 아이디어도 나눌 수 있으면 좋겠습니다. 감사합니다.

    안녕하세요 선생님. 저는 압구정에서 모발이식병원을 운영하고 있는 석지웅입니다.
    인스타에서 우연히 선생님 계정을 접하게 되었는데 인상 깊은 내용이 많아 팔로우 신청드렸습니다.
    같은 의사로서 많이 배우고, 종종 좋은 교류 이어갈 수 있으면 좋겠습니다. 감사합니다.

    안녕하세요 선생님. 압구정에서 모발이식병원을 운영하고 있는 석지웅이라고 합니다.
    우연히 인스타에서 선생님을 알게 되었는데 올려주시는 내용들이 인상 깊어 팔로우 신청드립니다.
    서로 분야는 조금 다르더라도 종종 교류하며 좋은 아이디어를 얻을 수 있으면 좋겠습니다. 감사합니다.

DM은 단순 템플릿 random substitution 방식이 아니라 후보의 전문 분야나 실제 콘텐츠를 자연스럽게 반영할 수 있는 구조를 검토한다.

### 6. Approval Workflow

아직 실제 Instagram 액션의 자동화 수준은 확정하지 않았다.

다음 세 가지 모드를 지원할 수 있는 구조를 설계한다.

- MANUAL
  - 시스템은 후보, 댓글, DM 초안만 만든다.
  - 운영자가 Instagram에서 직접 실행한다.

- APPROVAL_REQUIRED
  - 시스템이 액션 초안을 생성한다.
  - 운영자가 approve/edit/reject한다.
  - 승인된 액션만 실행 가능한 구조이다.

- FULL_AUTO
  - 사전에 정한 정책을 통과한 경우 자동 실행한다.
  - 단, Instagram 공식 API와 서비스 정책상 실제 지원 가능한 범위를 먼저 확인해야 한다.

초기 운영 모드로는 `APPROVAL_REQUIRED`를 우선 검토한다.

## 반드시 조사할 기술적 쟁점

2026년 현재 공식 Meta/Instagram 개발자 문서를 우선 source of truth로 사용한다.

다음을 각각 조사한다.

1. Instagram API에서 다른 Professional Account를 탐색하거나 조회할 수 있는 범위
2. Business Discovery의 기능과 제한
3. follower 수 조회 가능 범위
4. 다른 계정의 최근 media/reels 조회 가능 범위
5. 다른 사람 게시물에 댓글을 등록할 수 있는 공식 API 범위
6. 좋아요 관련 공식 API 범위
7. 먼저 보내는 cold DM을 공식 API로 지원하는지
8. Messaging API의 conversation initiation 및 24-hour window 규칙
9. Instagram 자동화 및 automated data collection 관련 약관
10. API rate limit
11. Business/Creator/Personal Account별 제한

불확실한 기능을 가능한 것으로 추측하지 않는다.

각 기능을 다음과 같이 구분한다.

- OFFICIAL_API_SUPPORTED
- PARTIALLY_SUPPORTED
- NOT_SUPPORTED
- NEEDS_VERIFICATION

## 브라우저 자동화

Playwright, Selenium 등으로 Instagram 웹 UI를 직접 조작하는 방법은 별도 대안으로만 분석한다.

다음 내용을 반드시 설명한다.

- 기술적으로 가능한 범위
- Instagram 약관 및 계정 제한 위험
- 유지보수 비용
- UI 변경 취약성
- 로그인/2FA/session 관리 문제
- rate limit 및 anti-abuse 위험

공식 API와 브라우저 자동화를 혼합해야 한다고 가정하지 않는다.

## 후보 발굴 전략

공식 Instagram API만으로 원하는 계정을 검색할 수 없는 경우 다음 방식을 비교한다.

- 검색엔진 기반 공개 웹 검색
- 사용자가 제공하는 seed account
- 병원/학회/전문가 공개 디렉터리 기반 후보 발견
- Instagram Business Discovery 기반 검증
- 수동 후보 입력
- 기타 정책적으로 지속 가능한 방법

각 전략의 precision, recall, 운영비용, 자동화 가능성, 정책 위험을 비교한다.

## 데이터 모델 초안

최소한 다음 entity를 포함하는 데이터 모델을 제안한다.

- Candidate
- CandidateEvidence
- SocialProfile
- ContentItem
- GeneratedComment
- GeneratedDM
- OutreachAction
- Approval
- InteractionHistory
- DiscoveryRun

동일 대상에게 지나치게 자주 접근하지 않도록 interaction history와 cooldown 개념도 검토한다.

## 실행 정책

실제 외부 액션과 AI 생성 로직을 분리한다.

예:

    generation
        candidate discovery
        classification
        content analysis
        comment generation
        dm generation

    execution
        Instagram comment
        Instagram DM
        follow
        like

execution layer는 provider/interface abstraction을 두는 방안을 검토한다.

## 운영 화면

운영자가 매일 다음 작업을 빠르게 수행할 수 있는 최소 UI를 제안한다.

- 오늘의 후보 15명 보기
- 적합 판정 근거 확인
- 프로필 열기
- 최근 콘텐츠 확인
- 댓글 초안 확인 및 수정
- DM 초안 확인 및 수정
- approve
- reject
- skip
- 실행 여부 확인
- 과거 interaction 확인

## 측정 지표

단순히 하루 15명을 채우는 것 외에 다음 KPI를 검토한다.

- candidate precision
- 모발이식 계열 false negative
- 의료인 판정 accuracy
- approval rate
- comment edit rate
- DM edit rate
- follow-back rate
- DM response rate
- interaction continuation rate
- duplicate outreach rate

특히 모발이식 계열 false negative는 매우 심각한 오류로 취급한다.

## 이번 작업 결과물

다음 보고서를 작성한다.

    agent_outputs/reports/instagram_medical_outreach_system_design.md

보고서에는 최소한 다음 내용을 포함한다.

1. 요구사항 정리
2. 사용자 업무 흐름
3. Instagram 공식 API 기능 매트릭스
4. 후보 발굴 전략 비교
5. 추천 아키텍처
6. 데이터 모델
7. approval workflow
8. Instagram action abstraction
9. 정책 및 운영 위험
10. MVP 범위
11. 단계별 개발 로드맵
12. 아직 결정되지 않은 사항
13. 사용자 확인이 필요한 질문

사용자 확인이 필요한 질문이 존재하면 `AGENTS.md`의 표준 질문지 형식에 따라 별도 clarification request도 생성한다.

    agent_outputs/clarification_requests/instagram_medical_outreach_requirements.md

## 중요

이번 작업에서는 실제 Instagram 계정 로그인, follow, comment, DM 전송을 실행하지 않는다.

애플리케이션 구현도 하지 않는다.

공식적으로 확인하지 못한 Instagram 기능을 구현 가능한 것으로 확정하지 않는다.

기존 `PROJECT_CONTEXT.md`와 `HANDOFF.md`의 source-of-truth 역할을 존중한다.

장기적으로 확정된 요구사항이 있다면 어떤 내용을 `PROJECT_CONTEXT.md`에 반영해야 하는지도 보고서에서 제안한다.
