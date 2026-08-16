package com.losmos.hrsnsauto.candidate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CandidateController.class)
class CandidateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CandidateService candidateService;

	@Test
	void servesCandidateListAndNewCandidateForm() throws Exception {
		when(candidateService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/candidates"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/list"))
				.andExpect(content().string(containsString("후보 목록")));
		mockMvc.perform(get("/candidates/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/form"))
				.andExpect(content().string(containsString("신규 후보 등록")))
				.andExpect(content().string(containsString("서로 다른 URL의 WEAK 2개")));
	}

	@Test
	void servesCandidateDetailWithEligibilityReasonAndEvidenceForm() throws Exception {
		Candidate candidate = new Candidate("doctor_a", "홍길동", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
		CandidateEvidence hairEvidence = new CandidateEvidence(
				candidate,
				EvidenceType.HAIR_TRANSPLANT,
				EvidenceStrength.STRONG,
				HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED,
				"https://hospital.example/services",
				"모발이식 비관련성을 확인함",
				Instant.parse("2026-08-17T00:00:00Z"));
		when(candidateService.getCandidate(7L)).thenReturn(candidate);
		when(candidateService.getEvidence(7L)).thenReturn(List.of(hairEvidence));

		mockMvc.perform(get("/candidates/7"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/detail"))
				.andExpect(content().string(containsString("REVIEW_REQUIRED")))
				.andExpect(content().string(containsString("Evidence 추가")))
				.andExpect(content().string(containsString("HAIR_TRANSPLANT evidence")))
				.andExpect(content().string(containsString("모발이식 evidence 판단")))
				.andExpect(content().string(containsString("관련 없음 근거")))
				.andExpect(content().string(containsString("SUPPORTS_NOT_RELATED")))
				.andExpect(content().string(containsString("관련 없음 근거를 자동 선택하지 않는다")));
	}

	@Test
	void hairTransplantEvidenceWithoutFindingShowsValidationError() throws Exception {
		Candidate candidate = new Candidate("doctor_a", "홍길동", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
		when(candidateService.getCandidate(7L)).thenReturn(candidate);
		when(candidateService.getEvidence(7L)).thenReturn(List.of());

		mockMvc.perform(post("/candidates/7/evidence")
				.param("type", "HAIR_TRANSPLANT")
				.param("strength", "STRONG")
				.param("sourceUrl", "https://hospital.example/services")
				.param("summary", "공개 페이지를 확인함")
				.param("observedAt", "2026-08-17T09:00"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/detail"))
				.andExpect(model().attributeHasFieldErrors("evidenceForm", "hairTransplantFinding"))
				.andExpect(content().string(containsString("HAIR_TRANSPLANT evidence는 finding을 선택한다")));

		verify(candidateService, never()).addEvidence(any(Long.class), any(EvidenceForm.class));
	}

	@Test
	void nonHairEvidenceWithHairFindingShowsValidationError() throws Exception {
		Candidate candidate = new Candidate("doctor_a", "홍길동", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
		when(candidateService.getCandidate(7L)).thenReturn(candidate);
		when(candidateService.getEvidence(7L)).thenReturn(List.of());

		mockMvc.perform(post("/candidates/7/evidence")
				.param("type", "PROFESSION")
				.param("strength", "STRONG")
				.param("hairTransplantFinding", "SUPPORTS_NOT_RELATED")
				.param("sourceUrl", "https://hospital.example/doctors/a")
				.param("summary", "공개 페이지를 확인함")
				.param("observedAt", "2026-08-17T09:00"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/detail"))
				.andExpect(model().attributeHasFieldErrors("evidenceForm", "hairTransplantFinding"))
				.andExpect(content().string(
						containsString("HAIR_TRANSPLANT 이외 evidence에는 hair finding을 지정하지 않는다")));

		verify(candidateService, never()).addEvidence(any(Long.class), any(EvidenceForm.class));
	}

	@Test
	void invalidCandidateFormShowsHumanReadableValidationErrors() throws Exception {
		mockMvc.perform(post("/candidates")
				.param("instagramUsername", "invalid username")
				.param("displayName", "")
				.param("profession", "DOCTOR")
				.param("followerCount", "-1")
				.param("hairTransplantRelation", "NOT_RELATED"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/form"))
				.andExpect(model().attributeHasFieldErrors("candidateForm",
						"instagramUsername", "displayName", "followerCount"))
				.andExpect(content().string(containsString("follower 수는 0 이상으로 입력한다")));

		verifyNoInteractions(candidateService);
	}

	@Test
	void validCandidateFormCreatesCandidateAndRedirectsToDetail() throws Exception {
		Candidate savedCandidate = mock(Candidate.class);
		when(savedCandidate.getId()).thenReturn(42L);
		when(candidateService.createCandidate(any(CandidateForm.class))).thenReturn(savedCandidate);

		mockMvc.perform(post("/candidates")
				.param("instagramUsername", "Doctor_A")
				.param("displayName", "홍길동")
				.param("profession", "DOCTOR")
				.param("specialty", "내과")
				.param("followerCount", "5000")
				.param("hairTransplantRelation", "NOT_RELATED"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/candidates/42"));
	}
}
