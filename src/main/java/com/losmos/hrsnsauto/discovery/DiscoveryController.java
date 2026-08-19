package com.losmos.hrsnsauto.discovery;

import java.net.URI;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/discovery")
public class DiscoveryController {

	private final DiscoveryService discoveryService;

	public DiscoveryController(DiscoveryService discoveryService) {
		this.discoveryService = discoveryService;
	}

	@ModelAttribute("reviewStatuses")
	DiscoveryReviewStatus[] reviewStatuses() {
		return DiscoveryReviewStatus.values();
	}

	@GetMapping
	public String index(@RequestParam(required = false) DiscoveryReviewStatus status, Model model) {
		populateIndex(status, model);
		if (!model.containsAttribute("hashtagForm")) {
			model.addAttribute("hashtagForm", new DiscoveryHashtagForm());
		}
		return "discovery/index";
	}

	@PostMapping("/hashtags")
	public String addHashtag(@Valid @ModelAttribute("hashtagForm") DiscoveryHashtagForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		if (!bindingResult.hasErrors()) {
			try {
				discoveryService.addHashtag(form.getKeyword());
			}
			catch (DuplicateDiscoveryHashtagException | InvalidDiscoveryHashtagException exception) {
				bindingResult.rejectValue("keyword", "invalid", exception.getMessage());
			}
		}
		if (bindingResult.hasErrors()) {
			populateIndex(null, model);
			return "discovery/index";
		}

		redirectAttributes.addFlashAttribute("message", "hashtag를 추가함");
		return "redirect:/discovery";
	}

	@PostMapping("/hashtags/{hashtagId}/enable")
	public String enableHashtag(@PathVariable Long hashtagId, RedirectAttributes redirectAttributes) {
		discoveryService.setHashtagEnabled(hashtagId, true);
		redirectAttributes.addFlashAttribute("message", "hashtag를 활성화함");
		return "redirect:/discovery";
	}

	@PostMapping("/hashtags/{hashtagId}/disable")
	public String disableHashtag(@PathVariable Long hashtagId, RedirectAttributes redirectAttributes) {
		discoveryService.setHashtagEnabled(hashtagId, false);
		redirectAttributes.addFlashAttribute("message", "hashtag를 비활성화함");
		return "redirect:/discovery";
	}

	@PostMapping("/sync")
	public String sync(RedirectAttributes redirectAttributes) {
		try {
			DiscoverySyncResult result = discoveryService.syncRecentMedia();
			redirectAttributes.addFlashAttribute("syncResult", result);
			redirectAttributes.addFlashAttribute("message", "최근 게시물 가져오기를 완료함");
		}
		catch (MetaInstagramConfigurationException exception) {
			redirectAttributes.addFlashAttribute("error", exception.getMessage());
		}
		return "redirect:/discovery";
	}

	@GetMapping("/items/{itemId}/open")
	public RedirectView open(@PathVariable Long itemId) {
		URI permalink = discoveryService.markOpened(itemId);
		RedirectView redirectView = new RedirectView(permalink.toString());
		redirectView.setExposeModelAttributes(false);
		return redirectView;
	}

	@PostMapping("/items/{itemId}/dismiss")
	public String dismiss(@PathVariable Long itemId, RedirectAttributes redirectAttributes) {
		discoveryService.dismiss(itemId);
		redirectAttributes.addFlashAttribute("message", "게시물을 관심 없음으로 표시함");
		return "redirect:/discovery";
	}

	private void populateIndex(DiscoveryReviewStatus status, Model model) {
		model.addAttribute("hashtags", discoveryService.findAllHashtags());
		model.addAttribute("items", discoveryService.findItems(status));
		model.addAttribute("counts", discoveryService.getInboxCounts());
		model.addAttribute("selectedStatus", status == null ? "ALL" : status.name());
	}
}
