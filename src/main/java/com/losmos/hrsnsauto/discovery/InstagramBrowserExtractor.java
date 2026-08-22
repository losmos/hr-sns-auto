package com.losmos.hrsnsauto.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

@Component
public class InstagramBrowserExtractor {

	private static final int MAX_POST_ROOT_LINKS = 30;
	private static final int MAX_EARLY_AUTHOR_LINKS = 12;
	private static final int MAX_DIAGNOSTIC_USERNAMES = 3;
	private static final int MAX_DIAGNOSTIC_PATH_LENGTH = 160;
	private static final int MAX_VISIBLE_TEXT_LENGTH = 12_000;
	private static final int MAX_METADATA_TEXT_CODE_POINTS = 2_000;
	private static final int POST_EXTRACTION_ATTEMPTS = 3;
	private static final double SEMANTIC_ARTICLE_WAIT_MILLIS = 1_500;
	private static final double MAIN_FALLBACK_WAIT_MILLIS = 1_200;
	private static final double AUTHOR_LINK_WAIT_MILLIS = 1_200;
	private static final double AUTHOR_RETRY_DELAY_MILLIS = 400;
	private static final Pattern USERNAME_PATTERN = Pattern.compile(
			"^[A-Za-z0-9_](?:[A-Za-z0-9._]{0,28}[A-Za-z0-9_])?$");
	private static final Pattern SUPPORTED_POST_PATH_PATTERN = Pattern.compile(
			"^/(p|reel|reels|tv)/([A-Za-z0-9_-]+)/?$", Pattern.CASE_INSENSITIVE);
	private static final Set<String> NON_PROFILE_PATHS = Set.of(
			"p", "reel", "reels", "tv", "explore", "accounts", "direct", "stories",
			"about", "developer", "legal", "web", "api");
	// generated CSS class에 의존하지 않고 semantic article을 먼저 짧게 기다린다.
	private static final String SEMANTIC_ARTICLE_SELECTOR = String.join(", ",
			"[role='dialog'] article:visible",
			"main article:visible");
	private static final String DIALOG_ARTICLE_SELECTOR = "[role='dialog'] article:visible";
	private static final String MAIN_ARTICLE_SELECTOR = "main article:visible";
	private static final String MAIN_ELEMENT_SELECTOR = "main:visible";
	private static final String ROLE_MAIN_SELECTOR = "[role='main']:visible";
	private static final String VISIBLE_MAIN_SELECTOR = String.join(", ",
			MAIN_ELEMENT_SELECTOR,
			ROLE_MAIN_SELECTOR);
	private static final String SEMANTIC_AUTHOR_LINK_SELECTOR = String.join(", ",
			"header a[href]",
			"h1 a[href]",
			"h2 a[href]");
	private static final List<String> PROFILE_POST_LABELS = List.of("posts", "post", "게시물");
	private static final List<String> PROFILE_FOLLOWER_LABELS = List.of(
			"followers", "follower", "팔로워");
	private static final List<String> PROFILE_FOLLOWING_LABELS = List.of(
			"following", "팔로잉", "팔로우");
	private static final List<List<String>> PROFILE_METRIC_LABEL_GROUPS = List.of(
			PROFILE_POST_LABELS, PROFILE_FOLLOWER_LABELS, PROFILE_FOLLOWING_LABELS);
	private static final Set<String> PROFILE_CONTROL_LINES = Set.of(
			"follow", "following", "follow back", "message", "contact", "edit profile",
			"see translation", "verified", "팔로우", "팔로잉", "맞팔로우", "메시지",
			"메시지 보내기", "연락처", "프로필 편집", "번역 보기", "인증됨");
	private static final Set<String> PROFILE_HIGHLIGHT_BOUNDARY_LINES = Set.of(
			"highlights", "story highlights", "하이라이트", "스토리 하이라이트");
	private static final Pattern EXTERNAL_URL_PATTERN = Pattern.compile(
			"(?i)^(?:(?:https?://|www\\.)\\S+|[\\p{L}0-9](?:[\\p{L}0-9.-]*\\.)"
					+ "[A-Za-z]{2,}(?:/\\S*)?)$");
	private static final String METRIC_NUMBER_TOKEN =
			"([0-9]+(?:[.,][0-9]+)*(?:[\\s\\p{Zs}]*[KkMm천만])?)";
	private static final String METRIC_SEPARATOR = "[\\s\\p{Zs}]*[:：]?[\\s\\p{Zs}]*";

