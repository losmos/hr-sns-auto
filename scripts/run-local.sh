#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly ENV_FILE="$REPO_ROOT/.env.local"
readonly KEYCHAIN_SERVICE="hr-sns-auto-meta-access-token"
readonly KEYCHAIN_ACCOUNT="META_ACCESS_TOKEN"
readonly DEFAULT_BROWSER_USER_DATA_DIR=".local/instagram-browser-profile"

INSTALL_BROWSER=false
RESET_TOKEN=false
TOKEN_SOURCE=""
PROMPT_RESULT=""

META_GRAPH_API_VERSION="${META_GRAPH_API_VERSION-}"
META_IG_USER_ID="${META_IG_USER_ID-}"
SERVER_PORT="${SERVER_PORT-}"
INSTAGRAM_BROWSER_AUTOMATION_ENABLED="${INSTAGRAM_BROWSER_AUTOMATION_ENABLED-}"
INSTAGRAM_BROWSER_HEADLESS="${INSTAGRAM_BROWSER_HEADLESS-}"
INSTAGRAM_BROWSER_BATCH_SIZE="${INSTAGRAM_BROWSER_BATCH_SIZE-}"
INSTAGRAM_BROWSER_USER_DATA_DIR="${INSTAGRAM_BROWSER_USER_DATA_DIR-}"
META_ACCESS_TOKEN="${META_ACCESS_TOKEN-}"

usage() {
	cat <<'EOF'
Usage:
  ./scripts/run-local.sh
  ./scripts/run-local.sh --install-browser
  ./scripts/run-local.sh --reset-token

Options:
  --install-browser  Playwright Chromium을 설치한 뒤 애플리케이션을 실행한다.
  --reset-token      저장된 macOS Keychain token을 지우고 새 token을 입력한다.
  -h, --help         이 도움말을 출력한다.
EOF
}

die() {
	printf '오류: %s\n' "$*" >&2
	exit 1
}

warn() {
	printf '경고: %s\n' "$*" >&2
}

require_interactive_terminal() {
	if [[ ! -t 0 ]]; then
		die "터미널 입력이 필요하다. 대화형 터미널에서 ./scripts/run-local.sh를 실행한다."
	fi
}

is_valid_api_version() {
	[[ "$1" =~ ^v[0-9]+\.[0-9]+$ ]]
}

is_valid_ig_user_id() {
	[[ "$1" =~ ^[0-9]+$ ]]
}

is_valid_port() {
	local value="$1"
	[[ "$value" =~ ^[1-9][0-9]{0,4}$ ]] || return 1
	(( 10#$value <= 65535 ))
}

is_valid_boolean() {
	[[ "$1" == "true" || "$1" == "false" ]]
}

is_valid_batch_size() {
	local value="$1"
	[[ "$value" =~ ^[1-9][0-9]*$ ]] || return 1
	(( 10#$value >= 1 && 10#$value <= 15 ))
}

is_valid_browser_data_dir() {
	[[ "$1" =~ ^[A-Za-z0-9_./+-]+$ ]]
}

is_valid_token() {
	[[ -n "$1" && ! "$1" =~ [[:space:]] ]]
}

prompt_with_default() {
	local label="$1"
	local default_value="$2"
	local input=""

	printf '%s [%s]: ' "$label" "$default_value" >&2
	IFS= read -r input || die "입력을 읽지 못했다."
	PROMPT_RESULT="${input:-$default_value}"
}

prompt_required() {
	local label="$1"
	local input=""

	printf '%s: ' "$label" >&2
	IFS= read -r input || die "입력을 읽지 못했다."
	PROMPT_RESULT="$input"
}

prompt_boolean() {
	local label="$1"
	local default_value="$2"
	local hint=""
	local input=""

	if [[ "$default_value" == "true" ]]; then
		hint="Y/n"
	else
		hint="y/N"
	fi

	while true; do
		printf '%s [%s]: ' "$label" "$hint" >&2
		IFS= read -r input || die "입력을 읽지 못했다."
		case "$input" in
			"") PROMPT_RESULT="$default_value"; return ;;
			y|Y|yes|YES|Yes) PROMPT_RESULT="true"; return ;;
			n|N|no|NO|No) PROMPT_RESULT="false"; return ;;
			*) warn "y 또는 n으로 입력한다." ;;
		esac
	done
}

