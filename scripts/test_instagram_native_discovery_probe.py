import json
import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from scripts.instagram_native_discovery_probe import (
    ApiResult,
    CapabilityTracker,
    CandidateCollector,
    GraphClient,
    GraphUrlBuilder,
    ProbeConfig,
    RequestSpec,
    ProbeState,
    _record_baseline_capability,
    assess_collection_identity,
    assess_object_identity,
    classify_failed_result,
    execute_probe,
    redact_text,
    sanitize_graph_error,
    sanitize_url,
)


class FakeResponse:
    def __init__(self, payload: object, status: int = 200) -> None:
        self._raw = json.dumps(payload).encode("utf-8")
        self._status = status

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        return None

    def read(self, size: int = -1) -> bytes:
        return self._raw if size < 0 else self._raw[:size]

    def getcode(self) -> int:
        return self._status


class SecurityTest(unittest.TestCase):
    def test_redacts_raw_encoded_query_and_bearer_tokens(self) -> None:
        token = "synthetic-token+/="
        value = (
            "raw=synthetic-token+/= "
            "access_token=synthetic-token%2B%2F%3D "
            "Authorization: Bearer synthetic-token+/="
        )

        redacted = redact_text(value, (token,))

        self.assertNotIn(token, redacted)
        self.assertNotIn("synthetic-token%2B%2F%3D", redacted)
        self.assertGreaterEqual(redacted.count("[REDACTED]"), 3)

    def test_sanitized_url_removes_token_and_cursor(self) -> None:
        url = (
            "https://graph.facebook.com/v25.0/me/accounts?"
            "access_token=secret-token&after=opaque-cursor&fields=id"
        )

        sanitized = sanitize_url(url, ("secret-token",))

        self.assertNotIn("secret-token", sanitized)
        self.assertNotIn("opaque-cursor", sanitized)
        self.assertIn("%5BREDACTED%5D", sanitized)
        self.assertIn("%5BCURSOR%5D", sanitized)

    def test_graph_error_keeps_only_diagnostic_fields(self) -> None:
        payload = {
            "error": {
                "type": "OAuthException",
                "code": 190,
                "message": "token secret-token expired",
                "fbtrace_id": "trace-that-is-not-needed",
            }
        }

        sanitized = sanitize_graph_error(payload, ("secret-token",))

        self.assertEqual(
            {
                "type": "OAuthException",
                "code": 190,
                "message": "token [REDACTED] expired",
            },
            sanitized,
        )


class EndpointAndClientTest(unittest.TestCase):
    def test_endpoint_builder_is_versioned_and_never_adds_access_token(self) -> None:
        urls = GraphUrlBuilder("v25.0")

        lookup_url = urls.url_for(urls.hashtag_lookup("123", "의사스타그램"))
        media_url = urls.url_for(
            urls.recent_media("456", "123", "id,username", 25, "MEDIA_USERNAME")
        )

        self.assertTrue(lookup_url.startswith("https://graph.facebook.com/v25.0/ig_hashtag_search?"))
        self.assertIn("user_id=123", lookup_url)
        self.assertIn("q=%EC%9D%98%EC%82%AC", lookup_url)
        self.assertTrue(media_url.startswith("https://graph.facebook.com/v25.0/456/recent_media?"))
        self.assertNotIn("access_token", lookup_url)
        self.assertNotIn("access_token", media_url)

    def test_client_uses_bearer_header_and_report_url_has_no_token(self) -> None:
        token = "secret-token"
        urls = GraphUrlBuilder("v25.0")
        client = GraphClient(token, urls, 1.0)
        spec = RequestSpec("TEST", "/object", {"fields": "id"})

        with patch(
            "scripts.instagram_native_discovery_probe.urllib.request.urlopen",
            return_value=FakeResponse({"id": "1"}),
        ) as urlopen:
            result = client.get(spec)

        request = urlopen.call_args.args[0]
        self.assertEqual(f"Bearer {token}", request.get_header("Authorization"))
        self.assertNotIn(token, request.full_url)
        self.assertNotIn(token, result.request_url)
        self.assertTrue(result.ok)


