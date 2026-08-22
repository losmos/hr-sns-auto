# hr-sns-auto

Instagram 기반 의료계 네트워킹 후보 발굴과 운영자 검토를 지원하는 local Spring Boot 애플리케이션이다.

## Local 실행

Java 21과 Docker Desktop 또는 Docker Engine이 필요하다. 일상적인 local 실행 명령은 다음 하나이다.

```bash
./scripts/run-local.sh
```

스크립트는 repository root로 이동하고 다음 작업을 수행한다.

- `.env.local`의 non-secret 설정을 검증해 환경변수로 내보낸다.
- Meta access token을 process environment, macOS Keychain, hidden terminal prompt 순서로 찾는다.
- `docker compose up -d postgres`로 PostgreSQL을 시작하고 health 상태를 기다린다.
- 설정 요약과 `/discovery` URL을 표시한 뒤 Spring Boot를 실행한다.

`Ctrl+C`는 Spring Boot만 종료한다. PostgreSQL container, PostgreSQL volume, `.local/instagram-browser-profile`은 삭제하지 않는다.

## 첫 실행

`.env.local`이 없으면 스크립트가 다음 값을 묻고 파일을 생성한다.

- Meta Graph API version, 기본값 `v26.0`
- 연결 Instagram User ID
- server port, 기본값 `18080`
- browser automation 활성화 여부, 기본값 `true`
- headless 여부, 기본값 `false`

생성 형식은 [.env.local.example](.env.local.example)을 참고한다. `.env.local`은 gitignored이며 `META_ACCESS_TOKEN`을 넣을 수 없다. 현재 process의 non-secret 환경변수가 있으면 같은 이름의 `.env.local` 값보다 우선한다.

## Meta access token

token은 `.env.local`, source, DB, 로그에 저장하지 않는다. 스크립트는 다음 순서로 token을 결정한다.

1. 현재 process의 `META_ACCESS_TOKEN`을 사용한다.
2. macOS에서는 Keychain service `hr-sns-auto-meta-access-token`을 조회한다.
3. 없으면 terminal에서 값을 보이지 않는 hidden prompt를 표시한다.
4. macOS에서는 입력한 token의 Keychain 저장 여부를 묻는다.

저장한 token을 교체하려면 다음 명령을 사용한다. 기존 프로젝트 전용 Keychain entry를 삭제하고 새 token을 hidden prompt로 받은 뒤 일반 실행을 계속한다.

```bash
./scripts/run-local.sh --reset-token
```

Linux와 기타 운영체제에서는 hidden prompt로 받은 token을 현재 실행에서만 사용한다.

## Playwright Chromium 설치

Chromium binary가 없거나 Playwright version이 변경됐을 때만 다음 명령을 사용한다. 설치 후 PostgreSQL과 Spring Boot의 일반 실행을 계속한다.

```bash
./scripts/run-local.sh --install-browser
```

일반 실행에서는 browser binary를 다시 설치하지 않는다. Instagram 로그인 session은 gitignored된 `.local/instagram-browser-profile`에 유지한다. 이 디렉토리에는 cookie와 local storage가 있을 수 있으므로 복사하거나 commit하지 않는다.

전체 option은 다음 명령으로 확인한다.

```bash
./scripts/run-local.sh --help
```
