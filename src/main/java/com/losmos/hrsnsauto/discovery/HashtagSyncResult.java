package com.losmos.hrsnsauto.discovery;

public record HashtagSyncResult(
		String keyword,
		boolean successful,
		int fetchedCount,
		int createdCount,
		String errorMessage) {

	static HashtagSyncResult success(String keyword, int fetchedCount, int createdCount) {
		return new HashtagSyncResult(keyword, true, fetchedCount, createdCount, null);
	}

	static HashtagSyncResult failure(String keyword, String errorMessage) {
		return new HashtagSyncResult(keyword, false, 0, 0, errorMessage);
	}
}
