#!/usr/bin/env bash

set -euo pipefail

readonly TEST_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 개발 shell의 실제 credential이 fixture에 섞이지 않도록 source 전에 제거한다.
unset META_ACCESS_TOKEN
source "$TEST_SCRIPT_DIR/run-local.sh"

TEST_FAILURE_COUNT=0

fail_assertion() {
	printf 'assertion failed: %s\n' "$1" >&2
	return 1
}

assert_equals() {
	local expected="$1"
	local actual="$2"
	local label="$3"

	[[ "$actual" == "$expected" ]] || fail_assertion "$label"
}

assert_contains() {
	local value="$1"
	local expected_fragment="$2"
	local label="$3"

	[[ "$value" == *"$expected_fragment"* ]] || fail_assertion "$label"
}

assert_not_contains() {
	local value="$1"
	local forbidden_fragment="$2"
	local label="$3"

	[[ "$value" != *"$forbidden_fragment"* ]] || fail_assertion "$label"
}

run_test() {
	local name="$1"
	local test_function="$2"

	if "$test_function"; then
		printf 'PASS %s\n' "$name"
	else
		printf 'FAIL %s\n' "$name" >&2
		TEST_FAILURE_COUNT=$((TEST_FAILURE_COUNT + 1))
	fi
}

test_valid_response_continues() (
	META_IG_USER_ID="17841400000000000"
	classify_meta_validation_response "200" '{"id":"17841400000000000"}'

	assert_equals "valid" "$META_VALIDATION_RESULT" "configured ID의 2xx response는 valid여야 한다"
)

test_oauth_code_190_requires_replacement() (
	META_IG_USER_ID="17841400000000000"
	classify_meta_validation_response "400" \
		'{"error":{"message":"expired","type":"OAuthException","code":190,"error_subcode":463}}'

	assert_equals "invalid" "$META_VALIDATION_RESULT" "OAuth code 190은 invalid로 분류해야 한다" || return 1
	assert_equals "190" "$META_VALIDATION_GRAPH_CODE" "Graph error code를 안전한 숫자로 보존해야 한다"
)

test_permission_error_is_not_expiry() (
	META_IG_USER_ID="17841400000000000"
	classify_meta_validation_response "400" \
		'{"error":{"message":"Application does not have permission","type":"OAuthException","code":10}}'

	assert_equals "api_error" "$META_VALIDATION_RESULT" "permission error를 invalid token으로 오판하면 안 된다" || return 1
	assert_equals "10" "$META_VALIDATION_GRAPH_CODE" "permission error code를 진단에 남겨야 한다"
)

test_curl_receives_bearer_via_stdin_config() (
	local synthetic_token="synthetic-curl-token+/="
	local temp_dir=""
	local argument_log=""
	local stdin_log=""
	local arguments=""
	local stdin_config=""

	temp_dir="$(mktemp -d)"
	trap 'rm -rf "$temp_dir"' EXIT
	argument_log="$temp_dir/curl-arguments"
	stdin_log="$temp_dir/curl-stdin"

	curl() {
		printf '%s\n' "$@" >"$argument_log"
		while IFS= read -r line; do
			printf '%s\n' "$line"
		done >"$stdin_log"
		printf '{"id":"17841400000000000"}\n200'
	}

	META_GRAPH_API_VERSION="v26.0"
	META_IG_USER_ID="17841400000000000"
	perform_meta_validation_request "$synthetic_token"
	arguments="$(<"$argument_log")"
	stdin_config="$(<"$stdin_log")"

	assert_equals "valid" "$META_VALIDATION_RESULT" "fake curl valid response를 통과해야 한다" || return 1
	assert_not_contains "$arguments" "$synthetic_token" "token이 curl argv에 포함되면 안 된다" || return 1
	assert_not_contains "$arguments" "Authorization: Bearer" "Authorization header가 curl argv에 포함되면 안 된다" || return 1
	assert_contains "$stdin_config" "Authorization: Bearer $synthetic_token" "Bearer header는 curl stdin config로 전달해야 한다"
)

