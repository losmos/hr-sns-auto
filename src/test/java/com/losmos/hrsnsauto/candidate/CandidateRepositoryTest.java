package com.losmos.hrsnsauto.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CandidateRepositoryTest {

	@Autowired
	private CandidateRepository candidateRepository;

	@Autowired
	private CandidateEvidenceRepository evidenceRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savesCandidateWithCurrentEligibility() {
		Candidate candidate = candidate("saved_doctor");

		Candidate saved = candidateRepository.saveAndFlush(candidate);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getEligibilityStatus()).isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void rejectsDuplicateInstagramUsername() {
		candidateRepository.saveAndFlush(candidate("duplicate_doctor"));

		assertThatThrownBy(() -> candidateRepository.saveAndFlush(candidate("duplicate_doctor")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void savesEvidenceRelationAndFindsItByCandidate() {
		Candidate candidate = candidateRepository.saveAndFlush(candidate("evidence_doctor"));
		CandidateEvidence evidence = new CandidateEvidence(
				candidate,
				EvidenceType.PROFESSION,
				EvidenceStrength.STRONG,
				null,
				"https://hospital.example/doctors/evidence_doctor",
				"병원 의료진 페이지에서 의사임을 확인함",
				Instant.parse("2026-08-17T00:00:00Z"));

		evidenceRepository.saveAndFlush(evidence);

		assertThat(evidenceRepository.findAllByCandidateIdOrderByObservedAtDescIdDesc(candidate.getId()))
				.singleElement()
				.satisfies(savedEvidence -> {
					assertThat(savedEvidence.getCandidate().getId()).isEqualTo(candidate.getId());
					assertThat(savedEvidence.getSourceUrl()).isEqualTo(evidence.getSourceUrl());
					assertThat(savedEvidence.getHairTransplantFinding()).isNull();
				});
	}

	@Test
	void savesAndLoadsHairTransplantFindingEnum() {
		Candidate candidate = candidateRepository.saveAndFlush(candidate("hair_evidence_doctor"));
		CandidateEvidence evidence = new CandidateEvidence(
				candidate,
				EvidenceType.HAIR_TRANSPLANT,
				EvidenceStrength.STRONG,
				HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED,
				"https://hospital.example/services",
				"공개 진료 안내에서 모발이식 비관련성을 확인함",
				Instant.parse("2026-08-17T00:00:00Z"));
		evidenceRepository.saveAndFlush(evidence);
		entityManager.clear();

		assertThat(evidenceRepository.findAllByCandidateIdOrderByObservedAtDescIdDesc(candidate.getId()))
				.singleElement()
				.extracting(CandidateEvidence::getHairTransplantFinding)
				.isEqualTo(HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED);
	}

	@Test
	void databaseRejectsHairEvidenceWithoutFinding() {
		Candidate candidate = candidateRepository.saveAndFlush(candidate("missing_hair_finding"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO candidate_evidence (
				    candidate_id, type, strength, hair_transplant_finding,
				    source_url, summary, observed_at, created_at
				) VALUES (
				    ?, 'HAIR_TRANSPLANT', 'STRONG', NULL,
				    'https://hospital.example/services', '공개 근거', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				)
				""", candidate.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsHairFindingForNonHairEvidence() {
		Candidate candidate = candidateRepository.saveAndFlush(candidate("unexpected_hair_finding"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO candidate_evidence (
				    candidate_id, type, strength, hair_transplant_finding,
				    source_url, summary, observed_at, created_at
				) VALUES (
				    ?, 'PROFESSION', 'STRONG', 'SUPPORTS_NOT_RELATED',
				    'https://hospital.example/doctors/a', '공개 근거', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				)
				""", candidate.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Candidate candidate(String username) {
		return new Candidate(username, "테스트 의사", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
	}
}
