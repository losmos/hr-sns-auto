package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InstagramBrowserExtractorTest {

	private final InstagramBrowserExtractor extractor = new InstagramBrowserExtractor(
			new InstagramMetricParser());

	@Test
	void acceptsOnlyAVisibleProfileLinkWhoseLabelMatchesUsername() {
		InstagramBrowserExtractor.InstagramProfileLink candidate = extractor.findAuthorCandidate(List.of(
				new InstagramBrowserExtractor.VisibleLink("/p/post-code/", "post"),
				new InstagramBrowserExtractor.VisibleLink("/explore/", "doctor.alpha"),
				new InstagramBrowserExtractor.VisibleLink("/caption_mention/", "different.user"),
				new InstagramBrowserExtractor.VisibleLink("/Doctor.Alpha/", "@doctor.alpha")))
				.orElseThrow();

		assertThat(candidate.username()).isEqualTo("doctor.alpha");
		assertThat(candidate.profileUrl()).isEqualTo("https://www.instagram.com/doctor.alpha/");
	}

	@Test
	void rejectsCaptionGuessNonProfilePathsMultiSegmentAndForeignHosts() {
		assertThat(extractor.authorCandidate("/caption_guess/", "actual.author")).isEmpty();
		assertThat(extractor.authorCandidate("/p/example/", "p")).isEmpty();
		assertThat(extractor.authorCandidate("/accounts/login/", "accounts")).isEmpty();
		assertThat(extractor.authorCandidate("/doctor.alpha/tagged/", "doctor.alpha")).isEmpty();
		assertThat(extractor.authorCandidate("https://example.com/doctor.alpha/", "doctor.alpha")).isEmpty();
		assertThat(extractor.authorCandidate("/doctor..alpha/", "doctor..alpha")).isEmpty();
	}

	@Test
	void validatesProfileUsernameAndSupportedPostPermalinks() {
		assertThat(extractor.profileUsernameFromUrl("https://www.instagram.com/Doctor_1/?hl=ko"))
				.contains("doctor_1");
		assertThat(extractor.profileUsernameFromUrl("/doctor.1/")).contains("doctor.1");
		assertThat(extractor.profileUsernameFromUrl("/reel/")).isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/doctor%2E1/")).isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/doctor./")).isEmpty();

		assertThat(extractor.isInstagramPostUrl("https://www.instagram.com/p/AbC123/"))
				.isTrue();
		assertThat(extractor.isInstagramPostUrl("https://instagram.com/reel/AbC123/?utm_source=x"))
				.isTrue();
		assertThat(extractor.isInstagramPostUrl("https://instagram.com/explore/AbC123/"))
				.isFalse();
	}
}
