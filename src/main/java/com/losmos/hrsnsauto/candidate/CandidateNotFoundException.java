package com.losmos.hrsnsauto.candidate;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CandidateNotFoundException extends RuntimeException {

	public CandidateNotFoundException(Long candidateId) {
		super("후보를 찾을 수 없음: " + candidateId);
	}
}