	private final InstagramMetricParser metricParser;

	public InstagramBrowserExtractor(InstagramMetricParser metricParser) {
		this.metricParser = metricParser;
	}

	Optional<InstagramPostBrowserSnapshot> extractPost(Page page) {
		return extractPostWithDiagnostic(page).snapshot();
	}

	PostExtractionResult extractPostWithDiagnostic(Page page) {
		PageLocation initialLocation = pageLocation(page.url());
		if (!initialLocation.isPost()) {
			return PostExtractionResult.notFound(diagnostic(
					page, initialLocation, PostRootType.NONE, List.of()));
		}

		waitForPostRoot(page);
		PostRoot initialRoot = selectPostRoot(page, pageLocation(page.url()));
		if (initialRoot.isPresent()) {
			waitForInitialRootLink(initialRoot.locator());
		}

		PostExtractionDiagnostic latestDiagnostic = diagnostic(
				page, initialLocation, PostRootType.NONE, List.of());
		for (int attempt = 0; attempt < POST_EXTRACTION_ATTEMPTS; attempt++) {
			PageLocation currentLocation = pageLocation(page.url());
			PostRoot postRoot = selectPostRoot(page, currentLocation);
			if (!postRoot.isPresent()) {
				latestDiagnostic = diagnostic(
						page, currentLocation, PostRootType.NONE, List.of());
			}
			else {
				List<VisibleLink> rootLinks = visibleLinks(
						postRoot.locator().locator("a[href]"), MAX_POST_ROOT_LINKS);
				latestDiagnostic = diagnostic(page, currentLocation, postRoot.type(), rootLinks);
				Optional<InstagramProfileLink> author;
				if (postRoot.type() == PostRootType.ARTICLE) {
					List<VisibleLink> semanticLinks = visibleLinks(
							postRoot.locator().locator(SEMANTIC_AUTHOR_LINK_SELECTOR),
							MAX_EARLY_AUTHOR_LINKS);
					author = findAuthorCandidate(semanticLinks, rootLinks);
				}
				else {
					author = findMainFallbackAuthorCandidate(rootLinks);
				}
				if (author.isPresent()) {
					String postText = boundedText(postRoot.locator());
					return PostExtractionResult.found(
							new InstagramPostBrowserSnapshot(
									author.get().username(),
									author.get().profileUrl(),
									metricFromText(postText, List.of("likes", "like", "좋아요")),
									metricFromText(postText, List.of("comments", "comment", "댓글")),
									metricFromText(postText,
											List.of("views", "view", "plays", "play", "조회", "재생"))),
							latestDiagnostic);
				}
			}
			if (attempt < POST_EXTRACTION_ATTEMPTS - 1) {
				// SPA 재렌더링을 위한 짧고 고정된 retry이며 random/stealth timing으로 사용하지 않는다.
				page.waitForTimeout(AUTHOR_RETRY_DELAY_MILLIS);
			}
		}
		return PostExtractionResult.notFound(latestDiagnostic);
	}

