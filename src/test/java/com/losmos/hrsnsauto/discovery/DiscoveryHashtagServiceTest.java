package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoveryHashtagServiceTest {

	@Mock
	private DiscoveryHashtagRepository hashtagRepository;

	@Mock
	private DiscoveryItemRepository itemRepository;

	@Mock
	private MetaInstagramClient metaInstagramClient;

	private DiscoveryService discoveryService;

	@BeforeEach
	void setUp() {
		discoveryService = new DiscoveryService(
				hashtagRepository,
				itemRepository,
				metaInstagramClient,
				Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void stripsLeadingHashAndPreventsCaseInsensitiveDuplicates() {
		when(hashtagRepository.existsByKeyword("doctorlife")).thenReturn(false);
		when(hashtagRepository.saveAndFlush(any(DiscoveryHashtag.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		DiscoveryHashtag created = discoveryService.addHashtag("  #DoctorLife  ");

		assertThat(created.getKeyword()).isEqualTo("doctorlife");
		assertThat(created.isEnabled()).isTrue();
		ArgumentCaptor<DiscoveryHashtag> saved = ArgumentCaptor.forClass(DiscoveryHashtag.class);
		verify(hashtagRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getKeyword()).isEqualTo("doctorlife");

		when(hashtagRepository.existsByKeyword("doctorlife")).thenReturn(true);
		assertThatThrownBy(() -> discoveryService.addHashtag("#DOCTORLIFE"))
				.isInstanceOf(DuplicateDiscoveryHashtagException.class);
	}

	@Test
	void rejectsInputThatContainsOnlyLeadingHashes() {
		assertThatThrownBy(() -> discoveryService.addHashtag(" ## "))
				.isInstanceOf(InvalidDiscoveryHashtagException.class)
				.hasMessageContaining("hashtag");
	}

	@Test
	void disablesAndReenablesWithoutDeletingHashtag() {
		DiscoveryHashtag hashtag = new DiscoveryHashtag("피부과");
		when(hashtagRepository.findById(7L)).thenReturn(Optional.of(hashtag));

		discoveryService.setHashtagEnabled(7L, false);
		assertThat(hashtag.isEnabled()).isFalse();

		discoveryService.setHashtagEnabled(7L, true);
		assertThat(hashtag.isEnabled()).isTrue();
		verify(hashtagRepository, times(2)).save(hashtag);
	}

	@Test
	void ordersNewItemsFirstAndThenByPublishedTime() {
		DiscoveryHashtag hashtag = new DiscoveryHashtag("피부과");
		DiscoveryItem newestNew = item("1001", hashtag, "2026-08-19T03:00:00Z");
		DiscoveryItem olderNew = item("1002", hashtag, "2026-08-19T01:00:00Z");
		DiscoveryItem opened = item("1003", hashtag, "2026-08-19T04:00:00Z");
		opened.markOpened();
		DiscoveryItem dismissed = item("1004", hashtag, "2026-08-19T05:00:00Z");
		dismissed.dismiss();
		when(itemRepository.findAllByOrderByPublishedAtDescIdDesc())
				.thenReturn(List.of(dismissed, opened, newestNew, olderNew));

		assertThat(discoveryService.findItems(null))
				.containsExactly(newestNew, olderNew, opened, dismissed);
	}

	private DiscoveryItem item(String mediaId, DiscoveryHashtag hashtag, String publishedAt) {
		InstagramMedia media = new InstagramMedia(
				mediaId,
				"IMAGE",
				"https://www.instagram.com/p/" + mediaId + "/",
				Instant.parse(publishedAt),
				"공개 캡션");
		return new DiscoveryItem(media, hashtag, Instant.parse("2026-08-19T06:00:00Z"));
	}
}
