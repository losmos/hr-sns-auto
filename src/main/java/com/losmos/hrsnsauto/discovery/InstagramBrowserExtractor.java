package com.losmos.hrsnsauto.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

@Component
public class InstagramBrowserExtractor {

	private static final int MAX_AUTHOR_LINKS = 30;
	private static final int MAX_VISIBLE_TEXT_LENGTH = 12_000;
	private static final Pattern USERNAME_PATTERN = Pattern.compile(
			"^[A-Za-z0-9_](?:[A-Za-z0-9._]{0,28}[A-Za-z0-9_])?$");
	private static final Set<String> NON_PROFILE_PATHS = Set.of(
			"p", "reel", "reels", "tv", "explore", "accounts", "direct", "stories",
			"about", "developer", "legal", "web", "api");
	private static final Set<String> POST_PATHS = Set.of("p", "reel", "tv");
	// author는 caption이나 전역 navigation이 아니라 post의 semantic header/heading 안 link에서만 찾는다.
	// Instagram이 generated CSS class를 바꿔도 이 fallback 목록 한 곳만 수정하도록 격리한다.
	private static final String AUTHOR_LINK_SELECTOR = String.join(", ",
			"main article header a[href]",
			"[role='dialog'] article header a[href]",
			"main article h1 a[href]",
			"main article h2 a[href]");
	private static final List<String> PROFILE_METRIC_LABELS = List.of(
			"posts", "post", "followers", "follower", "following", "게시물", "팔로워", "팔로우");
	private static final List<String> PROFILE_CONTROL_LABELS = List.of(
			"follow", "following", "message", "contact", "edit profile", "see translation",
			"팔로우", "메시지", "연락처", "프로필 편집", "번역 보기", "인증됨", "verified");

	private final InstagramMetricParser metricParser;

	public InstagramBrowserExtractor(InstagramMetricParser metricParser) {
		this.metricParser = metricParser;
	}

	Optional<InstagramPostBrowserSnapshot> extractPost(Page page) {
		Optional<InstagramProfileLink> author = findAuthorCandidate(visibleLinks(page));
		if (author.isEmpty()) {
			return Optional.empty();
		}
		String articleText = firstVisibleText(page.locator("main article, [role='dialog'] article"));
		return Optional.of(new InstagramPostBrowserSnapshot(
				author.get().username(),
				author.get().profileUrl(),
				metricFromText(articleText, List.of("likes", "like", "좋아요")),
				metricFromText(articleText, List.of("comments", "comment", "댓글")),
				metricFromText(articleText, List.of("views", "view", "plays", "play", "조회", "재생"))));
	}

	Optional<InstagramProfileBrowserSnapshot> extractProfile(Page page, String expectedUsername) {
		String headerText = firstVisibleText(page.locator("main header"));
		if (headerText == null || !containsExactLine(headerText, expectedUsername)) {
			return Optional.empty();
		}

		Long followerCount = metricFromLocator(
				page.locator("main header a[href$='/followers/']"),
				List.of("followers", "follower", "팔로워"));
		Long followingCount = metricFromLocator(
				page.locator("main header a[href$='/following/']"),
				List.of("following", "팔로우"));
		Long postCount = metricFromText(headerText, List.of("posts", "post", "게시물"));

		List<String> lines = meaningfulLines(headerText);
		String displayName = displayName(page, expectedUsername, lines);
		String biographyExcerpt = biographyExcerpt(lines, expectedUsername, displayName);
		Boolean verified = hasVisible(page.locator(String.join(", ",
				"main header [aria-label*='Verified']",
				"main header [aria-label*='인증']",
				"main header [title*='Verified']",
				"main header [title*='인증']")));
		Boolean privateAccount = containsAny(
				boundedText(page.locator("main")),
				"this account is private", "account is private", "비공개 계정입니다", "비공개 계정");

		return Optional.of(new InstagramProfileBrowserSnapshot(
				displayName,
				followerCount,
				followingCount,
				postCount,
				biographyExcerpt,
				verified,
				privateAccount));
	}

	BrowserPageState pageState(Page page) {
		String url = safeLower(page.url());
		if (url.contains("/challenge/") || url.contains("/checkpoint/") || url.contains("/accounts/suspended/")) {
			return BrowserPageState.ACTION_REQUIRED;
		}
		if (url.contains("/accounts/login/") || hasVisible(page.locator("input[name='password']"))) {
			return BrowserPageState.LOGIN_REQUIRED;
		}
		String mainText = safeLower(boundedText(page.locator("main")));
		if (containsAny(mainText,
				"captcha", "challenge required", "confirm it's you", "suspicious login attempt",
				"보안 코드", "본인임을 확인", "의심스러운 로그인 시도")) {
			return BrowserPageState.ACTION_REQUIRED;
		}
		return BrowserPageState.READY;
	}

	boolean isUnavailable(Page page) {
		return containsAny(
				safeLower(boundedText(page.locator("main"))),
				"sorry, this page isn't available", "page isn't available", "content isn't available",
				"페이지를 사용할 수 없습니다", "콘텐츠를 이용할 수 없습니다");
	}

