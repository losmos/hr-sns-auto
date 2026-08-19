package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class MetaInstagramClientTest {

	private static final String ACCESS_TOKEN = "synthetic-secret-token";

	private MetaInstagramProperties properties;
	private RecordingTransport transport;
	private MetaInstagramClient client;

	@BeforeEach
	void setUp() {
		properties = new MetaInstagramProperties();
		properties.setAccessToken(ACCESS_TOKEN);
		properties.setApiVersion("v26.0");
		properties.setIgUserId("17841400000000000");
		transport = new RecordingTransport();
		client = new MetaInstagramClient(properties, transport);
	}

	@Test
	void springCreatesBeanWithoutMetaConfigurationAndDefersValidationUntilUse() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.register(MetaInstagramProperties.class, MetaInstagramClient.class);
			context.refresh();

			MetaInstagramClient springClient = context.getBean(MetaInstagramClient.class);
			assertThatThrownBy(springClient::validateConfiguration)
					.isInstanceOf(MetaInstagramConfigurationException.class)
					.hasMessageContaining("META_ACCESS_TOKEN")
					.hasMessageContaining("META_GRAPH_API_VERSION")
					.hasMessageContaining("META_IG_USER_ID");
		}
	}

	@Test
	void buildsVersionedRequestsWithBearerTokenAndParsesResponses() {
		transport.respond(200, """
				{"data":[{"id":"17843800000000001"}]}
				""");
		transport.respond(200, """
				{
				  "data": [{
				    "id": "18000000000000001",
				    "caption": "공개 게시물 캡션",
				    "media_type": "IMAGE",
				    "permalink": "https://www.instagram.com/p/example/",
				    "timestamp": "2026-08-18T11:22:33+0000"
				  }],
				  "paging": {"cursors": {"after": "opaque-cursor"}}
				}
				""");

		String hashtagId = client.lookupHashtagId("의사스타그램");
		List<InstagramMedia> media = client.getRecentMedia(hashtagId);

		assertThat(hashtagId).isEqualTo("17843800000000001");
		assertThat(media).singleElement().satisfies(item -> {
			assertThat(item.mediaId()).isEqualTo("18000000000000001");
			assertThat(item.mediaType()).isEqualTo("IMAGE");
			assertThat(item.permalink()).isEqualTo("https://www.instagram.com/p/example/");
			assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-08-18T11:22:33Z"));
			assertThat(item.caption()).isEqualTo("공개 게시물 캡션");
		});

		assertThat(transport.requests).hasSize(2);
		HttpRequest lookupRequest = transport.requests.get(0);
		assertThat(lookupRequest.uri().getPath()).isEqualTo("/v26.0/ig_hashtag_search");
		assertThat(lookupRequest.uri().getRawQuery())
				.contains("user_id=17841400000000000")
				.contains("q=%EC%9D%98%EC%82%AC%EC%8A%A4%ED%83%80%EA%B7%B8%EB%9E%A8")
				.doesNotContain("access_token")
				.doesNotContain(ACCESS_TOKEN);
		assertThat(lookupRequest.headers().firstValue("Authorization"))
				.contains("Bearer " + ACCESS_TOKEN);

		HttpRequest recentRequest = transport.requests.get(1);
		assertThat(recentRequest.uri().getPath())
				.isEqualTo("/v26.0/17843800000000001/recent_media");
		assertThat(recentRequest.uri().getRawQuery())
				.contains("fields=id%2Ccaption%2Cmedia_type%2Cpermalink%2Ctimestamp")
				.contains("limit=25")
				.doesNotContain("access_token")
				.doesNotContain(ACCESS_TOKEN);
	}

	@Test
	void redactsTokenAndKeepsOnlyUsefulGraphErrorDetails() {
		transport.respond(HttpURLConnection.HTTP_BAD_REQUEST, """
				{
				  "error": {
				    "message": "Access token synthetic-secret-token is invalid",
				    "type": "OAuthException",
				    "code": 190,
				    "fbtrace_id": "trace-that-must-not-be-shown"
				  },
				  "unrelated_raw_value": "do-not-show"
				}
				""");

		assertThatThrownBy(() -> client.lookupHashtagId("피부과"))
				.isInstanceOf(MetaInstagramException.class)
				.hasMessageContaining("HTTP 400")
				.hasMessageContaining("code 190")
				.hasMessageContaining("OAuthException")
				.hasMessageContaining("[REDACTED]")
				.hasMessageNotContaining(ACCESS_TOKEN)
				.hasMessageNotContaining("trace-that-must-not-be-shown")
				.hasMessageNotContaining("do-not-show");
	}

	@Test
	void rejectsMalformedLookupAndRecentMediaWithoutExposingRawPayload() {
		transport.respond(200, "{\"unexpected\":\"raw-private-value\"}");

		assertThatThrownBy(() -> client.lookupHashtagId("피부과"))
				.isInstanceOf(MetaInstagramException.class)
				.hasMessageContaining("data 배열이 없음")
				.hasMessageNotContaining("raw-private-value");

		transport.respond(200, """
				{"data":[{
				  "id":"18000000000000001",
				  "media_type":"IMAGE",
				  "permalink":"javascript:alert(1)",
				  "timestamp":"not-an-instant"
				}]}
				""");

		assertThatThrownBy(() -> client.getRecentMedia("17843800000000001"))
				.isInstanceOf(MetaInstagramException.class)
				.hasMessageContaining("permalink")
				.hasMessageNotContaining("javascript:alert(1)");
	}

	@Test
	void redactsTokenFromTransportFailure() {
		transport.failure = new IOException("connection failed with " + ACCESS_TOKEN);

		assertThatThrownBy(() -> client.lookupHashtagId("피부과"))
				.isInstanceOf(MetaInstagramException.class)
				.hasMessageContaining("연결 실패")
				.hasMessageContaining("[REDACTED]")
				.hasMessageNotContaining(ACCESS_TOKEN);
	}

	@Test
	void reportsMissingConfigurationWithoutMakingARequest() {
		properties.setAccessToken("");
		properties.setApiVersion("");
		properties.setIgUserId("");

		assertThatThrownBy(client::validateConfiguration)
				.isInstanceOf(MetaInstagramConfigurationException.class)
				.hasMessageContaining("META_ACCESS_TOKEN")
				.hasMessageContaining("META_GRAPH_API_VERSION")
				.hasMessageContaining("META_IG_USER_ID")
				.hasMessageNotContaining(ACCESS_TOKEN);
		assertThat(transport.requests).isEmpty();
	}

	private static class RecordingTransport implements MetaInstagramClient.GraphHttpTransport {

		private final List<HttpRequest> requests = new ArrayList<>();
		private final Deque<MetaInstagramClient.TransportResponse> responses = new ArrayDeque<>();
		private IOException failure;

		void respond(int statusCode, String body) {
			responses.add(new MetaInstagramClient.TransportResponse(statusCode, body));
		}

		@Override
		public MetaInstagramClient.TransportResponse send(HttpRequest request) throws IOException {
			requests.add(request);
			if (failure != null) {
				throw failure;
			}
			return responses.removeFirst();
		}
	}
}
