# Handoff

## 마지막 갱신일

- 2026-08-17 02:34:44 KST

# 중단기 작업 기억

## 이번 범위

- `HAIR_TRANSPLANT` evidence가 모발이식 비관련·관련·불충분 중 어느 방향을 지지하는지 구조적으로 저장하도록 최소 범위로 확장했다.
- `NOT_RELATED` gate가 비관련 방향 evidence만 집계하도록 바꾸고, 관련 방향 evidence와 Candidate 판정이 충돌하면 fail-closed review로 보낸다.
- 기존 evidence를 안전하게 이관하는 Flyway V3와 type/finding DB CHECK invariant를 추가했다.
- evidence 입력·목록 UI와 폼 오류를 운영자가 방향의 의미를 이해할 수 있게 보강했다.

## 현재 상태

- `HairTransplantEvidenceFinding`은 `SUPPORTS_NOT_RELATED`, `SUPPORTS_RELATED`, `INCONCLUSIVE` 세 값을 사용한다.
- `CandidateEvidence`는 `HAIR_TRANSPLANT`이면 finding이 필수이고 다른 type이면 finding이 null이어야 한다. 생성 시 검증과 DB CHECK가 같은 invariant를 방어한다.
- `NOT_RELATED`의 strong 1개 또는 독립 weak URL 2개 gate에는 `SUPPORTS_NOT_RELATED` evidence만 포함한다.
- `SUPPORTS_RELATED` evidence가 하나라도 `NOT_RELATED`와 공존하면 strength와 관계없이 `REVIEW_REQUIRED`이다.
- `INCONCLUSIVE`, `SUPPORTS_RELATED`, finding 없음은 비관련성 통과 근거로 계산하지 않는다.
- `RELATED` hard exclude가 review보다 우선하고 `UNKNOWN`은 evidence와 관계없이 `REVIEW_REQUIRED`인 기존 우선순위를 유지한다.
- profession·identity·follower 정책과 URL의 `trim().distinct()` 독립성 계산은 유지한다.

## Migration과 기존 저장 상태

- `V3__add_hair_transplant_evidence_finding.sql`은 nullable column을 추가한 뒤 기존 `HAIR_TRANSPLANT` row를 `INCONCLUSIVE`로 이관하고 type/finding CHECK를 추가한다.
- 기존 evidence를 `SUPPORTS_NOT_RELATED`로 추측하지 않는다. non-hair row는 null을 유지한다.
- migration은 기존 Candidate의 저장 `eligibilityStatus`를 일괄 재계산하지 않는다. 기존 후보에는 evidence 추가 또는 명시적 재판정으로 최신 policy를 적용한다.
- Hibernate `ddl-auto=validate` 설정은 유지했다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다. 새 Decision ID 없이 기존 `DEC-20260817-hair-ambiguity-review`에 evidence 방향, 비관련 gate 집계 대상, 상충 review, 기존 row의 `INCONCLUSIVE` 이관 정책을 추가했다.

## 사용자 제공 변경 전 검증 사실

- 사용자의 실제 개발 환경에서 2026-08-17 최신 commit 기준 `./mvnw test` 27/27과 `./mvnw package`가 성공했다.
- PostgreSQL 18.4 연결, Flyway V1/V2 validation, schema version 2, JPA persistence, executable Spring Boot JAR을 확인했다.
- 이는 직전 Handoff의 PostgreSQL 전체 검증 미확인이 Codex sandbox 제한이었음을 구분해 확인한 사실이다.
- 위 결과는 이번 V3 변경 전 기준이며 이번 변경의 DB 검증 결과로 간주하지 않는다.

## 이번 작업 delta

- enum/entity/form/service: hair finding 저장과 type별 불변식, null 안전 기본값을 추가했다.
- Flyway V3: 기존 hair row의 `INCONCLUSIVE` backfill과 양방향 CHECK invariant를 추가했다.
- `EligibilityPolicy`: `SUPPORTS_NOT_RELATED` 전용 집계와 `SUPPORTS_RELATED` 상충 사유를 추가했다.
- Candidate 상세 UI: 세 finding의 한국어 의미, 선택 안내, 기존 evidence의 finding 표시를 추가했다.
- golden test: 방향별 strong·weak 판정, hard-exclude 우선순위, 폼 오류, enum persistence, DB CHECK를 보강했다.

