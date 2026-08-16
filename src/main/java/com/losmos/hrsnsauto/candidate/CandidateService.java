package com.losmos.hrsnsauto.candidate;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CandidateService {

	private static final ZoneId OPERATOR_ZONE = ZoneId.of("Asia/Seoul");

	private final CandidateRepository candidateRepository;
	private final CandidateEvidenceRepository evidenceRepository;
	private final EligibilityPolicy eligibilityPolicy;

	public CandidateService(CandidateRepository candidateRepository, CandidateEvidenceRepository evidenceRepository,
			EligibilityPolicy eligibilityPolicy) {
		this.candidateRepository = candidateRepository;
		this.evidenceRepository = evidenceRepository;
		this.eligibilityPolicy = eligibilityPolicy;
	}

	public List<Candidate> findAll() {
		return candidateRepository.findAllByOrderByCreatedAtDescIdDesc();
	}

	public Candidate getCandidate(Long candidateId) {
		return candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(candidateId));
	}

	public List<CandidateEvidence> getEvidence(Long candidateId) {
		return evidenceRepository.findAllByCandidateIdOrderByObservedAtDescIdDesc(candidateId);
	}

	@Transactional
	public Candidate createCandidate(CandidateForm form) {
		String normalizedUsername = form.getInstagramUsername().trim().toLowerCase(Locale.ROOT);
		if (candidateRepository.existsByInstagramUsername(normalizedUsername)) {
			throw new DuplicateInstagramUsernameException(normalizedUsername);
		}

		Candidate candidate = new Candidate(
				normalizedUsername,
				form.getDisplayName().trim(),
				form.getProfession(),
				nullIfBlank(form.getSpecialty()),
				form.getFollowerCount(),
				form.getHairTransplantRelation());
		reassess(candidate, List.of());

		try {
			// flush까지 수행해 동시 등록에서 발생한 DB unique 위반도 form 오류로 돌려보낸다.
			return candidateRepository.saveAndFlush(candidate);
		}
		catch (DataIntegrityViolationException exception) {
			throw new DuplicateInstagramUsernameException(normalizedUsername, exception);
		}
	}

	@Transactional
	public Candidate addEvidence(Long candidateId, EvidenceForm form) {
		Candidate candidate = getCandidate(candidateId);
		CandidateEvidence evidence = new CandidateEvidence(
				candidate,
				form.getType(),
				form.getStrength(),
				form.getSourceUrl().trim(),
				form.getSummary().trim(),
				form.getObservedAt().atZone(OPERATOR_ZONE).toInstant());
		evidenceRepository.saveAndFlush(evidence);

		// 새 evidence까지 DB에서 다시 읽어 동일한 경로로 항상 전체 evidence를 평가한다.
		reassess(candidate, getEvidence(candidateId));
		return candidateRepository.save(candidate);
	}

	@Transactional
	public Candidate reassess(Long candidateId) {
		Candidate candidate = getCandidate(candidateId);
		reassess(candidate, getEvidence(candidateId));
		return candidateRepository.save(candidate);
	}

	private void reassess(Candidate candidate, List<CandidateEvidence> evidence) {
		candidate.applyEligibility(eligibilityPolicy.assess(candidate, evidence));
	}

	private String nullIfBlank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
