# DiscoveryPersistenceTest 실제 개발 DB 데이터 의존성 수정

## 작업 시작

루트 `AGENTS.md`를 따른다.

다음 순서로 읽는다.

1. `docs/harness/HANDOFF.md`
2. `docs/harness/PROJECT_CONTEXT.md`

그 다음 이번 문제와 직접 관련된 파일만 읽는다.

특히:

- `src/test/java/com/losmos/hrsnsauto/discovery/DiscoveryPersistenceTest.java`
- `src/main/java/com/losmos/hrsnsauto/discovery/DiscoveryItem.java`
- `src/main/java/com/losmos/hrsnsauto/discovery/DiscoveryItemRepository.java`
- `src/main/java/com/losmos/hrsnsauto/discovery/DiscoveryService.java`

## 현재 Git 상태

사용자가 실수로 검증 전에 browser enrichment 구현을 commit하고 push했다.

이미 원격 main에 올라간 commit:

`43c45ec feat: add Instagram browser enrichment`

이 commit을 revert하거나 history rewrite하지 않는다.

이번 작업은 그 위에 surgical fix commit으로 테스트 독립성 문제만 수정한다.

## 실제 사용자 환경 결과

PostgreSQL에는 이전 live Instagram Discovery sync로 저장된 실제 DiscoveryItem 데이터가 존재한다.

Browser enrichment 구현 후:

- Flyway V5 migration 성공
- browser 관련 신규 unit tests 대부분 성공
- 전체 tests: 76
- failures: 2
- errors: 0

실패는 모두 `DiscoveryPersistenceTest`이다.

### 실패 1

`repeatedMediaStaysUniqueUpdatesLastSeenAndPreservesAllHashtagSourcesAndReviewState`

오류:

```text
Expected size: 500 but was: 506
```

실패 출력에 테스트 fixture가 아니라 기존 실제 Instagram caption이 나타났다.

현재 테스트는 sync 후:

```java
itemRepository.findAllByOrderByPublishedAtDescIdDesc().getFirst()
```

를 사용한다.

이미 실제 Discovery 데이터가 존재하기 때문에
테스트가 생성한 media가 아닌 DB의 다른 최신 row를 선택할 수 있다.

또한 production `DiscoveryItem.excerpt()`는 UTF-16 `String.length()`가 아니라
Unicode code point 기준 최대 500개로 자른다.

emoji 등 supplementary character가 포함되면
500 code point의 Java `String.length()`가 500보다 클 수 있다.

따라서 `hasSize(500)`은 production contract와 일치하지 않는다.

### 실패 2

`oneHashtagFailureDoesNotDiscardOtherSuccessfulObservations`

오류:

```text
expected: 2L
but was: 36L
```

현재 테스트는:

```java
assertThat(itemRepository.count()).isEqualTo(2);
```

처럼 전체 DB가 빈 상태라고 가정한다.

실제 개발 DB에는 live DiscoveryItem이 이미 존재하므로 이 assertion은 잘못됐다.

## 근본 원인

`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`으로
실제 로컬 PostgreSQL을 사용하면서 일부 persistence test가
database 전체가 비어 있다고 가정하고 있다.

테스트가 production/dev 데이터의 존재 여부와 무관하게 동작해야 한다.

## 목표

이번 작업에서는 production browser enrichment 코드를 되돌리지 않는다.

`DiscoveryPersistenceTest`와 직접 필요한 테스트 코드만 수정하여:

- 기존 실제 DiscoveryItem이 있어도 테스트 성공
- 기존 BrowserObservation이 있어도 테스트 성공
- 사용자가 hashtag를 추가했어도 테스트가 불필요하게 실패하지 않음
- 테스트가 생성한 row를 명시적으로 조회
- Unicode caption length contract를 정확하게 검증

하도록 한다.

## 매우 중요한 안전 조건

테스트 독립성을 만들기 위해 실제 개발 DB 데이터를 삭제하면 안 된다.

