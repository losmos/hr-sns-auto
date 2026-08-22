# Local Meta access token lifecycle UX 개선

## 시작

루트 AGENTS.md를 따른다.

먼저:

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md
3. scripts/run-local.sh
4. .env.local.example
5. Meta API client/config 관련 코드

를 읽는다.

## 배경

현재 `scripts/run-local.sh`는 token source 우선순위가:

1. process environment META_ACCESS_TOKEN
2. macOS Keychain
3. interactive hidden prompt

이다.

사용자가 과거 shell에서 export한 META_ACCESS_TOKEN이 남아 있으면
Keychain이나 신규 입력 없이 오래된 token이 계속 재사용될 수 있다.

또한 Keychain에 저장된 token도 실제 Meta API에서
expired/revoked 되었는지 확인하지 않고 Spring Boot를 실행한다.

사용자 요구는:

- 평소에는 `./scripts/run-local.sh` 한 줄
- token을 매번 입력하지 않음
- token이 만료/invalid 됐을 때만 자연스럽게 새 token 입력
- shell에 남은 과거 export 때문에 의도치 않게 stale token을 쓰지 않음
- token literal을 파일/로그/history에 남기지 않음

이다.

## 목표 UX

정상 상태:

```text
$ ./scripts/run-local.sh

Meta token: validating...
Meta token: valid (Keychain)

...
Spring Boot 시작
```

만료 상태:

```text
$ ./scripts/run-local.sh

Meta token: validating...
Meta access token이 만료되었거나 유효하지 않다.
새 META_ACCESS_TOKEN:
macOS Keychain에 저장할지 [Y/n]:

Meta token: valid
...
Spring Boot 시작
```

즉 사용자가 만료 주기나 export 명령을 기억하지 않아야 한다.

## 1. process environment stale-token UX 수정

현재 process environment의 META_ACCESS_TOKEN이
항상 Keychain보다 우선하는 동작을 재검토한다.

일반 `./scripts/run-local.sh` 사용에서는
persistent local secret store인 macOS Keychain을 primary source로 삼는 것이
더 예측 가능한 UX인지 검토하고 적용한다.

단 CI/명시적 automation에서는 env override가 유용할 수 있으므로
무조건 기능을 제거하지 않는다.

예를 들어 다음 중 작고 명확한 정책을 선택할 수 있다.

- interactive local macOS:
  Keychain > process env > prompt

또는

- process env가 있으면 사용하되 source를 명확히 보여주고
  반드시 validity check를 거친다.

중요한 것은 stale env token이 silent하게 사용되지 않는 것이다.

과도한 option/framework는 만들지 않는다.

## 2. startup token validity check

Spring Boot 실행 전에 현재 META_ACCESS_TOKEN이
실제 Meta Graph API에서 사용 가능한지 가볍게 검증한다.

이미 프로젝트가 사용하는 공식 Graph API endpoint와
현재 설정된 META_IG_USER_ID를 활용한다.

검증 요청은 read-only여야 한다.

예시 방향:

- configured IG user에 대한 최소 field self lookup
- 또는 프로젝트의 Meta client가 실제로 요구하는 최소 read endpoint

정확한 endpoint는 현재 구현/권한과 맞는 것을 코드에서 확인하고 선택한다.

### 중요

HTTP 실패를 전부 "token expired"라고 판단하지 않는다.

Meta Graph API의 OAuth invalid/expired token 오류,
특히 code 190 계열만 token replacement 대상으로 분류한다.

permission/access/network/rate limit/other error는
별도 오류 메시지로 보여준다.

network 일시 오류 때문에 Keychain token을 삭제하지 않는다.

## 3. expired/invalid token replacement

token이 invalid/expired로 판정되면:

- 기존 token 값을 출력하지 않는다.
- hidden prompt로 새 token을 받는다.
- 새 token도 validity check한다.
- valid일 때만 macOS Keychain에 저장/교체한다.
- invalid한 새 token이면 재입력할 수 있다.
- bounded retry 또는 사용자가 Ctrl+C로 취소 가능하게 한다.