## 검증 상태

- `docker compose up -d postgres`, `docker compose ps`: `/var/run/docker.sock` 권한 거부로 실패했다.
- `./mvnw -Dtest=CandidateEvidenceTest,EligibilityPolicyTest,CandidateServiceTest,CandidateControllerTest test`: DB 비의존 30개 모두 통과했다.
- `./mvnw test`: 총 37개 중 30개 통과, failures 0, PostgreSQL 연결이 필요한 application context 1개와 repository test 6개가 errors 7로 실패했다.
- `./mvnw package`: 동일한 PostgreSQL 연결 errors 7로 실패했다.
- `./mvnw -DskipTests package`: 실행 가능한 Spring Boot JAR 생성에 성공했다.
- `git diff --check`: 통과했다.
- 새 enum persistence, non-hair null persistence, DB CHECK 2개 test는 작성했으나 Codex sandbox에서는 PostgreSQL이 없어 실행하지 못했다.
- 따라서 Flyway V3 적용, schema version 3, Hibernate schema validation, 새 persistence·CHECK test는 PostgreSQL 접근 환경에서 추가 확인이 필요하다.

## 변경 파일

- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `src/main/java/com/losmos/hrsnsauto/candidate/HairTransplantEvidenceFinding.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/CandidateEvidence.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/EvidenceForm.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/CandidateService.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/CandidateController.java`
- `src/main/java/com/losmos/hrsnsauto/candidate/EligibilityPolicy.java`
- `src/main/resources/db/migration/V3__add_hair_transplant_evidence_finding.sql`
- `src/main/resources/templates/candidates/form.html`
- `src/main/resources/templates/candidates/detail.html`
- `src/test/java/com/losmos/hrsnsauto/candidate/CandidateEvidenceTest.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/EligibilityPolicyTest.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/CandidateServiceTest.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/CandidateControllerTest.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/CandidateRepositoryTest.java`

## 생성 산출물

- 새 migration과 새 enum·unit test 파일을 만들었다.
- 새 report와 clarification request는 만들지 않았다.
- 작업 시작 전 존재한 미추적 `prompts/tasks/add_hair_evidence_finding.md`는 수정하지 않았다.

## 다음 추천 작업 상세

1. Docker 접근이 가능한 실제 개발 환경에서 PostgreSQL 18.4를 시작하고 `./mvnw test`, `./mvnw package`를 실행해 Flyway schema version 3과 37개 전체 test를 확인한다.
2. 실제 저장 Candidate가 있다면 V3 적용 후 방향이 `INCONCLUSIVE`로 이관된 기존 evidence를 검토하고 evidence 추가 또는 명시적 재판정으로 최신 policy를 적용한다.
3. 운영자 샘플로 세 finding 입력·목록 표시·상충 판정 사유가 이해 가능한지 확인한다.

## 이전 추천 작업과의 관계

- 직전 Handoff의 PostgreSQL 전체 검증은 사용자가 제공한 실제 환경 결과로 V2 기준 해소됐다.
- 최신 사용자의 evidence 방향성 안전성 강화 요청을 우선했으며, 실제 운영자 샘플 확인은 이번 구현 범위 밖이므로 다음 추천 작업으로 유지했다.
- V3 이후 전체 DB 검증은 Codex sandbox 제한으로 새 미확인 항목이다.

## 사용 에이전트

- Codex를 사용했다.

## 주의할 점

- 기존 DB의 과거 `ELIGIBLE` 저장 상태는 V3만으로 자동 갱신되지 않는다.
- `SUPPORTS_RELATED` evidence는 Candidate relation을 자동으로 `RELATED`로 바꾸거나 자동 `INELIGIBLE`로 승격하지 않고 명시적 상충 review만 발생시킨다.
- local thin slice에 인증이 없으므로 외부 네트워크에 노출하지 않는다.