	boolean isExpectedProfileUrl(String value, String expectedUsername) {
		return profileUsernameFromUrl(value)
				.map(username -> username.equalsIgnoreCase(expectedUsername))
				.orElse(false);
	}

	boolean isInstagramPostUrl(String value) {
		try {
			URI uri = URI.create(value);
			if (!isInstagramHttpsUri(uri)) {
				return false;
			}
			String[] segments = pathSegments(uri);
			return segments.length == 2
					&& POST_PATHS.contains(segments[0].toLowerCase(Locale.ROOT))
					&& !segments[1].isBlank();
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
	}

	Optional<InstagramProfileLink> findAuthorCandidate(List<VisibleLink> links) {
		for (VisibleLink link : links) {
			Optional<InstagramProfileLink> candidate = authorCandidate(link.href(), link.visibleLabel());
			if (candidate.isPresent()) {
				return candidate;
			}
		}
		return Optional.empty();
	}

	Optional<InstagramProfileLink> authorCandidate(String href, String visibleLabel) {
		if (href == null || href.isBlank() || visibleLabel == null || visibleLabel.isBlank()) {
			return Optional.empty();
		}
		Optional<String> username = profileUsernameFromUrl(href);
		if (username.isEmpty() || !containsExactLine(visibleLabel, username.get())) {
			return Optional.empty();
		}
		return Optional.of(new InstagramProfileLink(
				username.get(),
				"https://www.instagram.com/" + username.get() + "/"));
	}

	Optional<String> profileUsernameFromUrl(String value) {
		try {
			URI uri = URI.create(value.strip());
			if (uri.isAbsolute() && !isInstagramHttpsUri(uri)) {
				return Optional.empty();
			}
			if (!uri.isAbsolute() && !value.strip().startsWith("/")) {
				return Optional.empty();
			}
			if (uri.getRawPath() != null && uri.getRawPath().contains("%")) {
				return Optional.empty();
			}
			String[] segments = pathSegments(uri);
			if (segments.length != 1) {
				return Optional.empty();
			}
			String username = segments[0].toLowerCase(Locale.ROOT);
			if (!USERNAME_PATTERN.matcher(username).matches()
					|| username.contains("..")
					|| NON_PROFILE_PATHS.contains(username)) {
				return Optional.empty();
			}
			return Optional.of(username);
		}
		catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private List<VisibleLink> visibleLinks(Page page) {
		List<VisibleLink> links = new ArrayList<>();
		Locator locator = page.locator(AUTHOR_LINK_SELECTOR);
		int count = Math.min(safeCount(locator), MAX_AUTHOR_LINKS);
		for (int index = 0; index < count; index++) {
			Locator anchor = locator.nth(index);
			try {
				if (!anchor.isVisible()) {
					continue;
				}
				String label = trimmed(anchor.innerText());
				if (label == null) {
					label = trimmed(anchor.getAttribute("aria-label"));
				}
				links.add(new VisibleLink(anchor.getAttribute("href"), label));
			}
			catch (PlaywrightException ignored) {
				// 한 locator가 DOM 재렌더링으로 사라져도 같은 author 영역의 다음 후보를 확인한다.
			}
		}
		return links;
	}

	private Long metricFromLocator(Locator locator, List<String> labels) {
		int count = Math.min(safeCount(locator), 5);
		for (int index = 0; index < count; index++) {
			Locator item = locator.nth(index);
			try {
				if (!item.isVisible()) {
					continue;
				}
				for (String value : List.of(
						valueOrEmpty(item.getAttribute("title")),
						valueOrEmpty(item.getAttribute("aria-label")),
						valueOrEmpty(item.innerText()))) {
					Long metric = metricFromText(value, labels);
					if (metric != null) {
						return metric;
					}
				}
			}
			catch (PlaywrightException ignored) {
				// optional metric 하나의 locator 실패는 전체 profile 관찰 실패로 확대하지 않는다.
			}
		}
		return null;
	}

	private Long metricFromText(String text, List<String> labels) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Optional<Long> direct = metricParser.parse(text);
		if (direct.isPresent()) {
			return direct.get();
		}
		String token = "([0-9][0-9.,]*\\s*[KkMm천만]?)";
		for (String label : labels) {
			String quotedLabel = Pattern.quote(label);
			for (Pattern pattern : List.of(
					Pattern.compile(token + "\\s*" + quotedLabel, Pattern.CASE_INSENSITIVE),
					Pattern.compile(quotedLabel + "\\s*" + token, Pattern.CASE_INSENSITIVE))) {
				Matcher matcher = pattern.matcher(text);
				if (matcher.find()) {
					Optional<Long> parsed = metricParser.parse(matcher.group(1));
					if (parsed.isPresent()) {
						return parsed.get();
					}
				}
			}
		}
		return null;
	}

	private String displayName(Page page, String username, List<String> headerLines) {
		for (String heading : visibleTexts(page.locator("main header h1, main header h2"), 10)) {
			if (!heading.equalsIgnoreCase(username) && isProfileText(heading)) {
				return excerpt(heading, 255);
			}
		}

		boolean metricsSeen = false;
		for (String line : headerLines) {
			if (containsLabel(line, PROFILE_METRIC_LABELS)) {
				metricsSeen = true;
				continue;
			}
			if (metricsSeen && !line.equalsIgnoreCase(username) && isProfileText(line)) {
				return excerpt(line, 255);
			}
		}
		return null;
	}

	private String biographyExcerpt(List<String> headerLines, String username, String displayName) {
		List<String> biographyLines = new ArrayList<>();
		boolean displayNameSeen = displayName == null;
		for (String line : headerLines) {
			if (line.equalsIgnoreCase(username)
					|| containsLabel(line, PROFILE_METRIC_LABELS)
					|| containsLabel(line, PROFILE_CONTROL_LABELS)) {
				continue;
			}
			if (!displayNameSeen && line.equals(displayName)) {
				displayNameSeen = true;
				continue;
			}
			if (displayNameSeen && !line.equals(displayName)) {
				biographyLines.add(line);
			}
		}
		return excerpt(String.join(" · ", biographyLines), DiscoveryBrowserObservation.BIOGRAPHY_EXCERPT_MAX_LENGTH);
	}

	private boolean isProfileText(String value) {
		return value != null
				&& !value.isBlank()
				&& !containsLabel(value, PROFILE_METRIC_LABELS)
				&& !containsLabel(value, PROFILE_CONTROL_LABELS)
				&& value.codePointCount(0, value.length()) <= 255;
	}

	private List<String> meaningfulLines(String text) {
		Set<String> unique = new LinkedHashSet<>();
		for (String line : text.split("[\\r\\n]+")) {
			String trimmed = trimmed(line);
			if (trimmed != null) {
				unique.add(trimmed);
			}
		}
		return List.copyOf(unique);
	}

	private List<String> visibleTexts(Locator locator, int maxItems) {
		List<String> values = new ArrayList<>();
		int count = Math.min(safeCount(locator), maxItems);
		for (int index = 0; index < count; index++) {
			try {
				Locator item = locator.nth(index);
				if (item.isVisible()) {
					String value = trimmed(item.innerText());
					if (value != null) {
						values.add(value);
					}
				}
			}
			catch (PlaywrightException ignored) {
				// optional heading의 재렌더링은 다음 fallback으로 처리한다.
			}
		}
		return values;
	}

	private String firstVisibleText(Locator locator) {
		for (String value : visibleTexts(locator, 5)) {
			return value;
		}
		return null;
	}

	private String boundedText(Locator locator) {
		String text = firstVisibleText(locator);
		if (text == null || text.length() <= MAX_VISIBLE_TEXT_LENGTH) {
			return text;
		}
		return text.substring(0, MAX_VISIBLE_TEXT_LENGTH);
	}

	private boolean hasVisible(Locator locator) {
		int count = Math.min(safeCount(locator), 10);
		for (int index = 0; index < count; index++) {
			try {
				if (locator.nth(index).isVisible()) {
					return true;
				}
			}
			catch (PlaywrightException ignored) {
				// 다른 fallback locator를 계속 확인한다.
			}
		}
		return false;
	}

	private int safeCount(Locator locator) {
		try {
			return locator.count();
		}
		catch (PlaywrightException exception) {
			return 0;
		}
	}

	private boolean containsExactLine(String value, String expected) {
		if (value == null || expected == null) {
			return false;
		}
		for (String line : value.split("[\\r\\n]+")) {
			String normalized = line.strip();
			if (normalized.startsWith("@")) {
				normalized = normalized.substring(1);
			}
			if (normalized.equalsIgnoreCase(expected)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsLabel(String value, List<String> labels) {
		String normalized = safeLower(value);
		return labels.stream().anyMatch(normalized::contains);
	}

	private boolean containsAny(String value, String... candidates) {
		if (value == null) {
			return false;
		}
		String normalized = safeLower(value);
		for (String candidate : candidates) {
			if (normalized.contains(candidate.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private boolean isInstagramHttpsUri(URI uri) {
		String host = uri.getHost();
		return "https".equalsIgnoreCase(uri.getScheme())
				&& host != null
				&& (host.equalsIgnoreCase("instagram.com")
						|| host.toLowerCase(Locale.ROOT).endsWith(".instagram.com"));
	}

	private String[] pathSegments(URI uri) {
		return Arrays.stream(uri.getPath().split("/"))
				.filter(segment -> !segment.isBlank())
				.toArray(String[]::new);
	}

	private String excerpt(String value, int maxCodePoints) {
		String trimmed = trimmed(value);
		if (trimmed == null) {
			return null;
		}
		int count = trimmed.codePointCount(0, trimmed.length());
		return count <= maxCodePoints
				? trimmed
				: trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints));
	}

	private String trimmed(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}

	private String safeLower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	record VisibleLink(String href, String visibleLabel) {
	}

	record InstagramProfileLink(String username, String profileUrl) {
	}

	enum BrowserPageState {
		READY,
		LOGIN_REQUIRED,
		ACTION_REQUIRED
	}
}
