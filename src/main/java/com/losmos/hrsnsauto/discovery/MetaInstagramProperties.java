package com.losmos.hrsnsauto.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "meta.instagram")
public class MetaInstagramProperties {

	private String accessToken = "";
	private String apiVersion = "";
	private String igUserId = "";

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken == null ? "" : accessToken;
	}

	public String getApiVersion() {
		return apiVersion;
	}

	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion == null ? "" : apiVersion;
	}

	public String getIgUserId() {
		return igUserId;
	}

	public void setIgUserId(String igUserId) {
		this.igUserId = igUserId == null ? "" : igUserId;
	}
}
