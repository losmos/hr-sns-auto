# HAIR_TRANSPLANT evidence 방향성 명시 및 eligibility 안전성 강화

## 목표

현재 Candidate eligibility의 모발이식 false negative 방어를 한 단계 더 강화한다.

현재 `HAIR_TRANSPLANT` evidence에는 type, strength, source URL, summary만 있고
그 evidence가 실제로 다음 중 무엇을 지지하는지 구조적으로 표현하지 않는다.

- 모발이식과 관련 없음
- 모발이식과 관련 있음
- 결론 불충분

이 때문에 Candidate가 `NOT_RELATED`인 상태에서
실제로는 모발이식 관련성을 보여주는 evidence도
형식상 HAIR_TRANSPLANT evidence라는 이유만으로
비관련성 gate에 사용될 수 있다.

이번 작업에서는 이 논리적 모순을 fail-closed 방식으로 차단한다.

범위를 작게 유지한다.

## 먼저 읽을 문서

`AGENTS.md`의 읽기 순서를 따른다.

반드시 순서대로 읽는다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

그 후 이번 작업에 직접 관련된 파일만 읽는다.

최소:

- `src/main/java/com/losmos/hrsnsauto/candidate/CandidateEvidence.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/EvidenceForm.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/EligibilityPolicy.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/CandidateService.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/CandidateController.java`
- 관련 enum
- `V2__create_candidate_and_evidence.sql`
- candidate 관련 tests
- candidate detail template

repo 전체를 무작정 읽지 않는다.

## 최신 검증 사실

Codex sandbox에서는 PostgreSQL 접근이 제한됐지만
사용자의 실제 개발 환경에서 최신 commit 기준 다음을 확인했다.

    ./mvnw test
    ./mvnw package

결과:

- PostgreSQL 18.4 연결 성공
- Flyway V1/V2 validation 성공
- schema version 2 정상
- JPA persistence test 성공
- 총 27 tests
- Failures 0
- Errors 0
- Skipped 0
- `BUILD SUCCESS`
- executable Spring Boot JAR package 성공

따라서 HANDOFF의 Docker/PostgreSQL 미검증 상태는
이번 작업 종료 시 실제 최신 상태와 구분되도록 정리한다.

Codex 자체 환경에서 새 작업의 DB test를 실행하지 못하면
그 제한은 새 작업 검증 결과로 별도로 기록한다.

## 새 모델

HAIR_TRANSPLANT evidence에만 적용되는 enum을 추가한다.

이름은 코드 문맥에 가장 명확한 것을 사용하되
기본 제안은 다음이다.

    HairTransplantEvidenceFinding

값:

    SUPPORTS_NOT_RELATED
    SUPPORTS_RELATED
    INCONCLUSIVE

의미:

### SUPPORTS_NOT_RELATED

공개 evidence가 해당 후보가
모발이식/탈모수술/헤어라인 수술 등 프로젝트 hard-exclude 영역과
관련되지 않는다는 판단을 지지한다.

### SUPPORTS_RELATED

공개 evidence가 후보의 모발이식 관련성을 지지한다.

### INCONCLUSIVE

evidence가 존재하지만
모발이식 관련 여부를 안전하게 확정하기에는 부족하거나 모호하다.

## CandidateEvidence 변경

`CandidateEvidence`에 nullable field를 추가한다.

예:

    HairTransplantEvidenceFinding hairTransplantFinding

규칙:

- `type == HAIR_TRANSPLANT`
  - finding은 반드시 존재해야 한다.
- `type != HAIR_TRANSPLANT`
  - finding은 반드시 null이어야 한다.

PROFESSION, IDENTITY, OTHER evidence에
무의미한 hair finding이 저장되지 않게 한다.

범용 `EvidenceFinding` abstraction으로 확장하지 않는다.

현재 필요한 HAIR_TRANSPLANT semantics만 모델링한다.

## Flyway V3

새 migration을 만든다.

예상:

    V3__add_hair_transplant_evidence_finding.sql

