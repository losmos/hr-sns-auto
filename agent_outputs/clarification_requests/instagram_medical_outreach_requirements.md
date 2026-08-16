# Instagram 의료인 네트워킹 요구사항 확인 질문지

## 빠른 답변표

| 질문 ID | Priority | 한 줄 질문 | 답변 방식 | 권장 다음 행동 |
| --- | --- | --- | --- | --- |
| Q1 | P1 Investigation | 하루 최대 15명의 어느 단계를 목표로 하는가 | 객관식 | A 권장 |
| Q2 | P1 Investigation | 대상 지역·언어·의료 섹터 범위는 무엇인가 | 객관식 | A 권장 |
| Q3 | P1 Investigation | 의사·약사 신원에 필요한 최소 근거는 무엇인가 | 객관식 | A 권장 |
| Q4 | P1 Investigation | 최근 활동 기준을 어떻게 정할 것인가 | 객관식 | A 권장 |
| Q5 | P1 Investigation | follow·comment·DM 순서와 일일 action 수는 무엇인가 | 객관식 | A 권장 |
| Q6 | P1 Investigation | 무응답 후보의 재접촉·cooldown 정책은 무엇인가 | 객관식 | A 권장 |
| Q7 | P1 Investigation | 발신 계정 유형과 Facebook Page 연결 상태는 무엇인가 | 객관식 | 계정 상태 확인 |
| Q8 | P1 Investigation | 공개 profile·콘텐츠의 보유와 외부 AI 전달 기준은 무엇인가 | 객관식 | A 권장 |

빠른 답변 예시는 `Q1=A, Q2=A, Q3=A, Q4=A, Q5=A, Q6=A, Q7=D, Q8=A`이다.

## Q1

### Priority

`P1 Investigation`

### 질문

“매일 최대 15명”은 어느 단계의 수를 뜻하는가?

### 답변 선택지

- A. `ELIGIBLE`이며 운영자가 검토할 수 있는 후보 최대 15명이다. 추천한다.
- B. source에서 처음 발견한 raw 후보 최대 15명이다.
- C. 댓글·DM draft까지 준비된 승인 대기 후보 최대 15명이다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: ____

### 답변하지 않으면

보고서에서는 품질을 우선해 A를 보수적 제안으로 사용하지만 확정 결정으로 기록하지 않는다.

### 근거 요약

raw 후보와 적합 후보는 hard exclude·중복·근거 부족 때문에 수가 크게 다를 수 있다. 숫자를 채우는 압력이 모발이식 false negative를 늘리면 안 된다.

## Q2

### Priority

`P1 Investigation`

### 질문

초기 후보의 지역·언어·의료 섹터 범위를 어떻게 정할 것인가?

### 답변 선택지

- A. 대한민국의 한국어 계정을 기본으로 하고 모발이식 외 의사·약사 섹터를 폭넓게 다룬다. 추천한다.
- B. 서울·수도권 계정만 다룬다.
- C. 지역 제한 없이 한국어 소통 가능한 국내외 계정을 다루고 우선 섹터 목록을 별도로 제공한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 지역, 언어, 우선·제외 섹터를 적는다: ____

### 답변하지 않으면

국내 한국어 계정을 우선하는 조사 설계만 유지하고 실제 discovery query·sector quota는 확정하지 않는다.

### 근거 요약

지역과 언어는 검색 query, 디렉터리 선택, 문안 어조, source precision에 직접 영향을 준다.

## Q3

### Priority

`P1 Investigation`

### 질문

의사·약사와 실제 개인 계정임을 통과시키는 최소 evidence를 어떻게 정할 것인가?

### 답변 선택지

- A. 소속 기관·학회 등 강한 공개 근거 1개와 Instagram identity 일치 근거를 요구하고, 약한 근거만 있으면 독립 source 2개를 요구한다. 추천한다.
- B. Instagram bio와 category가 의사·약사라고 표시되면 통과시킨다.
- C. 운영자가 profile을 보고 수동 확정하면 외부 source 없이 통과시킨다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 허용 source와 최소 조합을 적는다: ____

### 답변하지 않으면

A를 설계 제안으로 두고, 강한 근거가 없으면 `REVIEW_REQUIRED`로 유지한다.

### 근거 요약

Professional Account category는 사용자가 설정할 수 있어 자격 증명과 동일하지 않다. 반대로 공개 자격 검색 source의 이용조건도 확인해야 한다.

## Q4

### Priority

`P1 Investigation`

### 질문

“최근 게시 활동”의 기준과 비활성 계정 처리를 어떻게 정할 것인가?

### 답변 선택지

- A. 최근 30일 활동을 우선하고 90일 초과는 낮은 우선순위 또는 review로 둔다. 비활성만으로 즉시 hard exclude하지 않는다. 추천한다.
- B. 최근 30일 게시가 없으면 `INELIGIBLE`로 처리한다.
- C. 최근 90일 게시가 있으면 동일하게 적합하며 ranking에 반영하지 않는다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 기간과 상태 규칙을 적는다: ____