class ClassificationTest(unittest.TestCase):
    def test_auth_error_is_auth_blocked(self) -> None:
        result = ApiResult(
            "TEST",
            "https://example.test",
            400,
            graph_error={
                "type": "OAuthException",
                "code": 190,
                "message": "Invalid OAuth access token",
            },
        )

        self.assertEqual("AUTH_BLOCKED", classify_failed_result(result))

    def test_unknown_field_is_unsupported(self) -> None:
        result = ApiResult(
            "TEST",
            "https://example.test",
            400,
            graph_error={
                "type": "OAuthException",
                "code": 100,
                "message": "Tried accessing nonexisting field (username)",
            },
        )

        self.assertEqual("UNSUPPORTED", classify_failed_result(result))

    def test_transport_or_server_error_stays_unknown(self) -> None:
        result = ApiResult(
            "TEST", "https://example.test", 500, local_error="temporary server error"
        )

        self.assertEqual("UNKNOWN", classify_failed_result(result))

    def test_baseline_field_error_can_fall_back_to_minimal_id(self) -> None:
        tracker = CapabilityTracker()
        state = ProbeState()
        field_error = ApiResult(
            "RECENT_MEDIA",
            "https://example.test/recent_media?fields=caption",
            400,
            graph_error={
                "type": "OAuthException",
                "code": 100,
                "message": "Tried accessing nonexisting field (caption)",
            },
        )
        minimal_success = ApiResult(
            "RECENT_MEDIA",
            "https://example.test/recent_media?fields=id",
            200,
            payload={"data": [{"id": "m1"}]},
        )

        first_status = _record_baseline_capability(
            tracker, state, field_error, "의사스타그램", "metadata baseline"
        )
        second_status = _record_baseline_capability(
            tracker, state, minimal_success, "의사스타그램", "minimal id fallback"
        )

        self.assertEqual("UNSUPPORTED", first_status)
        self.assertEqual("SUPPORTED", second_status)
        self.assertEqual("SUPPORTED", tracker.status("RECENT_MEDIA"))
        self.assertEqual({"m1"}, state.observed_media_keys)


class IdentityParsingTest(unittest.TestCase):
    def test_collection_username_uses_only_explicit_username_field(self) -> None:
        assessment = assess_collection_identity(
            {
                "data": [
                    {
                        "id": "m1",
                        "caption": "@caption_name",
                        "permalink": "https://instagram.example/guessed_name/post",
                        "username": "api_username",
                    },
                    {
                        "id": "m2",
                        "caption": "@must_not_be_used",
                    },
                ]
            },
            "username",
        )

        self.assertEqual("SUPPORTED", assessment.status)
        self.assertEqual(["api_username"], [record.username for record in assessment.usernames])
        self.assertNotIn("caption_name", [record.username for record in assessment.usernames])
        self.assertNotIn("must_not_be_used", [record.username for record in assessment.usernames])

    def test_collection_without_explicit_identity_is_unsupported(self) -> None:
        assessment = assess_collection_identity(
            {"data": [{"id": "m1", "caption": "@not_identity"}]}, "username"
        )

        self.assertEqual("UNSUPPORTED", assessment.status)
        self.assertEqual([], assessment.usernames)

    def test_owner_identity_and_nested_username_are_observed_separately(self) -> None:
        assessment = assess_collection_identity(
            {
                "data": [
                    {"id": "m1", "owner": {"id": "a1"}},
                    {"id": "m2", "owner": {"id": "a2", "username": "owner_user"}},
                ]
            },
            "owner",
        )

        self.assertEqual("SUPPORTED", assessment.status)
        self.assertEqual({"m1", "m2"}, assessment.identity_media_keys)
        self.assertEqual(["owner_user"], [record.username for record in assessment.usernames])

    def test_empty_data_is_unknown_not_unsupported(self) -> None:
        assessment = assess_collection_identity({"data": []}, "username")

        self.assertEqual("UNKNOWN", assessment.status)

    def test_followup_owner_id_without_username_is_supported_identity(self) -> None:
        assessment = assess_object_identity(
            {"id": "m1", "owner": {"id": "a1"}}, "owner"
        )

        self.assertEqual("SUPPORTED", assessment.status)
        self.assertEqual({"m1"}, assessment.identity_media_keys)
        self.assertEqual([], assessment.usernames)


