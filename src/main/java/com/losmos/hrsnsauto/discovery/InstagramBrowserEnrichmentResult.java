package com.losmos.hrsnsauto.discovery;

public class InstagramBrowserEnrichmentResult {

	private final DiscoveryBrowserObservationStatus status;
	private final InstagramBrowserErrorCode errorCode;
	private final String errorSummary;
	private final InstagramPostBrowserSnapshot post;
	private final InstagramProfileBrowserSnapshot profile;

	private InstagramBrowserEnrichmentResult(DiscoveryBrowserObservationStatus status,
			InstagramBrowserErrorCode errorCode, String errorSummary,
			InstagramPostBrowserSnapshot post, InstagramProfileBrowserSnapshot profile) {
		this.status = status;
		this.errorCode = errorCode;
		this.errorSummary = errorSummary;
		this.post = post;
		this.profile = profile;
	}

	static InstagramBrowserEnrichmentResult success(
			InstagramPostBrowserSnapshot post, InstagramProfileBrowserSnapshot profile) {
		if (post == null || profile == null) {
			throw new IllegalArgumentException("성공 결과에는 post와 profile 관찰값이 필요함");
		}
		DiscoveryBrowserObservationStatus status = profile.isPartial()
				? DiscoveryBrowserObservationStatus.PARTIAL
				: DiscoveryBrowserObservationStatus.SUCCESS;
		return new InstagramBrowserEnrichmentResult(status, null, null, post, profile);
	}

	static InstagramBrowserEnrichmentResult failure(DiscoveryBrowserObservationStatus status,
			InstagramBrowserErrorCode errorCode, String errorSummary) {
		return failure(status, errorCode, errorSummary, null);
	}

	static InstagramBrowserEnrichmentResult failure(DiscoveryBrowserObservationStatus status,
			InstagramBrowserErrorCode errorCode, String errorSummary, InstagramPostBrowserSnapshot post) {
		if (status == DiscoveryBrowserObservationStatus.SUCCESS
				|| status == DiscoveryBrowserObservationStatus.PARTIAL
				|| errorCode == null
				|| errorSummary == null
				|| errorSummary.isBlank()) {
			throw new IllegalArgumentException("실패 결과에는 실패 상태, 오류 코드와 안전한 요약이 필요함");
		}
		return new InstagramBrowserEnrichmentResult(status, errorCode, errorSummary, post, null);
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

	public DiscoveryBrowserObservationStatus getStatus() {
		return status;
	}

	public InstagramBrowserErrorCode getErrorCode() {
		return errorCode;
	}

	public String getErrorSummary() {
		return errorSummary;
	}

	public String getAuthorUsername() {
		return post == null ? null : post.authorUsername();
	}

	public String getAuthorDisplayName() {
		return profile == null ? null : profile.displayName();
	}

	public String getProfileUrl() {
		return post == null ? null : post.profileUrl();
	}

	public Long getFollowerCount() {
		return profile == null ? null : profile.followerCount();
	}

	public Long getFollowingCount() {
		return profile == null ? null : profile.followingCount();
	}

	public Long getPostCount() {
		return profile == null ? null : profile.postCount();
	}

	public String getBiographyExcerpt() {
		return profile == null ? null : profile.biographyExcerpt();
	}

	public Boolean getVerified() {
		return profile == null ? null : profile.verified();
	}

	public Boolean getPrivateAccount() {
		return profile == null ? null : profile.privateAccount();
	}

	public Long getPostLikeCount() {
		return post == null ? null : post.likeCount();
	}

	public Long getPostCommentCount() {
		return post == null ? null : post.commentCount();
	}

	public Long getPostViewCount() {
		return post == null ? null : post.viewCount();
	}
}