create_local_env() {
	local api_version=""
	local ig_user_id=""
	local server_port=""
	local browser_enabled=""
	local browser_headless=""
	local temp_file=""

	require_interactive_terminal
	printf '\n.env.local이 없어 최초 설정을 시작한다. 비밀값은 이 파일에 저장하지 않는다.\n\n'

	while true; do
		prompt_with_default "Meta Graph API version" "v26.0"
		api_version="$PROMPT_RESULT"
		is_valid_api_version "$api_version" && break
		warn "v26.0과 같은 vXX.X 형식으로 입력한다."
	done

	while true; do
		prompt_required "연결 Instagram User ID"
		ig_user_id="$PROMPT_RESULT"
		is_valid_ig_user_id "$ig_user_id" && break
		warn "숫자로 된 연결 Instagram User ID를 입력한다."
	done

	while true; do
		prompt_with_default "Spring Boot server port" "18080"
		server_port="$PROMPT_RESULT"
		is_valid_port "$server_port" && break
		warn "1~65535 범위의 port를 입력한다."
	done

	prompt_boolean "Instagram browser automation을 활성화할지" "true"
	browser_enabled="$PROMPT_RESULT"
	prompt_boolean "Chromium을 headless mode로 실행할지" "false"
	browser_headless="$PROMPT_RESULT"

	temp_file="$(mktemp "$REPO_ROOT/.env.local.tmp.XXXXXX")" \
		|| die ".env.local 임시 파일을 만들지 못했다."
	if ! printf '%s\n' \
		"META_GRAPH_API_VERSION=$api_version" \
		"META_IG_USER_ID=$ig_user_id" \
		"SERVER_PORT=$server_port" \
		"" \
		"INSTAGRAM_BROWSER_AUTOMATION_ENABLED=$browser_enabled" \
		"INSTAGRAM_BROWSER_HEADLESS=$browser_headless" \
		"INSTAGRAM_BROWSER_BATCH_SIZE=10" >"$temp_file"; then
		rm -f "$temp_file"
		die ".env.local 내용을 쓰지 못했다."
	fi
	chmod 600 "$temp_file"
	if ! mv "$temp_file" "$ENV_FILE"; then
		rm -f "$temp_file"
		die ".env.local을 생성하지 못했다."
	fi

	printf '\n.env.local을 생성했다. META_ACCESS_TOKEN은 기록하지 않았다.\n'
}

apply_env_value() {
	local key="$1"
	local value="$2"
	local line_number="$3"

	case "$key" in
		META_GRAPH_API_VERSION)
			[[ -n "$META_GRAPH_API_VERSION" ]] || META_GRAPH_API_VERSION="$value"
			;;
		META_IG_USER_ID)
			[[ -n "$META_IG_USER_ID" ]] || META_IG_USER_ID="$value"
			;;
		SERVER_PORT)
			[[ -n "$SERVER_PORT" ]] || SERVER_PORT="$value"
			;;
		INSTAGRAM_BROWSER_AUTOMATION_ENABLED)
			[[ -n "$INSTAGRAM_BROWSER_AUTOMATION_ENABLED" ]] \
				|| INSTAGRAM_BROWSER_AUTOMATION_ENABLED="$value"
			;;
		INSTAGRAM_BROWSER_HEADLESS)
			[[ -n "$INSTAGRAM_BROWSER_HEADLESS" ]] || INSTAGRAM_BROWSER_HEADLESS="$value"
			;;
		INSTAGRAM_BROWSER_BATCH_SIZE)
			[[ -n "$INSTAGRAM_BROWSER_BATCH_SIZE" ]] || INSTAGRAM_BROWSER_BATCH_SIZE="$value"
			;;
		INSTAGRAM_BROWSER_USER_DATA_DIR)
			[[ -n "$INSTAGRAM_BROWSER_USER_DATA_DIR" ]] || INSTAGRAM_BROWSER_USER_DATA_DIR="$value"
			;;
		META_ACCESS_TOKEN)
			die ".env.local ${line_number}행에 META_ACCESS_TOKEN이 있다. token은 이 파일에 저장하지 않는다."
			;;
		*)
			die ".env.local ${line_number}행의 허용되지 않은 key: $key"
			;;
	esac
}

