package com.losmos.hrsnsauto.discovery;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "discovery_hashtags")
public class DiscoveryHashtag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100, unique = true)
	private String keyword;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DiscoveryHashtag() {
	}

	public DiscoveryHashtag(String normalizedKeyword) {
		this.keyword = normalizedKeyword;
		this.enabled = true;
	}

	void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@PrePersist
	void recordCreationTime() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void recordUpdateTime() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getKeyword() {
		return keyword;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
