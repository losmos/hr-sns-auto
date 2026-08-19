package com.losmos.hrsnsauto.discovery;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "discovery_browser_observations")
public class DiscoveryBrowserObservation {

	public static final int BIOGRAPHY_EXCERPT_MAX_LENGTH = 300;
	public static final int ERROR_SUMMARY_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "discovery_item_id", nullable = false, unique = true)
	private DiscoveryItem discoveryItem;

	@Column(name = "author_username", length = 30)
	private String authorUsername;

	@Column(name = "author_display_name", length = 255)
	private String authorDisplayName;

	@Column(name = "profile_url", length = 2048)
	private String profileUrl;

	@Column(name = "follower_count")
	private Long followerCount;

	@Column(name = "following_count")
	private Long followingCount;

	@Column(name = "post_count")
	private Long postCount;

	@Column(name = "biography_excerpt", length = BIOGRAPHY_EXCERPT_MAX_LENGTH)
	private String biographyExcerpt;

	@Column
	private Boolean verified;

	@Column(name = "private_account")
	private Boolean privateAccount;

	@Column(name = "post_like_count")
	private Long postLikeCount;

	@Column(name = "post_comment_count")
	private Long postCommentCount;

	@Column(name = "post_view_count")
	private Long postViewCount;

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DiscoveryBrowserObservationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "error_code", length = 64)
	private InstagramBrowserErrorCode errorCode;

	@Column(name = "error_summary", length = ERROR_SUMMARY_MAX_LENGTH)
	private String errorSummary;

	protected DiscoveryBrowserObservation() {
	}

	DiscoveryBrowserObservation(DiscoveryItem discoveryItem) {
		this.discoveryItem = discoveryItem;
	}

	void replaceWith(InstagramBrowserEnrichmentResult result, Instant observedAt) {
		this.authorUsername = normalizeUsername(result.getAuthorUsername());
		this.authorDisplayName = excerpt(result.getAuthorDisplayName(), 255);
		this.profileUrl = excerpt(result.getProfileUrl(), 2048);
		this.followerCount = nonnegative(result.getFollowerCount(), "followerCount");
		this.followingCount = nonnegative(result.getFollowingCount(), "followingCount");
		this.postCount = nonnegative(result.getPostCount(), "postCount");
		this.biographyExcerpt = excerpt(result.getBiographyExcerpt(), BIOGRAPHY_EXCERPT_MAX_LENGTH);
		this.verified = result.getVerified();
		this.privateAccount = result.getPrivateAccount();
		this.postLikeCount = nonnegative(result.getPostLikeCount(), "postLikeCount");
		this.postCommentCount = nonnegative(result.getPostCommentCount(), "postCommentCount");
		this.postViewCount = nonnegative(result.getPostViewCount(), "postViewCount");
		this.observedAt = observedAt;
		this.status = result.getStatus();
		this.errorCode = result.getErrorCode();
		this.errorSummary = excerpt(result.getErrorSummary(), ERROR_SUMMARY_MAX_LENGTH);
	}

	private static String normalizeUsername(String username) {
		String normalized = excerpt(username, 30);
		return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private static Long nonnegative(Long value, String fieldName) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(fieldName + "은 음수일 수 없음");
		}
		return value;
	}

	private static String excerpt(String value, int maxCodePoints) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.strip();
		int codePointCount = trimmed.codePointCount(0, trimmed.length());
		if (codePointCount <= maxCodePoints) {
			return trimmed;
		}
		return trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints));
	}

	public Long getId() {
		return id;
	}

	public DiscoveryItem getDiscoveryItem() {
		return discoveryItem;
	}

	public String getAuthorUsername() {
		return authorUsername;
	}

	public String getAuthorDisplayName() {
		return authorDisplayName;
	}

	public String getProfileUrl() {
		return profileUrl;
	}

	public Long getFollowerCount() {
		return followerCount;
	}

	public Long getFollowingCount() {
		return followingCount;
	}

	public Long getPostCount() {
		return postCount;
	}

	public String getBiographyExcerpt() {
		return biographyExcerpt;
	}

	public Boolean getVerified() {
		return verified;
	}

	public Boolean getPrivateAccount() {
		return privateAccount;
	}

	public Long getPostLikeCount() {
		return postLikeCount;
	}

	public Long getPostCommentCount() {
		return postCommentCount;
	}

	public Long getPostViewCount() {
		return postViewCount;
	}

	public Instant getObservedAt() {
		return observedAt;
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
}