load_local_env() {
	local line=""
	local line_number=0
	local key=""
	local value=""

	# Shell code 실행을 막기 위해 source/eval 대신 허용된 KEY=value만 직접 적용한다.
	while IFS= read -r line || [[ -n "$line" ]]; do
		line_number=$((line_number + 1))
		line="${line%$'\r'}"
		[[ -z "$line" || "$line" == \#* ]] && continue

		if [[ ! "$line" =~ ^[A-Z][A-Z0-9_]*=.*$ ]]; then
			die ".env.local ${line_number}행 형식이 올바르지 않다. KEY=value만 사용한다."
		fi
		key="${line%%=*}"
		value="${line#*=}"
		apply_env_value "$key" "$value" "$line_number"
	done <"$ENV_FILE"
}

validate_configuration() {
	INSTAGRAM_BROWSER_AUTOMATION_ENABLED="${INSTAGRAM_BROWSER_AUTOMATION_ENABLED:-true}"
	INSTAGRAM_BROWSER_HEADLESS="${INSTAGRAM_BROWSER_HEADLESS:-false}"
	INSTAGRAM_BROWSER_BATCH_SIZE="${INSTAGRAM_BROWSER_BATCH_SIZE:-10}"
	INSTAGRAM_BROWSER_USER_DATA_DIR="${INSTAGRAM_BROWSER_USER_DATA_DIR:-$DEFAULT_BROWSER_USER_DATA_DIR}"

	[[ -n "$META_GRAPH_API_VERSION" ]] \
		|| die "META_GRAPH_API_VERSION이 비어 있다. .env.local을 수정한다."
	is_valid_api_version "$META_GRAPH_API_VERSION" \
		|| die "META_GRAPH_API_VERSION은 v26.0과 같은 vXX.X 형식이어야 한다."
	[[ -n "$META_IG_USER_ID" ]] \
		|| die "META_IG_USER_ID가 비어 있다. .env.local을 수정한다."
	is_valid_ig_user_id "$META_IG_USER_ID" \
		|| die "META_IG_USER_ID는 숫자여야 한다."
	[[ -n "$SERVER_PORT" ]] \
		|| die "SERVER_PORT가 비어 있다. .env.local을 수정한다."
	is_valid_port "$SERVER_PORT" \
		|| die "SERVER_PORT는 1~65535 범위의 정수여야 한다."
	is_valid_boolean "$INSTAGRAM_BROWSER_AUTOMATION_ENABLED" \
		|| die "INSTAGRAM_BROWSER_AUTOMATION_ENABLED는 true 또는 false여야 한다."
	is_valid_boolean "$INSTAGRAM_BROWSER_HEADLESS" \
		|| die "INSTAGRAM_BROWSER_HEADLESS는 true 또는 false여야 한다."
	is_valid_batch_size "$INSTAGRAM_BROWSER_BATCH_SIZE" \
		|| die "INSTAGRAM_BROWSER_BATCH_SIZE는 1~15 범위의 정수여야 한다."
	is_valid_browser_data_dir "$INSTAGRAM_BROWSER_USER_DATA_DIR" \
		|| die "INSTAGRAM_BROWSER_USER_DATA_DIR에는 영문자, 숫자, 점, slash, underscore, plus, hyphen만 사용한다."

	export META_GRAPH_API_VERSION
	export META_IG_USER_ID
	export SERVER_PORT
	export INSTAGRAM_BROWSER_AUTOMATION_ENABLED
	export INSTAGRAM_BROWSER_HEADLESS
	export INSTAGRAM_BROWSER_BATCH_SIZE
	export INSTAGRAM_BROWSER_USER_DATA_DIR
}

is_macos() {
	[[ "$(uname -s)" == "Darwin" ]]
}

prompt_for_token() {
	local token=""

	require_interactive_terminal
	while true; do
		printf 'META_ACCESS_TOKEN: ' >&2
		IFS= read -r -s token || die "token 입력을 읽지 못했다."
		printf '\n' >&2
		if is_valid_token "$token"; then
			META_ACCESS_TOKEN="$token"
			TOKEN_SOURCE="terminal (현재 실행)"
			return
		fi
		warn "token은 비어 있거나 공백을 포함할 수 없다."
	done
}

offer_keychain_storage() {
	local answer=""

	while true; do
		printf 'macOS Keychain에 token을 저장할지 [Y/n]: ' >&2
		IFS= read -r answer || die "입력을 읽지 못했다."
		case "$answer" in
			""|y|Y|yes|YES|Yes)
				if security add-generic-password \
					-a "$KEYCHAIN_ACCOUNT" \
					-s "$KEYCHAIN_SERVICE" \
					-l "hr-sns-auto Meta access token" \
					-w "$META_ACCESS_TOKEN" \
					-U >/dev/null 2>&1; then
					TOKEN_SOURCE="Keychain"
				else
					warn "Keychain 저장에 실패했다. token은 현재 실행에서만 사용한다."
				fi
				return
				;;
			n|N|no|NO|No) return ;;
			*) warn "y 또는 n으로 입력한다." ;;
		esac
	done
}

