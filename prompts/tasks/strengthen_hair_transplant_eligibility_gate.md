# 모발이식 eligibility gate 안전성 강화

## 목표

현재 Candidate eligibility vertical slice의 가장 중요한 안전 조건인
모발이식 false negative 방지를 강화한다.

이번 작업은 기존 구조를 확장하는 기능 개발이 아니라
현재 eligibility policy의 작은 안전성 보완 작업이다.

범위를 최소화한다.

## 먼저 읽을 문서

`AGENTS.md`의 읽기 순서를 따른다.

반드시 먼저 확인한다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

그다음 현재 구현과 테스트 중 이번 작업에 직접 필요한 파일만 읽는다.

최소 대상:

- `src/main/java/com/losmos/hrsnsauto/candidate/EligibilityPolicy.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/HairTransplantRelation.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/EvidenceType.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/EvidenceStrength.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/EligibilityPolicyTest.java`
- 관련 candidate UI

repo 전체를 무작정 읽지 않는다.

## 현재 문제

현재 `Candidate.hairTransplantRelation == NOT_RELATED`이면
실제 `HAIR_TRANSPLANT` evidence가 없어도
profession과 identity evidence만 충분하면 `ELIGIBLE`이 될 수 있다.

이는 프로젝트의 다음 핵심 안전 원칙보다 느슨하다.

- 모발이식 계열 false negative는 매우 심각한 오류이다.
- 모발이식 관련 여부가 충분히 확인되지 않으면 `ELIGIBLE`로 통과시키지 않는다.
- 불확실하면 `REVIEW_REQUIRED`로 fail-closed 처리한다.

단순히 운영자가 `NOT_RELATED`를 선택했다는 사실만으로
모발이식 검증 gate를 통과시키지 않는다.

## 확정할 정책

### 1. RELATED

다음은 기존처럼 즉시 hard exclude이다.

    hairTransplantRelation == RELATED
    -> INELIGIBLE

다른 evidence가 부족하거나 상충해도 hard exclude가 우선한다.

### 2. UNKNOWN

다음은 기존처럼 review이다.

    hairTransplantRelation == UNKNOWN
    -> REVIEW_REQUIRED

### 3. NOT_RELATED

`hairTransplantRelation == NOT_RELATED`라는 값만으로는
모발이식 gate를 통과하지 않는다.

다음 둘 중 하나를 충족해야 한다.

#### 조건 A

    EvidenceType.HAIR_TRANSPLANT
    + EvidenceStrength.STRONG
    + 유효한 sourceUrl

1개 이상

#### 조건 B

    EvidenceType.HAIR_TRANSPLANT
    + EvidenceStrength.WEAK
    + 유효한 sourceUrl

서로 다른 source URL 2개 이상

동일 source URL에서 나온 weak evidence 여러 개는
독립 근거 여러 개로 계산하지 않는다.

위 조건 A 또는 B를 만족하지 못하면:

    REVIEW_REQUIRED

로 판정한다.

판정 사유는 사람이 이해할 수 있게 작성한다.

예:

    모발이식 비관련성을 뒷받침하는 공개 근거가 최소 기준을 충족하지 못함

## Evidence 해석 범위

현재 데이터 모델에는 evidence가 어떤 결론을 지지하는지 별도 finding field가 없다.

이번 작업에서 새로운 schema나 conclusion enum을 추가하지 않는다.

현재 thin slice에서는:

- Candidate의 `hairTransplantRelation`
- `HAIR_TRANSPLANT` evidence의 type
- strength
- source URL
- 운영자가 작성한 summary

조합으로 운영자가 판단한 결론과 근거를 기록한다.

추후 실제 사용에서 evidence 방향성까지 구조적으로 검증할 필요가 확인되면
별도 slice로 설계한다.

이번 작업에서 미래 구조를 미리 추가하지 않는다.

## EligibilityPolicy 구현 원칙

기존 코드의 단순성과 fail-closed 구조를 유지한다.

가능하면 profession evidence와 유사한 방식으로 구현하되
불필요한 generic abstraction이나 공통 framework를 만들지 않는다.

다음 동작 순서를 유지한다.

1. hard exclude 평가
2. review condition 평가
3. 모든 gate 통과 시에만 ELIGIBLE

`RELATED`나 follower 10,000 이상과 같은 hard exclude는
hair evidence 부족에 따른 REVIEW_REQUIRED보다 우선해야 한다.

## 테스트

`EligibilityPolicyTest`의 기존 golden fixture를 새 정책에 맞게 수정한다.

기존 정상 ELIGIBLE fixture가 새 hair gate 때문에 의도치 않게 review가 되지 않도록
정상 fixture에는 필요한 HAIR_TRANSPLANT evidence를 명시적으로 포함한다.

최소 다음 테스트를 반드시 추가하거나 명확하게 유지한다.

### 1. NOT_RELATED지만 hair evidence 없음

조건:

- DOCTOR
- follower < 10,000
- hairTransplantRelation = NOT_RELATED
- profession evidence 충분
- identity evidence 충분
- HAIR_TRANSPLANT evidence 없음

결과:

    REVIEW_REQUIRED

### 2. NOT_RELATED + strong hair evidence

조건:

- 다른 eligibility 조건 모두 충족
- `HAIR_TRANSPLANT + STRONG` evidence 1개

