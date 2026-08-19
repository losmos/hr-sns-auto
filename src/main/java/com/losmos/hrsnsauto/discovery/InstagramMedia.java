package com.losmos.hrsnsauto.discovery;

import java.time.Instant;

public record InstagramMedia(
		String mediaId,
		String mediaType,
		String permalink,
		Instant publishedAt,
		String caption) {
}
