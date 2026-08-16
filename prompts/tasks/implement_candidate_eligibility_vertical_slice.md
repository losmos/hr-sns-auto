# Candidate → Evidence → Eligibility Thin Vertical Slice 구현

## 목표

`hr-sns-auto`의 첫 실제 업무 기능을 아주 얇은 end-to-end vertical slice로 구현한다.

이번 단계의 목적은 정식 제품 구조를 완성하는 것이 아니라 다음 핵심 가설을 빠르게 검증하는 것이다.

- 운영자가 Instagram 후보를 수동 입력할 수 있다.
- 후보의 공개 evidence를 기록할 수 있다.
- 핵심 hard exclude와 evidence 조건에 따라 deterministic eligibility 판정을 할 수 있다.
- 운영자가 브라우저에서 후보 목록과 상세 판정 근거를 확인할 수 있다.

현재 저장소에는 Spring Boot baseline이 이미 생성되어 있고 테스트와 PostgreSQL 연결이 확인된 상태이다.

## 먼저 읽을 문서

`AGENTS.md`의 읽기 순서를 따른다.

반드시 먼저 읽는다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

필요한 경우에만 다음을 확인한다.

- `agent_outputs/reports/mvp_implementation_plan.md`
- `agent_outputs/reports/instagram_medical_outreach_system_design.md`
- 기존 Flyway migration
- `pom.xml`
- `compose.yaml`
- `src/`

## 최신 사용자 방향

기존 문서보다 다음 최신 방향을 우선한다.

### 구현 전략 변경

사용자는 정식 제품 구조를 먼저 크게 만드는 것보다 실제 핵심 기능이 유용한지 빠르게 확인하기를 원한다.

따라서 기존 상세 roadmap을 버리는 것은 아니지만 현재는 `Thin Vertical Slice`를 우선한다.

첫 기능 검증 범위는:

    Candidate 수동 입력
        -> Evidence 입력
        -> deterministic Eligibility 판정
        -> ELIGIBLE / REVIEW_REQUIRED / INELIGIBLE
        -> 후보 목록 / 상세 UI

이다.

### 현재 확정 기술 스택

실제 저장소에 다음 baseline이 생성되어 정상 동작을 확인했다.

- Java 21
- Spring Boot 4.1.0
- Spring MVC
- Thymeleaf
- Spring Data JPA
- PostgreSQL 18.4
- Flyway
- Docker Compose
- Maven Wrapper

이 기술 스택은 현재 프로젝트의 확정 기술 스택으로 취급한다.

baseline에서 확인된 사항:

- `./mvnw test` 성공
- PostgreSQL Docker container health 정상
- Spring Boot와 PostgreSQL 연결 성공
- Flyway migration 성공
- JPA 초기화 성공

### 인증 정책 변경

기존 문서의 operator 인증 방식은 이번 local thin-slice 구현의 `P0 Blocker`로 취급하지 않는다.

현재 thin slice는 로컬 기능 가치 검증을 목적으로 하므로 Spring Security와 로그인 기능을 구현하지 않는다.

인증은 외부 네트워크 배포 또는 실제 운영 전 반드시 해결해야 하는 후속 항목으로 남긴다.

shared anonymous production access를 허용한다는 결정이 아니다.

## 이번 작업에서 구현할 것

### 1. Candidate

최소한 다음 정보를 저장한다.

- id
- instagramUsername
- displayName
- profession
- specialty
- followerCount
- hairTransplantRelation
- eligibilityStatus
- eligibilityReason
- createdAt
- updatedAt

`instagramUsername`은 중복될 수 없다.

### Profession

최소 enum:

- DOCTOR
- PHARMACIST
- KOREAN_MEDICINE
- OTHER
- UNKNOWN

### HairTransplantRelation

최소 enum:

- NOT_RELATED
- RELATED
- UNKNOWN

의미:

