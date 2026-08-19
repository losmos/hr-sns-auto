package com.losmos.hrsnsauto.discovery;

public record InstagramBrowserSessionResult(
		InstagramBrowserSessionStatus status,
		InstagramBrowserErrorCode errorCode,
		String message) {

	static InstagramBrowserSessionResult ready() {
		return new InstagramBrowserSessionResult(
				InstagramBrowserSessionStatus.READY,
				null,
				"Instagram 브라우저를 열었음. 화면에서 로그인 상태를 확인한다.");
	}

	static InstagramBrowserSessionResult loginRequired() {
		return new InstagramBrowserSessionResult(
				InstagramBrowserSessionStatus.LOGIN_REQUIRED,
				InstagramBrowserErrorCode.LOGIN_REQUIRED,
				"Instagram 로그인이 필요함. 열린 브라우저에서 직접 로그인한다.");
	}

	static InstagramBrowserSessionResult actionRequired() {
		return new InstagramBrowserSessionResult(
				InstagramBrowserSessionStatus.ACTION_REQUIRED,
				InstagramBrowserErrorCode.ACTION_REQUIRED,
				"Instagram challenge/checkpoint 확인이 필요함. 자동 우회 없이 브라우저에서 직접 확인한다.");
	}

	static InstagramBrowserSessionResult failed(InstagramBrowserErrorCode errorCode, String message) {
		return new InstagramBrowserSessionResult(InstagramBrowserSessionStatus.FAILED, errorCode, message);
	}

	public boolean isOperatorMessage() {
		return status == InstagramBrowserSessionStatus.READY
				|| status == InstagramBrowserSessionStatus.LOGIN_REQUIRED;
	}
}
