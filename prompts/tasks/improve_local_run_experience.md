# Local development 실행 UX 개선

## 시작

루트 AGENTS.md를 따른다.

먼저 다음을 읽는다.

1. docs/harness/HANDOFF.md
2. docs/harness/PROJECT_CONTEXT.md

그리고 다음을 확인한다.

- `.gitignore`
- `application.properties`
- `docker-compose.yml` 또는 `compose.yaml`
- `scripts/`
- 현재 Instagram browser enrichment 환경변수

## 배경

현재 macOS local에서 browser enrichment를 live 검증 중이다.

현재 실행하려면 사용자가 매번 직접 다음과 유사한 작업을 한다.

- META_GRAPH_API_VERSION export
- META_IG_USER_ID export
- META_ACCESS_TOKEN hidden input
- INSTAGRAM_BROWSER_AUTOMATION_ENABLED export
- INSTAGRAM_BROWSER_HEADLESS export
- SERVER_PORT 지정
- PostgreSQL Docker 실행
- Spring Boot 실행

또한 macOS 기본 zsh와 bash의 `read` 문법 차이 때문에
사용자가 shell 문법을 기억해야 하는 문제가 있었다.

이는 local developer UX가 지나치게 불편하다.

## 목표

일상적인 local 실행을 다음 한 줄로 만든다.

```bash
./scripts/run-local.sh
```

사용자는 shell별 export/read 문법을 기억할 필요가 없어야 한다.

## 설계

### 1. `.env.local.example`

repository에 `.env.local.example`을 commit한다.

예:

```dotenv
META_GRAPH_API_VERSION=v26.0
META_IG_USER_ID=
SERVER_PORT=18080

INSTAGRAM_BROWSER_AUTOMATION_ENABLED=true
INSTAGRAM_BROWSER_HEADLESS=false
INSTAGRAM_BROWSER_BATCH_SIZE=10
```

DB가 docker-compose 기본값으로 충분하면 불필요한 DB 설정은 넣지 않는다.

실제 access token은 example에 값을 넣지 않는다.

### 2. `.env.local`

실제 local 설정용 `.env.local`은 gitignore한다.

`.gitignore`에 명시적으로:

```gitignore
.env.local
```

을 추가한다.

이미 존재하는 `.local/` browser profile ignore는 유지한다.

### 3. access token

`META_ACCESS_TOKEN`을 repository나 `.env.local`에 평문 저장하도록 기본 안내하지 않는다.

macOS에서는 Keychain을 우선 지원한다.

Keychain service name은 프로젝트 전용으로 명확하게 한다.

예:

```text
hr-sns-auto-meta-access-token
```

동작:

1. 현재 process에 `META_ACCESS_TOKEN`이 이미 있으면 그대로 사용
2. macOS이고 Keychain에 token이 있으면 `security find-generic-password`로 읽음
3. 없으면 터미널에서 hidden prompt
4. macOS에서는 최초 입력 후 Keychain 저장 여부를 물을 수 있음
5. Linux/기타에서는 hidden prompt만 사용

token 값을 stdout/log에 절대 출력하지 않는다.

shell history에도 token literal이 남지 않는 구조여야 한다.

### 4. `scripts/run-local.sh`

portable bash script로 작성한다.

shebang:

```bash
#!/usr/bin/env bash
```

`set -euo pipefail`을 사용한다.

호출한 사용자의 현재 shell이 zsh여도 동작해야 한다.
즉 사용자가 직접 zsh `read` syntax를 사용할 필요가 없어야 한다.

스크립트는 자신의 위치 기준으로 repository root를 찾아 `cd`한다.

### 5. 첫 실행

`.env.local`이 없으면 불친절하게 실패시키지 않는다.

가능하면 interactive setup을 수행한다.

최소 입력:

- META_GRAPH_API_VERSION, default v26.0
- META_IG_USER_ID
- SERVER_PORT, default 18080
- browser automation enabled, default true
- headless, macOS local default false

입력한 non-secret 값으로 `.env.local`을 생성한다.

`META_ACCESS_TOKEN`은 `.env.local`에 기록하지 않는다.

이미 알려진 특정 사용자 ID를 repository default로 hardcode하지 않는다.
`.env.local.example`은 placeholder/empty로 유지한다.

### 6. `.env.local` loader

`.env.local`을 안전하게 source할 수 있도록
허용하는 포맷을 단순 `KEY=value`로 제한한다.

이 파일은 사용자의 local trusted configuration이라는 전제지만,
실행 시 값 존재 여부를 검증한다.

최소:

- META_GRAPH_API_VERSION
- META_IG_USER_ID
- SERVER_PORT