reset_keychain_token() {
	META_ACCESS_TOKEN=""
	if is_macos && command -v security >/dev/null 2>&1; then
		if security delete-generic-password \
			-a "$KEYCHAIN_ACCOUNT" \
			-s "$KEYCHAIN_SERVICE" >/dev/null 2>&1; then
			printf '기존 macOS Keychain token을 삭제했다.\n'
		else
			printf '삭제할 macOS Keychain token이 없다.\n'
		fi
	elif is_macos; then
		warn "macOS security 명령을 찾지 못해 Keychain entry를 삭제할 수 없다."
	else
		printf '이 운영체제에서는 Keychain entry 없이 새 token을 현재 실행에만 사용한다.\n'
	fi
}

configure_token() {
	local keychain_token=""

	# token은 파일에 쓰거나 출력하지 않고 현재 Spring Boot process 환경으로만 전달한다.
	if [[ "$RESET_TOKEN" == "true" ]]; then
		require_interactive_terminal
		reset_keychain_token
		prompt_for_token
		if is_macos && command -v security >/dev/null 2>&1; then
			offer_keychain_storage
		fi
	elif [[ -n "$META_ACCESS_TOKEN" ]]; then
		is_valid_token "$META_ACCESS_TOKEN" \
			|| die "현재 process의 META_ACCESS_TOKEN 형식이 올바르지 않다."
		TOKEN_SOURCE="process environment"
	elif is_macos && command -v security >/dev/null 2>&1 \
		&& keychain_token="$(security find-generic-password \
			-a "$KEYCHAIN_ACCOUNT" \
			-s "$KEYCHAIN_SERVICE" \
			-w 2>/dev/null)" \
		&& is_valid_token "$keychain_token"; then
		META_ACCESS_TOKEN="$keychain_token"
		TOKEN_SOURCE="Keychain"
	else
		prompt_for_token
		if is_macos && command -v security >/dev/null 2>&1; then
			offer_keychain_storage
		fi
	fi

	export META_ACCESS_TOKEN
}

