package com.losmos.hrsnsauto.discovery;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "discovery_items")
public class DiscoveryItem {

	public static final int CAPTION_EXCERPT_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "instagram_media_id", nullable = false, length = 255, unique = true)
	private String instagramMediaId;

	@Column(name = "media_type", nullable = false, length = 32)
	private String mediaType;

	@Column(nullable = false, length = 2048)
	private String permalink;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	@Column(name = "first_discovered_at", nullable = false, updatable = false)
	private Instant firstDiscoveredAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "caption_excerpt", length = CAPTION_EXCERPT_MAX_LENGTH)
	private String captionExcerpt;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false, length = 16)
	private DiscoveryReviewStatus reviewStatus;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			name = "discovery_item_hashtags",
			joinColumns = @JoinColumn(name = "discovery_item_id"),
			inverseJoinColumns = @JoinColumn(name = "discovery_hashtag_id"))
	@OrderBy("keyword ASC")
	private Set<DiscoveryHashtag> hashtags = new LinkedHashSet<>();

	@OneToOne(mappedBy = "discoveryItem", fetch = FetchType.LAZY)
	private DiscoveryBrowserObservation browserObservation;

	protected DiscoveryItem() {
	}

	public DiscoveryItem(InstagramMedia media, DiscoveryHashtag hashtag, Instant discoveredAt) {
		this.instagramMediaId = media.mediaId();
		this.firstDiscoveredAt = discoveredAt;
		this.reviewStatus = DiscoveryReviewStatus.NEW;
		observe(media, hashtag, discoveredAt);
	}

	void observe(InstagramMedia media, DiscoveryHashtag hashtag, Instant observedAt) {
		if (!this.instagramMediaId.equals(media.mediaId())) {
			throw new IllegalArgumentException("다른 Instagram media ID로 discovery item을 갱신할 수 없음");
		}
		this.mediaType = media.mediaType();
		this.permalink = media.permalink();
		this.publishedAt = media.publishedAt();
		this.captionExcerpt = excerpt(media.caption());
		this.lastSeenAt = observedAt;
		this.hashtags.add(hashtag);
	}

	void markOpened() {
		// DISMISSED는 운영자의 명시적 제외 결정이므로 링크를 다시 열어도 상태를 되돌리지 않는다.
		if (reviewStatus == DiscoveryReviewStatus.NEW) {
			this.reviewStatus = DiscoveryReviewStatus.OPENED;
		}
	}

	void dismiss() {
		this.reviewStatus = DiscoveryReviewStatus.DISMISSED;
	}

	static String excerpt(String caption) {
		if (caption == null || caption.isBlank()) {
			return null;
		}
		String trimmed = caption.strip();
		int codePointCount = trimmed.codePointCount(0, trimmed.length());
		if (codePointCount <= CAPTION_EXCERPT_MAX_LENGTH) {
			return trimmed;
		}
		int endIndex = trimmed.offsetByCodePoints(0, CAPTION_EXCERPT_MAX_LENGTH);
		return trimmed.substring(0, endIndex);
	}

	public Long getId() {
		return id;
	}

	public String getInstagramMediaId() {
		return instagramMediaId;
	}

	public String getMediaType() {
		return mediaType;
	}

	public String getPermalink() {
		return permalink;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public Instant getFirstDiscoveredAt() {
		return firstDiscoveredAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public String getCaptionExcerpt() {
		return captionExcerpt;
	}

	public DiscoveryReviewStatus getReviewStatus() {
		return reviewStatus;
	}

	public Set<DiscoveryHashtag> getHashtags() {
		return Collections.unmodifiableSet(hashtags);
	}

	public DiscoveryBrowserObservation getBrowserObservation() {
		return browserObservation;
	}
}
