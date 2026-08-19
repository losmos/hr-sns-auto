package com.losmos.hrsnsauto.discovery;

public record BrowserEnrichmentItemResult(
		Long itemId,
		DiscoveryBrowserObservationStatus status,
		InstagramBrowserErrorCode errorCode,
		String errorSummary) {

	static BrowserEnrichmentItemResult from(Long itemId, InstagramBrowserEnrichmentResult result) {
		return new BrowserEnrichmentItemResult(
				itemId,
				result.getStatus(),
				result.getErrorCode(),
				result.getErrorSummary());
	}

	static BrowserEnrichmentItemResult persistenceFailure(Long itemId) {
		return new BrowserEnrichmentItemResult(
				itemId,
				DiscoveryBrowserObservationStatus.FAILED,
				InstagramBrowserErrorCode.PERSISTENCE_FAILED,
				"browser observation 저장에 실패함");
	}

	public boolean completedWithScreeningData() {
		return status == DiscoveryBrowserObservationStatus.SUCCESS
				|| status == DiscoveryBrowserObservationStatus.PARTIAL;
	}

	public boolean requiresBatchStop() {
		return status == DiscoveryBrowserObservationStatus.LOGIN_REQUIRED
				|| status == DiscoveryBrowserObservationStatus.ACTION_REQUIRED
				|| errorCode == InstagramBrowserErrorCode.BROWSER_BINARY_MISSING
				|| errorCode == InstagramBrowserErrorCode.BROWSER_PROFILE_IN_USE;
	}
}