	Optional<InstagramProfileBrowserSnapshot> extractProfile(Page page, String expectedUsername) {
		String headerText = firstVisibleText(page.locator("main header"));
		if (headerText == null || !containsExactLine(headerText, expectedUsername)) {
			return Optional.empty();
		}

		List<String> lines = meaningfulLines(headerText);
		Long followerCount = metricFromLines(lines, PROFILE_FOLLOWER_LABELS);
		Long followingCount = metricFromLines(lines, PROFILE_FOLLOWING_LABELS);
		Long postCount = metricFromLines(lines, PROFILE_POST_LABELS);

		// href suffix는 지원되는 기존 UI의 secondary source로만 유지한다.
		if (followerCount == null) {
			followerCount = metricFromLocator(
					page.locator("main header a[href$='/followers/']"),
					PROFILE_FOLLOWER_LABELS);
		}
		if (followingCount == null) {
			followingCount = metricFromLocator(
					page.locator("main header a[href$='/following/']"),
					PROFILE_FOLLOWING_LABELS);
		}

		// href="#"인 현재 UI와 다른 semantic anchor 형태도 label-number pair로만 읽는다.
		if (followerCount == null) {
			followerCount = labeledMetricFromLocator(
					page.locator("main header a[href]"), PROFILE_FOLLOWER_LABELS);
		}
		if (followingCount == null) {
			followingCount = labeledMetricFromLocator(
					page.locator("main header a[href]"), PROFILE_FOLLOWING_LABELS);
		}
		if (postCount == null) {
			postCount = labeledMetricFromLocator(
					page.locator("main header a[href]"), PROFILE_POST_LABELS);
		}

		// Metadata는 visible/semantic source에서 누락된 field만 보완하며 기존 값을 덮어쓰지 않는다.
		if (followerCount == null || followingCount == null || postCount == null) {
			List<String> descriptions = profileDescriptionMetadata(page);
			if (followerCount == null) {
				followerCount = metricFromTexts(descriptions, PROFILE_FOLLOWER_LABELS);
			}
			if (followingCount == null) {
				followingCount = metricFromTexts(descriptions, PROFILE_FOLLOWING_LABELS);
			}
			if (postCount == null) {
				postCount = metricFromTexts(descriptions, PROFILE_POST_LABELS);
			}
		}

		String displayName = displayName(expectedUsername, lines);
		if (displayName == null) {
			displayName = displayNameFromMetadata(page, expectedUsername);
		}
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
		PageClassification classification = pageLocation(page.url()).classification();
		if (classification == PageClassification.ACTION_REQUIRED) {
			return BrowserPageState.ACTION_REQUIRED;
		}
		if (classification == PageClassification.LOGIN
				|| hasVisible(page.locator("input[name='password']"))) {
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
		return pageLocation(value).isPost();
	}

	boolean isExpectedPostUrl(String requestedUrl, String finalUrl) {
		Optional<PostIdentity> requestedPost = postIdentity(requestedUrl);
		Optional<PostIdentity> finalPost = postIdentity(finalUrl);
		return requestedPost.isPresent()
				&& finalPost.isPresent()
				&& requestedPost.get().equals(finalPost.get());
	}

	PageLocation pageLocation(String value) {
		try {
			if (value == null || value.isBlank()) {
				return PageLocation.external();
			}
			URI uri = URI.create(value.strip());
			if (!isInstagramHttpsUri(uri)) {
				return PageLocation.external();
			}

			String finalPath = safeFinalPath(uri);
			String[] segments = pathSegments(uri);
			if (segments.length == 0) {
				return new PageLocation(PageClassification.HOME, finalPath);
			}

			String firstSegment = segments[0].toLowerCase(Locale.ROOT);
			if (firstSegment.equals("challenge")
					|| firstSegment.equals("checkpoint")
					|| (firstSegment.equals("accounts")
							&& segments.length > 1
							&& segments[1].equalsIgnoreCase("suspended"))) {
				return new PageLocation(PageClassification.ACTION_REQUIRED, finalPath);
			}
			if (firstSegment.equals("accounts")
					&& segments.length > 1
					&& segments[1].equalsIgnoreCase("login")) {
				return new PageLocation(PageClassification.LOGIN, finalPath);
			}
			if (isSupportedPostPath(uri)) {
				return new PageLocation(PageClassification.POST, finalPath);
			}
			return new PageLocation(PageClassification.OTHER_INSTAGRAM, finalPath);
		}
		catch (IllegalArgumentException exception) {
			return PageLocation.external();
		}
	}

	Optional<InstagramProfileLink> findAuthorCandidate(List<VisibleLink> links) {
		return findAuthorCandidate(List.of(), links);
	}

	Optional<InstagramProfileLink> findAuthorCandidate(
			List<VisibleLink> semanticLinks, List<VisibleLink> articleLinks) {
		Optional<InstagramProfileLink> semanticCandidate = findTrustedCandidate(semanticLinks, true);
		if (semanticCandidate.isPresent()) {
			return semanticCandidate;
		}

		// Caption mention과 commenter가 주로 뒤에 오는 DOM 특성을 이용하되 상단 검사 범위는 작게 제한한다.
		int endIndex = Math.min(articleLinks.size(), MAX_EARLY_AUTHOR_LINKS);
		return findTrustedCandidate(articleLinks.subList(0, endIndex), true);
	}

	private Optional<InstagramProfileLink> findMainFallbackAuthorCandidate(List<VisibleLink> rootLinks) {
		// main 전체에서는 navigation/comment 영역이 섞일 수 있으므로 초반 link만 검사한다.
		// @mention 단일 text는 author evidence로 인정하지 않고 반복 href나 명시 label을 우선한다.
		int endIndex = Math.min(rootLinks.size(), MAX_EARLY_AUTHOR_LINKS);
		return findTrustedCandidate(rootLinks.subList(0, endIndex), false);
	}

	Optional<InstagramProfileLink> authorCandidate(String href, String visibleLabel) {
		return trustedLabeledCandidate(new VisibleLink(href, visibleLabel, null, null));
	}

	Optional<String> profileUsernameFromUrl(String value) {
		try {
			if (value == null || value.isBlank()) {
				return Optional.empty();
			}
			String stripped = value.strip();
			URI uri = URI.create(stripped);
			if (uri.isAbsolute() && !isInstagramHttpsUri(uri)) {
				return Optional.empty();
			}
			if (!uri.isAbsolute()
					&& (uri.getRawAuthority() != null || !stripped.startsWith("/") || stripped.startsWith("//"))) {
				return Optional.empty();
			}
			if (uri.getRawPath() != null && uri.getRawPath().contains("%")) {
				return Optional.empty();
			}
			String[] segments = pathSegments(uri);
			boolean canonicalProfile = segments.length == 1;
			boolean profileReelsTab = segments.length == 2 && segments[1].equals("reels");
			if (!canonicalProfile && !profileReelsTab) {
				return Optional.empty();
			}
			String username = segments[0].toLowerCase(Locale.ROOT);
			// `reels`는 username 예약어로 유지하고 정확한 profile tab suffix로만 허용한다.
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

	private void waitForPostRoot(Page page) {
		try {
			page.locator(SEMANTIC_ARTICLE_SELECTOR).first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(SEMANTIC_ARTICLE_WAIT_MILLIS));
			return;
		}
		catch (TimeoutError exception) {
			// article 없는 상세 화면을 위해 지원 post URL에서만 main을 짧게 기다린다.
		}

		if (!pageLocation(page.url()).isPost()) {
			return;
		}
		try {
			page.locator(VISIBLE_MAIN_SELECTOR).first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(MAIN_FALLBACK_WAIT_MILLIS));
		}
		catch (TimeoutError ignored) {
			// Root가 아직 없더라도 아래 bounded retry가 최종 diagnostic과 안전한 실패를 만든다.
		}
	}

	private void waitForInitialRootLink(Locator postRoot) {
		try {
			postRoot.locator("a[href]:visible").first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(AUTHOR_LINK_WAIT_MILLIS));
		}
		catch (TimeoutError ignored) {
			// Link가 아직 없더라도 아래 bounded retry가 최종 diagnostic과 안전한 실패를 만든다.
		}
	}

