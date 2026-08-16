package com.losmos.hrsnsauto.candidate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateEvidenceRepository extends JpaRepository<CandidateEvidence, Long> {

	List<CandidateEvidence> findAllByCandidateIdOrderByObservedAtDescIdDesc(Long candidateId);
}