금지:

- repository.deleteAll()
- truncate
- DELETE FROM discovery_items
- DELETE FROM discovery_hashtags
- @BeforeEach에서 전체 DB cleanup
- Flyway clean
- DB drop/recreate
- 기존 사용자의 live Discovery 데이터 수정/삭제

현재 DB에는 실제 사용자가 수집한 Instagram Discovery 데이터가 있으므로
이를 테스트 fixture처럼 취급하거나 정리하지 않는다.

테스트가 자기 데이터만 식별해 검증하는 방식으로 수정한다.

## 구체적으로 감사할 부분

`DiscoveryPersistenceTest` 전체를 읽고
DB가 비어 있다고 가정하는 assertion을 모두 찾는다.

현재 확인된 예:

### 1. test row 선택

다음과 같은 전역 조회:

```java
findAllByOrderByPublishedAtDescIdDesc().getFirst()
```

로 test fixture를 선택하지 않는다.

테스트가 넣은 고유 `instagramMediaId`로 조회한다.

예:

```java
itemRepository.findOneByInstagramMediaId(TEST_MEDIA_ID)
```

정확한 구현은 기존 repository API를 재사용한다.

### 2. caption 500자 assertion

production contract가 Unicode code point 500개라면
테스트도 code point count를 검증한다.

예시 의미:

```java
String excerpt = item.getCaptionExcerpt();

assertThat(
    excerpt.codePointCount(0, excerpt.length())
).isEqualTo(DiscoveryItem.CAPTION_EXCERPT_MAX_LENGTH);
```

또는 test input이 `"가".repeat(600)`이라면
정확히 `"가".repeat(500)`인지 검증해도 된다.

production 코드를 단순히 테스트에 맞추기 위해 UTF-16 500 char로 변경하지 않는다.

### 3. global item count

다음처럼 DB 전체 count가 1 또는 2라고 가정하지 않는다.

```java
itemRepository.count() == 1
itemRepository.count() == 2
```

대신 테스트가 생성한 known media ID가 존재하는지,
두 번째 sync에서 `createdCount == 0`인지,
source association/review state가 올바른지 등을 직접 검증한다.

정말 count가 필요한 경우에는 test 시작 시 baseline count를 기록하고 delta를 검증할 수 있지만,
가능하면 known ID 기반 assertion을 선호한다.

### 4. BrowserObservation global count

현재 테스트에 다음과 같은 assertion이 있다면 감사한다.

```java
observationRepository.count() == 1
```

실제 browser observation이 DB에 존재하기 시작하면 깨질 수 있다.

한 DiscoveryItem당 최신 observation 하나라는 정책은
해당 test item의 observation ID 또는
`findOneByDiscoveryItemId(testItemId)`를 통해 검증한다.

예:

- 첫 저장 observation ID 보존
- replace/update 후 같은 observation ID인지 확인
- 해당 discoveryItem에 하나의 observation이 연결됨을 검증

전체 DB count를 1이라고 가정하지 않는다.

### 5. 기본 hashtag seed

migration seed test가 다음처럼:

```java
containsExactlyInAnyOrder(...)
```

로 전체 현재 enabled hashtag 집합이 정확히 3개라고 가정하면
사용자가 hashtag를 추가하거나 비활성화한 뒤 실패할 수 있다.

migration의 불변 사실과 runtime mutable state를 구분한다.

이번 shared dev DB persistence test에서는 최소한:

- 기본 keyword row들이 존재하는지

를 검증하도록 한다.

사용자가 이후 enable/disable 할 수 있는 runtime 상태를
항상 migration 직후 상태라고 가정하지 않는다.

단, 기존 테스트 의도를 과도하게 약화하지 않는다.

## 테스트 fixture ID

실제 Instagram media ID와 충돌할 가능성을 없애도록
테스트 전용으로 명확한 synthetic ID를 사용한다.

예:

