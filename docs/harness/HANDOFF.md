# Handoff

## 마지막 갱신일

- 2026-08-17 02:15:19 KST

# 중단기 작업 기억

## 이번 범위

- Candidate eligibility의 모발이식 false negative 방지 gate를 최소 범위로 강화했다.
- `NOT_RELATED` 값만으로 통과시키지 않고 `HAIR_TRANSPLANT` strong evidence 1개 또는 서로 다른 source URL의 weak evidence 2개를 요구한다.
- 기존 hard exclude 우선순위와 profession·identity·follower 정책을 유지했다.
- 신규 후보 등록과 상세 evidence 입력 화면에 새 기준을 설명하는 최소 안내 문구를 추가했다.

## 현재 상태

- `RELATED`는 evidence 부족이나 상충 여부와 관계없이 `INELIGIBLE`이다.
- `UNKNOWN`은 충분한 hair evidence가 있어도 `REVIEW_REQUIRED`이다.
- `NOT_RELATED`는 hair evidence 최소 기준을 충족하지 못하면 `REVIEW_REQUIRED`이다.
- weak hair evidence는 앞뒤 공백을 제거한 source URL 기준으로 중복을 제거해 독립 source 수를 계산한다.
- profession·identity·follower gate를 포함한 모든 조건을 통과한 경우에만 `ELIGIBLE`이다.
- DB schema와 entity·enum은 변경하지 않았다.

## 검증 환경 제약

- `docker compose up -d postgres`와 `docker compose ps`는 `/var/run/docker.sock` 접근 권한 거부로 실패했다.
- PostgreSQL이 없어 전체 test와 기본 package는 기존 DB 의존 테스트 4개에서만 실패했다.
- DB 비의존 테스트와 테스트를 생략한 package는 현재 Maven cache로 정상 실행됐다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다. 기존 `DEC-20260817-hair-ambiguity-review`에 `NOT_RELATED` hair evidence 최소 기준과 미충족·`UNKNOWN`·`RELATED` 판정을 짧게 추가했다.

## 직전 작업 delta

- `EligibilityPolicy`: `NOT_RELATED` 전용 strong 1개 또는 독립 weak source 2개 gate와 사람이 이해할 수 있는 review 사유를 추가했다.
- `EligibilityPolicyTest`: 정상 fixture에 strong hair evidence를 넣고 근거 없음, strong 1개, 독립 weak 2개, 동일 URL weak 2개, weak 1개, `UNKNOWN`, `RELATED`, hard exclude 우선순위를 golden test로 고정했다.
- `CandidateServiceTest`: 자동 재판정 fixture가 새 hair gate까지 충족하도록 보완했다.
- `CandidateControllerTest`와 후보 등록·상세 template: 새 정책 안내 문구의 렌더링을 확인하도록 보완했다.
- `PROJECT_CONTEXT.md`: 기존 모발이식 ambiguity Decision을 새 정책과 일치시켰다.

## 마지막 작업 요약

- 새 schema, entity, enum, migration, rule framework 없이 기존 deterministic policy의 모발이식 gate만 fail-closed로 강화했다.

## 변경 파일

- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `src/main/java/com/losmos/hrsnsauto/candidate/EligibilityPolicy.java`
- `src/main/resources/templates/candidates/form.html`
- `src/main/resources/templates/candidates/detail.html`
- `src/test/java/com/losmos/hrsnsauto/candidate/EligibilityPolicyTest.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/CandidateServiceTest.java`
- `src/test/java/com/losmos/hrsnsauto/candidate/CandidateControllerTest.java`

## 생성 산출물

- 새 report, clarification request, DB migration을 만들지 않았다.
- 작업 시작 전 존재한 미추적 `prompts/tasks/strengthen_hair_transplant_eligibility_gate.md`는 수정하지 않았다.

## 다음 추천 작업 상세

1. Docker 접근이 가능한 환경에서 PostgreSQL을 시작하고 전체 `./mvnw test`와 `./mvnw package`를 다시 실행한다.
2. 기존 저장 후보가 있다면 현재 evidence로 명시적 재판정을 실행해 과거에 저장된 eligibility 상태를 새 gate에 맞춘다.
3. 운영자 샘플 후보로 strong 1개와 독립 weak 2개 입력 흐름 및 판정 사유의 이해 가능성을 확인한다.

## 이전 추천 작업과의 관계

- 이전 Handoff의 환경 재검증·실제 운영자 샘플 확인보다 최신 사용자의 hair eligibility 안전성 강화 요청을 우선했다.
- 전체 DB 검증은 Docker 권한 제한으로 남았고 실제 샘플 확인은 이번 구현 범위 밖이므로 다음 추천 작업으로 유지했다.

## 검증 상태

- 변경 전 신규 golden test로 근거 없음, weak 1개, 동일 URL weak 2개가 잘못 `ELIGIBLE`이 되는 실패를 재현했다.
- `./mvnw -Dtest=EligibilityPolicyTest test`: 18개 모두 통과했다.
- `./mvnw -Dtest=EligibilityPolicyTest,CandidateServiceTest,CandidateControllerTest test`: DB 비의존 23개 모두 통과했다.
- `./mvnw test`: 총 27개 중 23개 통과, PostgreSQL 연결이 필요한 기존 4개만 오류가 발생했다.
- `./mvnw package`: 같은 DB 의존 테스트 4개 오류로 package 단계 전에 실패했다.
- `./mvnw -DskipTests package`: 실행 가능한 Spring Boot JAR 생성에 성공했다.
- `git diff --check`: 통과했다.

## 사용 에이전트

- Codex를 사용했다.

## 주의할 점

- 기존 DB에 과거 정책으로 저장된 `ELIGIBLE` 후보가 있다면 policy 코드 변경만으로 저장 상태가 자동 갱신되지는 않는다. evidence 추가 또는 상세 화면의 재판정 동작으로 새 기준을 적용해야 한다.
- 현재 모델에는 evidence 방향성 field가 없으므로 운영자가 `hairTransplantRelation`, evidence type·strength·source URL·summary 조합으로 결론과 근거를 기록한다.
- local thin slice에 인증이 없으므로 외부 네트워크에 노출하지 않는다.
