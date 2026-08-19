package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiscoveryPersistenceTest {

	private static final String REPEATED_MEDIA_ID = "test-repeat-media-001";
	private static final String SUCCESS_MEDIA_ID_ONE = "test-success-media-001";
	private static final String SUCCESS_MEDIA_ID_TWO = "test-success-media-002";
	private static final String BROWSER_OBSERVATION_MEDIA_ID = "test-browser-observation-001";

	@Autowired
	private DiscoveryHashtagRepository hashtagRepository;

	@Autowired
	private DiscoveryItemRepository itemRepository;

	@Autowired
	private DiscoveryBrowserObservationRepository observationRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void migrationDefaultHashtagsExistRegardlessOfRuntimeEnabledState() {
		assertThat(hashtagRepository.findAll())
				.extracting(DiscoveryHashtag::getKeyword)
				.contains("의사스타그램", "약사스타그램", "피부과");
	}

	@Test
	void repeatedMediaStaysUniqueUpdatesLastSeenAndPreservesAllHashtagSourcesAndReviewState() {
		hashtagRepository.saveAllAndFlush(List.of(
				new DiscoveryHashtag("test-repeat-source-a"),
				new DiscoveryHashtag("test-repeat-source-b")));
		List<String> enabledSourceKeywords = hashtagRepository.findAllByEnabledTrueOrderByIdAsc().stream()
				.map(DiscoveryHashtag::getKeyword)
				.toList();
		MetaInstagramClient client = mock(MetaInstagramClient.class);
		when(client.lookupHashtagId(anyString())).thenReturn("test-repeat-hashtag-id");
		InstagramMedia media = new InstagramMedia(
				REPEATED_MEDIA_ID,
				"IMAGE",
				"https://www.instagram.com/p/test-repeat-media-001/",
				Instant.parse("2026-08-18T11:22:33Z"),
				"😀".repeat(600));
		when(client.getRecentMedia("test-repeat-hashtag-id")).thenReturn(List.of(media));

		Instant firstSyncAt = Instant.parse("2026-08-19T00:00:00Z");
		DiscoveryService firstService = service(client, firstSyncAt);
		DiscoverySyncResult firstResult = firstService.syncRecentMedia();

		assertThat(firstResult.getSuccessfulCount()).isEqualTo(enabledSourceKeywords.size());
		assertThat(firstResult.getFetchedCount()).isEqualTo(enabledSourceKeywords.size());
		assertThat(firstResult.getCreatedCount()).isEqualTo(1);
		DiscoveryItem firstItem = itemRepository.findOneByInstagramMediaId(REPEATED_MEDIA_ID).orElseThrow();
		String captionExcerpt = firstItem.getCaptionExcerpt();
		assertThat(captionExcerpt.codePointCount(0, captionExcerpt.length()))
				.isEqualTo(DiscoveryItem.CAPTION_EXCERPT_MAX_LENGTH);
		assertThat(captionExcerpt).isEqualTo("😀".repeat(DiscoveryItem.CAPTION_EXCERPT_MAX_LENGTH));
		assertThat(firstItem.getHashtags())
				.extracting(DiscoveryHashtag::getKeyword)
				.containsExactlyInAnyOrderElementsOf(enabledSourceKeywords);
		assertThat(firstItem.getFirstDiscoveredAt()).isEqualTo(firstSyncAt);
		assertThat(firstItem.getLastSeenAt()).isEqualTo(firstSyncAt);

		firstService.markOpened(firstItem.getId());
		Instant secondSyncAt = Instant.parse("2026-08-19T01:00:00Z");
		DiscoverySyncResult secondResult = service(client, secondSyncAt).syncRecentMedia();
		itemRepository.flush();
		entityManager.clear();

		assertThat(secondResult.getCreatedCount()).isZero();
		DiscoveryItem refreshed = itemRepository.findOneByInstagramMediaId(REPEATED_MEDIA_ID).orElseThrow();
		assertThat(refreshed.getFirstDiscoveredAt()).isEqualTo(firstSyncAt);
		assertThat(refreshed.getLastSeenAt()).isEqualTo(secondSyncAt);
		assertThat(refreshed.getHashtags())
				.extracting(DiscoveryHashtag::getKeyword)
				.containsExactlyInAnyOrderElementsOf(enabledSourceKeywords);
		assertThat(refreshed.getReviewStatus()).isEqualTo(DiscoveryReviewStatus.OPENED);

		service(client, secondSyncAt).dismiss(refreshed.getId());
		itemRepository.flush();
		entityManager.clear();
		assertThat(itemRepository.findOneByInstagramMediaId(REPEATED_MEDIA_ID).orElseThrow()
				.getReviewStatus()).isEqualTo(DiscoveryReviewStatus.DISMISSED);
	}

	@Test
	void oneHashtagFailureDoesNotDiscardOtherSuccessfulObservations() {
		DiscoveryHashtag successfulSourceOne = new DiscoveryHashtag("test-partial-success-source-a");
		DiscoveryHashtag successfulSourceTwo = new DiscoveryHashtag("test-partial-success-source-b");
		DiscoveryHashtag failingSource = new DiscoveryHashtag("test-partial-failure-source");
		hashtagRepository.saveAllAndFlush(List.of(successfulSourceOne, successfulSourceTwo, failingSource));
		int enabledHashtagCount = hashtagRepository.findAllByEnabledTrueOrderByIdAsc().size();

		MetaInstagramClient client = mock(MetaInstagramClient.class);
		when(client.lookupHashtagId(anyString())).thenAnswer(invocation -> {
			String keyword = invocation.getArgument(0);
			if (keyword.equals(failingSource.getKeyword())) {
				throw new MetaInstagramException("Meta Graph API 오류 (HTTP 400, code 10): 권한 부족");
			}
			if (keyword.equals(successfulSourceOne.getKeyword())) {
				return "test-success-hashtag-id-001";
			}
			if (keyword.equals(successfulSourceTwo.getKeyword())) {
				return "test-success-hashtag-id-002";
			}
			// Shared dev DB에 이미 있는 enabled hashtag는 이 테스트의 fixture를 만들지 않는다.
			return "test-empty-hashtag-id";
		});
		when(client.getRecentMedia("test-success-hashtag-id-001"))
				.thenReturn(List.of(media(SUCCESS_MEDIA_ID_ONE, "test-success-media-001")));
		when(client.getRecentMedia("test-success-hashtag-id-002"))
				.thenReturn(List.of(media(SUCCESS_MEDIA_ID_TWO, "test-success-media-002")));
		when(client.getRecentMedia("test-empty-hashtag-id")).thenReturn(List.of());

		DiscoverySyncResult result = service(client, Instant.parse("2026-08-19T00:00:00Z"))
				.syncRecentMedia();
		entityManager.clear();

		assertThat(result.getSuccessfulCount()).isEqualTo(enabledHashtagCount - 1);
		assertThat(result.getFailedCount()).isEqualTo(1);
		assertThat(result.getFetchedCount()).isEqualTo(2);
		assertThat(result.getCreatedCount()).isEqualTo(2);
		assertThat(result.getHashtagResults())
				.filteredOn(item -> !item.successful())
				.singleElement()
				.extracting(HashtagSyncResult::keyword, HashtagSyncResult::errorMessage)
				.containsExactly(failingSource.getKeyword(),
						"Meta Graph API 오류 (HTTP 400, code 10): 권한 부족");
		assertThat(itemRepository.findOneByInstagramMediaId(SUCCESS_MEDIA_ID_ONE).orElseThrow()
				.getHashtags())
				.extracting(DiscoveryHashtag::getKeyword)
				.containsExactly(successfulSourceOne.getKeyword());
		assertThat(itemRepository.findOneByInstagramMediaId(SUCCESS_MEDIA_ID_TWO).orElseThrow()
				.getHashtags())
				.extracting(DiscoveryHashtag::getKeyword)
				.containsExactly(successfulSourceTwo.getKeyword());
	}

	@Test
	void persistsOneLatestBrowserObservationPerDiscoveryItem() {
		DiscoveryHashtag hashtag = hashtagRepository.saveAndFlush(
				new DiscoveryHashtag("test-browser-observation-source"));
		DiscoveryItem item = itemRepository.saveAndFlush(new DiscoveryItem(
				media(BROWSER_OBSERVATION_MEDIA_ID, "test-browser-observation-001"),
				hashtag,
				Instant.parse("2026-08-19T01:00:00Z")));
		Long itemId = item.getId();
		InstagramBrowserEnrichmentResult firstResult = InstagramBrowserEnrichmentResult.success(
				new InstagramPostBrowserSnapshot(
						"doctor.one", "https://www.instagram.com/doctor.one/", 1_234L, 25L, null),
				new InstagramProfileBrowserSnapshot(
						"닥터 원", 4_800L, 320L, 523L, "피부 건강 정보", false, false));
		DiscoveryBrowserObservation observation = new DiscoveryBrowserObservation(item);
		observation.replaceWith(firstResult, Instant.parse("2026-08-19T02:00:00Z"));
		observationRepository.saveAndFlush(observation);
		Long observationId = observation.getId();
		entityManager.clear();

		DiscoveryItem loaded = itemRepository.findOneByInstagramMediaId(BROWSER_OBSERVATION_MEDIA_ID).orElseThrow();
		assertThat(loaded.getId()).isEqualTo(itemId);
		DiscoveryBrowserObservation loadedObservation = observationRepository
				.findOneByDiscoveryItemId(loaded.getId())
				.orElseThrow();
		assertThat(loadedObservation.getId()).isEqualTo(observationId);
		assertThat(loadedObservation.getAuthorUsername()).isEqualTo("doctor.one");
		assertThat(loadedObservation.getFollowerCount()).isEqualTo(4_800L);
		assertThat(loadedObservation.getPostLikeCount()).isEqualTo(1_234L);
		assertThat(loadedObservation.getStatus()).isEqualTo(DiscoveryBrowserObservationStatus.SUCCESS);

		InstagramBrowserEnrichmentResult latestResult = InstagramBrowserEnrichmentResult.failure(
				DiscoveryBrowserObservationStatus.LOGIN_REQUIRED,
				InstagramBrowserErrorCode.LOGIN_REQUIRED,
				"Instagram 로그인이 필요함");
		loadedObservation.replaceWith(latestResult, Instant.parse("2026-08-19T03:00:00Z"));
		observationRepository.saveAndFlush(loadedObservation);
		entityManager.clear();

		DiscoveryBrowserObservation latest = observationRepository
				.findOneByDiscoveryItemId(itemId)
				.orElseThrow();
		assertThat(latest.getId()).isEqualTo(observationId);
		assertThat(latest.getStatus()).isEqualTo(DiscoveryBrowserObservationStatus.LOGIN_REQUIRED);
		assertThat(latest.getObservedAt()).isEqualTo(Instant.parse("2026-08-19T03:00:00Z"));
		assertThat(latest.getAuthorUsername()).isNull();
	}

	private DiscoveryService service(MetaInstagramClient client, Instant instant) {
		return new DiscoveryService(
				hashtagRepository,
				itemRepository,
				client,
				Clock.fixed(instant, ZoneOffset.UTC));
	}

	private InstagramMedia media(String mediaId, String slug) {
		return new InstagramMedia(
				mediaId,
				"IMAGE",
				"https://www.instagram.com/p/" + slug + "/",
				Instant.parse("2026-08-18T11:22:33Z"),
				"공개 캡션");
	}
}
