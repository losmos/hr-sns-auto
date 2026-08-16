package com.losmos.hrsnsauto.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidateEvidenceRepository evidenceRepository;

	private CandidateService candidateService;

	@BeforeEach
	void setUp() {
		candidateService = new CandidateService(candidateRepository, evidenceRepository, new EligibilityPolicy());
	}

	@Test
	void addingEvidenceAutomaticallyReassessesCandidate() {
		Candidate candidate = new Candidate("doctor_a", "홍길동", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
		CandidateEvidence professionEvidence = evidence(candidate, EvidenceType.PROFESSION,
				"https://hospital.example/doctors/a");
		CandidateEvidence identityEvidence = evidence(candidate, EvidenceType.IDENTITY,
				"https://hospital.example/doctors/a/instagram");
		CandidateEvidence hairEvidence = evidence(candidate, EvidenceType.HAIR_TRANSPLANT,
				"https://hospital.example/services");
		EvidenceForm form = validEvidenceForm();

		when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
		when(evidenceRepository.findAllByCandidateIdOrderByObservedAtDescIdDesc(1L))
				.thenReturn(List.of(professionEvidence, identityEvidence, hairEvidence));
		when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Candidate reassessed = candidateService.addEvidence(1L, form);

		assertThat(reassessed.getEligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
		ArgumentCaptor<CandidateEvidence> savedEvidence = ArgumentCaptor.forClass(CandidateEvidence.class);
		verify(evidenceRepository).saveAndFlush(savedEvidence.capture());
		assertThat(savedEvidence.getValue().getHairTransplantFinding())
				.isEqualTo(HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED);
	}

	private EvidenceForm validEvidenceForm() {
		EvidenceForm form = new EvidenceForm();
		form.setType(EvidenceType.HAIR_TRANSPLANT);
		form.setStrength(EvidenceStrength.STRONG);
		form.setHairTransplantFinding(HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED);
		form.setSourceUrl("https://hospital.example/services");
		form.setSummary("공개 진료 안내에서 모발이식 비관련성을 확인함");
		form.setObservedAt(LocalDateTime.of(2026, 8, 17, 9, 0));
		return form;
	}

	private CandidateEvidence evidence(Candidate candidate, EvidenceType type, String sourceUrl) {
		return new CandidateEvidence(candidate, type,
				type == EvidenceType.IDENTITY ? EvidenceStrength.WEAK : EvidenceStrength.STRONG,
				type == EvidenceType.HAIR_TRANSPLANT
						? HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED
						: null,
				sourceUrl, "공개 근거", Instant.parse("2026-08-17T00:00:00Z"));
	}
}
