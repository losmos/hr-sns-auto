package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstagramBrowserErrorSanitizerTest {

	@Test
	void removesCredentialsProfilePathAndMultilinePageDetails() {
		InstagramBrowserErrorSanitizer sanitizer = new InstagramBrowserErrorSanitizer();
		String message = "launch failed password=hunter2 sessionid=secret Cookie: private "
				+ "Authorization: Bearer bearer-secret {\"access_token\":\"json-secret\"} "
				+ "/work/.local/instagram-browser-profile"
				+ "\nfull Instagram page text and call log must not survive";

		String sanitized = sanitizer.sanitize(message, "/work/.local/instagram-browser-profile");

		assertThat(sanitized)
				.contains("password=[REDACTED]", "sessionid=[REDACTED]", "Cookie: [REDACTED]", "[BROWSER_PROFILE]")
				.doesNotContain(
						"hunter2", "secret", "private", "bearer-secret", "json-secret",
						"full Instagram page text", ".local");
	}

	@Test
	void limitsUnexpectedBrowserErrorLength() {
		InstagramBrowserErrorSanitizer sanitizer = new InstagramBrowserErrorSanitizer();
		assertThat(sanitizer.sanitize("x".repeat(1_000), null)).hasSize(300);
	}
}