	private PostRoot selectPostRoot(Page page, PageLocation location) {
		if (!location.isPost()) {
			return PostRoot.none();
		}

		Locator dialogArticle = firstExisting(page.locator(DIALOG_ARTICLE_SELECTOR));
		if (dialogArticle != null) {
			return new PostRoot(PostRootType.ARTICLE, dialogArticle);
		}
		Locator mainArticle = firstExisting(page.locator(MAIN_ARTICLE_SELECTOR));
		if (mainArticle != null) {
			return new PostRoot(PostRootType.ARTICLE, mainArticle);
		}
		Locator main = firstExisting(page.locator(MAIN_ELEMENT_SELECTOR));
		if (main != null) {
			return new PostRoot(PostRootType.MAIN_FALLBACK, main);
		}
		Locator roleMain = firstExisting(page.locator(ROLE_MAIN_SELECTOR));
		if (roleMain != null) {
			return new PostRoot(PostRootType.MAIN_FALLBACK, roleMain);
		}
		return PostRoot.none();
	}

	private Locator firstExisting(Locator locator) {
		if (safeCount(locator) == 0) {
			return null;
		}
		return locator.first();
	}

	private Optional<InstagramProfileLink> findTrustedCandidate(
			List<VisibleLink> links, boolean allowAtPrefixedVisibleText) {
		Map<String, List<VisibleLink>> linksByUsername = new LinkedHashMap<>();
		for (VisibleLink link : links) {
			profileUsernameFromUrl(link.href()).ifPresent(username ->
					linksByUsername.computeIfAbsent(username, ignored -> new ArrayList<>()).add(link));
		}

		// 동일 href 반복은 avatar와 username link 조합을 포착하며 단일 선행 navigation link보다 강하다.
		for (Map.Entry<String, List<VisibleLink>> entry : linksByUsername.entrySet()) {
			if (entry.getValue().size() >= 2
					&& (allowAtPrefixedVisibleText
							|| entry.getValue().stream().noneMatch(link ->
									containsAtPrefixedUsername(link.innerText(), entry.getKey())))) {
				return Optional.of(profileLink(entry.getKey()));
			}
		}
		for (Map.Entry<String, List<VisibleLink>> entry : linksByUsername.entrySet()) {
			for (VisibleLink link : entry.getValue()) {
				if (containsExactUsernameLabel(
						link.innerText(), entry.getKey(), allowAtPrefixedVisibleText)) {
					return Optional.of(profileLink(entry.getKey()));
				}
			}
		}
		for (Map.Entry<String, List<VisibleLink>> entry : linksByUsername.entrySet()) {
			for (VisibleLink link : entry.getValue()) {
				if (explicitlyNamesUsername(link.ariaLabel(), entry.getKey())
						|| explicitlyNamesUsername(link.title(), entry.getKey())) {
					return Optional.of(profileLink(entry.getKey()));
				}
			}
		}
		return Optional.empty();
	}

