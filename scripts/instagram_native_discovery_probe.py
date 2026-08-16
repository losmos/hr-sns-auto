#!/usr/bin/env python3
"""Probe official Instagram hashtag media author-identity capabilities.

This is an intentionally standalone feasibility probe. It uses only Python's
standard library, never scrapes Instagram HTML, and never infers an account
identity from captions, permalinks, or media shortcodes.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


GRAPH_API_ROOT = "https://graph.facebook.com"
DEFAULT_HASHTAGS = ("의사스타그램", "약사스타그램", "피부과")
BASELINE_MEDIA_FIELDS = "id,caption,media_type,permalink,timestamp"
CAPABILITY_NAMES = (
    "HASHTAG_LOOKUP",
    "RECENT_MEDIA",
    "MEDIA_USERNAME",
    "MEDIA_OWNER",
    "FOLLOWUP_MEDIA_USERNAME",
    "FOLLOWUP_MEDIA_OWNER",
)
CAPABILITY_STATUSES = ("SUPPORTED", "UNSUPPORTED", "AUTH_BLOCKED", "UNKNOWN")
VERSION_PATTERN = re.compile(r"^v[0-9]+\.[0-9]+$")
OBJECT_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")
ACCESS_TOKEN_PATTERN = re.compile(
    r"(?i)(access_token(?:=|%3[dD]))([^&\s]+)"
)
BEARER_PATTERN = re.compile(r"(?i)(authorization\s*:\s*bearer\s+)([^\s,]+)")
MAX_RESPONSE_BYTES = 2 * 1024 * 1024
AUTH_ERROR_CODES = {10, 190, 200, 294, 298}
AUTH_ERROR_MARKERS = (
    "access token",
    "permission",
    "permissions",
    "not authorized",
    "requires an app",
    "requires a page",
    "instagram public content access",
    "session has expired",
)
UNSUPPORTED_ERROR_MARKERS = (
    "cannot query field",
    "nonexisting field",
    "non-existing field",
    "tried accessing nonexisting field",
    "unsupported get request",
    "unknown path components",
    "does not exist",
)


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def isoformat_utc(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def redact_text(value: object, secrets: Iterable[str] = ()) -> str:
    """Remove tokens from any string before it reaches output or a report."""

    text = str(value)
    for secret in secrets:
        if not secret:
            continue
        text = text.replace(secret, "[REDACTED]")
        encoded = urllib.parse.quote(secret, safe="")
        if encoded != secret:
            text = text.replace(encoded, "[REDACTED]")
    text = ACCESS_TOKEN_PATTERN.sub(r"\1[REDACTED]", text)
    text = BEARER_PATTERN.sub(r"\1[REDACTED]", text)
    return text


def sanitize_url(url: str, secrets: Iterable[str] = ()) -> str:
    """Return a diagnostic URL without tokens or opaque paging cursors."""

    try:
        parsed = urllib.parse.urlsplit(url)
        sanitized_query: list[tuple[str, str]] = []
        for key, value in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True):
            lowered = key.casefold()
            if lowered == "access_token":
                sanitized_query.append((key, "[REDACTED]"))
            elif lowered in {"after", "before"}:
                sanitized_query.append((key, "[CURSOR]"))
            else:
                sanitized_query.append((key, redact_text(value, secrets)))
        return urllib.parse.urlunsplit(
            (
                parsed.scheme,
                parsed.netloc,
                parsed.path,
                urllib.parse.urlencode(sanitized_query),
                "",
            )
        )
    except (TypeError, ValueError):
        return redact_text(url, secrets)


def sanitize_graph_error(
    payload: Any, secrets: Iterable[str] = ()
) -> dict[str, object] | None:
    """Keep only the Graph error fields needed to diagnose prerequisites."""

    if not isinstance(payload, dict) or not isinstance(payload.get("error"), dict):
        return None
    error = payload["error"]
    sanitized: dict[str, object] = {}
    for key in ("type", "code", "error_subcode", "message"):
        value = error.get(key)
        if value is None:
            continue
        if key in {"code", "error_subcode"} and isinstance(value, (int, float)):
            sanitized[key] = int(value)
        else:
            sanitized[key] = redact_text(value, secrets)
    return sanitized or None


def _decode_json(raw: bytes) -> tuple[Any | None, str | None]:
    if not raw:
        return None, "empty response body"
    try:
        return json.loads(raw.decode("utf-8")), None
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        return None, f"response was not valid UTF-8 JSON: {error}"


def _read_limited(response: Any) -> tuple[bytes, bool]:
    raw = response.read(MAX_RESPONSE_BYTES + 1)
    if len(raw) > MAX_RESPONSE_BYTES:
        return raw[:MAX_RESPONSE_BYTES], True
    return raw, False


@dataclass(frozen=True)
class RequestSpec:
    operation: str
    path: str
    params: Mapping[str, str]


@dataclass
class ApiResult:
    operation: str
    request_url: str
    http_status: int | None
    payload: Any | None = None
    graph_error: dict[str, object] | None = None
    local_error: str | None = None

    @property
    def ok(self) -> bool:
        return (
            self.http_status is not None
            and 200 <= self.http_status < 300
            and self.local_error is None
            and self.graph_error is None
        )


class GraphUrlBuilder:
    """Isolate the endpoint layout that the live probe is intended to verify."""

    def __init__(self, api_version: str) -> None:
        if not VERSION_PATTERN.fullmatch(api_version):
            raise ValueError("META_GRAPH_API_VERSION must look like vXX.X")
        self.api_version = api_version

    @staticmethod
    def _object_segment(value: str, label: str) -> str:
        if not OBJECT_ID_PATTERN.fullmatch(value):
            raise ValueError(f"{label} contains unsupported characters")
        return urllib.parse.quote(value, safe="")

    def url_for(self, request: RequestSpec) -> str:
        if any(key.casefold() == "access_token" for key in request.params):
            raise ValueError("access_token must never be placed in a query parameter")
        query = urllib.parse.urlencode(request.params)
        base = f"{GRAPH_API_ROOT}/{self.api_version}{request.path}"
        return f"{base}?{query}" if query else base

    def verify_ig_user(self, ig_user_id: str) -> RequestSpec:
        object_id = self._object_segment(ig_user_id, "META_IG_USER_ID")
        return RequestSpec("PREFLIGHT_IG_USER", f"/{object_id}", {"fields": "id"})

    def managed_pages(self, after: str | None = None) -> RequestSpec:
        params = {
            "fields": "id,name,instagram_business_account{id}",
            "limit": "100",
        }
        if after:
            params["after"] = after
        return RequestSpec("PREFLIGHT_MANAGED_PAGES", "/me/accounts", params)

    def hashtag_lookup(self, ig_user_id: str, hashtag: str) -> RequestSpec:
        return RequestSpec(
            "HASHTAG_LOOKUP",
            "/ig_hashtag_search",
            {"user_id": ig_user_id, "q": hashtag},
        )

    def recent_media(
        self,
        hashtag_id: str,
        ig_user_id: str,
        fields: str,
        limit: int,
        operation: str,
    ) -> RequestSpec:
        object_id = self._object_segment(hashtag_id, "hashtag ID")
        return RequestSpec(
            operation,
            f"/{object_id}/recent_media",
            {"user_id": ig_user_id, "fields": fields, "limit": str(limit)},
        )

    def media(self, media_id: str, fields: str, operation: str) -> RequestSpec:
        object_id = self._object_segment(media_id, "media ID")
        return RequestSpec(operation, f"/{object_id}", {"fields": fields})


class GraphClient:
    """Small urllib client that always sends credentials in a Bearer header."""

    def __init__(
        self,
        access_token: str,
        url_builder: GraphUrlBuilder,
        timeout_seconds: float,
    ) -> None:
        self._access_token = access_token
        self._url_builder = url_builder
        self._timeout_seconds = timeout_seconds
        self.request_count = 0

    def get(self, request_spec: RequestSpec) -> ApiResult:
        request_url = self._url_builder.url_for(request_spec)
        safe_url = sanitize_url(request_url, (self._access_token,))
        request = urllib.request.Request(
            request_url,
            headers={
                "Accept": "application/json",
                "Authorization": f"Bearer {self._access_token}",
                "User-Agent": "hr-sns-auto-instagram-discovery-probe/1.0",
            },
            method="GET",
        )
        self.request_count += 1
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                raw, truncated = _read_limited(response)
                status = response.getcode()
            if truncated:
                return ApiResult(
                    request_spec.operation,
                    safe_url,
                    status,
                    local_error=f"response exceeded {MAX_RESPONSE_BYTES} bytes",
                )
            payload, decode_error = _decode_json(raw)
            return ApiResult(
                request_spec.operation,
                safe_url,
                status,
                payload=payload,
                graph_error=sanitize_graph_error(payload, (self._access_token,)),
                local_error=(
                    redact_text(decode_error, (self._access_token,))
                    if decode_error
                    else None
                ),
            )
        except urllib.error.HTTPError as error:
            raw, truncated = _read_limited(error)
            payload, decode_error = _decode_json(raw)
            graph_error = sanitize_graph_error(payload, (self._access_token,))
            local_error: str | None = None
            if truncated:
                local_error = f"error response exceeded {MAX_RESPONSE_BYTES} bytes"
            elif graph_error is None:
                detail = decode_error or f"HTTP {error.code}: {error.reason}"
                local_error = redact_text(detail, (self._access_token,))
            return ApiResult(
                request_spec.operation,
                safe_url,
                error.code,
                payload=payload,
                graph_error=graph_error,
                local_error=local_error,
            )
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            return ApiResult(
                request_spec.operation,
                safe_url,
                None,
                local_error=redact_text(error, (self._access_token,)),
            )


def classify_failed_result(result: ApiResult) -> str:
    """Classify Graph failures without treating every HTTP 400 as unsupported."""

    if result.ok:
        return "SUPPORTED"
    if result.http_status in {401, 403}:
        return "AUTH_BLOCKED"
    graph_error = result.graph_error or {}
    code = graph_error.get("code")
    message = str(graph_error.get("message", "")).casefold()
    if code in AUTH_ERROR_CODES or any(marker in message for marker in AUTH_ERROR_MARKERS):
        return "AUTH_BLOCKED"
    if any(marker in message for marker in UNSUPPORTED_ERROR_MARKERS):
        return "UNSUPPORTED"
    if result.http_status == 404:
        return "UNSUPPORTED"
    return "UNKNOWN"


class CapabilityTracker:
    def __init__(self) -> None:
        self._attempts: dict[str, list[dict[str, object]]] = {
            name: [] for name in CAPABILITY_NAMES
        }

    def record(
        self,
        capability: str,
        status: str,
        result: ApiResult,
        context: Mapping[str, object],
        observation: str,
    ) -> None:
        if capability not in self._attempts:
            raise ValueError(f"unknown capability: {capability}")
        if status not in CAPABILITY_STATUSES:
            raise ValueError(f"unknown capability status: {status}")
        attempt: dict[str, object] = {
            "status": status,
            "httpStatus": result.http_status,
            "request": {"method": "GET", "url": result.request_url},
            "context": dict(context),
            "observation": observation,
        }
        if result.graph_error:
            attempt["graphError"] = result.graph_error
        if result.local_error:
            attempt["localError"] = result.local_error
        self._attempts[capability].append(attempt)

    def status(self, capability: str) -> str:
        statuses = [attempt["status"] for attempt in self._attempts[capability]]
        if not statuses:
            return "UNKNOWN"
        if "SUPPORTED" in statuses:
            return "SUPPORTED"
        if "AUTH_BLOCKED" in statuses:
            return "AUTH_BLOCKED"
        if "UNSUPPORTED" in statuses:
            return "UNSUPPORTED"
        return "UNKNOWN"

    def as_report(self) -> dict[str, object]:
        return {
            capability: {
                "status": self.status(capability),
                "attempts": list(self._attempts[capability]),
            }
            for capability in CAPABILITY_NAMES
        }


def _data_list(payload: Any) -> list[dict[str, Any]] | None:
    if not isinstance(payload, dict) or not isinstance(payload.get("data"), list):
        return None
    return [item for item in payload["data"] if isinstance(item, dict)]


def _valid_username(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    username = value.strip()
    if not username or len(username) > 100:
        return None
    if any(character.isspace() for character in username):
        return None
    return username


def _media_id(item: Mapping[str, Any], fallback: str) -> str:
    value = item.get("id")
    if isinstance(value, (str, int)) and str(value).strip():
        return str(value)
    return fallback


@dataclass(frozen=True)
class IdentityRecord:
    username: str
    media_key: str


@dataclass
class IdentityAssessment:
    status: str
    observation: str
    usernames: list[IdentityRecord] = field(default_factory=list)
    identity_media_keys: set[str] = field(default_factory=set)
    observed_media_keys: set[str] = field(default_factory=set)


def assess_collection_identity(payload: Any, identity_field: str) -> IdentityAssessment:
    items = _data_list(payload)
    if items is None:
        return IdentityAssessment("UNKNOWN", "response did not contain a data list")
    if not items:
        return IdentityAssessment("UNKNOWN", "data list was empty; no media sample")

    usernames: list[IdentityRecord] = []
    identity_media_keys: set[str] = set()
    observed_media_keys: set[str] = set()
    for index, item in enumerate(items):
        media_key = _media_id(item, f"response-item-{index}")
        observed_media_keys.add(media_key)
        if identity_field == "username":
            username = _valid_username(item.get("username"))
            if username:
                usernames.append(IdentityRecord(username, media_key))
                identity_media_keys.add(media_key)
        elif identity_field == "owner":
            owner = item.get("owner")
            if isinstance(owner, dict):
                owner_id = owner.get("id")
                username = _valid_username(owner.get("username"))
                if owner_id is not None or username:
                    identity_media_keys.add(media_key)
                if username:
                    usernames.append(IdentityRecord(username, media_key))
            elif isinstance(owner, (str, int)) and str(owner).strip():
                identity_media_keys.add(media_key)
        else:
            raise ValueError(f"unsupported identity field: {identity_field}")

    identity_count = len(identity_media_keys)
    username_count = len({record.media_key for record in usernames})
    status = "SUPPORTED" if identity_count else "UNSUPPORTED"
    observation = (
        f"mediaItems={len(items)}, identityItems={identity_count}, "
        f"usernameItems={username_count}"
    )
    return IdentityAssessment(
        status,
        observation,
        usernames,
        identity_media_keys,
        observed_media_keys,
    )


def assess_object_identity(payload: Any, identity_field: str) -> IdentityAssessment:
    if not isinstance(payload, dict):
        return IdentityAssessment("UNKNOWN", "response was not a media object")
    media_key = _media_id(payload, "followup-response-item")
    observed = {media_key}
    if identity_field == "username":
        username = _valid_username(payload.get("username"))
        if username:
            return IdentityAssessment(
                "SUPPORTED",
                "mediaObject=1, identityItems=1, usernameItems=1",
                [IdentityRecord(username, media_key)],
                {media_key},
                observed,
            )
    elif identity_field == "owner":
        owner = payload.get("owner")
        if isinstance(owner, dict):
            username = _valid_username(owner.get("username"))
            if owner.get("id") is not None or username:
                records = [IdentityRecord(username, media_key)] if username else []
                return IdentityAssessment(
                    "SUPPORTED",
                    (
                        "mediaObject=1, identityItems=1, "
                        f"usernameItems={1 if username else 0}"
                    ),
                    records,
                    {media_key},
                    observed,
                )
        elif isinstance(owner, (str, int)) and str(owner).strip():
            return IdentityAssessment(
                "SUPPORTED",
                "mediaObject=1, identityItems=1, usernameItems=0",
                identity_media_keys={media_key},
                observed_media_keys=observed,
            )
    else:
        raise ValueError(f"unsupported identity field: {identity_field}")
    return IdentityAssessment(
        "UNSUPPORTED",
        "mediaObject=1, identityItems=0, usernameItems=0",
        observed_media_keys=observed,
    )


@dataclass
class CandidateEntry:
    username: str
    discovered_by: list[str] = field(default_factory=list)
    _hashtags: set[str] = field(default_factory=set)
    _media_keys: set[str] = field(default_factory=set)

    def add_source(self, hashtags: Sequence[str], media_key: str) -> None:
        for hashtag in hashtags:
            if hashtag not in self._hashtags:
                self._hashtags.add(hashtag)
                self.discovered_by.append(hashtag)
        self._media_keys.add(media_key)

    def as_report(self) -> dict[str, object]:
        return {
            "username": self.username,
            "discoveredBy": list(self.discovered_by),
            "sourceMediaCount": len(self._media_keys),
        }


class CandidateCollector:
    def __init__(self) -> None:
        self._entries: dict[str, CandidateEntry] = {}

    def add(self, username: str, hashtags: Sequence[str], media_key: str) -> None:
        normalized = username.casefold()
        entry = self._entries.get(normalized)
        if entry is None:
            entry = CandidateEntry(username)
            self._entries[normalized] = entry
        entry.add_source(hashtags, media_key)

    @property
    def unique_count(self) -> int:
        return len(self._entries)

    @property
    def username_media_count(self) -> int:
        media_keys: set[str] = set()
        for entry in self._entries.values():
            media_keys.update(entry._media_keys)
        return len(media_keys)

    def report_items(self, limit: int) -> list[dict[str, object]]:
        return [entry.as_report() for entry in list(self._entries.values())[:limit]]


@dataclass(frozen=True)
class ProbeConfig:
    access_token: str = field(repr=False)
    api_version: str
    ig_user_id: str | None
    hashtags: tuple[str, ...]
    media_limit: int
    followup_media_limit: int
    max_candidates: int
    timeout_seconds: float


@dataclass
class ProbeState:
    candidates: CandidateCollector = field(default_factory=CandidateCollector)
    observed_media_keys: set[str] = field(default_factory=set)
    account_identity_media_keys: set[str] = field(default_factory=set)
    media_hashtags: dict[str, list[str]] = field(default_factory=dict)

    def remember_media(self, media_key: str, hashtag: str) -> None:
        self.observed_media_keys.add(media_key)
        hashtags = self.media_hashtags.setdefault(media_key, [])
        if hashtag not in hashtags:
            hashtags.append(hashtag)

    def apply_identity(self, assessment: IdentityAssessment, hashtag: str) -> None:
        for media_key in assessment.observed_media_keys:
            self.remember_media(media_key, hashtag)
        self.account_identity_media_keys.update(assessment.identity_media_keys)
        for record in assessment.usernames:
            hashtags = self.media_hashtags.get(record.media_key, [hashtag])
            self.candidates.add(record.username, hashtags, record.media_key)


def _result_attempt(
    result: ApiResult, status: str, observation: str
) -> dict[str, object]:
    attempt: dict[str, object] = {
        "status": status,
        "httpStatus": result.http_status,
        "request": {"method": "GET", "url": result.request_url},
        "observation": observation,
    }
    if result.graph_error:
        attempt["graphError"] = result.graph_error
    if result.local_error:
        attempt["localError"] = result.local_error
    return attempt


def _next_page_cursor(payload: Any) -> str | None:
    if not isinstance(payload, dict):
        return None
    paging = payload.get("paging")
    if not isinstance(paging, dict) or not paging.get("next"):
        return None
    cursors = paging.get("cursors")
    if not isinstance(cursors, dict):
        return None
    after = cursors.get("after")
    return str(after) if after else None


def resolve_ig_user_id(
    config: ProbeConfig,
    client: GraphClient,
    urls: GraphUrlBuilder,
) -> tuple[str | None, dict[str, object], str | None]:
    attempts: list[dict[str, object]] = []
    if config.ig_user_id:
        result = client.get(urls.verify_ig_user(config.ig_user_id))
        if result.ok and isinstance(result.payload, dict) and result.payload.get("id"):
            attempts.append(_result_attempt(result, "SUPPORTED", "IG User ID is readable"))
            return (
                config.ig_user_id,
                {"status": "SUPPORTED", "igUserIdSource": "META_IG_USER_ID", "attempts": attempts},
                None,
            )
        status = classify_failed_result(result) if not result.ok else "UNKNOWN"
        attempts.append(_result_attempt(result, status, "IG User ID could not be verified"))
        return (
            None,
            {"status": status, "igUserIdSource": "META_IG_USER_ID", "attempts": attempts},
            "META_IG_USER_ID is not readable with the supplied token",
        )

    found_ids: set[str] = set()
    after: str | None = None
    seen_cursors: set[str] = set()
    for page_number in range(1, 11):
        result = client.get(urls.managed_pages(after))
        if not result.ok:
            status = classify_failed_result(result)
            attempts.append(
                _result_attempt(
                    result,
                    status,
                    f"managed Page metadata page {page_number} was not readable",
                )
            )
            return (
                None,
                {"status": status, "igUserIdSource": "AUTO_DISCOVERY", "attempts": attempts},
                "could not discover a connected Professional Instagram Account",
            )
        pages = _data_list(result.payload)
        if pages is None:
            attempts.append(
                _result_attempt(
                    result,
                    "UNKNOWN",
                    f"managed Page response page {page_number} had no data list",
                )
            )
            return (
                None,
                {"status": "UNKNOWN", "igUserIdSource": "AUTO_DISCOVERY", "attempts": attempts},
                "managed Page metadata response had an unexpected shape",
            )
        for page in pages:
            account = page.get("instagram_business_account")
            if isinstance(account, dict) and account.get("id") is not None:
                found_ids.add(str(account["id"]))
        attempts.append(
            _result_attempt(
                result,
                "SUPPORTED",
                (
                    f"managed Page metadata page {page_number} read; "
                    f"connectedAccountsFoundSoFar={len(found_ids)}"
                ),
            )
        )
        after = _next_page_cursor(result.payload)
        if not after:
            break
        if after in seen_cursors:
            return (
                None,
                {"status": "UNKNOWN", "igUserIdSource": "AUTO_DISCOVERY", "attempts": attempts},
                "managed Page pagination repeated a cursor",
            )
        seen_cursors.add(after)
    else:
        return (
            None,
            {"status": "UNKNOWN", "igUserIdSource": "AUTO_DISCOVERY", "attempts": attempts},
            "managed Page discovery exceeded the 10-page safety limit",
        )

    if len(found_ids) == 1:
        return (
            next(iter(found_ids)),
            {"status": "SUPPORTED", "igUserIdSource": "AUTO_DISCOVERY", "attempts": attempts},
            None,
        )
    if not found_ids:
        blocker = (
            "no connected Professional Instagram Account was found; set META_IG_USER_ID "
            "after connecting a Page and Professional account"
        )
    else:
        blocker = (
            "multiple connected Professional Instagram Accounts were found; set "
            "META_IG_USER_ID explicitly"
        )
    return (
        None,
        {"status": "UNKNOWN", "igUserIdSource": "AUTO_DISCOVERY", "attempts": attempts},
        blocker,
    )


def _record_collection_capability(
    tracker: CapabilityTracker,
    state: ProbeState,
    capability: str,
    result: ApiResult,
    hashtag: str,
    identity_field: str,
) -> None:
    if not result.ok:
        tracker.record(
            capability,
            classify_failed_result(result),
            result,
            {"hashtag": hashtag},
            "request failed before identity coverage could be observed",
        )
        return
    assessment = assess_collection_identity(result.payload, identity_field)
    tracker.record(
        capability,
        assessment.status,
        result,
        {"hashtag": hashtag},
        assessment.observation,
    )
    state.apply_identity(assessment, hashtag)


def _record_baseline_capability(
    tracker: CapabilityTracker,
    state: ProbeState,
    result: ApiResult,
    hashtag: str,
    request_label: str,
) -> str:
    if not result.ok:
        status = classify_failed_result(result)
        tracker.record(
            "RECENT_MEDIA",
            status,
            result,
            {"hashtag": hashtag, "request": request_label},
            f"{request_label} recent-media request failed",
        )
        return status
    media_items = _data_list(result.payload)
    if media_items is None:
        tracker.record(
            "RECENT_MEDIA",
            "UNKNOWN",
            result,
            {"hashtag": hashtag, "request": request_label},
            "response did not contain a data list",
        )
        return "UNKNOWN"
    tracker.record(
        "RECENT_MEDIA",
        "SUPPORTED",
        result,
        {"hashtag": hashtag, "request": request_label},
        f"mediaItems={len(media_items)}",
    )
    for index, media in enumerate(media_items):
        state.remember_media(
            _media_id(media, f"baseline-{hashtag}-{index}"), hashtag
        )
    return "SUPPORTED"


def _record_followup_capability(
    tracker: CapabilityTracker,
    state: ProbeState,
    capability: str,
    result: ApiResult,
    media_key: str,
    sample_number: int,
    identity_field: str,
) -> None:
    context = {"mediaSample": sample_number}
    if not result.ok:
        tracker.record(
            capability,
            classify_failed_result(result),
            result,
            context,
            "follow-up request failed before identity coverage could be observed",
        )
        return
    assessment = assess_object_identity(result.payload, identity_field)
    tracker.record(capability, assessment.status, result, context, assessment.observation)
    hashtags = state.media_hashtags.get(media_key, [])
    state.account_identity_media_keys.update(assessment.identity_media_keys)
    for record in assessment.usernames:
        state.candidates.add(record.username, hashtags, media_key)


def _derive_feasibility(
    tracker: CapabilityTracker,
    state: ProbeState,
) -> tuple[str, str]:
    candidate_count = state.candidates.unique_count
    username_media_count = state.candidates.username_media_count
    if candidate_count >= 2 and username_media_count >= 2:
        reason = "official Instagram-native discovery via hashtag is feasible"
        if 5 <= candidate_count <= 15:
            reason += "; this run produced the target-range 5–15 unique candidates"
        return "FEASIBLE", reason
    if candidate_count:
        return (
            "PARTIALLY_FEASIBLE",
            (
                "an explicit username was returned by the official API, but this run did "
                "not establish repeatable multi-candidate coverage"
            ),
        )
    if state.account_identity_media_keys:
        return (
            "PARTIALLY_FEASIBLE",
            (
                "official API responses exposed an owner/account identity for at least one "
                "media object, but no candidate username was returned"
            ),
        )

    recent_status = tracker.status("RECENT_MEDIA")
    lookup_status = tracker.status("HASHTAG_LOOKUP")
    if recent_status == "SUPPORTED":
        if state.observed_media_keys:
            return (
                "NOT_FEASIBLE_WITH_CURRENT_OFFICIAL_PATH",
                (
                    "hashtag media was returned, but none of the isolated official API "
                    "probes returned an author identity or username"
                ),
            )
        return (
            "PARTIALLY_FEASIBLE",
            "hashtag media endpoint was readable, but no media sample existed to test identity coverage",
        )
    if "AUTH_BLOCKED" in {recent_status, lookup_status}:
        return (
            "PREREQUISITE_BLOCKED",
            "token permission, app access, Page, or Professional account prerequisites blocked the core probe",
        )
    if "UNSUPPORTED" in {recent_status, lookup_status}:
        return (
            "NOT_FEASIBLE_WITH_CURRENT_OFFICIAL_PATH",
            "the current versioned official hashtag lookup or recent-media path was rejected as unsupported",
        )
    return (
        "PREREQUISITE_BLOCKED",
        "the core probe produced only transport, server, or unexpected-response results and is inconclusive",
    )


def execute_probe(config: ProbeConfig, started_at: datetime) -> dict[str, object]:
    urls = GraphUrlBuilder(config.api_version)
    client = GraphClient(config.access_token, urls, config.timeout_seconds)
    tracker = CapabilityTracker()
    state = ProbeState()
    report: dict[str, object] = {
        "schemaVersion": 1,
        "probe": "instagram_native_candidate_discovery",
        "startedAt": isoformat_utc(started_at),
        "executionStatus": "RUNNING",
        "feasibility": "NOT_RUN",
        "config": {
            "graphApiVersion": config.api_version,
            "hashtags": list(config.hashtags),
            "hashtagCount": len(config.hashtags),
            "igUserIdProvided": config.ig_user_id is not None,
            "mediaLimitPerRequest": config.media_limit,
            "followupMediaLimit": config.followup_media_limit,
            "candidateSummaryLimit": config.max_candidates,
        },
    }

    ig_user_id, preflight, blocker = resolve_ig_user_id(config, client, urls)
    report["preflight"] = preflight
    if not ig_user_id:
        report.update(
            {
                "executionStatus": "BLOCKED",
                "feasibility": "PREREQUISITE_BLOCKED",
                "verdictReason": blocker or "preflight did not produce an IG User ID",
                "capabilities": tracker.as_report(),
                "summary": {
                    "requestsMade": client.request_count,
                    "mediaObserved": 0,
                    "uniqueCandidates": 0,
                    "candidatesShown": 0,
                },
                "candidates": [],
                "finishedAt": isoformat_utc(utc_now()),
            }
        )
        return report

    for hashtag in config.hashtags:
        lookup_result = client.get(urls.hashtag_lookup(ig_user_id, hashtag))
        if not lookup_result.ok:
            tracker.record(
                "HASHTAG_LOOKUP",
                classify_failed_result(lookup_result),
                lookup_result,
                {"hashtag": hashtag},
                "hashtag lookup request failed",
            )
            continue
        lookup_items = _data_list(lookup_result.payload)
        if lookup_items is None:
            tracker.record(
                "HASHTAG_LOOKUP",
                "UNKNOWN",
                lookup_result,
                {"hashtag": hashtag},
                "response did not contain a data list",
            )
            continue
        tracker.record(
            "HASHTAG_LOOKUP",
            "SUPPORTED",
            lookup_result,
            {"hashtag": hashtag},
            f"matches={len(lookup_items)}",
        )
        hashtag_id = next(
            (
                str(item["id"])
                for item in lookup_items
                if item.get("id") is not None and str(item["id"]).strip()
            ),
            None,
        )
        if not hashtag_id:
            continue

        baseline_result = client.get(
            urls.recent_media(
                hashtag_id,
                ig_user_id,
                BASELINE_MEDIA_FIELDS,
                config.media_limit,
                "RECENT_MEDIA",
            )
        )
        baseline_status = _record_baseline_capability(
            tracker, state, baseline_result, hashtag, "metadata baseline"
        )
        if baseline_status == "UNSUPPORTED":
            # A field-level error must not be mistaken for rejection of the
            # recent_media edge itself. Retry once with only the object ID.
            minimal_result = client.get(
                urls.recent_media(
                    hashtag_id,
                    ig_user_id,
                    "id",
                    config.media_limit,
                    "RECENT_MEDIA",
                )
            )
            _record_baseline_capability(
                tracker, state, minimal_result, hashtag, "minimal id fallback"
            )

        username_result = client.get(
            urls.recent_media(
                hashtag_id,
                ig_user_id,
                "id,username",
                config.media_limit,
                "MEDIA_USERNAME",
            )
        )
        _record_collection_capability(
            tracker,
            state,
            "MEDIA_USERNAME",
            username_result,
            hashtag,
            "username",
        )

        owner_result = client.get(
            urls.recent_media(
                hashtag_id,
                ig_user_id,
                "id,owner",
                config.media_limit,
                "MEDIA_OWNER",
            )
        )
        _record_collection_capability(
            tracker,
            state,
            "MEDIA_OWNER",
            owner_result,
            hashtag,
            "owner",
        )

    followup_media = list(state.media_hashtags)[: config.followup_media_limit]
    for sample_number, media_key in enumerate(followup_media, start=1):
        if not OBJECT_ID_PATTERN.fullmatch(media_key):
            continue
        username_result = client.get(
            urls.media(media_key, "id,username", "FOLLOWUP_MEDIA_USERNAME")
        )
        _record_followup_capability(
            tracker,
            state,
            "FOLLOWUP_MEDIA_USERNAME",
            username_result,
            media_key,
            sample_number,
            "username",
        )
        owner_result = client.get(urls.media(media_key, "id,owner", "FOLLOWUP_MEDIA_OWNER"))
        _record_followup_capability(
            tracker,
            state,
            "FOLLOWUP_MEDIA_OWNER",
            owner_result,
            media_key,
            sample_number,
            "owner",
        )

    feasibility, reason = _derive_feasibility(tracker, state)
    candidates = state.candidates.report_items(config.max_candidates)
    report.update(
        {
            "executionStatus": (
                "BLOCKED" if feasibility == "PREREQUISITE_BLOCKED" else "COMPLETED"
            ),
            "feasibility": feasibility,
            "verdictReason": reason,
            "capabilities": tracker.as_report(),
            "summary": {
                "requestsMade": client.request_count,
                "mediaObserved": len(state.observed_media_keys),
                "mediaWithAccountIdentity": len(state.account_identity_media_keys),
                "mediaWithUsername": state.candidates.username_media_count,
                "uniqueCandidates": state.candidates.unique_count,
                "candidatesShown": len(candidates),
            },
            "candidates": candidates,
            "finishedAt": isoformat_utc(utc_now()),
        }
    )
    return report


def _normalize_hashtags(raw_value: str | None) -> tuple[tuple[str, ...], list[str]]:
    raw_hashtags = raw_value.split(",") if raw_value else list(DEFAULT_HASHTAGS)
    hashtags: list[str] = []
    seen: set[str] = set()
    errors: list[str] = []
    for raw_hashtag in raw_hashtags:
        hashtag = raw_hashtag.strip().removeprefix("#")
        if not hashtag:
            continue
        if any(character.isspace() for character in hashtag):
            errors.append(f"hashtag contains whitespace: {hashtag!r}")
            continue
        normalized = hashtag.casefold()
        if normalized not in seen:
            seen.add(normalized)
            hashtags.append(hashtag)
    if not hashtags:
        errors.append("DISCOVERY_HASHTAGS did not contain a usable hashtag")
    if len(hashtags) > 10:
        errors.append("DISCOVERY_HASHTAGS is limited to 10 values per probe run")
    return tuple(hashtags), errors


def load_config(args: argparse.Namespace) -> tuple[ProbeConfig | None, list[str], dict[str, object]]:
    access_token = os.environ.get("META_ACCESS_TOKEN", "").strip()
    api_version = os.environ.get("META_GRAPH_API_VERSION", "").strip()
    ig_user_id = os.environ.get("META_IG_USER_ID", "").strip() or None
    hashtags, errors = _normalize_hashtags(os.environ.get("DISCOVERY_HASHTAGS"))
    missing: list[str] = []
    if not access_token:
        missing.append("META_ACCESS_TOKEN")
    if not api_version:
        missing.append("META_GRAPH_API_VERSION")
    if api_version and not VERSION_PATTERN.fullmatch(api_version):
        errors.append("META_GRAPH_API_VERSION must look like vXX.X; no version is guessed")
    if ig_user_id and not OBJECT_ID_PATTERN.fullmatch(ig_user_id):
        errors.append("META_IG_USER_ID contains unsupported characters")

    public_config: dict[str, object] = {
        "graphApiVersion": api_version or None,
        "hashtags": list(hashtags),
        "hashtagCount": len(hashtags),
        "igUserIdProvided": ig_user_id is not None,
        "mediaLimitPerRequest": args.media_limit,
        "followupMediaLimit": args.followup_media_limit,
        "candidateSummaryLimit": args.max_candidates,
    }
    if missing:
        errors.append("missing required environment variables: " + ", ".join(missing))
    if errors:
        return None, errors, public_config
    return (
        ProbeConfig(
            access_token=access_token,
            api_version=api_version,
            ig_user_id=ig_user_id,
            hashtags=hashtags,
            media_limit=args.media_limit,
            followup_media_limit=args.followup_media_limit,
            max_candidates=args.max_candidates,
            timeout_seconds=args.timeout_seconds,
        ),
        [],
        public_config,
    )


def not_run_report(
    started_at: datetime,
    config_errors: Sequence[str],
    public_config: Mapping[str, object],
) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "probe": "instagram_native_candidate_discovery",
        "startedAt": isoformat_utc(started_at),
        "finishedAt": isoformat_utc(utc_now()),
        "executionStatus": "NOT_RUN",
        "feasibility": "NOT_RUN",
        "verdictReason": "live Graph API requests were not made because configuration is incomplete",
        "config": dict(public_config),
        "configurationErrors": list(config_errors),
        "preflight": {"status": "NOT_RUN", "attempts": []},
        "capabilities": {
            name: {"status": "UNKNOWN", "attempts": []} for name in CAPABILITY_NAMES
        },
        "summary": {
            "requestsMade": 0,
            "mediaObserved": 0,
            "uniqueCandidates": 0,
            "candidatesShown": 0,
        },
        "candidates": [],
    }


def _default_report_path(started_at: datetime) -> Path:
    repository_root = Path(__file__).resolve().parents[1]
    timestamp = started_at.astimezone(timezone.utc).strftime("%Y%m%d_%H%M%S")
    return (
        repository_root
        / "agent_outputs"
        / "run_logs"
        / f"{timestamp}_instagram_discovery_probe.json"
    )


def write_json_report(report: Mapping[str, object], destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=destination.parent,
        prefix=f".{destination.name}.",
        suffix=".tmp",
        delete=False,
    ) as handle:
        temporary_path = Path(handle.name)
        json.dump(report, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    try:
        temporary_path.chmod(0o600)
        os.replace(temporary_path, destination)
        destination.chmod(0o600)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def _format_attempt_error(attempt: Mapping[str, object]) -> str | None:
    graph_error = attempt.get("graphError")
    if isinstance(graph_error, dict):
        error_type = graph_error.get("type", "GraphError")
        code = graph_error.get("code", "unknown")
        message = graph_error.get("message", "no message")
        return f"HTTP {attempt.get('httpStatus')} {error_type}/{code}: {message}"
    local_error = attempt.get("localError")
    if local_error:
        return f"HTTP {attempt.get('httpStatus')}: {local_error}"
    return None


def print_human_summary(report: Mapping[str, object], report_path: Path | None) -> None:
    print("Instagram-native Candidate Discovery probe")
    print(f"executionStatus: {report.get('executionStatus')}")
    print(f"feasibility: {report.get('feasibility')}")
    print(f"reason: {report.get('verdictReason')}")

    configuration_errors = report.get("configurationErrors")
    if isinstance(configuration_errors, list):
        for error in configuration_errors:
            print(f"configuration: {error}")

    preflight = report.get("preflight")
    if isinstance(preflight, dict):
        print(f"preflight: {preflight.get('status')}")
        for attempt in preflight.get("attempts", []):
            if isinstance(attempt, dict):
                error = _format_attempt_error(attempt)
                if error:
                    print(f"  {error}")

    print("capabilities:")
    capabilities = report.get("capabilities")
    if isinstance(capabilities, dict):
        for name in CAPABILITY_NAMES:
            capability = capabilities.get(name, {})
            status = capability.get("status", "UNKNOWN") if isinstance(capability, dict) else "UNKNOWN"
            print(f"  {name}: {status}")
            if isinstance(capability, dict):
                failures_shown = 0
                for attempt in capability.get("attempts", []):
                    if not isinstance(attempt, dict) or attempt.get("status") == "SUPPORTED":
                        continue
                    error = _format_attempt_error(attempt)
                    if error:
                        print(f"    {error}")
                        failures_shown += 1
                    if failures_shown >= 3:
                        break

    summary = report.get("summary")
    if isinstance(summary, dict):
        print(
            "summary: "
            f"requests={summary.get('requestsMade', 0)}, "
            f"media={summary.get('mediaObserved', 0)}, "
            f"uniqueCandidates={summary.get('uniqueCandidates', 0)}"
        )
    print("candidates:")
    candidates = report.get("candidates")
    if isinstance(candidates, list) and candidates:
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            discovered_by = ", ".join(str(value) for value in candidate.get("discoveredBy", []))
            print(
                f"  - username: {candidate.get('username')} | "
                f"discoveredBy: {discovered_by} | "
                f"sourceMediaCount: {candidate.get('sourceMediaCount')}"
            )
    else:
        print("  []")
    if report_path:
        print(f"jsonReport: {report_path}")
    else:
        print("jsonReport: disabled")


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Probe whether official Meta Instagram hashtag media responses expose "
            "author usernames/account identities. Credentials are read only from "
            "environment variables."
        )
    )
    parser.add_argument(
        "--json-output",
        type=Path,
        help=(
            "JSON report path. Defaults to an ignored agent_outputs/run_logs timestamped file."
        ),
    )
    parser.add_argument(
        "--no-json-report",
        action="store_true",
        help="Print only the human summary and do not write a JSON report.",
    )
    parser.add_argument(
        "--media-limit",
        type=int,
        choices=range(1, 51),
        default=25,
        metavar="1..50",
        help="Maximum media items requested per hashtag capability call (default: 25).",
    )
    parser.add_argument(
        "--followup-media-limit",
        type=int,
        choices=range(1, 6),
        default=3,
        metavar="1..5",
        help="Maximum baseline media objects used for follow-up GET probes (default: 3).",
    )
    parser.add_argument(
        "--max-candidates",
        type=int,
        choices=range(1, 16),
        default=15,
        metavar="1..15",
        help="Maximum usernames displayed and stored in the summary (default: 15).",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=20.0,
        help="Per-request network timeout in seconds (default: 20).",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_argument_parser()
    args = parser.parse_args(argv)
    if args.timeout_seconds <= 0 or args.timeout_seconds > 60:
        parser.error("--timeout-seconds must be greater than 0 and at most 60")
    if args.no_json_report and args.json_output:
        parser.error("--no-json-report and --json-output cannot be used together")

    started_at = utc_now()
    config, config_errors, public_config = load_config(args)
    if config is None:
        report = not_run_report(started_at, config_errors, public_config)
    else:
        try:
            report = execute_probe(config, started_at)
        except Exception as error:  # Last-resort containment must still redact the token.
            safe_error = redact_text(error, (config.access_token,))
            report = {
                "schemaVersion": 1,
                "probe": "instagram_native_candidate_discovery",
                "startedAt": isoformat_utc(started_at),
                "finishedAt": isoformat_utc(utc_now()),
                "executionStatus": "ERROR",
                "feasibility": "UNKNOWN",
                "verdictReason": "the probe stopped on an unexpected local error",
                "config": public_config,
                "localError": safe_error,
                "preflight": {"status": "UNKNOWN", "attempts": []},
                "capabilities": {
                    name: {"status": "UNKNOWN", "attempts": []}
                    for name in CAPABILITY_NAMES
                },
                "summary": {
                    "requestsMade": 0,
                    "mediaObserved": 0,
                    "uniqueCandidates": 0,
                    "candidatesShown": 0,
                },
                "candidates": [],
            }

    report_path: Path | None = None
    if not args.no_json_report:
        report_path = args.json_output or _default_report_path(started_at)
        try:
            write_json_report(report, report_path)
        except OSError as error:
            print(f"could not write JSON report: {redact_text(error)}", file=sys.stderr)
            report_path = None
            print_human_summary(report, report_path)
            return 4

    print_human_summary(report, report_path)
    if report.get("executionStatus") == "ERROR":
        return 1
    feasibility = report.get("feasibility")
    if feasibility == "NOT_RUN":
        return 2
    if feasibility == "PREREQUISITE_BLOCKED":
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
