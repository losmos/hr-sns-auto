package com.losmos.hrsnsauto.candidate;

public enum HairTransplantEvidenceFinding {
	SUPPORTS_NOT_RELATED(
			"관련 없음 근거",
			"모발이식·탈모수술·헤어라인 수술 등 제외 대상과 관련되지 않는다는 판단을 지지함"),
	SUPPORTS_RELATED(
			"관련 있음 근거",
			"모발이식·탈모수술·헤어라인 수술 등 제외 대상과 관련된다는 판단을 지지함"),
	INCONCLUSIVE(
			"결론 불충분",
			"모발이식 관련 여부를 안전하게 확정하기에는 부족하거나 모호함");

	private final String label;
	private final String description;

	HairTransplantEvidenceFinding(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}
}
