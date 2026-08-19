package com.losmos.hrsnsauto.discovery;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiscoveryController.class)
class DiscoveryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DiscoveryService discoveryService;

	@MockitoBean
	private InstagramBrowserEnrichmentService browserEnrichmentService;

	@BeforeEach
	void setUp() {
		when(discoveryService.findAllHashtags()).thenReturn(List.of());
		when(discoveryService.findItems(null)).thenReturn(List.of());
		when(discoveryService.getInboxCounts()).thenReturn(new DiscoveryInboxCounts(0, 0, 0));
		when(browserEnrichmentService.isEnabled()).thenReturn(false);
		when(browserEnrichmentService.getDisplayBatchSize()).thenReturn(10);
	}

	@Test
	void rendersDiscoveryInboxAndHashtagControls() throws Exception {
		DiscoveryHashtag hashtag = mock(DiscoveryHashtag.class);
		when(hashtag.getKeyword()).thenReturn("피부과");
		DiscoveryItem item = mock(DiscoveryItem.class);
		when(item.getId()).thenReturn(9L);
		when(item.getMediaType()).thenReturn("IMAGE");
		when(item.getPublishedAt()).thenReturn(Instant.parse("2026-08-18T11:22:33Z"));
		when(item.getCaptionExcerpt()).thenReturn("공개 게시물 캡션");
		when(item.getReviewStatus()).thenReturn(DiscoveryReviewStatus.NEW);
		when(item.getHashtags()).thenReturn(Set.of(hashtag));
		DiscoveryBrowserObservation observation = mock(DiscoveryBrowserObservation.class);
		when(observation.getStatus()).thenReturn(DiscoveryBrowserObservationStatus.PARTIAL);
		when(observation.getAuthorUsername()).thenReturn("doctor.one");
		when(observation.getAuthorDisplayName()).thenReturn("닥터 원");
		when(observation.getFollowerCount()).thenReturn(4_800L);
		when(observation.getFollowingCount()).thenReturn(321L);
		when(observation.getPostCount()).thenReturn(523L);
		when(observation.getVerified()).thenReturn(false);
		when(observation.getPrivateAccount()).thenReturn(false);
		when(observation.getProfileUrl()).thenReturn("https://www.instagram.com/doctor.one/");
		when(observation.getObservedAt()).thenReturn(Instant.parse("2026-08-19T12:34:56Z"));
		when(item.getBrowserObservation()).thenReturn(observation);
		when(discoveryService.findItems(null)).thenReturn(List.of(item));
		when(browserEnrichmentService.isEnabled()).thenReturn(true);
		DiscoverySyncResult syncResult = new DiscoverySyncResult(List.of(
				HashtagSyncResult.success("피부과", 2, 1),
				HashtagSyncResult.failure("약사스타그램", "Meta Graph API 오류 (HTTP 400, code 10)")));
		BrowserEnrichmentBatchResult browserBatchResult = new BrowserEnrichmentBatchResult(
				10,
				List.of(new BrowserEnrichmentItemResult(
						9L, DiscoveryBrowserObservationStatus.PARTIAL, null, null)));

		mockMvc.perform(get("/discovery")
				.flashAttr("syncResult", syncResult)
				.flashAttr("browserBatchResult", browserBatchResult))
				.andExpect(status().isOk())
				.andExpect(view().name("discovery/index"))
				.andExpect(content().string(containsString("Discovery Inbox")))
				.andExpect(content().string(containsString("최근 게시물 가져오기")))
				.andExpect(content().string(containsString("비활성화해도 기존 게시물의 발견 source는 유지된다")))
				.andExpect(content().string(containsString("신규 저장 1건")))
				.andExpect(content().string(containsString("처리 1건 · 성공 0건 · 부분 1건 · 실패 0건")))
				.andExpect(content().string(containsString("HTTP 400, code 10")))
				.andExpect(content().string(containsString("공개 게시물 캡션")))
				.andExpect(content().string(containsString("Instagram에서 열기")))
				.andExpect(content().string(containsString("브라우저 보강: <span>활성</span>")))
				.andExpect(content().string(containsString("Instagram 브라우저 열기 / 로그인 확인")))
				.andExpect(content().string(containsString("NEW 10건 브라우저 정보 보강")))
				.andExpect(content().string(containsString("브라우저 정보 가져오기")))
				.andExpect(content().string(containsString("doctor.one")))
				.andExpect(content().string(containsString("4800")))
				.andExpect(content().string(containsString("/discovery/items/9/open")));
	}

	@Test
	void rendersDisabledBrowserStateWithoutOperatorButtons() throws Exception {
		mockMvc.perform(get("/discovery"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("브라우저 보강: <span>비활성</span>")))
				.andExpect(content().string(containsString("INSTAGRAM_BROWSER_AUTOMATION_ENABLED=true")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						containsString("Instagram 브라우저 열기 / 로그인 확인"))));
	}

	@Test
	void addsAndTogglesHashtags() throws Exception {
		mockMvc.perform(post("/discovery/hashtags").param("keyword", "#피부과의사"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"));
		verify(discoveryService).addHashtag("#피부과의사");

		mockMvc.perform(post("/discovery/hashtags/7/disable"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"));
		verify(discoveryService).setHashtagEnabled(7L, false);

		mockMvc.perform(post("/discovery/hashtags/7/enable"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"));
		verify(discoveryService).setHashtagEnabled(7L, true);
	}

	@Test
	void invalidHashtagRendersFieldErrorWithoutCallingService() throws Exception {
		mockMvc.perform(post("/discovery/hashtags").param("keyword", " "))
				.andExpect(status().isOk())
				.andExpect(view().name("discovery/index"))
				.andExpect(model().attributeHasFieldErrors("hashtagForm", "keyword"))
				.andExpect(content().string(containsString("hashtag를 입력한다")));
		verify(discoveryService, never()).addHashtag(any(String.class));
	}

	@Test
	void manualSyncStoresSummaryInFlash() throws Exception {
		DiscoverySyncResult result = new DiscoverySyncResult(List.of(
				HashtagSyncResult.success("피부과", 3, 2)));
		when(discoveryService.syncRecentMedia()).thenReturn(result);

		mockMvc.perform(post("/discovery/sync"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"))
				.andExpect(flash().attribute("syncResult", result));
	}

	@Test
	void missingMetaConfigurationShowsSafeOperatorError() throws Exception {
		when(discoveryService.syncRecentMedia()).thenThrow(new MetaInstagramConfigurationException(
				"Meta Instagram 설정이 필요함: META_ACCESS_TOKEN, META_GRAPH_API_VERSION, META_IG_USER_ID"));

		mockMvc.perform(post("/discovery/sync"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"))
				.andExpect(flash().attribute("error", containsString("META_ACCESS_TOKEN")))
				.andExpect(flash().attribute("error", org.hamcrest.Matchers.not(containsString("secret"))));
	}

	@Test
	void openingMarksItemAndRedirectsToInstagramAndDismissUpdatesState() throws Exception {
		when(discoveryService.markOpened(9L))
				.thenReturn(URI.create("https://www.instagram.com/p/example/"));

		mockMvc.perform(get("/discovery/items/9/open"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("https://www.instagram.com/p/example/"));
		verify(discoveryService).markOpened(9L);

		mockMvc.perform(post("/discovery/items/9/dismiss"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"));
		verify(discoveryService).dismiss(9L);
	}

	@Test
	void routesSessionSingleAndSequentialNewBatchActions() throws Exception {
		when(browserEnrichmentService.prepareSession()).thenReturn(InstagramBrowserSessionResult.ready());
		when(browserEnrichmentService.enrichItem(9L)).thenReturn(new BrowserEnrichmentItemResult(
				9L, DiscoveryBrowserObservationStatus.PARTIAL, null, null));
		BrowserEnrichmentBatchResult batchResult = new BrowserEnrichmentBatchResult(
				10,
				List.of(new BrowserEnrichmentItemResult(
						9L, DiscoveryBrowserObservationStatus.SUCCESS, null, null)));
		when(browserEnrichmentService.enrichNewBatch()).thenReturn(batchResult);

		mockMvc.perform(post("/discovery/browser/session"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"))
				.andExpect(flash().attribute("message", containsString("브라우저를 열었음")));

		mockMvc.perform(post("/discovery/items/9/browser-enrichment"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"))
				.andExpect(flash().attribute("message", containsString("일부 공개 정보")));

		mockMvc.perform(post("/discovery/browser-enrichment/new"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"))
				.andExpect(flash().attribute("browserBatchResult", batchResult));
	}

	@Test
	void disabledBrowserRouteShowsSafeError() throws Exception {
		when(browserEnrichmentService.enrichItem(9L)).thenThrow(new InstagramBrowserOperationException(
				InstagramBrowserErrorCode.AUTOMATION_DISABLED,
				"Instagram 브라우저 보강이 비활성 상태임"));

		mockMvc.perform(post("/discovery/items/9/browser-enrichment"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/discovery"))
				.andExpect(flash().attribute("error", containsString("비활성")));
	}
}