- `RELATED`는 모발이식 관련성이 확인된 상태이다.
- `NOT_RELATED`는 운영자가 확보한 공개 evidence를 바탕으로 다른 의료 섹터임을 확인한 상태이다.
- `UNKNOWN`은 충분히 확인하지 못한 상태이다.

단순히 키워드가 없다는 이유만으로 자동 `NOT_RELATED`로 추론하지 않는다.

### EligibilityStatus

- ELIGIBLE
- INELIGIBLE
- REVIEW_REQUIRED

## 2. CandidateEvidence

최소한 다음을 저장한다.

- id
- candidateId
- type
- strength
- sourceUrl
- summary
- observedAt
- createdAt

### EvidenceType

최소 enum:

- PROFESSION
- IDENTITY
- HAIR_TRANSPLANT
- OTHER

### EvidenceStrength

- STRONG
- WEAK

`sourceUrl`은 운영자가 실제 공개 근거를 열어볼 수 있도록 저장한다.

이번 단계에서는 자동 scraping이나 URL 내용 자동 fetch를 구현하지 않는다.

## 3. deterministic EligibilityPolicy

LLM을 사용하지 않는다.

Eligibility 판정은 독립 service 또는 policy class로 구현하고 충분한 단위 테스트를 작성한다.

### Hard exclude

다음은 `INELIGIBLE`이다.

1. `profession == KOREAN_MEDICINE`
2. `profession == OTHER`
3. followerCount가 존재하고 `>= 10000`
4. `hairTransplantRelation == RELATED`

### Review

hard exclude가 없는 상태에서 다음 중 하나라도 만족하면 `REVIEW_REQUIRED`이다.

1. profession이 `UNKNOWN`
2. followerCount가 null
3. `hairTransplantRelation == UNKNOWN`
4. profession / identity 공개 근거가 최소 기준을 충족하지 못함

### Profession / identity evidence 최소 기준

Instagram bio/category만으로 통과시켜서는 안 된다는 기존 결정에 맞춰 다음 단순 규칙을 MVP에 적용한다.

통과 조건 A:

- `PROFESSION + STRONG` evidence 1개 이상
- `IDENTITY` evidence 1개 이상

또는 통과 조건 B:

- 서로 다른 `sourceUrl`을 가진 `PROFESSION + WEAK` evidence 2개 이상
- `IDENTITY` evidence 1개 이상

위 조건을 충족하지 못하면 `REVIEW_REQUIRED`이다.

`sourceUrl`이 같은 weak evidence 두 개를 독립 source 두 개로 계산하지 않는다.

### Eligible

모든 hard exclude가 없고 모든 review condition도 없을 때만 `ELIGIBLE`이다.

판정은 fail-closed로 구현한다.

불명확하거나 데이터가 부족하면 `ELIGIBLE`로 추정하지 않는다.

## 4. 판정 갱신

Candidate 생성 또는 관련 Candidate/Evidence 변경 후 eligibility를 다시 계산할 수 있어야 한다.

구현은 단순성을 우선한다.

다음 중 가장 단순하고 명확한 방식을 선택한다.

- candidate 저장 후 명시적인 `reassess`
- evidence 추가 후 service에서 자동 reassess

과도한 event architecture를 만들지 않는다.

현재 판정 결과와 사람이 읽을 수 있는 짧은 이유를 Candidate에 저장한다.

예:

    모발이식 관련성이 확인되어 제외됨
    follower 10,000 이상
    profession strong evidence가 없음
    identity evidence가 없음
    필수 검증 조건을 모두 충족함

## 5. DB migration

기존 baseline 이후 새 Flyway migration으로 만든다.

최소 테이블:

- candidates
- candidate_evidence

DB constraint를 사용한다.

최소 고려:

- unique instagram username
- not null이 필요한 enum/status
- follower count 음수 방지
- foreign key
- evidence candidate index

Hibernate `ddl-auto=validate` 정책을 유지한다.

JPA가 schema를 자동 생성하도록 바꾸지 않는다.

## 6. 최소 Thymeleaf UI

UI 디자인에 시간을 과하게 쓰지 않는다.