```text
test-repeat-media-001
test-success-media-001
test-success-media-002
test-browser-observation-001
```

production validation이 numeric ID를 요구하지 않는 현재 구조에서만 사용한다.

numeric semantics를 요구한다면 충분히 비현실적인 긴 test ID를 사용한다.

## production 코드

이번 실패는 우선 test isolation 문제로 보인다.

production 코드는 테스트를 통과시키기 위해 임의 변경하지 않는다.

코드를 읽은 결과 실제 production bug가 확인된 경우에만
최소 수정하고 그 이유를 마지막 출력에 명확히 기록한다.

특히:

- caption code-point truncate 정책을 임의 변경하지 않는다.
- browser enrichment 구현을 revert하지 않는다.
- V5 migration을 새로 만들거나 수정하지 않는다.
- 실제 DB data를 migration으로 정리하지 않는다.

이미 적용된 Flyway V5 migration 파일은 변경하지 않는다.

## 회귀 테스트

`DiscoveryPersistenceTest`가 다음 두 상황 모두 의미적으로 안전한 구조인지 확인한다.

1. 빈 DB에 가까운 fresh schema
2. 기존 DiscoveryItem / custom hashtag / browser observation이 존재하는 long-lived dev DB

두 번째 상황을 위해 실제 사용자 데이터를 fixture로 사용하지 않는다.
필요하면 synthetic pre-existing row를 테스트 transaction 안에서 추가하여
global-state assumption이 없는지 검증할 수 있다.

## 문서

이번 작업은 browser enrichment 설계 변경이 아니다.

`PROJECT_CONTEXT.md`에 새 장기 결정을 추가하지 않는다.

`HANDOFF.md`에는 필요한 경우 실제 검증 이력만 최소 갱신한다.

기록할 수 있는 사실:

- browser enrichment commit `43c45ec`이 검증 전에 push됨
- 사용자 PostgreSQL에서 V5 migration은 성공
- 첫 전체 검증에서 76 tests 중 DiscoveryPersistenceTest 2 failures 발생
- 원인은 shared dev DB의 기존 live Discovery data를 무시한 global-state test assumption
- 후속 fix에서 테스트를 known fixture identity 기반으로 격리
- 최종 test/package 결과

최종 결과를 확인하기 전에 성공했다고 기록하지 않는다.

## 검증

PostgreSQL의 실제 데이터를 삭제하거나 초기화하지 않는다.

다음 순서로 수행한다.

```bash
docker compose up -d postgres
docker compose ps

./mvnw test
./mvnw package
git diff --check

git status --short
git diff --stat
git diff
```

특히 실제 local DB에 기존 30건 이상의 DiscoveryItem이 있는 상태에서도
전체 tests가 통과하는지 확인한다.

## 성공 기준

다음을 모두 만족해야 한다.

1. 기존 실제 Discovery 데이터를 삭제하지 않음
2. `DiscoveryPersistenceTest`가 전체 DB row count에 의존하지 않음
3. test fixture를 known media ID로 조회
4. caption 500 제한을 production의 Unicode code-point contract에 맞게 검증
5. BrowserObservation test가 global count에 의존하지 않음
6. custom/runtime hashtag가 존재해도 불필요하게 실패하지 않도록 개선
7. Flyway V5 기존 migration 변경 없음
8. browser enrichment production 구현 유지
9. 전체 `./mvnw test` 성공
10. `./mvnw package` 성공
11. `git diff --check` 성공

## 마지막 출력

반드시 다음을 보고한다.

- 두 테스트가 실패한 root cause
- 수정한 global-state assumption
- caption 500 assertion 변경 이유
- production code 변경 여부
- 사용자 DB 데이터 삭제/변경 여부
- 전체 test 결과
- package 결과
- git diff --check 결과
- 변경 파일
- 다음 live browser smoke test 절차

P0 blocker가 없다면 질문하지 말고 위 범위에서 수정한다.
