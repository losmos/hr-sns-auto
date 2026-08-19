package com.losmos.hrsnsauto.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "instagram.browser")
public class InstagramBrowserProperties {

	public static final int DEFAULT_BATCH_SIZE = 10;
	public static final int MAX_BATCH_SIZE = 15;
	public static final String DEFAULT_USER_DATA_DIR = ".local/instagram-browser-profile";

	private boolean automationEnabled;
	private String userDataDir = DEFAULT_USER_DATA_DIR;
	private boolean headless;
	private int batchSize = DEFAULT_BATCH_SIZE;

	public void requireEnabled() {
		if (!automationEnabled) {
			throw new InstagramBrowserOperationException(
					InstagramBrowserErrorCode.AUTOMATION_DISABLED,
					"Instagram 브라우저 보강이 비활성 상태임");
		}
	}

	public int validatedBatchSize() {
		if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
			throw new InstagramBrowserOperationException(
					InstagramBrowserErrorCode.INVALID_CONFIGURATION,
					"INSTAGRAM_BROWSER_BATCH_SIZE는 1 이상 15 이하로 설정해야 함");
		}
		return batchSize;
	}

	public int displayBatchSize() {
		return batchSize >= 1 && batchSize <= MAX_BATCH_SIZE ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public boolean isAutomationEnabled() {
		return automationEnabled;
	}

	public void setAutomationEnabled(boolean automationEnabled) {
		this.automationEnabled = automationEnabled;
	}

	public String getUserDataDir() {
		return userDataDir;
	}

	public void setUserDataDir(String userDataDir) {
		this.userDataDir = userDataDir == null ? "" : userDataDir;
	}

	public boolean isHeadless() {
		return headless;
	}

	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}
}