`candidate_evidence`에 nullable column을 추가한다.

기존 데이터 migration은 반드시 fail-closed여야 한다.

기존:

    type = HAIR_TRANSPLANT

row에는:

    INCONCLUSIVE

를 설정한다.

기존 evidence를 자동으로 `SUPPORTS_NOT_RELATED`로 추측하지 않는다.

non-HAIR_TRANSPLANT row는 null을 유지한다.

DB CHECK constraint로 최소한 다음 invariant를 보장한다.

    type = HAIR_TRANSPLANT
        -> hair_transplant_finding IN (
             'SUPPORTS_NOT_RELATED',
             'SUPPORTS_RELATED',
             'INCONCLUSIVE'
           )

    type != HAIR_TRANSPLANT
        -> hair_transplant_finding IS NULL

migration은 기존 row가 존재해도 적용 가능해야 한다.

Hibernate `ddl-auto=validate`를 유지한다.

## EvidenceForm

운영자가 HAIR_TRANSPLANT evidence를 추가할 때 finding을 선택할 수 있게 한다.

필드:

    hairTransplantFinding

기본값을 `SUPPORTS_NOT_RELATED`로 자동 선택하지 않는다.

안전 기본값은 null 또는 INCONCLUSIVE 중
현재 form/Thymeleaf 구조에서 더 명확한 방법을 선택한다.

단, 운영자가 아무 생각 없이 form을 제출했을 때
`SUPPORTS_NOT_RELATED`가 자동 입력되는 구조는 금지한다.

validation:

### type == HAIR_TRANSPLANT

finding 필수

### type != HAIR_TRANSPLANT

finding은 null이어야 한다.

가능하면 form validation 단계에서 사람이 이해할 수 있는 오류를 보여준다.

예:

    HAIR_TRANSPLANT evidence는 finding을 선택한다.

    HAIR_TRANSPLANT 이외 evidence에는 hair finding을 지정하지 않는다.

과도한 custom validation framework를 만들지 않는다.

단순 class-level validation 또는 현재 코드 구조에 맞는 작은 validation을 사용한다.

## UI

Evidence 추가 form에 다음 field를 추가한다.

    모발이식 evidence 판단

선택값:

- 관련 없음 근거
- 관련 있음 근거
- 결론 불충분

영문 enum 그대로만 보여주기보다
사용자가 의미를 이해할 수 있는 간단한 설명을 함께 제공한다.

JavaScript framework를 추가하지 않는다.

JS 없이 항상 field를 표시해도 된다.
그 경우:

    HAIR_TRANSPLANT type에서만 선택한다.

라는 안내를 명확하게 표시한다.

기존 Evidence 목록에서도 HAIR_TRANSPLANT evidence라면
finding을 사람이 확인할 수 있게 표시한다.

## EligibilityPolicy 변경

### 기존 hard exclude

다음은 그대로 유지한다.

    candidate.hairTransplantRelation == RELATED
    -> INELIGIBLE

### UNKNOWN

다음도 그대로 유지한다.

    candidate.hairTransplantRelation == UNKNOWN
    -> REVIEW_REQUIRED

### NOT_RELATED gate

기존처럼:

- strong evidence 1개
또는
- independent weak evidence 2개

를 요구한다.

그러나 이제 집계 대상은 반드시:

    type == HAIR_TRANSPLANT
    AND finding == SUPPORTS_NOT_RELATED

인 evidence만 포함한다.

다음은 NOT_RELATED 통과 근거로 절대 계산하지 않는다.

    SUPPORTS_RELATED
    INCONCLUSIVE
    finding 없음

## 상충 evidence

Candidate가 `NOT_RELATED`인데
`SUPPORTS_RELATED` HAIR_TRANSPLANT evidence가 존재하면
silent하게 무시하지 않는다.

이번 thin slice에서는 evidence만으로 Candidate relation을
자동으로 `RELATED`로 변경하지 않는다.

다음처럼 fail-closed 처리한다.

    candidate relation = NOT_RELATED
    AND SUPPORTS_RELATED evidence 존재
    -> REVIEW_REQUIRED

