package com.losmos.hrsnsauto.candidate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class CandidateForm {

	@NotBlank(message = "Instagram username을 입력한다")
	@Pattern(regexp = "[A-Za-z0-9._]{1,30}",
			message = "username은 @ 없이 영문, 숫자, 마침표, 밑줄만 30자 이내로 입력한다")
	private String instagramUsername;

	@NotBlank(message = "표시 이름을 입력한다")
	@Size(max = 100, message = "표시 이름은 100자 이내로 입력한다")
	private String displayName;

	@NotNull(message = "profession을 선택한다")
	private Profession profession = Profession.UNKNOWN;

	@Size(max = 100, message = "전문 분야는 100자 이내로 입력한다")
	private String specialty;

	@PositiveOrZero(message = "follower 수는 0 이상으로 입력한다")
	private Integer followerCount;

	@NotNull(message = "모발이식 관련성을 선택한다")
	private HairTransplantRelation hairTransplantRelation = HairTransplantRelation.UNKNOWN;

	public String getInstagramUsername() {
		return instagramUsername;
	}

	public void setInstagramUsername(String instagramUsername) {
		this.instagramUsername = instagramUsername;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public Profession getProfession() {
		return profession;
	}

	public void setProfession(Profession profession) {
		this.profession = profession;
	}

	public String getSpecialty() {
		return specialty;
	}

	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}

	public Integer getFollowerCount() {
		return followerCount;
	}

	public void setFollowerCount(Integer followerCount) {
		this.followerCount = followerCount;
	}

	public HairTransplantRelation getHairTransplantRelation() {
		return hairTransplantRelation;
	}

	public void setHairTransplantRelation(HairTransplantRelation hairTransplantRelation) {
		this.hairTransplantRelation = hairTransplantRelation;
	}
}
