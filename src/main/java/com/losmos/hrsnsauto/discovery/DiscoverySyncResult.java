package com.losmos.hrsnsauto.discovery;

import java.util.List;

public class DiscoverySyncResult {

	private final List<HashtagSyncResult> hashtagResults;

	public DiscoverySyncResult(List<HashtagSyncResult> hashtagResults) {
		this.hashtagResults = List.copyOf(hashtagResults);
	}

	public List<HashtagSyncResult> getHashtagResults() {
		return hashtagResults;
	}

	public long getSuccessfulCount() {
		return hashtagResults.stream().filter(HashtagSyncResult::successful).count();
	}

	public long getFailedCount() {
		return hashtagResults.size() - getSuccessfulCount();
	}

	public int getFetchedCount() {
		return hashtagResults.stream().mapToInt(HashtagSyncResult::fetchedCount).sum();
	}

	public int getCreatedCount() {
		return hashtagResults.stream().mapToInt(HashtagSyncResult::createdCount).sum();
	}
}
