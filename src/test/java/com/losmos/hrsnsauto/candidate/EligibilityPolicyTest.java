package com.losmos.hrsnsauto.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class EligibilityPolicyTest {

	private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T00:00:00Z");

	private final EligibilityPolicy policy = new EligibilityPolicy();

	@Test
	void doctorWithRequiredEvidenceIsEligible() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
	}

	@Test
	void pharmacistWithRequiredEvidenceIsEligible() {
		Candidate candidate = candidate(Profession.PHARMACIST, 8_500, HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
	}

	@Test
	void koreanMedicineProfessionIsIneligible() {
		Candidate candidate = candidate(Profession.KOREAN_MEDICINE, 5_000,
				HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
		assertThat(decision.reason()).contains("한의사");
	}

	@Test
	void otherProfessionIsIneligible() {
		Candidate candidate = candidate(Profession.OTHER, 5_000, HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
	}

	@Test
	void hairTransplantRelatedCandidateIsIneligible() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
		assertThat(decision.reason()).contains("모발이식 관련성이 확인");
	}

	@Test
	void unknownHairTransplantRelationRequiresReview() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.UNKNOWN);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
		assertThat(decision.reason()).contains("모발이식 관련성이 확인되지 않음");
	}

	@Test
	void tenThousandFollowersIsIneligible() {
		Candidate candidate = candidate(Profession.DOCTOR, 10_000, HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
	}

	@Test
	void nineThousandNineHundredNinetyNineFollowersCanBeEligible() {
		Candidate candidate = candidate(Profession.DOCTOR, 9_999, HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
	}

	@Test
	void missingFollowerCountRequiresReview() {
		Candidate candidate = candidate(Profession.DOCTOR, null, HairTransplantRelation.NOT_RELATED);

		EligibilityDecision decision = policy.assess(candidate, requiredStrongEvidence(candidate));

		assertThat(decision.status()).isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
		assertThat(decision.reason()).contains("follower 수가 확인되지 않음");
	}

	@Test
	void missingStrongProfessionEvidenceRequiresReview() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.NOT_RELATED);
		List<CandidateEvidence> evidence = List.of(identityEvidence(candidate));

		EligibilityDecision decision = policy.assess(candidate, evidence);

		assertThat(decision.status()).isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
		assertThat(decision.reason()).contains("profession 공개 근거");
	}

	@Test
	void twoIndependentWeakProfessionSourcesWithIdentityAreEligible() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.NOT_RELATED);
		List<CandidateEvidence> evidence = List.of(
				weakProfessionEvidence(candidate, "https://hospital.example/doctors/a"),
				weakProfessionEvidence(candidate, "https://association.example/members/a"),
				identityEvidence(candidate));

		EligibilityDecision decision = policy.assess(candidate, evidence);

		assertThat(decision.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
	}

	@Test
	void twoWeakProfessionEntriesFromSameSourceRequireReview() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.NOT_RELATED);
		List<CandidateEvidence> evidence = List.of(
				weakProfessionEvidence(candidate, "https://hospital.example/doctors/a"),
				weakProfessionEvidence(candidate, "https://hospital.example/doctors/a"),
				identityEvidence(candidate));

		EligibilityDecision decision = policy.assess(candidate, evidence);

		assertThat(decision.status()).isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
	}

	@Test
	void missingIdentityEvidenceRequiresReview() {
		Candidate candidate = candidate(Profession.DOCTOR, 5_000, HairTransplantRelation.NOT_RELATED);
		List<CandidateEvidence> evidence = List.of(strongProfessionEvidence(candidate));

		EligibilityDecision decision = policy.assess(candidate, evidence);

		assertThat(decision.status()).isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
		assertThat(decision.reason()).contains("identity evidence가 없음");
	}

	@Test
	void hardExcludeTakesPrecedenceOverMissingEvidenceReview() {
		Candidate candidate = candidate(Profession.DOCTOR, 12_000, HairTransplantRelation.UNKNOWN);

		EligibilityDecision decision = policy.assess(candidate, List.of());

		assertThat(decision.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
		assertThat(decision.reason())
				.contains("follower 10,000 이상")
				.doesNotContain("identity evidence");
	}

	private Candidate candidate(Profession profession, Integer followerCount,
			HairTransplantRelation hairTransplantRelation) {
		return new Candidate("doctor_a", "테스트 후보", profession, "내과", followerCount,
				hairTransplantRelation);
	}

	private List<CandidateEvidence> requiredStrongEvidence(Candidate candidate) {
		return List.of(strongProfessionEvidence(candidate), identityEvidence(candidate));
	}

	private CandidateEvidence strongProfessionEvidence(Candidate candidate) {
		return evidence(candidate, EvidenceType.PROFESSION, EvidenceStrength.STRONG,
				"https://hospital.example/doctors/a");
	}

	private CandidateEvidence weakProfessionEvidence(Candidate candidate, String sourceUrl) {
		return evidence(candidate, EvidenceType.PROFESSION, EvidenceStrength.WEAK, sourceUrl);
	}

	private CandidateEvidence identityEvidence(Candidate candidate) {
		return evidence(candidate, EvidenceType.IDENTITY, EvidenceStrength.WEAK,
				"https://hospital.example/doctors/a/instagram");
	}

	private CandidateEvidence evidence(Candidate candidate, EvidenceType type, EvidenceStrength strength,
			String sourceUrl) {
		return new CandidateEvidence(candidate, type, strength, sourceUrl, "공개 페이지에서 확인함", OBSERVED_AT);
	}
}
