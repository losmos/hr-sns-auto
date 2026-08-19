package com.losmos.hrsnsauto.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DiscoveryHashtagNotFoundException extends RuntimeException {

	public DiscoveryHashtagNotFoundException(Long hashtagId) {
		super("Discovery hashtag를 찾을 수 없음: " + hashtagId);
	}
}