사람이 이해할 수 있는 판정 사유를 추가한다.

예:

    모발이식 관련성을 지지하는 evidence가 있어 NOT_RELATED 판정과 상충함

`SUPPORTS_RELATED` evidence가 STRONG인지 WEAK인지에 따라
자동으로 INELIGIBLE로 승격하는 추가 rule은 이번 작업에서 만들지 않는다.

Candidate의 명시적 relation과 evidence의 충돌을
REVIEW_REQUIRED로 보내는 데 집중한다.

단:

    candidate.hairTransplantRelation == RELATED

이면 기존 hard exclude가 최우선이다.

## URL 독립성

이번 작업의 주목적은 evidence semantics이다.

새 URL canonicalization framework를 만들지 않는다.

다만 기존 `.trim().distinct()` 동작을 유지하며
별도 TODO framework나 URL resolver를 추가하지 않는다.

URL independence의 고도화는 실제 sample 사용 후 별도 slice로 판단한다.

## 테스트

기존 golden tests를 유지하면서
새 finding semantics를 명시적으로 검증한다.

최소 다음 테스트를 추가 또는 수정한다.

### 1. 정상 NOT_RELATED

- Candidate = NOT_RELATED
- HAIR_TRANSPLANT
- STRONG
- SUPPORTS_NOT_RELATED
- profession/identity/follower 조건 충족

결과:

    ELIGIBLE

### 2. 같은 strong evidence지만 SUPPORTS_RELATED

Candidate:

    NOT_RELATED

Evidence:

    HAIR_TRANSPLANT
    STRONG
    SUPPORTS_RELATED

결과:

    REVIEW_REQUIRED

판정 reason에 상충 evidence 의미가 드러나야 한다.

### 3. INCONCLUSIVE

Candidate:

    NOT_RELATED

Evidence:

    HAIR_TRANSPLANT
    STRONG
    INCONCLUSIVE

결과:

    REVIEW_REQUIRED

STRONG이라고 해서 통과하면 안 된다.

### 4. weak 2개

서로 다른 URL의:

    WEAK + SUPPORTS_NOT_RELATED
    WEAK + SUPPORTS_NOT_RELATED

결과:

    ELIGIBLE

### 5. weak 방향 혼합

    WEAK + SUPPORTS_NOT_RELATED
    WEAK + SUPPORTS_RELATED

결과:

    REVIEW_REQUIRED

두 개의 HAIR_TRANSPLANT evidence라는 이유만으로 통과하면 안 된다.

### 6. RELATED candidate

Candidate:

    RELATED

어떤 finding evidence가 있어도:

    INELIGIBLE

hard exclude가 우선한다.

### 7. UNKNOWN candidate

Candidate:

    UNKNOWN

SUPPORTS_NOT_RELATED strong evidence가 있어도:

    REVIEW_REQUIRED

### 8. Form validation

HAIR_TRANSPLANT evidence인데 finding이 없으면 validation error.

### 9. non-hair validation

PROFESSION evidence 등에 hair finding을 함께 제출하면 validation error
또는 안전하게 거부되어야 한다.

### 10. Persistence

PostgreSQL 사용 가능 환경에서는:

- HAIR_TRANSPLANT + finding 저장
- 기존 enum mapping
- non-hair + null finding

을 확인한다.

가능하면 DB CHECK invariant도 테스트한다.

테스트를 통과시키기 위해 validation 또는 DB constraint를 약화하지 않는다.

## 기존 데이터

V3 migration으로 기존 HAIR_TRANSPLANT evidence가
`INCONCLUSIVE`가 되는 것을 source 문서에 짧게 기록한다.

기존 Candidate의 저장 eligibilityStatus는
migration만으로 일괄 재계산하지 않는다.

이유:

- eligibility는 application policy 결과이다.
- SQL migration에서 application policy 전체를 복제하지 않는다.

