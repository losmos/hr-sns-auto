package com.losmos.hrsnsauto.candidate;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CandidateEvidenceTest {

	private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T00:00:00Z");

	@Test
	void hairTransplantEvidenceRequiresFinding() {
		Candidate candidate = candidate();

		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvidence(
				candidate,
				EvidenceType.HAIR_TRANSPLANT,
				EvidenceStrength.STRONG,
				null,
				"https://hospital.example/services",
				"공개 근거",
				OBSERVED_AT));
	}

	@Test
	void nonHairEvidenceRejectsHairTransplantFinding() {
		Candidate candidate = candidate();

		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvidence(
				candidate,
				EvidenceType.PROFESSION,
				EvidenceStrength.STRONG,
				HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED,
				"https://hospital.example/doctors/a",
				"공개 근거",
				OBSERVED_AT));
	}

	private Candidate candidate() {
		return new Candidate("doctor_a", "테스트 후보", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
	}
}
