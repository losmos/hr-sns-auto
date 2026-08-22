#!/usr/bin/env bash

set -euo pipefail

# 시작 shell에 남은 token은 source 후보로만 보관하고 즉시 environment export를 해제한다.
PROCESS_META_ACCESS_TOKEN="${META_ACCESS_TOKEN-}"
unset META_ACCESS_TOKEN
META_ACCESS_TOKEN=""

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly ENV_FILE="$REPO_ROOT/.env.local"
readonly KEYCHAIN_SERVICE="hr-sns-auto-meta-access-token"
readonly KEYCHAIN_ACCOUNT="META_ACCESS_TOKEN"
readonly DEFAULT_BROWSER_USER_DATA_DIR=".local/instagram-browser-profile"
readonly TOKEN_INPUT_MAX_ATTEMPTS=3

INSTALL_BROWSER=false
RESET_TOKEN=false
TOKEN_SOURCE=""
PROMPT_RESULT=""
META_VALIDATION_RESULT=""
META_VALIDATION_HTTP_STATUS=""
META_VALIDATION_GRAPH_CODE=""

META_GRAPH_API_VERSION="${META_GRAPH_API_VERSION-}"
META_IG_USER_ID="${META_IG_USER_ID-}"
SERVER_PORT="${SERVER_PORT-}"
INSTAGRAM_BROWSER_AUTOMATION_ENABLED="${INSTAGRAM_BROWSER_AUTOMATION_ENABLED-}"
INSTAGRAM_BROWSER_HEADLESS="${INSTAGRAM_BROWSER_HEADLESS-}"
INSTAGRAM_BROWSER_BATCH_SIZE="${INSTAGRAM_BROWSER_BATCH_SIZE-}"
INSTAGRAM_BROWSER_USER_DATA_DIR="${INSTAGRAM_BROWSER_USER_DATA_DIR-}"

usage() {
	cat <<'EOF'
Usage:
  ./scripts/run-local.sh
  ./scripts/run-local.sh --install-browser
  ./scripts/run-local.sh --reset-token

Options:
  --install-browser  Playwright Chromium을 설치한 뒤 애플리케이션을 실행한다.
  --reset-token      새 token을 검증한 뒤 macOS Keychain 값을 교체한다.
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
	if ! is_interactive_terminal; then
		die "터미널 입력이 필요하다. 대화형 터미널에서 ./scripts/run-local.sh를 실행한다."
	fi
}

is_interactive_terminal() {
	[[ -t 0 ]]
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

keychain_is_available() {
	is_macos && command -v security >/dev/null 2>&1
}

read_keychain_token() {
	security find-generic-password \
		-a "$KEYCHAIN_ACCOUNT" \
		-s "$KEYCHAIN_SERVICE" \
		-w 2>/dev/null
}

read_hidden_token() {
	local label="$1"
	local token=""

	require_interactive_terminal
	printf '%s: ' "$label" >&2
	IFS= read -r -s token || die "token 입력을 읽지 못했다."
	printf '\n' >&2
	META_ACCESS_TOKEN="$token"
	TOKEN_SOURCE="terminal (현재 실행)"
}

classify_meta_validation_response() {
	local http_status="$1"
	local response_body="$2"
	local compact_body=""
	local graph_error_pattern='"error"[[:space:]]*:'
	local graph_code_pattern='"code"[[:space:]]*:[[:space:]]*([0-9]+)'
	local expected_id_pattern="\"id\"[[:space:]]*:[[:space:]]*\"${META_IG_USER_ID}\""

	META_VALIDATION_RESULT=""
	META_VALIDATION_HTTP_STATUS="$http_status"
	META_VALIDATION_GRAPH_CODE=""

	if [[ ! "$http_status" =~ ^[0-9]{3}$ ]]; then
		META_VALIDATION_RESULT="network_error"
		return
	fi

	# 응답 전문은 출력하지 않고 token 교체 여부에 필요한 숫자 code와 expected ID만 판정한다.
	compact_body="${response_body//$'\r'/}"
	compact_body="${compact_body//$'\n'/}"
	if [[ "$compact_body" =~ $graph_error_pattern ]]; then
		if [[ "$compact_body" =~ $graph_code_pattern ]]; then
			META_VALIDATION_GRAPH_CODE="${BASH_REMATCH[1]}"
		fi
		if [[ "$META_VALIDATION_GRAPH_CODE" == "190" ]]; then
			META_VALIDATION_RESULT="invalid"
		else
			META_VALIDATION_RESULT="api_error"
		fi
		return
	fi

	if [[ "$http_status" =~ ^2[0-9]{2}$ && "$compact_body" =~ $expected_id_pattern ]]; then
		META_VALIDATION_RESULT="valid"
	elif [[ "$http_status" =~ ^2[0-9]{2}$ ]]; then
		META_VALIDATION_RESULT="response_error"
	else
		META_VALIDATION_RESULT="api_error"
	fi
}

perform_meta_validation_request() {
	local token="$1"
	local escaped_token=""
	local response_with_status=""
	local response_body=""
	local http_status=""

	if ! command -v curl >/dev/null 2>&1; then
		META_VALIDATION_RESULT="dependency_error"
		META_VALIDATION_HTTP_STATUS=""
		META_VALIDATION_GRAPH_CODE=""
		return
	fi

	# Bearer value는 argv가 아닌 curl stdin config로 전달해 process 목록에 노출하지 않는다.
	escaped_token="${token//\\/\\\\}"
	escaped_token="${escaped_token//\"/\\\"}"
	if ! response_with_status="$(
		printf 'header = "Authorization: Bearer %s"\n' "$escaped_token" |
			curl --config - \
				--silent \
				--connect-timeout 10 \
				--max-time 20 \
				--max-filesize 1048576 \
				--proto '=https' \
				--header 'Accept: application/json' \
				--user-agent 'hr-sns-auto-token-validation/1.0' \
				--write-out $'\n%{http_code}' \
				--url "https://graph.facebook.com/${META_GRAPH_API_VERSION}/${META_IG_USER_ID}?fields=id" \
				2>/dev/null
	)"; then
		META_VALIDATION_RESULT="network_error"
		META_VALIDATION_HTTP_STATUS=""
		META_VALIDATION_GRAPH_CODE=""
		return
	fi

	http_status="${response_with_status##*$'\n'}"
	response_body="${response_with_status%$'\n'*}"
	classify_meta_validation_response "$http_status" "$response_body"
}

