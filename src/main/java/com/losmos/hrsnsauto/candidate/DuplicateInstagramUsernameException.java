package com.losmos.hrsnsauto.candidate;

public class DuplicateInstagramUsernameException extends RuntimeException {

	public DuplicateInstagramUsernameException(String instagramUsername) {
		super("이미 등록된 Instagram username이다: @" + instagramUsername);
	}

	public DuplicateInstagramUsernameException(String instagramUsername, Throwable cause) {
		super("이미 등록된 Instagram username이다: @" + instagramUsername, cause);
	}
}
