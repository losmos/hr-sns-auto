package com.losmos.hrsnsauto.candidate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
@RequestMapping
public class CandidateController {

	private final CandidateService candidateService;

	public CandidateController(CandidateService candidateService) {
		this.candidateService = candidateService;
	}

	@ModelAttribute("professions")
	Profession[] professions() {
		return Profession.values();
	}

	@ModelAttribute("hairRelations")
	HairTransplantRelation[] hairRelations() {
		return HairTransplantRelation.values();
	}

	@ModelAttribute("evidenceTypes")
	EvidenceType[] evidenceTypes() {
		return EvidenceType.values();
	}

	@ModelAttribute("evidenceStrengths")
	EvidenceStrength[] evidenceStrengths() {
		return EvidenceStrength.values();
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/candidates";
	}

	@GetMapping("/candidates")
	public String list(Model model) {
		model.addAttribute("candidates", candidateService.findAll());
		return "candidates/list";
	}

	@GetMapping("/candidates/new")
	public String newCandidate(Model model) {
		model.addAttribute("candidateForm", new CandidateForm());
		return "candidates/form";
	}

	@PostMapping("/candidates")
	public String create(@Valid @ModelAttribute("candidateForm") CandidateForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "candidates/form";
		}

		try {
			Candidate candidate = candidateService.createCandidate(form);
			redirectAttributes.addFlashAttribute("message", "후보를 등록하고 eligibility를 판정함");
			return "redirect:/candidates/" + candidate.getId();
		}
		catch (DuplicateInstagramUsernameException exception) {
			bindingResult.rejectValue("instagramUsername", "duplicate", exception.getMessage());
			return "candidates/form";
		}
	}

	@GetMapping("/candidates/{candidateId}")
	public String detail(@PathVariable Long candidateId, Model model) {
		populateDetail(candidateId, model);
		if (!model.containsAttribute("evidenceForm")) {
			model.addAttribute("evidenceForm", new EvidenceForm());
		}
		return "candidates/detail";
	}

	@PostMapping("/candidates/{candidateId}/evidence")
	public String addEvidence(@PathVariable Long candidateId,
			@Valid @ModelAttribute("evidenceForm") EvidenceForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			populateDetail(candidateId, model);
			return "candidates/detail";
		}

		candidateService.addEvidence(candidateId, form);
		redirectAttributes.addFlashAttribute("message", "evidence를 추가하고 eligibility를 다시 판정함");
		return "redirect:/candidates/" + candidateId;
	}

	@PostMapping("/candidates/{candidateId}/reassess")
	public String reassess(@PathVariable Long candidateId, RedirectAttributes redirectAttributes) {
		candidateService.reassess(candidateId);
		redirectAttributes.addFlashAttribute("message", "현재 evidence로 eligibility를 다시 판정함");
		return "redirect:/candidates/" + candidateId;
	}

	private void populateDetail(Long candidateId, Model model) {
		model.addAttribute("candidate", candidateService.getCandidate(candidateId));
		model.addAttribute("evidenceList", candidateService.getEvidence(candidateId));
	}
}
