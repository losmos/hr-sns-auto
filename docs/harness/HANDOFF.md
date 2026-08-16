# Handoff

## 마지막 갱신일

- 2026-08-17 01:56:29 KST

# 중단기 작업 기억

## 이번 범위

- Candidate 수동 입력 → Evidence 수동 입력 → deterministic Eligibility 판정 → 후보 목록·상세 UI의 첫 thin vertical slice를 구현했다.
- Candidate와 CandidateEvidence용 V2 Flyway migration, JPA entity·repository, 동기식 reassessment service를 추가했다.
- hard exclude 우선순위와 profession·identity evidence 최소 기준을 golden fixture로 고정했다.
- `AGENTS.md` Commands와 `PROJECT_CONTEXT.md`의 기술 스택·인증·현재 구현 상태를 실제 저장소에 맞게 교정했다.

## 현재 상태

- `/candidates`에서 후보 목록을 보고 `/candidates/new`에서 후보를 등록할 수 있다.
- `/candidates/{id}`에서 기본 정보, 현재 판정과 사유, evidence를 확인하고 evidence를 추가하거나 명시적으로 재판정할 수 있다.
- 후보 생성은 evidence 부족 상태를 `REVIEW_REQUIRED`로 저장하고, evidence 추가는 같은 service 흐름에서 자동 재판정한다.
- policy, service, MVC·Thymeleaf의 DB 비의존 테스트 19개가 통과한다.
- PostgreSQL이 필요한 context/persistence 테스트 4개는 현재 실행 환경의 Docker socket 접근 제한 때문에 검증하지 못했다.

## 검증 환경 제약

- `docker compose up -d postgres`는 `/var/run/docker.sock` 접근 권한 거부로 실행되지 않았다.
- localhost 5432에도 PostgreSQL이 열려 있지 않아 전체 `./mvnw test`는 DB 연결 단계에서만 실패했다.
- `./mvnw package`는 기본 Maven cache가 read-only이고 필요한 `maven-jar-plugin:3.5.0`이 미캐시 상태라 실패했다. writable 임시 cache로도 외부 DNS가 차단돼 plugin을 받을 수 없었다.
- Docker와 Maven Central 접근이 가능한 환경에서 전체 test/package와 live UI smoke를 다시 실행해야 한다.

# 직전 작업 기억

## PROJECT_CONTEXT 반영 여부

- 반영했다. 애플리케이션 코드 없음, 기술 스택 미확정, local 인증 P0 blocker라는 stale 내용을 제거했다.
- 확정 기술 스택, thin-slice 우선순위, local 인증 유예와 외부 배포 전 인증 필요성을 결정 사항에 기록했다.

## 직전 작업 delta

- `src/main/resources/db/migration/V2__create_candidate_and_evidence.sql`: candidates와 candidate_evidence 테이블, enum check, username unique·lowercase, follower 음수 방지, FK, index를 추가했다.
- `src/main/java/com/losmos/hrsnsauto/candidate/`: domain, enum, repository, fail-closed policy, form validation, service, MVC controller를 추가했다.
- `src/main/resources/templates/candidates/`, `src/main/resources/static/css/app.css`: 목록, 신규 등록, 상세·evidence 입력 UI를 추가했다.
- `src/test/java/com/losmos/hrsnsauto/candidate/`: golden policy 14개와 service, persistence, MVC·form validation 테스트를 추가했다.
- `src/test/resources/mockito-extensions/`: 현재 JVM에서 test mock을 안정적으로 생성하기 위한 test-scope mock maker 설정을 추가했다.
- `AGENTS.md`, `docs/harness/PROJECT_CONTEXT.md`, `docs/harness/HANDOFF.md`: 실행 명령과 source of truth를 현실화했다.

## 마지막 작업 요약

- 자동 discovery, scraping, LLM, outreach, 인증을 추가하지 않고 첫 핵심 업무 가설만 브라우저 기반 수직 흐름으로 연결했다.

## 변경 파일

- `AGENTS.md`
- `docs/harness/PROJECT_CONTEXT.md`
- `docs/harness/HANDOFF.md`
- `src/main/java/com/losmos/hrsnsauto/candidate/`
- `src/main/resources/db/migration/V2__create_candidate_and_evidence.sql`
- `src/main/resources/templates/candidates/`
- `src/main/resources/static/css/app.css`
- `src/test/java/com/losmos/hrsnsauto/candidate/`
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

## 생성 산출물

- 새 report나 clarification request를 만들지 않았다.
- 작업 시작 전 존재한 미추적 `prompts/tasks/implement_candidate_eligibility_vertical_slice.md`는 수정하지 않았다.

## 다음 추천 작업 상세

1. Docker 접근과 Maven Central 접근이 가능한 환경에서 `docker compose up -d postgres`, `docker compose ps`, `./mvnw test`, `./mvnw package`를 실행한다.
2. 임시 포트로 애플리케이션을 띄워 후보 등록, strong profession·identity evidence 추가, `REVIEW_REQUIRED` → `ELIGIBLE` 전환을 브라우저에서 확인한다.
3. 실제 운영자 샘플 후보를 소수 입력해 입력 편의성, 판정 사유의 이해 가능성, false positive·false negative를 기록한다.
4. 실제 사용에서 확인된 문제를 기준으로 Candidate/Evidence 수정 기능 등 다음 한 개의 작은 slice를 선택한다.

## 이전 추천 작업과의 관계

- 이전 Handoff는 기술 스택과 인증 답변 후 Phase 1 구현을 추천했다. 최신 사용자 요청이 기술 스택을 확정하고 local 인증을 blocker에서 제외했으므로 바로 thin vertical slice를 구현했다.
- 이전에 추천한 Search API·Meta spike와 generation·approval 후속 기능은 최신 범위 밖이므로 수행하지 않았다.

## 검증 상태

- `./mvnw -DskipTests compile`: 성공했다.
- `./mvnw -Dtest=EligibilityPolicyTest,CandidateServiceTest,CandidateControllerTest test`: DB 비의존 정책·service·MVC 테스트가 통과했다.
- 전체 `./mvnw test`: 총 23개 중 19개 통과, PostgreSQL 연결이 필요한 4개만 오류가 발생했다.
- `./mvnw package`: 필요한 Maven plugin을 현재 read-only/offline 환경에서 resolve할 수 없어 테스트 실행 전 실패했다.
- MockMvc로 후보 목록, 신규 후보 form, 후보 상세와 eligibility 사유·evidence form 렌더링을 확인했다.
- `git diff --check`, trailing whitespace 검색, 핵심 정책·DB constraint `rg` 검증이 통과했다.
- live application HTTP smoke는 PostgreSQL을 시작할 수 없어 수행하지 못했다.

## 사용 에이전트

- Codex를 사용했다.

## 주의할 점

- local thin slice에 인증이 없으므로 외부 네트워크에 노출하지 않는다.
- Evidence URL은 자동 fetch하지 않으며 운영자가 공개 근거를 직접 확인해 입력한다.
- Hibernate `ddl-auto=validate`를 유지하므로 새 환경에서 V2 Flyway migration 성공을 반드시 확인한다.
