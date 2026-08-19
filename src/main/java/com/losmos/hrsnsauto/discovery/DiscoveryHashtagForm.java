package com.losmos.hrsnsauto.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DiscoveryHashtagForm {

	@NotBlank(message = "hashtag를 입력한다")
	@Size(max = 100, message = "hashtag는 100자 이내로 입력한다")
	private String keyword;

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}
}
