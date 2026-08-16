package com.losmos.hrsnsauto.candidate;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EvidenceForm {

	@NotNull(message = "evidence type을 선택한다")
	private EvidenceType type = EvidenceType.PROFESSION;

	@NotNull(message = "evidence strength를 선택한다")
	private EvidenceStrength strength = EvidenceStrength.STRONG;

	// HAIR_TRANSPLANT을 선택해도 비관련 방향이 자동 입력되지 않게 안전한 null 기본값을 유지한다.
	private HairTransplantEvidenceFinding hairTransplantFinding;

	@NotBlank(message = "공개 근거 URL을 입력한다")
	@Size(max = 2048, message = "공개 근거 URL은 2,048자 이내로 입력한다")
	@Pattern(regexp = "https?://[^\\s]+", message = "공개 근거 URL은 http:// 또는 https://로 입력한다")
	private String sourceUrl;

	@NotBlank(message = "확인한 내용을 요약한다")
	@Size(max = 1000, message = "요약은 1,000자 이내로 입력한다")
	private String summary;

	@NotNull(message = "관찰 시각을 입력한다")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime observedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);

	public EvidenceType getType() {
		return type;
	}

	public void setType(EvidenceType type) {
		this.type = type;
	}

	public EvidenceStrength getStrength() {
		return strength;
	}

	public void setStrength(EvidenceStrength strength) {
		this.strength = strength;
	}

	public HairTransplantEvidenceFinding getHairTransplantFinding() {
		return hairTransplantFinding;
	}

	public void setHairTransplantFinding(HairTransplantEvidenceFinding hairTransplantFinding) {
		this.hairTransplantFinding = hairTransplantFinding;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public LocalDateTime getObservedAt() {
		return observedAt;
	}

	public void setObservedAt(LocalDateTime observedAt) {
		this.observedAt = observedAt;
	}
}