test_keychain_write_uses_stdin_and_update() (
	local synthetic_token="synthetic-keychain-token+/="
	local temp_dir=""
	local argument_log=""
	local stdin_log=""
	local arguments=""
	local stdin_command=""

	temp_dir="$(mktemp -d)"
	trap 'rm -rf "$temp_dir"' EXIT
	argument_log="$temp_dir/security-arguments"
	stdin_log="$temp_dir/security-stdin"

	security() {
		printf '%s\n' "$@" >"$argument_log"
		while IFS= read -r line; do
			printf '%s\n' "$line"
		done >"$stdin_log"
	}
	read_keychain_token() { printf '%s' "$synthetic_token"; }

	META_ACCESS_TOKEN="$synthetic_token"
	store_token_in_keychain
	arguments="$(<"$argument_log")"
	stdin_command="$(<"$stdin_log")"

	assert_not_contains "$arguments" "$synthetic_token" "token이 security argv에 포함되면 안 된다" || return 1
	assert_contains "$stdin_command" "add-generic-password" "generic password 저장 command여야 한다" || return 1
	assert_contains "$stdin_command" "-U" "Keychain 저장은 기존 값을 update해야 한다" || return 1
	assert_contains "$stdin_command" "$synthetic_token" "security interactive stdin에는 token이 전달되어야 한다"
)

test_interactive_macos_prefers_keychain_over_stale_env() (
	local expected_keychain_token="synthetic-keychain-current"
	local stale_env_token="synthetic-env-stale"
	local temp_dir=""
	local output_file=""
	local output=""

	temp_dir="$(mktemp -d)"
	trap 'rm -rf "$temp_dir"' EXIT
	output_file="$temp_dir/output"

	is_interactive_terminal() { return 0; }
	keychain_is_available() { return 0; }
	read_keychain_token() { printf '%s' "$expected_keychain_token"; }
	validate_meta_token() {
		assert_equals "$expected_keychain_token" "$1" "대화형 macOS에서는 Keychain token을 검증해야 한다" || return 1
		META_VALIDATION_RESULT="valid"
		META_VALIDATION_HTTP_STATUS="200"
		META_VALIDATION_GRAPH_CODE=""
	}

	RESET_TOKEN=false
	PROCESS_META_ACCESS_TOKEN="$stale_env_token"
	configure_token >"$output_file" 2>&1
	output="$(<"$output_file")"

	assert_equals "$expected_keychain_token" "$META_ACCESS_TOKEN" "Keychain token이 선택되어야 한다" || return 1
	assert_equals "" "$PROCESS_META_ACCESS_TOKEN" "stale process token 후보를 사용 후 지워야 한다" || return 1
	assert_equals "Keychain" "$TOKEN_SOURCE" "summary source가 Keychain이어야 한다" || return 1
	assert_contains "$output" "Meta token: valid (Keychain)" "선택 source를 명확히 출력해야 한다" || return 1
	assert_not_contains "$output" "$expected_keychain_token" "Keychain token raw value를 출력하면 안 된다" || return 1
	assert_not_contains "$output" "$stale_env_token" "stale env token raw value를 출력하면 안 된다"
)