결과:

    ELIGIBLE

### 3. NOT_RELATED + independent weak hair evidence 2개

조건:

- 서로 다른 source URL
- `HAIR_TRANSPLANT + WEAK` evidence 2개
- 다른 eligibility 조건 모두 충족

결과:

    ELIGIBLE

### 4. NOT_RELATED + 같은 URL의 weak evidence 2개

결과:

    REVIEW_REQUIRED

### 5. NOT_RELATED + weak hair evidence 1개

결과:

    REVIEW_REQUIRED

### 6. UNKNOWN

충분한 HAIR_TRANSPLANT evidence가 존재하더라도
Candidate의 현재 relation이 UNKNOWN이면:

    REVIEW_REQUIRED

Candidate의 명시적 판정값을 evidence만으로 자동 변경하지 않는다.

### 7. RELATED

충분한 다른 evidence가 존재하더라도:

    INELIGIBLE

### 8. hard exclude precedence

예:

- follower 12,000
- hair NOT_RELATED
- hair evidence 없음

결과:

    INELIGIBLE

`REVIEW_REQUIRED`가 hard exclude를 덮지 않는다.

## UI

대규모 UI 변경을 하지 않는다.

현재 신규 후보 등록 화면의 안내문과 Candidate 상세 화면에서
새 정책을 이해하는 데 꼭 필요한 최소 문구만 보완할 수 있다.

예:

    NOT_RELATED 판정을 ELIGIBLE로 사용하려면
    모발이식 비관련성을 뒷받침하는 공개 evidence가 필요하다.

Evidence 추가 화면에는 기존 `HAIR_TRANSPLANT` type을 그대로 사용한다.

새 화면이나 JavaScript를 추가하지 않는다.

## DB

이번 작업에서는 DB schema 변경이 필요하지 않은 것이 기본 예상이다.

기존:

- `EvidenceType.HAIR_TRANSPLANT`
- `EvidenceStrength`
- sourceUrl

을 활용한다.

필요하지 않은 Flyway migration을 만들지 않는다.

## source of truth

이 정책은 장기적으로 중요한 eligibility 안전 규칙이다.

`docs/harness/PROJECT_CONTEXT.md`에 기존 모발이식 관련 Decision을
새 정책과 모순되지 않도록 최소 범위로 보강한다.

핵심은 다음이다.

- `NOT_RELATED` 값만으로 통과하지 않는다.
- strong HAIR_TRANSPLANT evidence 1개 또는 독립 weak evidence 2개가 필요하다.
- 미충족 시 REVIEW_REQUIRED이다.

새 Decision ID가 꼭 필요하지 않으면 기존
`DEC-20260817-hair-ambiguity-review`
내용을 보강하는 방식을 우선한다.

긴 구현 세부 내용은 PROJECT_CONTEXT에 넣지 않는다.

작업 종료 시 `docs/harness/HANDOFF.md`를 최신 상태로 갱신한다.

## 이번 작업에서 하지 않을 것

다음을 구현하지 않는다.

- Candidate edit
- Evidence edit/delete
- Content
- LLM
- comment/DM
- discovery
- Search API
- Meta API
- authentication
- approval
- interaction history
- cooldown
- scheduler
- 신규 entity
- 신규 DB table
- generic rule engine
- policy framework
- architecture refactor

현재 안전 조건 강화와 직접 관련 없는 수정은 하지 않는다.

## 성공 기준

다음이 모두 만족되어야 한다.

1. `NOT_RELATED`만 선택해서는 ELIGIBLE이 될 수 없다.
2. strong HAIR_TRANSPLANT evidence 1개면 해당 gate를 통과한다.
3. 독립 weak HAIR_TRANSPLANT evidence 2개면 해당 gate를 통과한다.
4. 같은 URL의 weak evidence 2개는 통과하지 못한다.
5. weak evidence 1개는 통과하지 못한다.
6. UNKNOWN은 REVIEW_REQUIRED이다.
7. RELATED는 INELIGIBLE이다.
8. hard exclude가 review보다 우선한다.
9. 기존 profession/identity/follower 정책이 깨지지 않는다.
10. golden tests가 새 규칙을 명확하게 표현한다.
11. 불필요한 schema 변경이나 abstraction을 추가하지 않는다.
12. PROJECT_CONTEXT와 HANDOFF가 실제 정책과 일치한다.

## 검증

가능한 환경에서는 최소 다음을 실행한다.

    docker compose up -d postgres
    docker compose ps
    ./mvnw test
    ./mvnw package
    git diff --check
    git status --short
    git diff --stat
    git diff

Codex sandbox 제약 때문에 Docker 또는 외부 dependency 접근이 불가능하면
실행 가능한 unit test를 최대한 수행하고 제한을 HANDOFF에 정확히 기록한다.

테스트를 통과시키기 위해 테스트를 약화시키지 않는다.

## clarification

현재 요구사항으로 구현 가능하므로 새로운 질문을 만들 필요는 없을 것으로 예상한다.

실제 P0 모호성이 발견된 경우에만 표준 clarification request를 만든다.

## 마지막 출력

짧게 다음을 출력한다.

- 변경한 eligibility 규칙
- 추가/수정한 golden test
- UI 문구 변경 여부
- DB migration 생성 여부
- test/package 결과
- source document 갱신 여부
- 남은 blocker 여부
