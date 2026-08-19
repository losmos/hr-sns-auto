package com.losmos.hrsnsauto.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;

import jakarta.annotation.PreDestroy;

@Component
public class InstagramBrowserClient {

	private static final String INSTAGRAM_ROOT = "https://www.instagram.com/";
	private static final double LOCATOR_TIMEOUT_MILLIS = 8_000;
	private static final double NAVIGATION_TIMEOUT_MILLIS = 20_000;
	private static final double UI_SETTLE_MILLIS = 500;

	private final InstagramBrowserProperties properties;
	private final InstagramBrowserExtractor extractor;
	private final InstagramBrowserErrorSanitizer errorSanitizer;

	private Playwright playwright;
	private BrowserContext browserContext;

	public InstagramBrowserClient(InstagramBrowserProperties properties,
			InstagramBrowserExtractor extractor, InstagramBrowserErrorSanitizer errorSanitizer) {
		this.properties = properties;
		this.extractor = extractor;
		this.errorSanitizer = errorSanitizer;
	}

	public synchronized InstagramBrowserSessionResult openSession() {
		properties.requireEnabled();
		try {
			Page page = reusablePage();
			navigate(page, INSTAGRAM_ROOT);
			return switch (extractor.pageState(page)) {
				case READY -> InstagramBrowserSessionResult.ready();
				case LOGIN_REQUIRED -> InstagramBrowserSessionResult.loginRequired();
				case ACTION_REQUIRED -> InstagramBrowserSessionResult.actionRequired();
			};
		}
		catch (TimeoutError exception) {
			return InstagramBrowserSessionResult.failed(
					InstagramBrowserErrorCode.NAVIGATION_TIMEOUT,
					"Instagram 페이지를 여는 시간이 초과됨");
		}
		catch (PlaywrightException | IllegalStateException exception) {
			BrowserFailure failure = browserFailure(exception);
			return InstagramBrowserSessionResult.failed(failure.errorCode(), failure.summary());
		}
	}

	public synchronized InstagramBrowserEnrichmentResult enrich(String permalink) {
		properties.requireEnabled();
		if (!extractor.isInstagramPostUrl(permalink)) {
			return InstagramBrowserEnrichmentResult.failure(
					DiscoveryBrowserObservationStatus.FAILED,
					InstagramBrowserErrorCode.POST_UNAVAILABLE,
					"저장된 permalink가 지원하는 Instagram post URL이 아님");
		}

		InstagramPostBrowserSnapshot post = null;
		try {
			Page page = reusablePage();
			Response postResponse = navigate(page, permalink);
			InstagramBrowserEnrichmentResult blocked = blockedResult(extractor.pageState(page), null);
			if (blocked != null) {
				return blocked;
			}
			if (isUnavailable(postResponse) || extractor.isUnavailable(page)) {
				return InstagramBrowserEnrichmentResult.failure(
						DiscoveryBrowserObservationStatus.FAILED,
						InstagramBrowserErrorCode.POST_UNAVAILABLE,
						"Instagram 게시물을 사용할 수 없거나 삭제됨");
			}

			Optional<InstagramPostBrowserSnapshot> extractedPost = extractor.extractPost(page);
			if (extractedPost.isEmpty()) {
				return InstagramBrowserEnrichmentResult.failure(
						DiscoveryBrowserObservationStatus.FAILED,
						InstagramBrowserErrorCode.AUTHOR_EXTRACTION_FAILED,
						"게시물의 author profile link를 화면에서 확인하지 못함");
			}
			post = extractedPost.get();

			Response profileResponse = navigate(page, post.profileUrl());
			blocked = blockedResult(extractor.pageState(page), post);
			if (blocked != null) {
				return blocked;
			}
			if (isUnavailable(profileResponse)
					|| extractor.isUnavailable(page)
					|| !extractor.isExpectedProfileUrl(page.url(), post.authorUsername())) {
				return InstagramBrowserEnrichmentResult.failure(
						DiscoveryBrowserObservationStatus.FAILED,
						InstagramBrowserErrorCode.PROFILE_UNAVAILABLE,
						"author Instagram 프로필을 사용할 수 없음",
						post);
			}

			Optional<InstagramProfileBrowserSnapshot> profile = extractor.extractProfile(
					page, post.authorUsername());
			if (profile.isEmpty()) {
				return InstagramBrowserEnrichmentResult.failure(
						DiscoveryBrowserObservationStatus.FAILED,
						InstagramBrowserErrorCode.UNEXPECTED_DOM,
						"Instagram 프로필 화면에서 기본 profile 영역을 확인하지 못함",
						post);
			}
			return InstagramBrowserEnrichmentResult.success(post, profile.get());
		}
		catch (TimeoutError exception) {
			return InstagramBrowserEnrichmentResult.failure(
					DiscoveryBrowserObservationStatus.FAILED,
					InstagramBrowserErrorCode.NAVIGATION_TIMEOUT,
					"Instagram 페이지 이동 시간이 초과됨",
					post);
		}
		catch (PlaywrightException | IllegalStateException exception) {
			BrowserFailure failure = browserFailure(exception);
			return InstagramBrowserEnrichmentResult.failure(
					DiscoveryBrowserObservationStatus.FAILED,
					failure.errorCode(),
					failure.summary(),
					post);
		}
	}

