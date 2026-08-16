package com.losmos.hrsnsauto.candidate;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidates")
public class Candidate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "instagram_username", nullable = false, length = 30, unique = true)
	private String instagramUsername;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Profession profession;

	@Column(length = 100)
	private String specialty;

	@Column(name = "follower_count")
	private Integer followerCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "hair_transplant_relation", nullable = false, length = 32)
	private HairTransplantRelation hairTransplantRelation;

	@Enumerated(EnumType.STRING)
	@Column(name = "eligibility_status", nullable = false, length = 32)
	private EligibilityStatus eligibilityStatus;

	@Column(name = "eligibility_reason", nullable = false, length = 500)
	private String eligibilityReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Candidate() {
	}

	public Candidate(String instagramUsername, String displayName, Profession profession, String specialty,
			Integer followerCount, HairTransplantRelation hairTransplantRelation) {
		this.instagramUsername = instagramUsername;
		this.displayName = displayName;
		this.profession = profession;
		this.specialty = specialty;
		this.followerCount = followerCount;
		this.hairTransplantRelation = hairTransplantRelation;
		// 새 후보가 판정 없이 저장되더라도 eligible로 보이지 않도록 fail-closed 기본값을 둔다.
		this.eligibilityStatus = EligibilityStatus.REVIEW_REQUIRED;
		this.eligibilityReason = "아직 eligibility를 판정하지 않음";
	}

	void applyEligibility(EligibilityDecision decision) {
		this.eligibilityStatus = decision.status();
		this.eligibilityReason = decision.reason();
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

	public String getInstagramUsername() {
		return instagramUsername;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Profession getProfession() {
		return profession;
	}

	public String getSpecialty() {
		return specialty;
	}

	public Integer getFollowerCount() {
		return followerCount;
	}

	public HairTransplantRelation getHairTransplantRelation() {
		return hairTransplantRelation;
	}

	public EligibilityStatus getEligibilityStatus() {
		return eligibilityStatus;
	}

	public String getEligibilityReason() {
		return eligibilityReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
