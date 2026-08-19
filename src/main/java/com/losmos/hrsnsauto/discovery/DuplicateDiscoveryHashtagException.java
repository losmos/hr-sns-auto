package com.losmos.hrsnsauto.discovery;

public class DuplicateDiscoveryHashtagException extends RuntimeException {

	public DuplicateDiscoveryHashtagException(String keyword) {
		super("이미 등록된 hashtag이다: #" + keyword);
	}

	public DuplicateDiscoveryHashtagException(String keyword, Throwable cause) {
		super("이미 등록된 hashtag이다: #" + keyword, cause);
	}
}
