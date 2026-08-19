package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DiscoveryBrowserObservationTest {

	@Test
	void mapsPartialExtractionWithoutInventingMissingValues() {
		DiscoveryBrowserObservation observation = new DiscoveryBrowserObservation(item());
		InstagramBrowserEnrichmentResult result = InstagramBrowserEnrichmentResult.success(
				new InstagramPostBrowserSnapshot(
						"Doctor.One", "https://www.instagram.com/doctor.one/", 1_234L, null, null),
				new InstagramProfileBrowserSnapshot(
						"닥터 원", 4_800L, null, 523L, "피부 건강을 설명한다", false, false));

		observation.replaceWith(result, Instant.parse("2026-08-19T12:34:56Z"));

		assertThat(observation.getStatus()).isEqualTo(DiscoveryBrowserObservationStatus.PARTIAL);
		assertThat(observation.getAuthorUsername()).isEqualTo("doctor.one");
		assertThat(observation.getFollowerCount()).isEqualTo(4_800L);
		assertThat(observation.getFollowingCount()).isNull();
		assertThat(observation.getPostLikeCount()).isEqualTo(1_234L);
		assertThat(observation.getPostCommentCount()).isNull();
		assertThat(observation.getErrorCode()).isNull();
		assertThat(observation.getObservedAt()).isEqualTo(Instant.parse("2026-08-19T12:34:56Z"));
	}

	@Test
	void retainsAuthorAndPostMetricsWhenProfileStepFails() {
		DiscoveryBrowserObservation observation = new DiscoveryBrowserObservation(item());
		InstagramPostBrowserSnapshot post = new InstagramPostBrowserSnapshot(
				"doctor.one", "https://www.instagram.com/doctor.one/", 10L, 2L, 300L);
		InstagramBrowserEnrichmentResult result = InstagramBrowserEnrichmentResult.failure(
				DiscoveryBrowserObservationStatus.FAILED,
				InstagramBrowserErrorCode.PROFILE_UNAVAILABLE,
				"author Instagram 프로필을 사용할 수 없음",
				post);

		observation.replaceWith(result, Instant.parse("2026-08-19T12:34:56Z"));

		assertThat(observation.getAuthorUsername()).isEqualTo("doctor.one");
		assertThat(observation.getPostViewCount()).isEqualTo(300L);
		assertThat(observation.getFollowerCount()).isNull();
		assertThat(observation.getStatus()).isEqualTo(DiscoveryBrowserObservationStatus.FAILED);
		assertThat(observation.getErrorCode()).isEqualTo(InstagramBrowserErrorCode.PROFILE_UNAVAILABLE);
	}

	@Test
	void rejectsNegativeMetricsBeforePersistence() {
		DiscoveryBrowserObservation observation = new DiscoveryBrowserObservation(item());
		InstagramBrowserEnrichmentResult result = InstagramBrowserEnrichmentResult.success(
				new InstagramPostBrowserSnapshot(
						"doctor.one", "https://www.instagram.com/doctor.one/", -1L, null, null),
				new InstagramProfileBrowserSnapshot(
						"닥터 원", 10L, 10L, 10L, null, false, false));

		assertThatThrownBy(() -> observation.replaceWith(result, Instant.now()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("음수");
	}

	private DiscoveryItem item() {
		return new DiscoveryItem(
				new InstagramMedia(
						"1001",
						"IMAGE",
						"https://www.instagram.com/p/example/",
						Instant.parse("2026-08-18T11:22:33Z"),
						"공개 캡션"),
				new DiscoveryHashtag("피부과"),
				Instant.parse("2026-08-19T00:00:00Z"));
	}
}