### 답변하지 않으면

최근 활동은 ranking만 하고 eligibility hard gate로 확정하지 않는다.

### 근거 요약

현재 요구는 최근 활동을 “우선”한다고 했으며 필수 exclude라고 하지는 않았다.

## Q5

### Priority

`P1 Investigation`

### 질문

한 후보에게 follow·comment·DM을 어떤 순서와 강도로 제안할 것인가?

### 답변 선택지

- A. 후보당 하루 신규 outbound action 1개만 허용하고, 실제 콘텐츠 댓글을 먼저 검토한 뒤 DM은 별도 날짜·별도 승인으로 진행한다. 추천한다.
- B. 같은 날 follow, comment, DM 세 가지를 모두 승인 대상으로 만든다.
- C. DM만 우선하고 comment·follow는 선택적으로 둔다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 순서, 간격, 일일 수를 적는다: ____

### 답변하지 않으면

한 후보에게 하루 한 action이라는 보수적 제안만 문서에 유지하고 실제 workflow 정책은 확정하지 않는다.

### 근거 요약

같은 날 여러 action은 자동화 여부와 무관하게 상대에게 과도한 접근으로 보일 수 있고 중복·spam 위험을 키운다.

## Q6

### Priority

`P1 Investigation`

### 질문

응답이 없는 후보에게 다시 접근할 수 있는 조건은 무엇인가?

### 답변 선택지

- A. cold DM은 무응답이면 재발송하지 않고, 댓글은 동일 post 1회와 후보 단위 30일 cooldown을 둔다. 거절·차단은 영구 suppression한다. 추천한다.
- B. DM과 댓글 모두 14일 후 한 번 재시도할 수 있다.
- C. 운영자가 매번 수동 결정하고 고정 cooldown은 두지 않는다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: action별 기간과 예외를 적는다: ____

### 답변하지 않으면

A를 안전한 pilot 제안으로 두되 숫자는 `PolicyVersion` 설정값으로 남기고 확정 결정으로 기록하지 않는다.

### 근거 요약

interaction history만 저장하고 cooldown 규칙이 없으면 username 변경, 여러 operator, 여러 action type에서 중복 접근이 발생할 수 있다.

## Q7

### Priority

`P1 Investigation`

### 질문

석지웅 원장 발신 계정의 유형과 Facebook Page 연결 상태는 무엇인가?

### 답변 선택지

- A. Instagram Business Account이며 관리 가능한 Facebook Page에 연결되어 있다.
- B. Instagram Creator Account이며 관리 가능한 Facebook Page에 연결되어 있다.
- C. Professional Account이지만 Facebook Page에 연결되어 있지 않다.
- D. Personal Account이거나 현재 상태를 모르겠다 / 추가 조사 필요
- E. 기타: account type, Page 연결, Meta app 준비 상태를 적는다: ____

### 답변하지 않으면

Business Discovery 연동을 MVP 필수로 두지 않고 manual evidence 경로를 기본으로 유지한다.

### 근거 요약

Business Discovery를 사용하는 Facebook Login 구성은 Professional Account와 연결된 Page가 필요하다. 계정 상태를 모른 채 API 통합 가능성을 확정할 수 없다.

## Q8

### Priority

`P1 Investigation`

### 질문

공개 profile·최근 콘텐츠를 얼마나 보관하고 외부 AI provider에 어떤 범위로 전달할 수 있는가?

### 답변 선택지

- A. permalink·구조화 사실·최소 excerpt만 보관하고, 학습 미사용·보유 제한이 계약된 AI provider에 최소 입력만 전달한다. 구체 보유 기간은 개인정보 검토 후 정한다. 추천한다.
- B. 공개 caption과 필요한 thumbnail을 정한 기간 보관하되 법무 검토를 선행하고, 학습 미사용·보유 제한이 계약된 enterprise AI provider만 사용한다.
- C. 외부 AI에는 전달하지 않고 내부 또는 local model만 사용한다.
- D. 모르겠다 / 추가 조사 필요
- E. 기타: 보유 기간, 허용 데이터, AI provider 조건을 적는다: ____

### 답변하지 않으면

원본 media 저장과 외부 AI 전송을 설계 전제로 삼지 않고, 최소화된 text input만 가능한 구조로 둔다.

### 근거 요약

공개 정보라도 목적성 있는 저장·분석과 제3자 전송은 개인정보·보안 검토가 필요하다. 환자 사례 content에는 불필요한 민감 정보가 포함될 수 있다.

## 답변 후 처리

- 장기적으로 유효한 답변은 `docs/harness/PROJECT_CONTEXT.md` 승격 여부를 판단한다.
- 답변을 기준으로 eligibility policy, discovery query, activity rule, cooldown, approval policy를 구체화한다.
- 계정 조건이 확인되면 외부 action 없이 Business Discovery read-only spike 작업 프롬프트를 작성한다.
- 이 질문지는 source of truth가 아니며 사용자 답변과 후속 결정 문서가 source of truth이다.
