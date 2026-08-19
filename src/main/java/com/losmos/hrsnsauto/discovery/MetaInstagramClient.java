package com.losmos.hrsnsauto.discovery;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MetaInstagramClient {

	static final int RECENT_MEDIA_LIMIT = 25;
	static final String RECENT_MEDIA_FIELDS = "id,caption,media_type,permalink,timestamp";

	private static final String GRAPH_API_ROOT = "https://graph.facebook.com";
	private static final int MAX_RESPONSE_CHARACTERS = 2 * 1024 * 1024;
	private static final int MAX_ERROR_MESSAGE_CHARACTERS = 500;
	private static final Pattern VERSION_PATTERN = Pattern.compile("^v[0-9]+\\.[0-9]+$");
	private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
	private static final Pattern ACCESS_TOKEN_PARAMETER = Pattern.compile(
			"(?i)(access_token(?:=|%3[dD]))([^&\\s]+)");
	private static final Pattern BEARER_VALUE = Pattern.compile(
			"(?i)(authorization\\s*:\\s*bearer\\s+)([^\\s,]+)");
	private static final DateTimeFormatter META_OFFSET_TIMESTAMP = DateTimeFormatter
			.ofPattern("uuuu-MM-dd'T'HH:mm:ssZ");

	private final MetaInstagramProperties properties;
	private final GraphHttpTransport transport;
	private final ObjectMapper objectMapper;

	@Autowired
	public MetaInstagramClient(MetaInstagramProperties properties) {
		this(properties, defaultTransport(), JsonMapper.builder().build());
	}

	MetaInstagramClient(MetaInstagramProperties properties, GraphHttpTransport transport) {
		this(properties, transport, JsonMapper.builder().build());
	}

	MetaInstagramClient(MetaInstagramProperties properties, GraphHttpTransport transport, ObjectMapper objectMapper) {
		this.properties = properties;
		this.transport = transport;
		this.objectMapper = objectMapper;
	}

	public void validateConfiguration() {
		List<String> missingVariables = new ArrayList<>();
		if (properties.getAccessToken().isBlank()) {
			missingVariables.add("META_ACCESS_TOKEN");
		}
		if (properties.getApiVersion().isBlank()) {
			missingVariables.add("META_GRAPH_API_VERSION");
		}
		if (properties.getIgUserId().isBlank()) {
			missingVariables.add("META_IG_USER_ID");
		}
		if (!missingVariables.isEmpty()) {
			throw new MetaInstagramConfigurationException(
					"Meta Instagram 설정이 필요함: " + String.join(", ", missingVariables));
		}

		String apiVersion = properties.getApiVersion().strip();
		if (!VERSION_PATTERN.matcher(apiVersion).matches()) {
			throw new MetaInstagramConfigurationException(
					"META_GRAPH_API_VERSION은 vXX.X 형식으로 설정해야 함");
		}
		if (properties.getAccessToken().strip().chars().anyMatch(Character::isWhitespace)) {
			throw new MetaInstagramConfigurationException("META_ACCESS_TOKEN 형식이 올바르지 않음");
		}
	}

	public String lookupHashtagId(String keyword) {
		validateConfiguration();
		Map<String, String> query = new LinkedHashMap<>();
		query.put("user_id", properties.getIgUserId().strip());
		query.put("q", keyword);
		JsonNode payload = execute("/ig_hashtag_search", query);

		JsonNode data = payload.path("data");
		if (!data.isArray()) {
			throw malformedResponse("hashtag lookup 응답에 data 배열이 없음");
		}
		for (JsonNode item : data) {
			String hashtagId = optionalText(item, "id");
			if (hashtagId != null) {
				validateObjectId(hashtagId, "hashtag ID");
				return hashtagId;
			}
		}
		throw new MetaInstagramException("Meta hashtag lookup 결과에 hashtag ID가 없음");
	}

	public List<InstagramMedia> getRecentMedia(String hashtagId) {
		validateConfiguration();
		validateObjectId(hashtagId, "hashtag ID");
		Map<String, String> query = new LinkedHashMap<>();
		query.put("user_id", properties.getIgUserId().strip());
		query.put("fields", RECENT_MEDIA_FIELDS);
		query.put("limit", Integer.toString(RECENT_MEDIA_LIMIT));
		JsonNode payload = execute("/" + hashtagId + "/recent_media", query);

		JsonNode data = payload.path("data");
		if (!data.isArray()) {
			throw malformedResponse("recent media 응답에 data 배열이 없음");
		}

		List<InstagramMedia> media = new ArrayList<>();
		for (JsonNode item : data) {
			media.add(parseMedia(item));
		}
		return List.copyOf(media);
	}

	private InstagramMedia parseMedia(JsonNode item) {
		if (!item.isObject()) {
			throw malformedResponse("recent media 항목이 object가 아님");
		}
		String mediaId = requiredText(item, "id", 255);
		String mediaType = requiredText(item, "media_type", 32);
		String permalink = requiredText(item, "permalink", 2048);
		String timestamp = requiredText(item, "timestamp", 100);
		String caption = optionalText(item, "caption");

		validateObjectId(mediaId, "media ID");
		validatePermalink(permalink);
		return new InstagramMedia(mediaId, mediaType, permalink, parseTimestamp(timestamp), caption);
	}

	private JsonNode execute(String path, Map<String, String> query) {
		URI uri = buildUri(path, query);
		String accessToken = properties.getAccessToken().strip();
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(20))
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + accessToken)
				.header("User-Agent", "hr-sns-auto-instagram-discovery/1.0")
				.GET()
				.build();

		TransportResponse response;
		try {
			response = transport.send(request);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new MetaInstagramException("Meta Graph API 요청이 중단됨");
		}
		catch (IOException | RuntimeException exception) {
			String detail = sanitize(exception.getMessage());
			String suffix = detail.isBlank() ? "" : ": " + detail;
			// 원인 예외에는 transport가 만든 민감 문자열이 남을 수 있어 UI 경계 밖으로 전달하지 않는다.
			throw new MetaInstagramException("Meta Graph API 연결 실패" + suffix);
		}

		String body = response.body() == null ? "" : response.body();
		if (body.length() > MAX_RESPONSE_CHARACTERS) {
			throw new MetaInstagramException("Meta Graph API 응답이 허용 크기를 초과함");
		}

		JsonNode payload = parseJson(body);
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw graphError(response.statusCode(), payload);
		}
		if (payload == null) {
			throw malformedResponse("Meta Graph API 응답이 JSON이 아님");
		}
		if (payload.path("error").isObject()) {
			throw graphError(response.statusCode(), payload);
		}
		return payload;
	}

	private JsonNode parseJson(String body) {
		if (body.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(body);
		}
		catch (JacksonException exception) {
			return null;
		}
	}

	private MetaInstagramException graphError(int httpStatus, JsonNode payload) {
		StringBuilder message = new StringBuilder("Meta Graph API 오류 (HTTP ").append(httpStatus);
		JsonNode error = payload == null ? null : payload.path("error");
		if (error != null && error.isObject()) {
			if (error.path("code").isNumber()) {
				message.append(", code ").append(error.path("code").intValue());
			}
			String type = optionalText(error, "type");
			if (type != null) {
				message.append(", type ").append(sanitize(type));
			}
		}
		message.append(')');
		if (error != null && error.isObject()) {
			String graphMessage = optionalText(error, "message");
			if (graphMessage != null) {
				message.append(": ").append(sanitize(graphMessage));
			}
		}
		return new MetaInstagramException(message.toString());
	}

	private URI buildUri(String path, Map<String, String> query) {
		String encodedQuery = query.entrySet().stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
		String apiVersion = properties.getApiVersion().strip();
		return URI.create(GRAPH_API_ROOT + "/" + apiVersion + path + "?" + encodedQuery);
	}

	private String requiredText(JsonNode item, String field, int maxLength) {
		String value = optionalText(item, field);
		if (value == null || value.length() > maxLength) {
			throw malformedResponse("recent media의 " + field + " 값이 없거나 너무 김");
		}
		return value;
	}

	private String optionalText(JsonNode item, String field) {
		JsonNode value = item.path(field);
		if (!value.isString()) {
			return null;
		}
		String text = value.asString().strip();
		return text.isEmpty() ? null : text;
	}

	private void validateObjectId(String value, String label) {
		if (!OBJECT_ID_PATTERN.matcher(value).matches()) {
			throw malformedResponse(label + " 형식이 올바르지 않음");
		}
	}

	private void validatePermalink(String permalink) {
		try {
			URI uri = URI.create(permalink);
			String host = uri.getHost();
			boolean instagramHost = host != null
					&& (host.equalsIgnoreCase("instagram.com")
							|| host.toLowerCase(Locale.ROOT).endsWith(".instagram.com"));
			if (!"https".equalsIgnoreCase(uri.getScheme()) || !instagramHost) {
				throw malformedResponse("recent media permalink가 Instagram HTTPS URL이 아님");
			}
		}
		catch (IllegalArgumentException exception) {
			throw malformedResponse("recent media permalink 형식이 올바르지 않음");
		}
	}

	private Instant parseTimestamp(String timestamp) {
		try {
			return Instant.parse(timestamp);
		}
		catch (DateTimeParseException firstException) {
			try {
				// Graph 응답에서 확인되는 +0000 offset 형식도 ISO instant와 함께 허용한다.
				return OffsetDateTime.parse(timestamp, META_OFFSET_TIMESTAMP).toInstant();
			}
			catch (DateTimeParseException secondException) {
				throw malformedResponse("recent media timestamp 형식이 올바르지 않음");
			}
		}
	}

	private MetaInstagramException malformedResponse(String message) {
		return new MetaInstagramException("Meta Graph API 응답 오류: " + message);
	}

	private String sanitize(String value) {
		if (value == null) {
			return "";
		}
		String accessToken = properties.getAccessToken().strip();
		String sanitized = accessToken.isBlank() ? value : value.replace(accessToken, "[REDACTED]");
		if (!accessToken.isBlank()) {
			sanitized = sanitized.replace(encode(accessToken), "[REDACTED]");
		}
		sanitized = ACCESS_TOKEN_PARAMETER.matcher(sanitized).replaceAll("$1[REDACTED]");
		sanitized = BEARER_VALUE.matcher(sanitized).replaceAll("$1[REDACTED]");
		sanitized = sanitized.replace('\r', ' ').replace('\n', ' ').strip();
		if (sanitized.length() <= MAX_ERROR_MESSAGE_CHARACTERS) {
			return sanitized;
		}
		return sanitized.substring(0, MAX_ERROR_MESSAGE_CHARACTERS);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static GraphHttpTransport defaultTransport() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		return request -> {
			HttpResponse<String> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new TransportResponse(response.statusCode(), response.body());
		};
	}

	@FunctionalInterface
	interface GraphHttpTransport {
		TransportResponse send(HttpRequest request) throws IOException, InterruptedException;
	}

	record TransportResponse(int statusCode, String body) {
	}
}
