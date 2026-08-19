package com.losmos.hrsnsauto.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DiscoveryItemNotFoundException extends RuntimeException {

	public DiscoveryItemNotFoundException(Long itemId) {
		super("Discovery item을 찾을 수 없음: " + itemId);
	}
}
