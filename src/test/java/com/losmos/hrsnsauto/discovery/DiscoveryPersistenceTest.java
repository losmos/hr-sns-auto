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

	@Autowired
	private DiscoveryHashtagRepository hashtagRepository;

	@Autowired
	private DiscoveryItemRepository itemRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void migrationSeedsThreeEnabledDefaultHashtags() {
		assertThat(hashtagRepository.findAllByEnabledTrueOrderByIdAsc())
				.extracting(DiscoveryHashtag::getKeyword)
				.containsExactlyInAnyOrder("의사스타그램", "약사스타그램", "피부과");
	}

	@Test
	void repeatedMediaStaysUniqueUpdatesLastSeenAndPreservesAllHashtagSourcesAndReviewState() {
		MetaInstagramClient client = mock(MetaInstagramClient.class);
		when(client.lookupHashtagId(anyString())).thenReturn("17843800000000001");
		InstagramMedia media = new InstagramMedia(
				"18000000000000001",
				"IMAGE",
				"https://www.instagram.com/p/example/",
				Instant.parse("2026-08-18T11:22:33Z"),
				"가".repeat(600));
		when(client.getRecentMedia("17843800000000001")).thenReturn(List.of(media));

		Instant firstSyncAt = Instant.parse("2026-08-19T00:00:00Z");
		DiscoveryService firstService = service(client, firstSyncAt);
		DiscoverySyncResult firstResult = firstService.syncRecentMedia();

		assertThat(firstResult.getSuccessfulCount()).isEqualTo(3);
		assertThat(firstResult.getFetchedCount()).isEqualTo(3);
		assertThat(firstResult.getCreatedCount()).isEqualTo(1);
		DiscoveryItem firstItem = itemRepository.findAllByOrderByPublishedAtDescIdDesc().getFirst();
		assertThat(firstItem.getCaptionExcerpt()).hasSize(DiscoveryItem.CAPTION_EXCERPT_MAX_LENGTH);
		assertThat(firstItem.getHashtags()).hasSize(3);
		assertThat(firstItem.getFirstDiscoveredAt()).isEqualTo(firstSyncAt);
		assertThat(firstItem.getLastSeenAt()).isEqualTo(firstSyncAt);

		firstService.markOpened(firstItem.getId());
		Instant secondSyncAt = Instant.parse("2026-08-19T01:00:00Z");
		DiscoverySyncResult secondResult = service(client, secondSyncAt).syncRecentMedia();
		itemRepository.flush();
		entityManager.clear();

		assertThat(secondResult.getCreatedCount()).isZero();
		assertThat(itemRepository.count()).isEqualTo(1);
		DiscoveryItem refreshed = itemRepository.findOneByInstagramMediaId("18000000000000001").orElseThrow();
		assertThat(refreshed.getFirstDiscoveredAt()).isEqualTo(firstSyncAt);
		assertThat(refreshed.getLastSeenAt()).isEqualTo(secondSyncAt);
		assertThat(refreshed.getHashtags()).hasSize(3);
		assertThat(refreshed.getReviewStatus()).isEqualTo(DiscoveryReviewStatus.OPENED);

		service(client, secondSyncAt).dismiss(refreshed.getId());
		itemRepository.flush();
		entityManager.clear();
		assertThat(itemRepository.findOneByInstagramMediaId("18000000000000001").orElseThrow()
				.getReviewStatus()).isEqualTo(DiscoveryReviewStatus.DISMISSED);
	}

	@Test
	void oneHashtagFailureDoesNotDiscardOtherSuccessfulObservations() {
		MetaInstagramClient client = mock(MetaInstagramClient.class);
		when(client.lookupHashtagId(anyString())).thenAnswer(invocation -> {
			String keyword = invocation.getArgument(0);
			if (keyword.equals("약사스타그램")) {
				throw new MetaInstagramException("Meta Graph API 오류 (HTTP 400, code 10): 권한 부족");
			}
			return keyword.equals("의사스타그램") ? "1001" : "1002";
		});
		when(client.getRecentMedia("1001")).thenReturn(List.of(media("2001", "doctor-post")));
		when(client.getRecentMedia("1002")).thenReturn(List.of(media("2002", "dermatology-post")));

		DiscoverySyncResult result = service(client, Instant.parse("2026-08-19T00:00:00Z"))
				.syncRecentMedia();

		assertThat(result.getSuccessfulCount()).isEqualTo(2);
		assertThat(result.getFailedCount()).isEqualTo(1);
		assertThat(result.getHashtagResults())
				.filteredOn(item -> !item.successful())
				.singleElement()
				.extracting(HashtagSyncResult::keyword, HashtagSyncResult::errorMessage)
				.containsExactly("약사스타그램", "Meta Graph API 오류 (HTTP 400, code 10): 권한 부족");
		assertThat(itemRepository.count()).isEqualTo(2);
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