class CandidateCollectorTest(unittest.TestCase):
    def test_dedupes_username_case_insensitively_and_counts_unique_media(self) -> None:
        collector = CandidateCollector()

        collector.add("Example_Doctor", ["의사스타그램"], "m1")
        collector.add("example_doctor", ["피부과"], "m2")
        collector.add("EXAMPLE_DOCTOR", ["의사스타그램"], "m1")

        self.assertEqual(1, collector.unique_count)
        self.assertEqual(2, collector.username_media_count)
        self.assertEqual(
            [
                {
                    "username": "Example_Doctor",
                    "discoveredBy": ["의사스타그램", "피부과"],
                    "sourceMediaCount": 2,
                }
            ],
            collector.report_items(15),
        )


class SyntheticProbeTest(unittest.TestCase):
    class FakeGraphClient:
        def __init__(self, access_token, url_builder, timeout_seconds) -> None:
            self.request_count = 0

        def get(self, request_spec: RequestSpec) -> ApiResult:
            self.request_count += 1
            url = f"https://graph.facebook.com/v25.0{request_spec.path}"
            operation = request_spec.operation
            if operation == "PREFLIGHT_IG_USER":
                return ApiResult(operation, url, 200, payload={"id": "123"})
            if operation == "HASHTAG_LOOKUP":
                hashtag_id = "h1" if request_spec.params["q"] == "의사스타그램" else "h2"
                return ApiResult(operation, url, 200, payload={"data": [{"id": hashtag_id}]})
            if operation == "RECENT_MEDIA":
                media_id = "m1" if "/h1/" in request_spec.path else "m2"
                return ApiResult(
                    operation,
                    url,
                    200,
                    payload={
                        "data": [
                            {
                                "id": media_id,
                                "caption": "synthetic caption must not be reported",
                            }
                        ]
                    },
                )
            if operation in {"MEDIA_USERNAME", "FOLLOWUP_MEDIA_USERNAME"}:
                is_first = "/h1/" in request_spec.path or "/m1" in request_spec.path
                media_id = "m1" if is_first else "m2"
                username = "doctor_one" if is_first else "pharmacist_two"
                payload = (
                    {"data": [{"id": media_id, "username": username}]}
                    if operation == "MEDIA_USERNAME"
                    else {"id": media_id, "username": username}
                )
                return ApiResult(operation, url, 200, payload=payload)
            return ApiResult(
                operation,
                url,
                400,
                graph_error={
                    "type": "OAuthException",
                    "code": 100,
                    "message": "Tried accessing nonexisting field (owner)",
                },
            )

    def test_end_to_end_synthetic_responses_produce_feasible_candidates(self) -> None:
        config = ProbeConfig(
            access_token="synthetic-secret",
            api_version="v25.0",
            ig_user_id="123",
            hashtags=("의사스타그램", "약사스타그램"),
            media_limit=25,
            followup_media_limit=2,
            max_candidates=15,
            timeout_seconds=1.0,
        )

        with patch(
            "scripts.instagram_native_discovery_probe.GraphClient",
            self.FakeGraphClient,
        ):
            report = execute_probe(
                config, datetime(2026, 8, 17, tzinfo=timezone.utc)
            )

        serialized = json.dumps(report, ensure_ascii=False)
        self.assertEqual("COMPLETED", report["executionStatus"])
        self.assertEqual("FEASIBLE", report["feasibility"])
        self.assertEqual(2, report["summary"]["uniqueCandidates"])
        self.assertEqual(
            ["doctor_one", "pharmacist_two"],
            [candidate["username"] for candidate in report["candidates"]],
        )
        self.assertNotIn("synthetic caption must not be reported", serialized)
        self.assertNotIn("synthetic-secret", serialized)


if __name__ == "__main__":
    unittest.main()