최소 화면만 만든다.

### 후보 목록

예:

    @doctor_a | 홍길동 | DOCTOR | 5,240 | ELIGIBLE
    @doctor_b | 김약사 | PHARMACIST | 8,120 | REVIEW_REQUIRED

필수:

- 후보 리스트
- status 확인
- 신규 후보 등록 링크
- 상세 링크

### 신규 후보 등록

수동으로 Candidate 기본 정보를 입력한다.

Validation 실패 시 사람이 이해할 수 있는 메시지를 보여준다.

### 후보 상세

최소한 다음을 한 화면에서 본다.

- candidate 기본 정보
- 현재 eligibility status
- eligibility reason
- evidence 목록
- evidence 추가 form

Evidence 추가 후 최신 eligibility가 반영되어야 한다.

별도의 SPA나 JavaScript framework를 추가하지 않는다.

## 7. 테스트

이번 작업에서 가장 중요한 부분이다.

### EligibilityPolicy unit test

최소 다음 golden fixture를 검증한다.

#### 정상 의사

- DOCTOR
- follower 5,000
- hair NOT_RELATED
- strong profession evidence
- identity evidence

결과:

    ELIGIBLE

#### 정상 약사

- PHARMACIST
- follower 8,500
- hair NOT_RELATED
- strong profession evidence
- identity evidence

결과:

    ELIGIBLE

#### 한의사

결과:

    INELIGIBLE

#### 모발이식 관련

결과:

    INELIGIBLE

#### 모발이식 불명확

결과:

    REVIEW_REQUIRED

#### follower 10,000

결과:

    INELIGIBLE

#### follower 9,999

충분한 다른 evidence가 있으면:

    ELIGIBLE

#### follower null

결과:

    REVIEW_REQUIRED

#### strong profession evidence 없음

결과:

    REVIEW_REQUIRED

#### strong 대신 독립 weak profession evidence 두 개 + identity

결과:

    ELIGIBLE

#### 같은 source URL weak profession evidence 두 개

결과:

    REVIEW_REQUIRED

#### identity evidence 없음

결과:

    REVIEW_REQUIRED

가능하면 hard-exclude precedence도 검증한다.

예:

- evidence가 부족하지만 follower 12,000이면 `REVIEW_REQUIRED`가 아니라 `INELIGIBLE`

### Web / persistence

과도한 테스트 구조를 만들지 않되 최소한:

- Candidate 저장
- username unique
- Evidence relation
- Candidate form validation

의 핵심 실패가 잡히도록 필요한 테스트를 추가한다.

## 이번 작업에서 하지 않을 것

다음은 구현하지 않는다.

- Instagram 자동 candidate discovery
- Instagram scraping
- Search API
- Meta API
- LLM 호출
- comment 생성
- DM 생성
- Content entity
- Approval
- InteractionHistory
- cooldown
- scheduler
- Spring Security
- 로그인
- operator role
- Playwright / Selenium
- REST API
- React / Next.js
- microservice
- Kafka / message queue
- generic provider abstraction
- 복잡한 DDD / hexagonal architecture

현재 목적에 필요하지 않은 미래 구조를 미리 만들지 않는다.

## package 구조

현재 기능에 필요한 만큼만 단순하게 구성한다.

예:

    candidate/
        Candidate
        CandidateRepository
        CandidateService
        CandidateController
        CandidateEvidence
        EligibilityPolicy

실제 역할이 분리될 필요가 있으면 작은 하위 package를 사용할 수 있지만 미래 기능을 예상해 과도하게 나누지 않는다.

사람이 읽기 쉬운 naming을 사용한다.

비즈니스 규칙과 fail-closed 판단에는 이유를 알 수 있는 주석을 남긴다.

## 하네스 문서 현실화

이번 구현과 함께 현재 실제 repository 상태에 맞게 source 문서를 갱신한다.

### AGENTS.md

`Commands`를 실제 프로젝트 명령으로 갱신한다.

최소:

- 테스트: PostgreSQL 실행 후 `./mvnw test`
- 빌드: PostgreSQL 실행 후 `./mvnw package`
- 실행: PostgreSQL 실행 후 `./mvnw spring-boot:run`

전용 formatter/linter/static analysis tool이 아직 없다면 없는 것을 명확하게 기록하고 임의 dependency를 추가하지 않는다.

기본 8080이 이미 다른 local container에서 사용될 수 있다는 것은 프로젝트 전역 사실로 단정하지 않는다.

필요하면 로컬 실행 시:

    SERVER_PORT=18080 ./mvnw spring-boot:run

처럼 override할 수 있음을 짧게 기록할 수 있다.

### PROJECT_CONTEXT.md

실제 상태를 반영한다.

- 애플리케이션 skeleton이 존재함
- 확정 기술 스택
- baseline test 성공
- PostgreSQL/Flyway/JPA 연결 확인
- 인증은 local thin-slice blocker가 아님
- 인증은 외부 배포 / 실제 운영 전에 필요한 후속 결정
- 현재 우선순위는 thin vertical slice로 업무 가치 검증

기존의 다음 stale 내용을 제거 또는 교정한다.

- 애플리케이션 코드 없음
- 기술 스택 미확정
- 인증이 현재 local 구현 P0 blocker

긴 구현 내용을 PROJECT_CONTEXT에 복사하지 않는다.

### HANDOFF.md

이번 작업의 실제 delta와 다음 추천 작업을 갱신한다.

다음 추천 작업은 이번 vertical slice 결과를 실제 사용 관점에서 검증하는 방향을 우선한다.

## 성공 기준

다음이 모두 충족되어야 한다.

1. `docker compose up -d postgres`로 PostgreSQL이 healthy가 된다.
2. Flyway migration이 정상 적용된다.
3. `./mvnw test`가 성공한다.
4. `./mvnw package`가 성공한다.
5. golden eligibility test가 모두 존재하고 통과한다.
6. Candidate를 수동 등록할 수 있다.
7. Evidence를 수동 추가할 수 있다.
8. Evidence 변경 후 eligibility를 다시 판정할 수 있다.
9. 한의사와 모발이식 관련 후보는 `INELIGIBLE`이다.
10. 모발이식 불명확 후보는 `REVIEW_REQUIRED`이다.
11. follower 10,000은 `INELIGIBLE`, 9,999는 다른 조건 충족 시 통과 가능하다.
12. evidence 부족 후보는 `ELIGIBLE`로 fail-open하지 않는다.
13. 후보 목록과 상세 화면에서 판정 결과와 이유를 확인할 수 있다.
14. 범위 밖 기능을 구현하지 않는다.
15. source documents가 실제 repository 상태와 일치한다.

## 작업 후 검증

최소 다음을 실행한다.

    docker compose up -d postgres
    docker compose ps
    ./mvnw test
    ./mvnw package
    git diff --check
    git status --short
    git diff --stat
    git diff

가능하면 application을 임시 포트로 실행해 다음을 확인한다.

- 후보 목록 page HTTP 응답
- 신규 후보 form HTTP 응답

기존 8080 사용 여부를 확인하고 충돌 시 `SERVER_PORT=18080` 등 임시 포트를 사용한다.

검증을 위해 background process를 띄웠으면 작업 종료 전에 종료한다.

## clarification

현재 범위에서 P0 질문 없이 구현 가능하면 질문지를 새로 만들지 않는다.

실제 구현을 막는 새로운 모호성이 발견되면 추측하지 말고 `AGENTS.md`의 표준 형식으로 clarification request를 생성한다.

P1/P2가 생겨도 현재 구현 가능한 범위를 완료하고 질문을 별도 기록한다.

## 마지막 출력

짧게 다음을 출력한다.

- 구현한 기능
- 변경 파일 요약
- test/build 결과
- UI 확인 결과
- source document 갱신 여부
- 새 clarification request 여부
- 다음 추천 작업 한 줄
