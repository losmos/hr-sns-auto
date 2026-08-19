package com.losmos.hrsnsauto.discovery;

record InstagramProfileBrowserSnapshot(
		String displayName,
		Long followerCount,
		Long followingCount,
		Long postCount,
		String biographyExcerpt,
		Boolean verified,
		Boolean privateAccount) {

	boolean isPartial() {
		// biography는 실제로 비어 있을 수 있으므로 누락만으로 PARTIAL로 만들지 않는다.
		return displayName == null
				|| followerCount == null
				|| followingCount == null
				|| postCount == null
				|| verified == null
				|| privateAccount == null;
	}
}
