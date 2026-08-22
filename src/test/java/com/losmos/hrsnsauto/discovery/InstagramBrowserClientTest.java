package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

class InstagramBrowserClientTest {

	@Test
	void returnsPostUnavailableForUnexpectedFinalUrlsWithoutExposingQueryOrFragment() {
		InstagramBrowserEnrichmentResult home = enrichAt(
				"https://www.instagram.com/?access_token=HOME_SECRET#sessionid=HOME_FRAGMENT");
		InstagramBrowserEnrichmentResult explore = enrichAt(
				"https://www.instagram.com/explore/?token=EXPLORE_SECRET");
		InstagramBrowserEnrichmentResult external = enrichAt(
				"https://example.com/p/Fake/?sessionid=EXTERNAL_SECRET");
		InstagramBrowserEnrichmentResult differentPost = enrichAt(
				"https://www.instagram.com/p/Different123/?token=DIFFERENT_SECRET");

		assertUnexpectedFinalPage(home, "page=home", "finalPath=/");
		assertUnexpectedFinalPage(explore, "page=other_instagram", "finalPath=/explore/");
		assertUnexpectedFinalPage(external, "page=external", "finalPath=-");
		assertUnexpectedFinalPage(differentPost, "page=post", "finalPath=/p/Different123/");
		assertThat(List.of(
				home.getErrorSummary(), explore.getErrorSummary(), external.getErrorSummary(),
				differentPost.getErrorSummary()))
				.allSatisfy(summary -> assertThat(summary).doesNotContain(
						"access_token", "sessionid", "token=",
						"HOME_SECRET", "HOME_FRAGMENT", "EXPLORE_SECRET", "EXTERNAL_SECRET",
						"DIFFERENT_SECRET"));
	}

	private InstagramBrowserEnrichmentResult enrichAt(String finalUrl) {
		InstagramBrowserProperties properties = new InstagramBrowserProperties();
		properties.setAutomationEnabled(true);
		InstagramBrowserExtractor extractor = new InstagramBrowserExtractor(new InstagramMetricParser());
		InstagramBrowserClient client = new InstagramBrowserClient(
				properties, extractor, new InstagramBrowserErrorSanitizer());

		Page page = mock(Page.class);
		BrowserContext browserContext = mock(BrowserContext.class);
		when(page.url()).thenReturn(finalUrl);
		when(browserContext.pages()).thenReturn(List.of(page));
		ReflectionTestUtils.setField(client, "browserContext", browserContext);

		return client.enrich("https://www.instagram.com/p/Requested123/?utm_source=fixture");
	}

	private void assertUnexpectedFinalPage(
			InstagramBrowserEnrichmentResult result, String page, String finalPath) {
		assertThat(result.getStatus()).isEqualTo(DiscoveryBrowserObservationStatus.FAILED);
		assertThat(result.getErrorCode()).isEqualTo(InstagramBrowserErrorCode.POST_UNAVAILABLE);
		assertThat(result.getErrorSummary()).contains(page, finalPath);
	}
}
