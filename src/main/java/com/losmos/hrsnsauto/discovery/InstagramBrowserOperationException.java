package com.losmos.hrsnsauto.discovery;

public class InstagramBrowserOperationException extends RuntimeException {

	private final InstagramBrowserErrorCode errorCode;

	InstagramBrowserOperationException(InstagramBrowserErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	InstagramBrowserOperationException(InstagramBrowserErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public InstagramBrowserErrorCode getErrorCode() {
		return errorCode;
	}
}