	private boolean containsExactUsernameLabel(
			String value, String username, boolean allowAtPrefix) {
		if (value == null || username == null) {
			return false;
		}
		for (String line : value.split("[\\r\\n]+")) {
			String normalized = line.strip();
			if (allowAtPrefix && normalized.startsWith("@")) {
				normalized = normalized.substring(1);
			}
			if (normalized.equalsIgnoreCase(username)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsAtPrefixedUsername(String value, String username) {
		if (value == null || username == null) {
			return false;
		}
		return Arrays.stream(value.split("[\\r\\n]+"))
				.map(String::strip)
				.anyMatch(line -> line.equalsIgnoreCase("@" + username));
	}

	private Optional<InstagramProfileLink> trustedLabeledCandidate(VisibleLink link) {
		Optional<String> username = profileUsernameFromUrl(link.href());
		if (username.isEmpty() || !containsExactLine(link.innerText(), username.get())) {
			return Optional.empty();
		}
		return Optional.of(profileLink(username.get()));
	}

	private InstagramProfileLink profileLink(String username) {
		return new InstagramProfileLink(
				username,
				"https://www.instagram.com/" + username + "/");
	}

	private boolean explicitlyNamesUsername(String value, String username) {
		if (value == null || value.isBlank()) {
			return false;
		}
		Pattern explicitUsername = Pattern.compile(
				"(?i)(?<![A-Za-z0-9._])@?" + Pattern.quote(username) + "(?![A-Za-z0-9._])");
		return explicitUsername.matcher(value).find();
	}

	private PostExtractionDiagnostic diagnostic(
			Page page, PageLocation location, PostRootType postRootType, List<VisibleLink> rootLinks) {
		int profileLikeLinkCount = 0;
		Set<String> candidateUsernames = new LinkedHashSet<>();
		for (VisibleLink link : rootLinks) {
			Optional<String> username = profileUsernameFromUrl(link.href());
			if (username.isPresent()) {
				profileLikeLinkCount++;
				candidateUsernames.add(username.get());
			}
		}
		return new PostExtractionDiagnostic(
				location.classification(),
				location.finalPath(),
				postRootType,
				safeCount(page.locator(VISIBLE_MAIN_SELECTOR)),
				safeCount(page.locator("article:visible")),
				safeCount(page.locator("[role='dialog']:visible")),
				rootLinks.size(),
				profileLikeLinkCount,
				candidateUsernames.stream().limit(MAX_DIAGNOSTIC_USERNAMES).toList());
	}

	private List<VisibleLink> visibleLinks(Locator locator, int maxLinks) {
		List<VisibleLink> links = new ArrayList<>();
		int count = Math.min(safeCount(locator), maxLinks);
		for (int index = 0; index < count; index++) {
			Locator anchor = locator.nth(index);
			try {
				if (!anchor.isVisible()) {
					continue;
				}
				links.add(new VisibleLink(
						anchor.getAttribute("href"),
						trimmed(anchor.innerText()),
						trimmed(anchor.getAttribute("aria-label")),
						trimmed(anchor.getAttribute("title"))));
			}
			catch (PlaywrightException ignored) {
				// 한 anchor가 DOM 재렌더링으로 사라져도 같은 post root의 다음 후보를 확인한다.
			}
		}
		return links;
	}

	private Long metricFromLocator(Locator locator, List<String> labels) {
		return metricFromLocator(locator, labels, true);
	}

	private Long labeledMetricFromLocator(Locator locator, List<String> labels) {
		return metricFromLocator(locator, labels, false);
	}

	private Long metricFromLocator(Locator locator, List<String> labels, boolean allowDirectCount) {
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
					Long metric = allowDirectCount
							? metricFromText(value, labels)
							: labeledMetricFromText(value, labels);
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

	private Long metricFromLines(List<String> lines, List<String> labels) {
		return metricFromTexts(lines, labels);
	}

	private Long metricFromTexts(List<String> texts, List<String> labels) {
		for (String text : texts) {
			Long metric = labeledMetricFromText(text, labels);
			if (metric != null) {
				return metric;
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
		return labeledMetricFromText(text, labels);
	}

	private Long labeledMetricFromText(String text, List<String> labels) {
		if (text == null || text.isBlank()) {
			return null;
		}
		for (String label : labels) {
			String quotedLabel = Pattern.quote(label);
			for (Pattern pattern : List.of(
					Pattern.compile(
							METRIC_NUMBER_TOKEN + METRIC_SEPARATOR + quotedLabel,
							Pattern.CASE_INSENSITIVE),
					Pattern.compile(
							quotedLabel + METRIC_SEPARATOR + METRIC_NUMBER_TOKEN,
							Pattern.CASE_INSENSITIVE))) {
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

	private String displayName(String username, List<String> headerLines) {
		int usernameIndex = usernameLineIndex(headerLines, username);
		int firstMetricIndex = firstMetricLineIndex(headerLines, usernameIndex + 1);
		if (usernameIndex < 0 || firstMetricIndex < 0) {
			return null;
		}

		for (int index = usernameIndex + 1; index < firstMetricIndex; index++) {
			String candidate = headerLines.get(index);
			if (isDisplayNameCandidate(candidate, username)) {
				return candidate;
			}
		}
		return null;
	}

	private String displayNameFromMetadata(Page page, String username) {
		List<String> metadata = new ArrayList<>();
		metadata.addAll(metadataContents(page.locator("meta[property='og:title']")));
		metadata.addAll(profileDescriptionMetadata(page));

		Pattern parenthesizedUsername = Pattern.compile(
				"\\(\\s*@?" + Pattern.quote(username) + "\\s*\\)",
				Pattern.CASE_INSENSITIVE);
		Pattern bareUsername = Pattern.compile(
				"(?<![A-Za-z0-9._])@" + Pattern.quote(username) + "(?![A-Za-z0-9._])",
				Pattern.CASE_INSENSITIVE);
		for (String value : metadata) {
			Matcher parenthesizedMatcher = parenthesizedUsername.matcher(value);
			Matcher bareMatcher = bareUsername.matcher(value);
			int markerStart = parenthesizedMatcher.find()
					? parenthesizedMatcher.start()
					: bareMatcher.find() ? bareMatcher.start() : -1;
			if (markerStart <= 0) {
				continue;
			}
			String candidate = trimmed(value.substring(0, markerStart));
			if (isDisplayNameCandidate(candidate, username)) {
				return candidate;
			}
		}
		return null;
	}

	private List<String> profileDescriptionMetadata(Page page) {
		Set<String> descriptions = new LinkedHashSet<>();
		descriptions.addAll(metadataContents(page.locator("meta[property='og:description']")));
		descriptions.addAll(metadataContents(page.locator("meta[name='description']")));
		return List.copyOf(descriptions);
	}

	private List<String> metadataContents(Locator locator) {
		List<String> values = new ArrayList<>();
		int count = Math.min(safeCount(locator), 5);
		for (int index = 0; index < count; index++) {
			try {
				String content = excerpt(
						locator.nth(index).getAttribute("content"), MAX_METADATA_TEXT_CODE_POINTS);
				if (content != null) {
					values.add(content);
				}
			}
			catch (PlaywrightException ignored) {
				// 필요한 bounded content 하나가 사라져도 다음 metadata fallback을 확인한다.
			}
		}
		return values;
	}

	private String biographyExcerpt(List<String> headerLines, String username, String displayName) {
		int usernameIndex = usernameLineIndex(headerLines, username);
		int firstMetricIndex = firstMetricLineIndex(headerLines, usernameIndex + 1);
		if (usernameIndex < 0 || firstMetricIndex < 0) {
			return null;
		}

		int lastMetricIndex = firstMetricIndex;
		for (int index = firstMetricIndex + 1; index < headerLines.size(); index++) {
			if (!isMetricLine(headerLines.get(index))) {
				break;
			}
			lastMetricIndex = index;
		}

		List<String> biographyLines = new ArrayList<>();
		for (int index = lastMetricIndex + 1; index < headerLines.size(); index++) {
			String line = headerLines.get(index);
			if (isUsernameLine(line, username)
					|| line.equals(displayName)
					|| isMetricLine(line)) {
				continue;
			}
			if (isBiographyBoundary(line)) {
				break;
			}
			biographyLines.add(line);
		}
		return excerpt(String.join(" · ", biographyLines), DiscoveryBrowserObservation.BIOGRAPHY_EXCERPT_MAX_LENGTH);
	}

	private int usernameLineIndex(List<String> lines, String username) {
		for (int index = 0; index < lines.size(); index++) {
			if (isUsernameLine(lines.get(index), username)) {
				return index;
			}
		}
		return -1;
	}

	private int firstMetricLineIndex(List<String> lines, int startIndex) {
		for (int index = Math.max(0, startIndex); index < lines.size(); index++) {
			if (isMetricLine(lines.get(index))) {
				return index;
			}
		}
		return -1;
	}

	private boolean isMetricLine(String value) {
		for (List<String> labels : PROFILE_METRIC_LABEL_GROUPS) {
			if (labeledMetricFromText(value, labels) != null) {
				return true;
			}
		}
		return false;
	}

	private boolean isDisplayNameCandidate(String value, String username) {
		return value != null
				&& !value.isBlank()
				&& !isUsernameLine(value, username)
				&& !isMetricLine(value)
				&& !isControlLine(value)
				&& !isHighlightBoundaryLine(value)
				&& !looksLikeExternalUrl(value)
				&& value.codePointCount(0, value.length()) <= 255;
	}

	private boolean isBiographyBoundary(String value) {
		return isControlLine(value)
				|| isHighlightBoundaryLine(value)
				|| looksLikeExternalUrl(value);
	}

	private boolean isUsernameLine(String value, String username) {
		return containsExactLine(value, username);
	}

	private boolean isControlLine(String value) {
		return PROFILE_CONTROL_LINES.contains(safeLower(value).strip());
	}

	private boolean isHighlightBoundaryLine(String value) {
		return PROFILE_HIGHLIGHT_BOUNDARY_LINES.contains(safeLower(value).strip());
	}

	private boolean looksLikeExternalUrl(String value) {
		return value != null && EXTERNAL_URL_PATTERN.matcher(value.strip()).matches();
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
		if (locator == null) {
			return 0;
		}
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
				&& uri.getRawUserInfo() == null
				&& (uri.getPort() == -1 || uri.getPort() == 443)
				&& (host.equalsIgnoreCase("instagram.com")
						|| host.equalsIgnoreCase("www.instagram.com"));
	}

	private boolean isSupportedPostPath(URI uri) {
		String rawPath = uri.getRawPath();
		return rawPath != null && SUPPORTED_POST_PATH_PATTERN.matcher(rawPath).matches();
	}

	private Optional<PostIdentity> postIdentity(String value) {
		try {
			if (value == null || value.isBlank()) {
				return Optional.empty();
			}
			URI uri = URI.create(value.strip());
			if (!isInstagramHttpsUri(uri) || !isSupportedPostPath(uri)) {
				return Optional.empty();
			}
			Matcher pathMatcher = SUPPORTED_POST_PATH_PATTERN.matcher(uri.getRawPath());
			if (!pathMatcher.matches()) {
				return Optional.empty();
			}
			return Optional.of(new PostIdentity(
					canonicalPostType(pathMatcher.group(1)),
					pathMatcher.group(2)));
		}
		catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private String canonicalPostType(String pathType) {
		// Instagram navigation이 동일 Reel을 두 route로 표시해도 post identity는 하나로 유지한다.
		String normalized = pathType.toLowerCase(Locale.ROOT);
		return normalized.equals("reels") ? "reel" : normalized;
	}

	private String safeFinalPath(URI uri) {
		String rawPath = uri.getRawPath();
		if (rawPath == null || rawPath.isBlank()) {
			return "/";
		}
		String safePath = rawPath.replaceAll("[\\p{Cntrl}]", "");
		if (safePath.length() <= MAX_DIAGNOSTIC_PATH_LENGTH) {
			return safePath;
		}
		return safePath.substring(0, MAX_DIAGNOSTIC_PATH_LENGTH);
	}

	private String[] pathSegments(URI uri) {
		String path = uri.getPath();
		if (path == null) {
			return new String[0];
		}
		return Arrays.stream(path.split("/"))
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

	record VisibleLink(String href, String innerText, String ariaLabel, String title) {

		VisibleLink(String href, String innerText) {
			this(href, innerText, null, null);
		}
	}

	record InstagramProfileLink(String username, String profileUrl) {
	}

	record PostExtractionResult(
			Optional<InstagramPostBrowserSnapshot> snapshot,
			PostExtractionDiagnostic diagnostic) {

		PostExtractionResult {
			snapshot = snapshot == null ? Optional.empty() : snapshot;
			diagnostic = diagnostic == null
					? PostExtractionDiagnostic.empty()
					: diagnostic;
		}

		static PostExtractionResult found(
				InstagramPostBrowserSnapshot snapshot, PostExtractionDiagnostic diagnostic) {
			return new PostExtractionResult(Optional.of(snapshot), diagnostic);
		}

		static PostExtractionResult notFound(PostExtractionDiagnostic diagnostic) {
			return new PostExtractionResult(Optional.empty(), diagnostic);
		}
	}

	record PostExtractionDiagnostic(
			PageClassification pageClassification,
			String finalPath,
			PostRootType postRootType,
			int visibleMainCount,
			int visibleArticleCount,
			int visibleDialogCount,
			int visibleRootLinkCount,
			int profileLikeLinkCount,
			List<String> candidateUsernames) {

		PostExtractionDiagnostic {
			pageClassification = pageClassification == null
					? PageClassification.EXTERNAL
					: pageClassification;
			finalPath = finalPath == null || finalPath.isBlank() ? "-" : finalPath;
			postRootType = postRootType == null ? PostRootType.NONE : postRootType;
			candidateUsernames = candidateUsernames == null
					? List.of()
					: candidateUsernames.stream().limit(MAX_DIAGNOSTIC_USERNAMES).toList();
		}

		static PostExtractionDiagnostic empty() {
			return new PostExtractionDiagnostic(
					PageClassification.EXTERNAL,
					"-",
					PostRootType.NONE,
					0,
					0,
					0,
					0,
					0,
					List.of());
		}

		String compactSummary() {
			String usernames = candidateUsernames.isEmpty()
					? "-"
					: String.join(",", candidateUsernames);
			return "page=" + pageClassification.diagnosticLabel()
					+ ", finalPath=" + finalPath
					+ ", postRoot=" + postRootType.diagnosticLabel()
					+ ", main=" + visibleMainCount
					+ ", article=" + visibleArticleCount
					+ ", dialog=" + visibleDialogCount
					+ ", rootLinks=" + visibleRootLinkCount
					+ ", profileLinks=" + profileLikeLinkCount
					+ ", candidates=" + usernames;
		}
	}

	record PageLocation(PageClassification classification, String finalPath) {

		PageLocation {
			classification = classification == null ? PageClassification.EXTERNAL : classification;
			finalPath = finalPath == null || finalPath.isBlank() ? "-" : finalPath;
		}

		static PageLocation external() {
			return new PageLocation(PageClassification.EXTERNAL, "-");
		}

		boolean isPost() {
			return classification == PageClassification.POST;
		}

		String compactSummary() {
			return "page=" + classification.diagnosticLabel() + ", finalPath=" + finalPath;
		}
	}

	private record PostRoot(PostRootType type, Locator locator) {

		static PostRoot none() {
			return new PostRoot(PostRootType.NONE, null);
		}

		boolean isPresent() {
			return type != PostRootType.NONE && locator != null;
		}
	}

	private record PostIdentity(String pathType, String shortcode) {
	}

	enum PostRootType {
		ARTICLE("article"),
		MAIN_FALLBACK("main"),
		NONE("none");

		private final String diagnosticLabel;

		PostRootType(String diagnosticLabel) {
			this.diagnosticLabel = diagnosticLabel;
		}

		String diagnosticLabel() {
			return diagnosticLabel;
		}
	}

	enum PageClassification {
		POST("post"),
		LOGIN("login"),
		ACTION_REQUIRED("action_required"),
		HOME("home"),
		OTHER_INSTAGRAM("other_instagram"),
		EXTERNAL("external");

		private final String diagnosticLabel;

		PageClassification(String diagnosticLabel) {
			this.diagnosticLabel = diagnosticLabel;
		}

		String diagnosticLabel() {
			return diagnosticLabel;
		}
	}

	enum BrowserPageState {
		READY,
		LOGIN_REQUIRED,
		ACTION_REQUIRED
	}
}