test_invalid_replacement_retries_then_stores_only_valid_token() (
	local expired_keychain_token="synthetic-keychain-expired"
	local invalid_new_token="synthetic-new-invalid"
	local valid_new_token="synthetic-new-valid"
	local prompt_count=0
	local store_count=0
	local stored_token=""
	local events=""
	local temp_dir=""
	local output_file=""
	local output=""

	temp_dir="$(mktemp -d)"
	trap 'rm -rf "$temp_dir"' EXIT
	output_file="$temp_dir/output"

	is_interactive_terminal() { return 0; }
	keychain_is_available() { return 0; }
	read_keychain_token() { printf '%s' "$expired_keychain_token"; }
	read_hidden_token() {
		prompt_count=$((prompt_count + 1))
		if (( prompt_count == 1 )); then
			META_ACCESS_TOKEN="$invalid_new_token"
		else
			META_ACCESS_TOKEN="$valid_new_token"
		fi
		TOKEN_SOURCE="terminal (현재 실행)"
	}
	validate_meta_token() {
		events+="validate:$1 "
		META_VALIDATION_HTTP_STATUS="400"
		META_VALIDATION_GRAPH_CODE="190"
		if [[ "$1" == "$valid_new_token" ]]; then
			META_VALIDATION_RESULT="valid"
			META_VALIDATION_HTTP_STATUS="200"
			META_VALIDATION_GRAPH_CODE=""
		else
			META_VALIDATION_RESULT="invalid"
		fi
	}
	offer_keychain_storage() {
		store_count=$((store_count + 1))
		stored_token="$META_ACCESS_TOKEN"
		events+="store:$META_ACCESS_TOKEN "
		TOKEN_SOURCE="Keychain"
	}

	RESET_TOKEN=false
	PROCESS_META_ACCESS_TOKEN=""
	configure_token >"$output_file" 2>&1
	output="$(<"$output_file")"

	assert_equals "2" "$prompt_count" "invalid한 새 token은 bounded retry로 재입력해야 한다" || return 1
	assert_equals "1" "$store_count" "valid token 확인 후 한 번만 저장해야 한다" || return 1
	assert_equals "$valid_new_token" "$stored_token" "invalid token을 Keychain에 저장하면 안 된다" || return 1
	assert_equals \
		"validate:$expired_keychain_token validate:$invalid_new_token validate:$valid_new_token store:$valid_new_token " \
		"$events" "validation 전에는 저장하지 않아야 한다" || return 1
	assert_contains "$output" "invalid/expired - replacement required" "code 190은 replacement UX로 연결되어야 한다" || return 1
	assert_not_contains "$output" "$expired_keychain_token" "기존 token raw value를 출력하면 안 된다" || return 1
	assert_not_contains "$output" "$invalid_new_token" "invalid 새 token raw value를 출력하면 안 된다" || return 1
	assert_not_contains "$output" "$valid_new_token" "valid 새 token raw value를 출력하면 안 된다"
)

test_network_failure_preserves_keychain_and_does_not_prompt() (
	local current_keychain_token="synthetic-keychain-network"
	local temp_dir=""
	local output_file=""
	local mutation_log=""
	local output=""
	local status=0

	temp_dir="$(mktemp -d)"
	trap 'rm -rf "$temp_dir"' EXIT
	output_file="$temp_dir/output"
	mutation_log="$temp_dir/mutations"

	is_interactive_terminal() { return 0; }
	keychain_is_available() { return 0; }
	read_keychain_token() { printf '%s' "$current_keychain_token"; }
	validate_meta_token() {
		META_VALIDATION_RESULT="network_error"
		META_VALIDATION_HTTP_STATUS=""
		META_VALIDATION_GRAPH_CODE=""
	}
	read_hidden_token() { printf 'prompt\n' >>"$mutation_log"; }
	store_token_in_keychain() { printf 'store\n' >>"$mutation_log"; }

	RESET_TOKEN=false
	PROCESS_META_ACCESS_TOKEN=""
	if (configure_token) >"$output_file" 2>&1; then
		status=0
	else
		status=$?
	fi
	output="$(<"$output_file")"

	[[ "$status" -ne 0 ]] || fail_assertion "network failure는 실행을 중단해야 한다" || return 1
	[[ ! -e "$mutation_log" ]] || fail_assertion "network failure에서 prompt/store를 호출하면 안 된다" || return 1
	assert_contains "$output" "Keychain token은 변경하지 않았다" "network failure는 Keychain 보존을 안내해야 한다" || return 1
	assert_not_contains "$output" "$current_keychain_token" "network failure에도 raw token을 출력하면 안 된다"
)