기존 저장 Candidate가 실제로 존재할 경우
evidence 추가 또는 명시적 reassess를 통해 최신 정책을 적용한다.

현재 개발 DB에 테스트 외 실제 후보가 없더라도
migration은 데이터가 존재하는 경우를 안전하게 처리해야 한다.

## PROJECT_CONTEXT

기존:

    DEC-20260817-hair-ambiguity-review

를 보강한다.

장기 source of truth에는 구현 세부 코드가 아니라
다음 정책만 짧게 남긴다.

- HAIR_TRANSPLANT evidence는 관련/비관련/불충분 방향을 명시한다.
- NOT_RELATED gate에는 SUPPORTS_NOT_RELATED evidence만 사용한다.
- NOT_RELATED와 SUPPORTS_RELATED evidence가 상충하면 REVIEW_REQUIRED이다.
- 기존 evidence migration은 INCONCLUSIVE로 fail-closed 처리한다.

필요하지 않으면 새 Decision ID를 만들지 않는다.

## HANDOFF

작업 결과와 검증 상태를 최신화한다.

특히 직전 Handoff의:

    PostgreSQL 전체 test/package 미확인

기록은 사용자의 실제 개발 환경에서
2026-08-17에 다음이 성공했다는 최신 사실을 구분해서 반영한다.

- `./mvnw test`: 27/27 성공
- `./mvnw package`: 성공
- PostgreSQL 18.4
- Flyway schema version 2

이번 V3 이후 Codex sandbox에서 전체 검증이 불가능하면
그것은 이번 변경의 미검증 항목으로 별도 기록한다.

## 이번 작업에서 하지 않을 것

- Candidate edit
- Evidence edit/delete
- 자동 Candidate discovery
- Search API
- Meta API
- Content
- LLM
- comment/DM
- approval
- interaction history
- cooldown
- authentication
- scheduler
- generic Evidence abstraction
- generic rule engine
- URL crawler
- URL semantic equivalence system
- architecture refactor

## 성공 기준

1. HAIR_TRANSPLANT evidence의 방향성이 구조화되어 저장된다.
2. SUPPORTS_NOT_RELATED만 NOT_RELATED pass evidence로 계산된다.
3. SUPPORTS_RELATED는 NOT_RELATED 통과 근거로 계산되지 않는다.
4. NOT_RELATED와 SUPPORTS_RELATED가 공존하면 REVIEW_REQUIRED이다.
5. INCONCLUSIVE는 strong이어도 통과 근거가 아니다.
6. 기존 strong 1 / weak independent 2 정책이 유지된다.
7. RELATED hard exclude가 유지된다.
8. UNKNOWN review가 유지된다.
9. 기존 HAIR_TRANSPLANT rows는 V3에서 INCONCLUSIVE가 된다.
10. non-hair evidence에는 hair finding이 저장되지 않는다.
11. form에서 finding 의미를 사람이 이해할 수 있다.
12. DB invariant가 보장된다.
13. 기존 profession/identity/follower golden tests가 깨지지 않는다.
14. 범위 밖 기능을 추가하지 않는다.
15. PROJECT_CONTEXT와 HANDOFF가 실제 정책과 일치한다.

## 검증

가능한 환경에서:

    docker compose up -d postgres
    docker compose ps
    ./mvnw test
    ./mvnw package
    git diff --check
    git status --short
    git diff --stat
    git diff

Codex sandbox에서 PostgreSQL 접근이 안 되면
DB 비의존 test를 최대한 실행하고
그 제한을 정확히 HANDOFF에 기록한다.

## clarification

현재 요구사항으로 구현 가능하므로
새 clarification request는 기본적으로 만들지 않는다.

실제 P0 모호성이 발견된 경우에만
AGENTS.md 표준 형식을 따른다.

## 마지막 출력

짧게 다음을 출력한다.

- 추가한 finding 모델
- migration 전략
- 변경한 eligibility 규칙
- golden test 결과
- UI 변경
- 전체 test/package 결과 또는 환경 제한
- source document 갱신 여부
- 새 clarification 여부
