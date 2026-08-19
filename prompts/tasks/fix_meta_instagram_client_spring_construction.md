# MetaInstagramClient Spring bean 생성 실패 수정

## 작업 시작 규칙

루트 `AGENTS.md`를 따른다.

먼저 아래 순서로 읽는다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

그 다음 이번 오류와 직접 관련된 파일만 읽는다.

특히 다음을 확인한다.

- `src/main/java/.../discovery/MetaInstagramClient.java`
- `src/main/java/.../discovery/DiscoveryService.java`
- MetaInstagramClient 관련 configuration이 있다면 해당 파일
- `src/test/.../discovery/MetaInstagramClientTest.java`
- `src/test/.../HrSnsAutoApplicationTests.java`

현재 working tree에는 직전 Discovery Inbox 구현 변경이 존재하므로 이를 유지한다.

이번 오류와 무관한 리팩토링, 구조 변경, 기능 추가는 하지 않는다.

## 실제 사용자 환경 실패 결과

PostgreSQL 18.4가 정상 실행 중이며 Flyway V4 migration도 정상 적용됐다.

신규 Discovery 관련 테스트 대부분은 통과했지만 전체 `./mvnw test`에서 ApplicationContext 생성 오류 1건이 발생했다.

핵심 오류:

```text
UnsatisfiedDependencyException:
Error creating bean with name 'discoveryController'
...
Error creating bean with name 'discoveryService'
...
Error creating bean with name 'metaInstagramClient'
...
Failed to instantiate [com.losmos.hrsnsauto.discovery.MetaInstagramClient]:
No default constructor found

Caused by:
java.lang.NoSuchMethodException:
com.losmos.hrsnsauto.discovery.MetaInstagramClient.<init>()
```

실패 테스트:

```text
HrSnsAutoApplicationTests.contextLoads()
```

반면 아래 신규 테스트는 통과했다.

- `DiscoveryHashtagServiceTest`: 4
- `DiscoveryControllerTest`: 6
- `DiscoveryPersistenceTest`: 3
- `MetaInstagramClientTest`: 5

Flyway V4:

```text
Migrating schema "public" to version "4 - create instagram discovery inbox"
Successfully applied 1 migration
```

따라서 Discovery Inbox 전체를 재작성하지 말고
`MetaInstagramClient`의 Spring production bean construction 문제를 우선 해결한다.

## 목표

Spring Boot 4.1.0 / 현재 Spring Framework에서
`MetaInstagramClient`가 ApplicationContext 안에서 정상적으로 생성되게 한다.

동시에 다음 환경변수가 없어도 애플리케이션 자체는 정상 기동되어야 한다.

- `META_ACCESS_TOKEN`
- `META_GRAPH_API_VERSION`
- `META_IG_USER_ID`

credential/config 부재는 Spring bean 생성 실패가 되어서는 안 된다.

credential/config가 필요한 검증은 사용자가 실제 Discovery sync를 요청하는 시점에 수행하고,
안전한 configuration error로 반환해야 한다.

## 먼저 조사할 것

`MetaInstagramClient`의 실제 constructor 구성을 확인한다.

특히 다음 가능성을 코드 기준으로 조사한다.

- production constructor와 test seam constructor가 여러 개 존재하여 Spring이 constructor를 선택하지 못하는지
- package-private/test-only constructor와 component scanning 조합 때문에 자동 constructor resolution이 깨지는지
- Spring이 사용할 constructor를 명시해야 하는 구조인지
- `@Component` 등록과 별도 `@Bean` 등록이 충돌하거나 불완전한지
- constructor parameter 중 Spring bean으로 주입할 수 없는 값이 존재하는지
- 환경변수 값을 constructor에서 강제 요구하면서 context load를 막는지

원인을 확인하기 전에 단순히 public default constructor부터 추가하지 않는다.

## 수정 원칙

가장 작은 수정으로 해결한다.

선호하는 특성:

- production dependency가 명확한 constructor injection
- 기존 synthetic test seam 유지
- Meta credential이 없어도 ApplicationContext load 가능
- credential은 실제 sync 시점에 검증
- token을 로그나 exception에 노출하지 않음
- 기존 Discovery Inbox 설계 유지

피해야 하는 방식:

- Spring을 만족시키기 위한 의미 없는 public no-arg constructor
- static mutable global state
- Access Token 하드코딩
- `contextLoads()` 테스트 삭제/skip
- 테스트 skip으로 green 처리
- `DiscoveryService`에서 Meta client dependency를 제거해 오류 은폐
- 큰 provider hierarchy 또는 configuration framework 추가
- Discovery Inbox 전체 재설계

constructor가 여러 개인 것이 원인이라면,
Spring이 사용할 production constructor를 명확하게 결정하도록 하는 최소 수정을 우선한다.

정확한 방법은 현재 소스와 Spring 동작을 읽고 판단한다.

## Regression test

가능하면 이 버그를 직접 방지하는 테스트를 보강한다.

최소 다음이 보장되어야 한다.

1. Meta 환경변수가 없는 상태에서도 Spring ApplicationContext가 정상 생성된다.
2. 기존 `MetaInstagramClientTest` synthetic test가 계속 통과한다.
3. Meta credential/config 부재는 bean creation error가 아니다.
4. 실제 sync 요청 시에는 안전한 configuration error가 발생한다.
5. 기존 Candidate 테스트와 Discovery 테스트를 깨뜨리지 않는다.

테스트의 의미를 약화시키지 않는다.

## 보안

이번 수정에서도 다음 원칙을 유지한다.

- Access Token source commit 금지
- Access Token DB 저장 금지
- Access Token 로그 출력 금지
- token query parameter 사용 금지
- raw Meta response 저장 금지

## 문서

이번 작업이 단순 implementation wiring bug fix이고 장기 설계 결정이 바뀌지 않는다면
`PROJECT_CONTEXT.md`에 불필요한 결정 항목을 추가하지 않는다.

직전 Discovery Inbox 작업으로 `HANDOFF.md`가 갱신되어 있다면
검증 상태가 실제 결과와 일치하도록 최소한으로 수정한다.

## 검증

가능하면 PostgreSQL을 먼저 확인한다.

```bash
docker compose up -d postgres
docker compose ps
```

그 다음 전체 검증을 수행한다.

```bash
./mvnw test
./mvnw package
git diff --check
```

그리고 변경 내용을 확인한다.

```bash
git status --short
git diff --stat
git diff
```

전체 `./mvnw test`와 `./mvnw package`가 성공해야 작업 완료로 판단한다.

실패한다면 test skip이나 production 동작 완화로 억지 통과시키지 않고
정확한 원인을 마지막 출력에 남긴다.

## 작업 범위 밖

이번 bug fix에서 다음은 구현하지 않는다.

- 새로운 Discovery 기능
- author username 입력
- Candidate 연결
- Business Discovery
- InteractionHistory
- DM/comment 기능
- scheduler
- browser automation
- Instagram scraping
- unrelated refactoring

## 성공 기준

다음을 모두 만족해야 한다.

1. `MetaInstagramClient`가 Spring bean으로 정상 생성된다.
2. Meta credential 없이 ApplicationContext가 정상 load된다.
3. 기존 신규 Discovery 테스트가 모두 통과한다.
4. 기존 Candidate 테스트가 모두 통과한다.
5. `./mvnw test` 성공
6. `./mvnw package` 성공
7. `git diff --check` 성공
8. Access Token 관련 보안 원칙 유지
9. 직전 Discovery Inbox 구현 변경 보존

## 마지막 출력

작업 완료 후 다음을 보고한다.

- root cause
- 수정한 파일
- 수정 내용
- 해당 수정이 적절한 이유
- 추가/수정한 테스트
- 전체 테스트 결과
- package 결과
- `git diff --check` 결과
- 문서 갱신 여부
- 남은 문제

P0 blocker가 실제로 발견되면 임의로 우회하지 말고
`AGENTS.md`의 clarification 절차를 따른다.