test_reset_token_ignores_env_and_replaces_keychain_after_validation() (
	local stale_env_token="synthetic-reset-stale-env"
	local new_token="synthetic-reset-new-valid"
	local lookup_count=0
	local store_count=0
	local stored_token=""
	local temp_dir=""
	local output_file=""
	local output=""

	temp_dir="$(mktemp -d)"
	trap 'rm -rf "$temp_dir"' EXIT
	output_file="$temp_dir/output"

	is_interactive_terminal() { return 0; }
	keychain_is_available() { return 0; }
	read_keychain_token() { lookup_count=$((lookup_count + 1)); printf 'must-not-be-used'; }
	read_hidden_token() {
		META_ACCESS_TOKEN="$new_token"
		TOKEN_SOURCE="terminal (현재 실행)"
	}
	validate_meta_token() {
		assert_equals "$new_token" "$1" "reset은 새 token만 검증해야 한다" || return 1
		META_VALIDATION_RESULT="valid"
		META_VALIDATION_HTTP_STATUS="200"
		META_VALIDATION_GRAPH_CODE=""
	}
	store_token_in_keychain() {
		store_count=$((store_count + 1))
		stored_token="$META_ACCESS_TOKEN"
	}

	RESET_TOKEN=true
	PROCESS_META_ACCESS_TOKEN="$stale_env_token"
	configure_token >"$output_file" 2>&1
	output="$(<"$output_file")"

	assert_equals "0" "$lookup_count" "reset은 기존 Keychain token을 읽으면 안 된다" || return 1
	assert_equals "1" "$store_count" "reset valid token은 Keychain을 교체해야 한다" || return 1
	assert_equals "$new_token" "$stored_token" "reset은 새 valid token을 저장해야 한다" || return 1
	assert_equals "Keychain" "$TOKEN_SOURCE" "reset 저장 성공 후 source는 Keychain이어야 한다" || return 1
	assert_equals "" "$PROCESS_META_ACCESS_TOKEN" "reset은 stale env 후보를 폐기해야 한다" || return 1
	assert_not_contains "$output" "$stale_env_token" "reset output에 stale env token이 있으면 안 된다" || return 1
	assert_not_contains "$output" "$new_token" "reset output에 새 token이 있으면 안 된다"
)

test_noninteractive_automation_uses_process_env() (
	local automation_token="synthetic-automation-env"
	local keychain_read_count=0

	is_interactive_terminal() { return 1; }
	keychain_is_available() { return 0; }
	read_keychain_token() { keychain_read_count=$((keychain_read_count + 1)); printf 'must-not-be-used'; }

	PROCESS_META_ACCESS_TOKEN="$automation_token"
	select_preferred_token

	assert_equals "$automation_token" "$META_ACCESS_TOKEN" "automation에서는 process env를 선택해야 한다" || return 1
	assert_equals "process environment" "$TOKEN_SOURCE" "automation source를 명시해야 한다" || return 1
	assert_equals "0" "$keychain_read_count" "비대화형 automation은 Keychain을 읽지 않아야 한다"
)

run_test "valid token response" test_valid_response_continues
run_test "OAuth code 190 replacement classification" test_oauth_code_190_requires_replacement
run_test "permission error classification" test_permission_error_is_not_expiry
run_test "curl stdin Bearer transport" test_curl_receives_bearer_via_stdin_config
run_test "Keychain stdin update" test_keychain_write_uses_stdin_and_update
run_test "interactive Keychain precedence" test_interactive_macos_prefers_keychain_over_stale_env
run_test "invalid replacement retry and save" test_invalid_replacement_retries_then_stores_only_valid_token
run_test "network failure Keychain preservation" test_network_failure_preserves_keychain_and_does_not_prompt
run_test "--reset-token replacement" test_reset_token_ignores_env_and_replaces_keychain_after_validation
run_test "automation process env" test_noninteractive_automation_uses_process_env

if (( TEST_FAILURE_COUNT > 0 )); then
	printf '%s test(s) failed\n' "$TEST_FAILURE_COUNT" >&2
	exit 1
fi

printf 'All run-local token lifecycle tests passed.\n'
