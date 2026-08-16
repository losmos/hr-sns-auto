package com.losmos.hrsnsauto.candidate;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidate_evidence")
public class CandidateEvidence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "candidate_id", nullable = false)
	private Candidate candidate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private EvidenceType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private EvidenceStrength strength;

	@Column(name = "source_url", nullable = false, length = 2048)
	private String sourceUrl;

	@Column(nullable = false, length = 1000)
	private String summary;

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CandidateEvidence() {
	}

	public CandidateEvidence(Candidate candidate, EvidenceType type, EvidenceStrength strength, String sourceUrl,
			String summary, Instant observedAt) {
		this.candidate = candidate;
		this.type = type;
		this.strength = strength;
		this.sourceUrl = sourceUrl;
		this.summary = summary;
		this.observedAt = observedAt;
	}

	@PrePersist
	void recordCreationTime() {
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Candidate getCandidate() {
		return candidate;
	}

	public EvidenceType getType() {
		return type;
	}

	public EvidenceStrength getStrength() {
		return strength;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getSummary() {
		return summary;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
