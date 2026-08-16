package com.losmos.hrsnsauto.candidate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
				.andExpect(content().string(containsString("신규 후보 등록")));
	}

	@Test
	void servesCandidateDetailWithEligibilityReasonAndEvidenceForm() throws Exception {
		Candidate candidate = new Candidate("doctor_a", "홍길동", Profession.DOCTOR, "내과", 5_000,
				HairTransplantRelation.NOT_RELATED);
		when(candidateService.getCandidate(7L)).thenReturn(candidate);
		when(candidateService.getEvidence(7L)).thenReturn(List.of());

		mockMvc.perform(get("/candidates/7"))
				.andExpect(status().isOk())
				.andExpect(view().name("candidates/detail"))
				.andExpect(content().string(containsString("REVIEW_REQUIRED")))
				.andExpect(content().string(containsString("Evidence 추가")));
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