	private InstagramBrowserEnrichmentResult blockedResult(
			InstagramBrowserExtractor.BrowserPageState state, InstagramPostBrowserSnapshot post) {
		return switch (state) {
			case READY -> null;
			case LOGIN_REQUIRED -> InstagramBrowserEnrichmentResult.failure(
					DiscoveryBrowserObservationStatus.LOGIN_REQUIRED,
					InstagramBrowserErrorCode.LOGIN_REQUIRED,
					"Instagram 로그인이 필요함. session 준비 버튼으로 열린 브라우저에서 직접 로그인한다.",
					post);
			case ACTION_REQUIRED -> InstagramBrowserEnrichmentResult.failure(
					DiscoveryBrowserObservationStatus.ACTION_REQUIRED,
					InstagramBrowserErrorCode.ACTION_REQUIRED,
					"Instagram challenge/checkpoint 확인이 필요해 자동 처리 없이 중단함",
					post);
		};
	}

	private Response navigate(Page page, String url) {
		Response response = page.navigate(url, new Page.NavigateOptions()
				.setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
				.setTimeout(NAVIGATION_TIMEOUT_MILLIS));
		// 고정 wait는 DOM 초기 렌더링 안정화 목적이며 random/stealth timing으로 사용하지 않는다.
		page.waitForTimeout(UI_SETTLE_MILLIS);
		return response;
	}

	private boolean isUnavailable(Response response) {
		return response != null && (response.status() == 404 || response.status() == 410);
	}

	private Page reusablePage() {
		if (browserContext != null) {
			try {
				return existingOrNewPage(browserContext);
			}
			catch (PlaywrightException exception) {
				// 사용자가 창을 닫았거나 browser process가 끝났으면 같은 persistent profile로 다시 연다.
				resetBrowser();
			}
		}

		Path userDataDirectory = userDataDirectory();
		try {
			Files.createDirectories(userDataDirectory);
		}
		catch (IOException exception) {
			throw new IllegalStateException("browser session directory를 준비할 수 없음", exception);
		}

		playwright = Playwright.create();
		browserContext = playwright.chromium().launchPersistentContext(
				userDataDirectory,
				new BrowserType.LaunchPersistentContextOptions()
						.setHeadless(properties.isHeadless())
						.setAcceptDownloads(false));
		browserContext.setDefaultTimeout(LOCATOR_TIMEOUT_MILLIS);
		return existingOrNewPage(browserContext);
	}

	private Page existingOrNewPage(BrowserContext context) {
		List<Page> pages = context.pages();
		Page page = pages.isEmpty() ? context.newPage() : pages.getLast();
		page.bringToFront();
		return page;
	}

	private Path userDataDirectory() {
		String configured = properties.getUserDataDir();
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException("INSTAGRAM_BROWSER_USER_DATA_DIR 설정이 비어 있음");
		}
		try {
			return Path.of(configured.strip()).toAbsolutePath().normalize();
		}
		catch (InvalidPathException exception) {
			throw new IllegalStateException("INSTAGRAM_BROWSER_USER_DATA_DIR 경로 형식이 올바르지 않음");
		}
	}

	private BrowserFailure browserFailure(RuntimeException exception) {
		String rawMessage = exception.getMessage() == null ? "" : exception.getMessage();
		String lower = rawMessage.toLowerCase(Locale.ROOT);
		if (lower.contains("executable doesn't exist") || lower.contains("executable not found")) {
			resetBrowser();
			return new BrowserFailure(
					InstagramBrowserErrorCode.BROWSER_BINARY_MISSING,
					"Playwright Chromium binary가 없음. 안내된 install chromium 명령을 먼저 실행한다.");
		}
		if (lower.contains("processsingleton")
				|| lower.contains("user data directory is already in use")
				|| lower.contains("profile appears to be in use")) {
			resetBrowser();
			return new BrowserFailure(
					InstagramBrowserErrorCode.BROWSER_PROFILE_IN_USE,
					"같은 Instagram browser profile을 다른 Chromium instance가 사용 중임");
		}
		if (lower.contains("instagram_browser_user_data_dir")
				|| lower.contains("browser session directory")) {
			resetBrowser();
			return new BrowserFailure(
					InstagramBrowserErrorCode.INVALID_CONFIGURATION,
					"Instagram browser session directory 설정을 확인해야 함");
		}
		String safeDetail = errorSanitizer.sanitize(rawMessage, safeUserDataDirectory());
		resetBrowser();
		return new BrowserFailure(
				InstagramBrowserErrorCode.UNEXPECTED_DOM,
				"브라우저 작업 중 예상하지 못한 오류가 발생함"
						+ (safeDetail.isBlank() ? "" : ": " + safeDetail));
	}

	private String safeUserDataDirectory() {
		try {
			return userDataDirectory().toString();
		}
		catch (RuntimeException exception) {
			return properties.getUserDataDir();
		}
	}

	@PreDestroy
	public synchronized void close() {
		resetBrowser();
	}

	private void resetBrowser() {
		BrowserContext context = browserContext;
		Playwright currentPlaywright = playwright;
		browserContext = null;
		playwright = null;
		if (context != null) {
			try {
				context.close();
			}
			catch (RuntimeException ignored) {
				// 종료 과정의 오류에는 session 정보가 섞일 수 있어 로그나 상위 오류에 포함하지 않는다.
			}
		}
		if (currentPlaywright != null) {
			try {
				currentPlaywright.close();
			}
			catch (RuntimeException ignored) {
				// application shutdown 또는 browser 재생성 흐름을 방해하지 않는다.
			}
		}
	}

	private record BrowserFailure(InstagramBrowserErrorCode errorCode, String summary) {
	}
}