wait_for_postgres() {
	local container_id=""
	local status=""
	local attempt=0

	container_id="$(docker compose ps -q postgres)" \
		|| die "PostgreSQL container ID를 확인하지 못했다."
	[[ -n "$container_id" ]] || die "PostgreSQL container가 생성되지 않았다."

	while (( attempt < 60 )); do
		status="$(docker inspect \
			--format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
			"$container_id" 2>/dev/null || true)"
		case "$status" in
			healthy|running) return ;;
			unhealthy|exited|dead)
				die "PostgreSQL container 상태가 $status 이다. docker compose logs postgres로 확인한다."
				;;
		esac
		attempt=$((attempt + 1))
		sleep 1
	done

	die "PostgreSQL이 60초 안에 ready 상태가 되지 않았다. docker compose ps로 확인한다."
}

start_postgres() {
	command -v docker >/dev/null 2>&1 \
		|| die "Docker 명령을 찾을 수 없다. Docker Desktop 또는 Docker Engine을 설치한다."
	docker compose version >/dev/null 2>&1 \
		|| die "docker compose를 사용할 수 없다. Docker Compose plugin을 설치한다."
	docker info >/dev/null 2>&1 \
		|| die "Docker daemon에 연결할 수 없다. Docker Desktop 또는 Docker daemon을 시작한다."

	printf '\nPostgreSQL container를 시작한다.\n'
	if ! docker compose up -d postgres; then
		die "PostgreSQL container를 시작하지 못했다. docker compose logs postgres로 확인한다."
	fi
	# 종료 cleanup을 등록하지 않아 기존 volume과 실행 중인 container를 그대로 유지한다.
	wait_for_postgres
	docker compose ps postgres
}

install_browser() {
	printf '\nPlaywright Chromium을 설치한다. 이미 설치된 binary는 재사용한다.\n'
	./mvnw exec:java \
		-Dexec.mainClass=com.microsoft.playwright.CLI \
		-Dexec.args="install chromium"
}

mask_ig_user_id() {
	local value="$1"
	local length=${#value}

	if (( length <= 6 )); then
		printf 'configured'
	elif (( length <= 10 )); then
		printf '%s...%s' "${value:0:2}" "${value: -2}"
	else
		printf '%s...%s' "${value:0:4}" "${value: -5}"
	fi
}

browser_summary() {
	if [[ "$INSTAGRAM_BROWSER_AUTOMATION_ENABLED" == "false" ]]; then
		printf 'disabled'
	elif [[ "$INSTAGRAM_BROWSER_HEADLESS" == "true" ]]; then
		printf 'enabled / headless'
	else
		printf 'enabled / headed'
	fi
}

print_summary() {
	printf '\nhr-sns-auto local\n\n'
	printf 'Server:        http://localhost:%s\n' "$SERVER_PORT"
	printf 'Meta API:      %s\n' "$META_GRAPH_API_VERSION"
	printf 'IG User ID:    %s\n' "$(mask_ig_user_id "$META_IG_USER_ID")"
	printf 'Browser:       %s\n' "$(browser_summary)"
	printf 'Browser data:  %s\n' "$INSTAGRAM_BROWSER_USER_DATA_DIR"
	printf 'PostgreSQL:    ready\n'
	printf 'Meta token:    configured (%s)\n' "$TOKEN_SOURCE"
	printf '\nOpen: http://localhost:%s/discovery\n\n' "$SERVER_PORT"
}

parse_arguments() {
	while (( $# > 0 )); do
		case "$1" in
			--install-browser) INSTALL_BROWSER=true ;;
			--reset-token) RESET_TOKEN=true ;;
			-h|--help) usage; exit 0 ;;
			*) usage >&2; die "지원하지 않는 option: $1" ;;
		esac
		shift
	done
}

main() {
	parse_arguments "$@"
	cd "$REPO_ROOT"

	[[ -f "$ENV_FILE" ]] || create_local_env
	load_local_env
	validate_configuration
	configure_token
	start_postgres

	if [[ "$INSTALL_BROWSER" == "true" ]]; then
		install_browser
	fi

	print_summary
	exec ./mvnw spring-boot:run
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
	main "$@"
fi