browser 관련 값은 default를 적용할 수 있다.

### 7. PostgreSQL

실행 전:

```bash
docker compose up -d postgres
```

를 수행한다.

이미 실행 중이면 정상적으로 계속 진행한다.

Docker 명령 자체가 없거나 daemon 접근이 안 되면
이해하기 쉬운 오류를 출력한다.

사용자의 데이터를 delete/reset하지 않는다.

`docker compose down -v`, Flyway clean 등은 절대 수행하지 않는다.

### 8. Playwright Chromium

일상 실행마다 browser binary 전체를 재설치하지 않는다.

별도 option을 제공한다.

예:

```bash
./scripts/run-local.sh --install-browser
```

이면:

```bash
./mvnw exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
```

를 실행한 후 일반 실행을 계속하거나 명확히 종료한다.

정확한 CLI invocation은 현재 Playwright dependency와 맞는지 확인한다.

`--help`도 제공하면 좋다.

예:

```text
Usage:
  ./scripts/run-local.sh
  ./scripts/run-local.sh --install-browser
  ./scripts/run-local.sh --reset-token
```

### 9. token 갱신

Meta access token은 만료될 수 있다.

macOS Keychain을 사용하는 경우:

```bash
./scripts/run-local.sh --reset-token
```

같은 option으로 기존 Keychain entry를 삭제하거나 새 값을 입력하도록 한다.

삭제 후 새 token을 hidden prompt로 받고
다시 Keychain 저장 여부를 묻는다.

token 자체를 출력하지 않는다.

### 10. 최종 실행

필요한 값을 export한 후:

```bash
SERVER_PORT="$SERVER_PORT" ./mvnw spring-boot:run
```

또는 equivalent 방식으로 실행한다.

사용자에게 실행 전에 compact summary를 보여준다.

예:

```text
hr-sns-auto local

Server:        http://localhost:18080
Meta API:      v26.0
IG User ID:    1784...62008
Browser:       enabled / headed
Browser data:  .local/instagram-browser-profile
PostgreSQL:    ready
Meta token:    configured (Keychain)
```

token 전체는 절대 출력하지 않는다.

IG User ID도 전체 출력 대신 mask해도 된다.

마지막에:

```text
Open: http://localhost:18080/discovery
```

를 보여준다.

자동으로 브라우저를 여는 것은 필수 아님.

### 11. 종료

Ctrl+C로 Spring Boot를 종료해도 PostgreSQL container를 자동 삭제하지 않는다.

persistent browser profile도 삭제하지 않는다.

## 문서

README 또는 적절한 local development 문서에
기존의 여러 export 명령 대신 다음을 primary 실행 방법으로 기록한다.

```bash
./scripts/run-local.sh
```

manual environment variable 방식은 troubleshooting/advanced usage로 남겨도 된다.

HANDOFF에는 local execution UX 개선 사실을 짧게 기록한다.

PROJECT_CONTEXT에는 장기적으로 유효한 local secret/config 정책만
필요한 경우 최소한으로 기록한다.

## 보안

절대 commit 금지:

- real META_ACCESS_TOKEN
- `.env.local`
- browser session
- cookie
- localStorage
- Keychain export

테스트에서도 실제 token을 사용하지 않는다.

## 테스트

shell script 자체는 최소한 syntax 검증한다.

```bash
bash -n scripts/run-local.sh
```

가능하면 non-interactive helper 부분을 test 가능한 함수로 작게 유지한다.

실제 macOS Keychain을 테스트 과정에서 수정하지 않는다.

## 전체 검증

```bash
docker compose up -d postgres
docker compose ps

bash -n scripts/run-local.sh

./mvnw test
./mvnw package

git diff --check
git status --short
git diff --stat
git diff
```

## 성공 기준

- 일상 local 실행이 `./scripts/run-local.sh` 한 줄
- zsh/bash 차이를 사용자가 알 필요 없음
- non-secret 설정은 `.env.local`
- `.env.local` gitignored
- token은 기본적으로 파일에 저장하지 않음
- macOS Keychain 지원
- token reset 경로 있음
- PostgreSQL 자동 기동
- Playwright install option 있음
- 기존 persistent browser profile 유지
- 실제 DB 삭제/초기화 없음
- tests/package 성공
- git diff --check 성공

## 마지막 출력

다음을 보고한다.

- 최종 사용자가 실행해야 하는 명령
- 첫 실행 UX
- 이후 실행 UX
- token 저장 위치
- token 갱신 방법
- Playwright 설치 방법
- 생성/변경 파일
- test/package/diff-check 결과