validate_meta_token() {
	local token="$1"

	if ! is_valid_token "$token"; then
		META_VALIDATION_RESULT="invalid"
		META_VALIDATION_HTTP_STATUS=""
		META_VALIDATION_GRAPH_CODE=""
		return
	fi
	perform_meta_validation_request "$token"
}

print_meta_validation_result() {
	printf '\nMeta token: validating...\n'
	validate_meta_token "$META_ACCESS_TOKEN"
	case "$META_VALIDATION_RESULT" in
		valid) printf 'Meta token: valid (%s)\n' "$TOKEN_SOURCE" ;;
		invalid) printf 'Meta token: invalid/expired - replacement required\n' ;;
		*) printf 'Meta token: validation failed\n' ;;
	esac
}

validation_error_suffix() {
	local suffix=""

	if [[ -n "$META_VALIDATION_HTTP_STATUS" ]]; then
		suffix="HTTP $META_VALIDATION_HTTP_STATUS"
	fi
	if [[ -n "$META_VALIDATION_GRAPH_CODE" ]]; then
		if [[ -n "$suffix" ]]; then
			suffix+=", "
		fi
		suffix+="code $META_VALIDATION_GRAPH_CODE"
	fi
	if [[ -n "$suffix" ]]; then
		printf ' (%s)' "$suffix"
	fi
}

abort_for_meta_validation_failure() {
	local suffix=""

	suffix="$(validation_error_suffix)"
	case "$META_VALIDATION_RESULT" in
		network_error)
			die "Meta token 검증 중 Graph API에 연결하지 못했다. Keychain token은 변경하지 않았다. 네트워크를 확인하고 다시 실행한다."
			;;
		api_error)
			die "Meta token 검증이 token 만료가 아닌 Graph API 오류로 실패했다${suffix}. Keychain token은 변경하지 않았다. 권한, IG User ID, API version 또는 rate limit을 확인한다."
			;;
		response_error)
			die "Meta token 검증 응답에서 configured IG User ID를 확인하지 못했다${suffix}. Keychain token은 변경하지 않았다."
			;;
		dependency_error)
			die "Meta token 검증에 필요한 curl 명령을 찾을 수 없다. Keychain token은 변경하지 않았다."
			;;
		*) die "Meta token 검증 결과를 판정하지 못했다. Keychain token은 변경하지 않았다." ;;
	esac
}

