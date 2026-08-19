package com.losmos.hrsnsauto.discovery;

import java.util.List;

public class BrowserEnrichmentBatchResult {

	private final int requestedCount;
	private final List<BrowserEnrichmentItemResult> itemResults;

	BrowserEnrichmentBatchResult(int requestedCount, List<BrowserEnrichmentItemResult> itemResults) {
		this.requestedCount = requestedCount;
		this.itemResults = List.copyOf(itemResults);
	}

	public int getRequestedCount() {
		return requestedCount;
	}

	public List<BrowserEnrichmentItemResult> getItemResults() {
		return itemResults;
	}

	public int getProcessedCount() {
		return itemResults.size();
	}

	public long getSuccessCount() {
		return itemResults.stream()
				.filter(result -> result.status() == DiscoveryBrowserObservationStatus.SUCCESS)
				.count();
	}

	public long getPartialCount() {
		return itemResults.stream()
				.filter(result -> result.status() == DiscoveryBrowserObservationStatus.PARTIAL)
				.count();
	}

	public long getFailedCount() {
		return itemResults.stream().filter(result -> !result.completedWithScreeningData()).count();
	}

	public boolean isStoppedForOperatorAction() {
		return itemResults.stream().anyMatch(BrowserEnrichmentItemResult::requiresBatchStop);
	}
}