`.env.local`에는 절대 token을 저장하지 않는다.

## 4. Keychain

기존 service/account:

- service: `hr-sns-auto-meta-access-token`
- account: `META_ACCESS_TOKEN`

을 유지한다.

macOS에서 valid token을 저장할 때
`security add-generic-password ... -U`로 교체한다.

실패하면 현재 실행에서만 사용 가능하다고 안내한다.

## 5. --reset-token

기존:

```bash
./scripts/run-local.sh --reset-token
```

은 유지한다.

의미:

- environment 여부와 관계없이 사용자가 명시적으로 새 token 입력
- 새 token validity check
- valid하면 Keychain 교체
- 이후 정상 실행

## 6. token validation 결과 summary

token 자체나 token prefix를 출력하지 않는다.

다음 정도만 출력한다.

```text
Meta token:    valid (Keychain)
```

또는:

```text
Meta token:    valid (process environment)
```

만료/invalid:

```text
Meta token:    invalid/expired - replacement required
```

## 7. 자동 "만료 예정일" 기능은 이번 범위에서 제외

`debug_token`을 통한 expires_at 조회는
App ID/App Secret 또는 별도 app access token 관리가 필요할 수 있다.

이번 slice에서는 secret surface를 늘리지 않는다.

따라서:

- expiry date 사전 표시
- App Secret 저장
- automatic token exchange

은 구현하지 않는다.

현재 목적은 실행 시 실제 validity를 확인하여
필요할 때만 재입력시키는 것이다.

## 8. 보안

절대 저장/출력 금지:

- access token raw value
- token prefix
- app secret
- cookie/session
- HTTP Authorization header

curl `-v`, shell `set -x` 사용 금지.

token이 command line argument로 노출되어
`ps`에서 보이는 방식도 피할 수 있는지 검토한다.

가능하면 stdin/config/header를 안전하게 전달한다.

## 9. 테스트

실제 Meta network를 자동 테스트에서 호출하지 않는다.

token validation parsing/decision을
mock/fake 가능한 작은 helper로 분리하거나
shell test fixture로 검증한다.

최소:

- valid token response → 계속 실행
- OAuth code 190 → replacement prompt path
- permission error → token expired로 오판하지 않음
- network failure → Keychain 삭제하지 않음
- new token invalid → 저장하지 않음
- valid new token → 저장 가능
- raw token이 output에 나타나지 않음
- --reset-token 동작 유지

## 문서

README/local development 문서에 primary UX를:

```bash
./scripts/run-local.sh
```

로 유지한다.

token lifecycle 설명은:

- 평소 Keychain 재사용
- startup validity check
- invalid/expired일 때만 hidden prompt
- 강제 변경은 `--reset-token`

정도로 간단히 설명한다.

HANDOFF에 이번 UX 개선과 live token policy를 기록한다.

## 검증

```bash
bash -n scripts/run-local.sh

docker compose up -d postgres
docker compose ps

./mvnw test
./mvnw package

git diff --check
git status --short
git diff --stat
git diff
```

실제 Meta token/network live 호출은 Codex sandbox에서 수행하지 않는다.

## 성공 기준

- 평소 실행은 `./scripts/run-local.sh`
- token을 매번 입력하지 않음
- startup 시 validity 확인
- OAuth invalid/expired 시에만 token 재입력
- stale process env token이 silent하게 사용되지 않음
- Keychain persistent store 유지
- `.env.local`에 secret 없음
- raw token log 없음
- --reset-token 유지
- tests/package/diff-check 성공

## 마지막 출력

- 최종 token source precedence
- validity check 방식
- expired/invalid 판정 방식
- 사용자 normal 실행 방법
- token 만료 시 UX
- --reset-token UX
- tests/package 결과
- 변경 파일