store_token_in_keychain() {
	local escaped_token="${META_ACCESS_TOKEN//\\/\\\\}"
	local keychain_command=""
	local stored_token=""

	# security interactive mode의 stdin으로 전달해 token을 command line argument에 넣지 않는다.
	escaped_token="${escaped_token//\"/\\\"}"
	printf -v keychain_command \
		'add-generic-password -a "%s" -s "%s" -l "hr-sns-auto Meta access token" -w "%s" -U\n' \
		"$KEYCHAIN_ACCOUNT" "$KEYCHAIN_SERVICE" "$escaped_token"
	if ! printf '%s' "$keychain_command" | security -q -i >/dev/null 2>&1; then
		return 1
	fi
	# interactive security process의 종료 code와 별개로 실제 저장값을 다시 읽어 update 성공을 확인한다.
	stored_token="$(read_keychain_token)" || return 1
	[[ "$stored_token" == "$META_ACCESS_TOKEN" ]]
}

offer_keychain_storage() {
	local answer=""

	while true; do
		printf 'macOS Keychain에 token을 저장할지 [Y/n]: ' >&2
		IFS= read -r answer || die "입력을 읽지 못했다."
		case "$answer" in
			""|y|Y|yes|YES|Yes)
				if store_token_in_keychain; then
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

select_preferred_token() {
	local keychain_token=""

	# 일상적인 대화형 macOS 실행은 persistent store를 우선해 오래된 shell export를 가린다.
	if is_interactive_terminal && keychain_is_available \
		&& keychain_token="$(read_keychain_token)" \
		&& [[ -n "$keychain_token" ]]; then
		META_ACCESS_TOKEN="$keychain_token"
		TOKEN_SOURCE="Keychain"
		return 0
	fi

	# 비대화형 CI/automation과 Keychain이 없는 환경에서는 명시적 process env를 허용한다.
	if [[ -n "$PROCESS_META_ACCESS_TOKEN" ]]; then
		META_ACCESS_TOKEN="$PROCESS_META_ACCESS_TOKEN"
		TOKEN_SOURCE="process environment"
		return 0
	fi
	return 1
}

prompt_for_valid_token() {
	local prompt_label="$1"
	local storage_policy="$2"
	local attempt=1

	while (( attempt <= TOKEN_INPUT_MAX_ATTEMPTS )); do
		read_hidden_token "$prompt_label"
		print_meta_validation_result
		case "$META_VALIDATION_RESULT" in
			valid)
				if keychain_is_available; then
					if [[ "$storage_policy" == "replace" ]]; then
						if store_token_in_keychain; then
							TOKEN_SOURCE="Keychain"
							printf 'macOS Keychain token을 교체했다.\n'
						else
							warn "Keychain 저장에 실패했다. token은 현재 실행에서만 사용한다."
						fi
					else
						offer_keychain_storage
					fi
				elif is_macos; then
					warn "macOS security 명령을 찾지 못했다. token은 현재 실행에서만 사용한다."
				fi
				return
				;;
			invalid)
				if (( attempt < TOKEN_INPUT_MAX_ATTEMPTS )); then
					warn "입력한 token이 유효하지 않다. 다시 입력한다 ($attempt/$TOKEN_INPUT_MAX_ATTEMPTS)."
				fi
				;;
			*) abort_for_meta_validation_failure ;;
		esac
		attempt=$((attempt + 1))
	done

	die "유효한 Meta access token을 $TOKEN_INPUT_MAX_ATTEMPTS회 안에 확인하지 못했다."
}

configure_token() {
	# --reset-token은 env와 기존 Keychain을 읽지 않고 검증된 새 값으로만 교체한다.
	if [[ "$RESET_TOKEN" == "true" ]]; then
		require_interactive_terminal
		prompt_for_valid_token "새 META_ACCESS_TOKEN" "replace"
		PROCESS_META_ACCESS_TOKEN=""
		return
	fi

	if select_preferred_token; then
		PROCESS_META_ACCESS_TOKEN=""
		print_meta_validation_result
		case "$META_VALIDATION_RESULT" in
			valid) return ;;
			invalid)
				printf 'Meta access token이 만료되었거나 유효하지 않다.\n'
				prompt_for_valid_token "새 META_ACCESS_TOKEN" "offer"
				return
				;;
			*) abort_for_meta_validation_failure ;;
		esac
	fi

	PROCESS_META_ACCESS_TOKEN=""
	prompt_for_valid_token "META_ACCESS_TOKEN" "offer"
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
	printf 'Meta token:    valid (%s)\n' "$TOKEN_SOURCE"
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
	# 검증된 token은 Spring Boot를 exec하기 직전에만 process environment로 승격한다.
	export META_ACCESS_TOKEN
	exec ./mvnw spring-boot:run
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
	main "$@"
fi
