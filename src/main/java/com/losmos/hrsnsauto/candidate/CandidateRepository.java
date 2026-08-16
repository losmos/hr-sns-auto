package com.losmos.hrsnsauto.candidate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

	List<Candidate> findAllByOrderByCreatedAtDescIdDesc();

	boolean existsByInstagramUsername(String instagramUsername);
}
