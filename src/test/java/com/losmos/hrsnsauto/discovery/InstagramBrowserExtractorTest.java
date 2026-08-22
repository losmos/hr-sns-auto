package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

class InstagramBrowserExtractorTest {

	private final InstagramBrowserExtractor extractor = new InstagramBrowserExtractor(
			new InstagramMetricParser());

	@Test
	void extractsAuthorFromExistingMainArticleRoot() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/p/Article123/",
				"""
				<main>
				  <article>
				    <a href="/article.doctor/"><img alt="" /></a>
				    <a href="/article.doctor/">article.doctor</a>
				  </article>
				</main>
				""");

		assertThat(result.snapshot()).isPresent();
		assertThat(result.snapshot().orElseThrow().authorUsername()).isEqualTo("article.doctor");
		assertThat(result.diagnostic().postRootType())
				.isEqualTo(InstagramBrowserExtractor.PostRootType.ARTICLE);
	}

	@Test
	void extractsAuthorFromPostMainWhenArticleIsAbsent() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/reel/Main123/?utm_source=safe#ignored",
				"""
				<main>
				  <section>
				    <a href="/doctor.one/"><img alt="" /></a>
				    <a href="/doctor.one/">doctor.one</a>
				    <div>post content</div>
				  </section>
				</main>
				""");

		assertThat(result.snapshot()).isPresent();
		assertThat(result.snapshot().orElseThrow().authorUsername()).isEqualTo("doctor.one");
		assertThat(result.diagnostic().postRootType())
				.isEqualTo(InstagramBrowserExtractor.PostRootType.MAIN_FALLBACK);
	}

	@Test
	void mainFallbackPrefersRepeatedAuthorEvidenceOverEarlierUnrelatedNavigation() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/p/MainEvidence123/",
				"""
				<main>
				  <a href="/navigation.user/">navigation.user</a>
				  <section>
				    <a href="/actual.doctor/" aria-label="actual.doctor profile"><img alt="" /></a>
				    <a href="/actual.doctor/">actual.doctor</a>
				  </section>
				</main>
				""");

		assertThat(result.snapshot()).isPresent();
		assertThat(result.snapshot().orElseThrow().authorUsername()).isEqualTo("actual.doctor");
	}

	@Test
	void mainFallbackKeepsRepeatedAuthorAheadOfLaterCommenterAndMention() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/p/MainComment123/",
				"""
				<main>
				  <a href="/author.doctor/"><img alt="" /></a>
				  <a href="/author.doctor/">author.doctor</a>
				  <div>caption <a href="/mention.user/">@mention.user</a></div>
				  <div>comment <a href="/other.user/">other.user</a></div>
				</main>
				""");

		assertThat(result.snapshot()).isPresent();
		assertThat(result.snapshot().orElseThrow().authorUsername()).isEqualTo("author.doctor");
	}

	@Test
	void mainFallbackSelectsRepeatedProfileReelsTabAuthorFromLiveDomShape() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/reels/DcVyTw1tpwA/",
				"""
				<main>
				  <a href="/dr_howoo/reels/" aria-label="dr_howoo님의 릴스"><span></span></a>
				  <a href="/dr_howoo/reels/" aria-label="dr_howoo님의 릴스">dr_howoo</a>
				  <a href="/explore/tags/의사/">의사</a>
				  <a href="/reels/audio/39043968185201847/">audio</a>
				  <a href="/dino.the.nomad/reels/"><span></span></a>
				  <a href="/dino.the.nomad/reels/">dino.the.nomad</a>
				</main>
				""");

		assertThat(result.snapshot()).isPresent();
		InstagramPostBrowserSnapshot snapshot = result.snapshot().orElseThrow();
		assertThat(snapshot.authorUsername()).isEqualTo("dr_howoo");
		assertThat(snapshot.profileUrl()).isEqualTo("https://www.instagram.com/dr_howoo/");
		assertThat(result.diagnostic().profileLikeLinkCount()).isEqualTo(4);
		assertThat(result.diagnostic().candidateUsernames())
				.containsExactly("dr_howoo", "dino.the.nomad");
	}

	@Test
	void mainFallbackDoesNotInferAuthorFromCaptionMentionText() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/p/MentionOnly123/",
				"""
				<main>
				  <div>caption <a href="/mention.only/">@mention.only</a></div>
				  <div>repeated caption <a href="/mention.only/">@mention.only</a></div>
				</main>
				""");

		assertThat(result.snapshot()).isEmpty();
		assertThat(result.diagnostic().candidateUsernames()).containsExactly("mention.only");
	}

	@Test
	void doesNotUseMainFallbackOnHomeExploreOrExternalFinalUrls() throws Exception {
		String body = """
				<main>
				  <a href="/wrong.user/"><img alt="" /></a>
				  <a href="/wrong.user/">wrong.user</a>
				</main>
				""";

		InstagramBrowserExtractor.PostExtractionResult home = extractionFromSyntheticPage(
				"https://www.instagram.com/?next=%2Fp%2FSecret%2F", body);
		InstagramBrowserExtractor.PostExtractionResult explore = extractionFromSyntheticPage(
				"https://www.instagram.com/explore/?token=must-not-appear", body);
		InstagramBrowserExtractor.PostExtractionResult external = extractionFromSyntheticPage(
				"https://example.com/p/Fake/?sessionid=must-not-appear", body);

		assertThat(home.snapshot()).isEmpty();
		assertThat(home.diagnostic().pageClassification())
				.isEqualTo(InstagramBrowserExtractor.PageClassification.HOME);
		assertThat(home.diagnostic().postRootType())
				.isEqualTo(InstagramBrowserExtractor.PostRootType.NONE);
		assertThat(home.diagnostic().visibleRootLinkCount()).isZero();

		assertThat(explore.snapshot()).isEmpty();
		assertThat(explore.diagnostic().pageClassification())
				.isEqualTo(InstagramBrowserExtractor.PageClassification.OTHER_INSTAGRAM);
		assertThat(explore.diagnostic().postRootType())
				.isEqualTo(InstagramBrowserExtractor.PostRootType.NONE);

		assertThat(external.snapshot()).isEmpty();
		assertThat(external.diagnostic().pageClassification())
				.isEqualTo(InstagramBrowserExtractor.PageClassification.EXTERNAL);
		assertThat(external.diagnostic().finalPath()).isEqualTo("-");
	}

	@Test
	void safelyFailsWhenPostFinalUrlHasNoMainOrArticle() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://instagram.com/tv/NoRoot123/",
				"<div>post shell without a supported root</div>");

		assertThat(result.snapshot()).isEmpty();
		assertThat(result.diagnostic().pageClassification())
				.isEqualTo(InstagramBrowserExtractor.PageClassification.POST);
		assertThat(result.diagnostic().postRootType())
				.isEqualTo(InstagramBrowserExtractor.PostRootType.NONE);
	}

	@Test
	void diagnosticContainsOnlySafePathAndCompactCounts() throws Exception {
		InstagramBrowserExtractor.PostExtractionResult result = extractionFromSyntheticPage(
				"https://www.instagram.com/p/ABC123/?access_token=TOP_SECRET#sessionid=FRAGMENT_SECRET",
				"""
				<main>
				  <a href="/candidate.one/">different label</a>
				  <div>RAW_HTML_MARKER caption body must not appear</div>
				</main>
				""");

		assertThat(result.snapshot()).isEmpty();
		assertThat(result.diagnostic().compactSummary())
				.contains(
						"page=post",
						"finalPath=/p/ABC123/",
						"postRoot=main",
						"main=1",
						"article=0",
						"dialog=0",
						"rootLinks=1",
						"profileLinks=1",
						"candidates=candidate.one")
				.doesNotContain(
						"access_token", "TOP_SECRET", "sessionid", "FRAGMENT_SECRET",
						"RAW_HTML_MARKER", "caption body", "<main>");
	}

	@Test
	void extractsAuthorFromHeaderlessArticleWithAvatarAndUsernameLinks() throws Exception {
		InstagramBrowserExtractor.InstagramProfileLink author = authorFromSyntheticHtml("""
				<main>
				  <article>
				    <div>
				      <a href="/doctor.one/"><img alt="" /></a>
				      <a href="/doctor.one/">doctor.one</a>
				    </div>
				  </article>
				</main>
				""").orElseThrow();

		assertThat(author.username()).isEqualTo("doctor.one");
	}

	@Test
	void extractsRepeatedProfileHrefWhenAvatarLinkHasNoText() throws Exception {
		InstagramBrowserExtractor.InstagramProfileLink author = authorFromSyntheticHtml("""
				<main>
				  <article>
				    <div>
				      <a href="/repeat.doctor/"><img alt="" /></a>
				      <a href="/repeat.doctor/"><span></span></a>
				    </div>
				  </article>
				</main>
				""").orElseThrow();

		assertThat(author.username()).isEqualTo("repeat.doctor");
	}

	@Test
	void selectsEarlyAuthorInsteadOfCaptionMentionOrCommenter() throws Exception {
		InstagramBrowserExtractor.InstagramProfileLink author = authorFromSyntheticHtml("""
				<main>
				  <article>
				    <div>
				      <a href="/doctor.author/"><img alt="" /></a>
				      <a href="/doctor.author/">doctor.author</a>
				    </div>
				    <div>caption <a href="/other.user/">@other.user</a></div>
				    <div>comment by <a href="/comment.user/">comment.user</a></div>
				  </article>
				</main>
				""").orElseThrow();

		assertThat(author.username()).isEqualTo("doctor.author");
	}

	@Test
	void ignoresNavigationProfileLinkOutsidePostArticle() throws Exception {
		Optional<InstagramBrowserExtractor.InstagramProfileLink> author = authorFromSyntheticHtml("""
				<nav><a href="/global.doctor/">global.doctor</a></nav>
				<main>
				  <article><a href="/p/post-code/">post</a></article>
				</main>
				""");

		assertThat(author).isEmpty();
	}

	@Test
	void excludesNonProfilePathsInsideArticle() throws Exception {
		Optional<InstagramBrowserExtractor.InstagramProfileLink> author = authorFromSyntheticHtml("""
				<main>
				  <article>
				    <a href="/p/foo/">p</a>
				    <a href="/reel/bar/">reel</a>
				    <a href="/explore/">explore</a>
				    <a href="/accounts/login/">accounts</a>
				  </article>
				</main>
				""");

		assertThat(author).isEmpty();
	}

	@Test
	void acceptsAbsoluteInstagramProfileUrlWithQueryAndFragment() throws Exception {
		InstagramBrowserExtractor.InstagramProfileLink author = authorFromSyntheticHtml("""
				<main>
				  <article>
				    <a href="https://www.instagram.com/Doctor.Absolute/?hl=ko#profile">doctor.absolute</a>
				  </article>
				</main>
				""").orElseThrow();

		assertThat(author.username()).isEqualTo("doctor.absolute");
		assertThat(author.profileUrl()).isEqualTo("https://www.instagram.com/doctor.absolute/");
	}

	@Test
	void rejectsExternalHostProfileLink() throws Exception {
		Optional<InstagramBrowserExtractor.InstagramProfileLink> author = authorFromSyntheticHtml("""
				<main>
				  <article><a href="https://example.com/external.doctor/">external.doctor</a></article>
				</main>
				""");

		assertThat(author).isEmpty();
	}

	@Test
	void returnsEmptyWhenArticleHasNoValidAuthorLink() throws Exception {
		Optional<InstagramBrowserExtractor.InstagramProfileLink> author = authorFromSyntheticHtml("""
				<main>
				  <article><div>Caption only mentions @caption.guess as plain text.</div></article>
				</main>
				""");

		assertThat(author).isEmpty();
	}

	@Test
	void acceptsUsernameExplicitlyNamedByAccessibleLabel() throws Exception {
		InstagramBrowserExtractor.InstagramProfileLink author = authorFromSyntheticHtml("""
				<main>
				  <article>
				    <div><a href="/label.doctor/" title="Open label.doctor profile"><img alt="" /></a></div>
				  </article>
				</main>
				""").orElseThrow();

		assertThat(author.username()).isEqualTo("label.doctor");
	}

	@Test
	void waitsForPostContainerAndRetriesAuthorExtractionWithExplicitBounds() {
		Page page = mock(Page.class);
		Locator containers = mock(Locator.class);
		Locator article = mock(Locator.class);
		Locator initialLinks = mock(Locator.class);
		Locator initialLink = mock(Locator.class);
		Locator articleLinks = mock(Locator.class);
		Locator semanticLinks = mock(Locator.class);
		Locator avatarLink = visibleLink("/retry.doctor/", null);
		Locator usernameLink = visibleLink("/retry.doctor/", "retry.doctor");

		when(page.url()).thenReturn("https://www.instagram.com/p/Retry123/");
		when(page.locator("[role='dialog'] article:visible, main article:visible"))
				.thenReturn(containers);
		when(page.locator("main article:visible")).thenReturn(containers);
		when(containers.count()).thenReturn(1);
		when(containers.first()).thenReturn(article);
		when(article.locator("a[href]:visible")).thenReturn(initialLinks);
		when(initialLinks.first()).thenReturn(initialLink);
		when(article.locator("a[href]")).thenReturn(articleLinks);
		when(article.locator("header a[href], h1 a[href], h2 a[href]"))
				.thenReturn(semanticLinks);
		when(article.count()).thenReturn(1);
		when(article.nth(0)).thenReturn(article);
		when(article.isVisible()).thenReturn(true);
		when(article.innerText()).thenReturn("");
		when(semanticLinks.count()).thenReturn(0);
		when(articleLinks.count()).thenReturn(0, 2);
		when(articleLinks.nth(0)).thenReturn(avatarLink);
		when(articleLinks.nth(1)).thenReturn(usernameLink);

		InstagramBrowserExtractor.PostExtractionResult result = extractor.extractPostWithDiagnostic(page);

		assertThat(result.snapshot()).isPresent();
		assertThat(result.snapshot().orElseThrow().authorUsername()).isEqualTo("retry.doctor");
		verify(page, times(1)).waitForTimeout(400);

		ArgumentCaptor<Locator.WaitForOptions> containerWait = ArgumentCaptor.forClass(
				Locator.WaitForOptions.class);
		verify(article).waitFor(containerWait.capture());
		assertThat(containerWait.getValue().state).isEqualTo(WaitForSelectorState.VISIBLE);
		assertThat(containerWait.getValue().timeout).isEqualTo(1_500);

		ArgumentCaptor<Locator.WaitForOptions> linkWait = ArgumentCaptor.forClass(
				Locator.WaitForOptions.class);
		verify(initialLink).waitFor(linkWait.capture());
		assertThat(linkWait.getValue().state).isEqualTo(WaitForSelectorState.VISIBLE);
		assertThat(linkWait.getValue().timeout).isEqualTo(1_200);
	}

	@Test
	void limitsAuthorFailureDiagnosticToThreeValidatedUsernames() {
		InstagramBrowserExtractor.PostExtractionDiagnostic diagnostic =
				new InstagramBrowserExtractor.PostExtractionDiagnostic(
						InstagramBrowserExtractor.PageClassification.POST,
						"/p/ABC123/",
						InstagramBrowserExtractor.PostRootType.ARTICLE,
						1,
						1,
						0,
						30,
						5,
						List.of("one", "two", "three", "four"));

		assertThat(diagnostic.compactSummary())
				.isEqualTo("page=post, finalPath=/p/ABC123/, postRoot=article, main=1, "
						+ "article=1, dialog=0, rootLinks=30, profileLinks=5, candidates=one,two,three");
	}

	@Test
	void acceptsOnlyAVisibleProfileLinkWhoseLabelMatchesUsername() {
		InstagramBrowserExtractor.InstagramProfileLink candidate = extractor.findAuthorCandidate(List.of(
				new InstagramBrowserExtractor.VisibleLink("/p/post-code/", "post"),
				new InstagramBrowserExtractor.VisibleLink("/explore/", "doctor.alpha"),
				new InstagramBrowserExtractor.VisibleLink("/caption_mention/", "different.user"),
				new InstagramBrowserExtractor.VisibleLink("/Doctor.Alpha/", "@doctor.alpha")))
				.orElseThrow();

		assertThat(candidate.username()).isEqualTo("doctor.alpha");
		assertThat(candidate.profileUrl()).isEqualTo("https://www.instagram.com/doctor.alpha/");
	}

	@Test
	void rejectsCaptionGuessNonProfilePathsMultiSegmentAndForeignHosts() {
		assertThat(extractor.authorCandidate("/caption_guess/", "actual.author")).isEmpty();
		assertThat(extractor.authorCandidate("/p/example/", "p")).isEmpty();
		assertThat(extractor.authorCandidate("/accounts/login/", "accounts")).isEmpty();
		assertThat(extractor.authorCandidate("/doctor.alpha/tagged/", "doctor.alpha")).isEmpty();
		assertThat(extractor.authorCandidate("https://example.com/doctor.alpha/", "doctor.alpha")).isEmpty();
		assertThat(extractor.authorCandidate("//example.com/doctor.alpha/", "doctor.alpha")).isEmpty();
		assertThat(extractor.authorCandidate("/doctor..alpha/", "doctor..alpha")).isEmpty();
	}

	@Test
	void classifiesReelsRouteWithShortcodeAsPost() {
		assertThat(extractor.pageLocation("https://www.instagram.com/reels/ABC123/")
				.classification()).isEqualTo(InstagramBrowserExtractor.PageClassification.POST);
	}

	@Test
	void acceptsReelsRouteWithShortcodeAsInstagramPostUrl() {
		assertThat(extractor.isInstagramPostUrl("https://www.instagram.com/reels/ABC123/"))
				.isTrue();
	}

	@Test
	void treatsReelAndReelsRoutesAsTheSamePostIdentity() {
		assertThat(extractor.isExpectedPostUrl(
				"https://www.instagram.com/reel/ABC123/",
				"https://www.instagram.com/reels/ABC123/"))
				.isTrue();
	}

	@Test
	void treatsReelsAndReelRoutesAsTheSamePostIdentity() {
		assertThat(extractor.isExpectedPostUrl(
				"https://www.instagram.com/reels/ABC123/",
				"https://www.instagram.com/reel/ABC123/"))
				.isTrue();
	}

	@Test
	void rejectsReelRouteAliasWhenShortcodesDiffer() {
		assertThat(extractor.isExpectedPostUrl(
				"https://www.instagram.com/reel/ABC123/",
				"https://www.instagram.com/reels/OTHER456/"))
				.isFalse();
	}

	@Test
	void doesNotClassifyReelsRouteWithoutShortcodeAsPost() {
		assertThat(extractor.isInstagramPostUrl("https://www.instagram.com/reels/"))
				.isFalse();
	}

	@Test
	void doesNotTreatReelsReservedPathAsProfileUsername() {
		assertThat(extractor.profileUsernameFromUrl("/reels/"))
				.isEmpty();
	}

	@Test
	void recognizesCanonicalAndReelsTabProfileRoutes() {
		assertThat(extractor.profileUsernameFromUrl("/dr_howoo/"))
				.contains("dr_howoo");
		assertThat(extractor.profileUsernameFromUrl("/dr_howoo/reels/"))
				.contains("dr_howoo");
		assertThat(extractor.profileUsernameFromUrl(
				"https://www.instagram.com/dr_howoo/reels/"))
				.contains("dr_howoo");
	}

	@Test
	void rejectsPostAndNonProfileRoutesWhenParsingProfileUsername() {
		assertThat(extractor.profileUsernameFromUrl("/reels/DcVyTw1tpwA/"))
				.isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/reels/audio/39043968185201847/"))
				.isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/explore/tags/doctor/"))
				.isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/accounts/login/"))
				.isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/p/ABC123/"))
				.isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/reel/ABC123/"))
				.isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/dr_howoo/reels/extra/"))
				.isEmpty();
	}

	@Test
	void validatesProfileUsernameAndSupportedPostPermalinks() {
		assertThat(extractor.profileUsernameFromUrl("https://www.instagram.com/Doctor_1/?hl=ko"))
				.contains("doctor_1");
		assertThat(extractor.profileUsernameFromUrl("/doctor.1/")).contains("doctor.1");
		assertThat(extractor.profileUsernameFromUrl("/reel/")).isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/doctor%2E1/")).isEmpty();
		assertThat(extractor.profileUsernameFromUrl("/doctor./")).isEmpty();

		assertThat(extractor.isInstagramPostUrl("https://www.instagram.com/p/AbC123/"))
				.isTrue();
		assertThat(extractor.isInstagramPostUrl("https://instagram.com/reel/AbC123/?utm_source=x"))
				.isTrue();
		assertThat(extractor.isInstagramPostUrl("https://instagram.com/explore/AbC123/"))
				.isFalse();
		assertThat(extractor.isInstagramPostUrl("https://m.instagram.com/p/AbC123/"))
				.isFalse();
		assertThat(extractor.isInstagramPostUrl("http://www.instagram.com/p/AbC123/"))
				.isFalse();
		assertThat(extractor.isInstagramPostUrl("https://www.instagram.com/p//AbC123/"))
				.isFalse();
		assertThat(extractor.isInstagramPostUrl("https://www.instagram.com/p/AbC123/embed/"))
				.isFalse();
		assertThat(extractor.isExpectedPostUrl(
				"https://www.instagram.com/p/AbC123/?utm_source=request",
				"https://instagram.com/p/AbC123#final-fragment"))
				.isTrue();
		assertThat(extractor.isExpectedPostUrl(
				"https://www.instagram.com/p/AbC123/",
				"https://www.instagram.com/p/Different123/"))
				.isFalse();
		assertThat(extractor.isExpectedPostUrl(
				"https://www.instagram.com/p/AbC123/",
				"https://www.instagram.com/reel/AbC123/"))
				.isFalse();

		assertThat(extractor.pageLocation("https://www.instagram.com/accounts/login/?next=%2Fp%2Fx%2F")
				.classification()).isEqualTo(InstagramBrowserExtractor.PageClassification.LOGIN);
		assertThat(extractor.pageLocation("https://www.instagram.com/challenge/123/?token=secret")
				.classification()).isEqualTo(InstagramBrowserExtractor.PageClassification.ACTION_REQUIRED);
	}

	@Test
	void extractsLiveKoreanProfileHeaderBeforeMetadataWhenMetricLinksUseHashHref() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"nurschema_studycafe",
				"""
				<meta property="og:title"
				      content="Nurschema의 공부방 | 간호사가 되기 위한 임상 공부(@nurschema_studycafe) • Instagram 사진 및 동영상" />
				<meta property="og:description"
				      content="팔로워 3,554명, 팔로잉 2명, 게시물 81개 - profile metadata" />
				<main>
				  <header>
				    <a href="/nurschema_studycafe/"></a>
				    <a href="#">nurschema_studycafe</a>
				    <div>Nurschema의 공부방 | 간호사가 되기 위한 임상 공부</div>
				    <div>게시물 81</div>
				    <a href="#">팔로워 3568</a>
				    <a href="#">팔로우 2</a>
				    <div>🌸Nurse+Schema라는 뜻입니다.</div>
				    <div>🍀풀 영상 유튜브 참고!!</div>
				    <a href="https://www.youtube.com/example">www.youtube.com/example</a>
				    <button>팔로우</button>
				    <button>메시지 보내기</button>
				  </header>
				</main>
				""");

		assertThat(profile.displayName())
				.isEqualTo("Nurschema의 공부방 | 간호사가 되기 위한 임상 공부");
		assertThat(profile.postCount()).isEqualTo(81L);
		assertThat(profile.followerCount()).isEqualTo(3_568L);
		assertThat(profile.followingCount()).isEqualTo(2L);
		assertThat(profile.biographyExcerpt())
				.isEqualTo("🌸Nurse+Schema라는 뜻입니다. · 🍀풀 영상 유튜브 참고!!");
		assertThat(profile.isPartial()).isFalse();
	}

	@Test
	void fillsOnlyMissingHeaderMetricsFromMetadata() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"doctor_meta",
				"""
				<meta property="og:title" content="Metadata Doctor(@doctor_meta) • Instagram" />
				<meta property="og:description"
				      content="팔로워 3,554명, 팔로잉 2명, 게시물 81개 - metadata biography" />
				<meta name="description"
				      content="3,500 followers, 3 following, 80 posts - older metadata" />
				<main>
				  <header>
				    <div>doctor_meta</div>
				    <div>Header Doctor</div>
				    <div>게시물 82</div>
				  </header>
				</main>
				""");

		assertThat(profile.displayName()).isEqualTo("Header Doctor");
		assertThat(profile.postCount()).isEqualTo(82L);
		assertThat(profile.followerCount()).isEqualTo(3_554L);
		assertThat(profile.followingCount()).isEqualTo(2L);
	}

	@Test
	void extractsAllProfileMetricsFromMetadataWhenHeaderHasNoMetricLines() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"metadata_only",
				"""
				<meta property="og:title" content="Metadata Only(@metadata_only) • Instagram" />
				<meta property="og:description"
				      content="팔로워 3,554명, 팔로잉 2명, 게시물 81개 - metadata biography" />
				<main>
				  <header>
				    <div>metadata_only</div>
				  </header>
				</main>
				""");

		assertThat(profile.displayName()).isEqualTo("Metadata Only");
		assertThat(profile.postCount()).isEqualTo(81L);
		assertThat(profile.followerCount()).isEqualTo(3_554L);
		assertThat(profile.followingCount()).isEqualTo(2L);
	}

	@Test
	void extractsFieldSpecificEnglishHeaderMetrics() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"doctor_one",
				"""
				<main>
				  <header>
				    <div>doctor_one</div>
				    <div>Doctor One</div>
				    <div>125 posts</div>
				    <div>9,876 followers</div>
				    <div>102 following</div>
				  </header>
				</main>
				""");

		assertThat(profile.displayName()).isEqualTo("Doctor One");
		assertThat(profile.postCount()).isEqualTo(125L);
		assertThat(profile.followerCount()).isEqualTo(9_876L);
		assertThat(profile.followingCount()).isEqualTo(102L);
	}

	@Test
	void keepsDisplayNameBeforeMetricsInsteadOfAddressInBiography() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"kjmbc",
				"""
				<main>
				  <header>
				    <div>kjmbc</div>
				    <div>광주MBC</div>
				    <div>게시물 3698</div>
				    <div>팔로워 8,765</div>
				    <div>팔로우 123</div>
				    <div>남구 월산로116번길 17, Gwangju 503-728</div>
				    <button>팔로우</button>
				  </header>
				</main>
				""");

		assertThat(profile.displayName()).isEqualTo("광주MBC");
		assertThat(profile.displayName()).doesNotContain("월산로");
		assertThat(profile.biographyExcerpt())
				.isEqualTo("남구 월산로116번길 17, Gwangju 503-728");
	}

	@Test
	void usesMetadataDisplayNameBeforeUsingTextAfterMetrics() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"meta_name_doctor",
				"""
				<meta property="og:title" content="Meta Name Doctor (@meta_name_doctor) • Instagram" />
				<main>
				  <header>
				    <div>meta_name_doctor</div>
				    <div>81 posts</div>
				    <div>3,554 followers</div>
				    <div>2 following</div>
				    <div>Seoul clinic biography, not a display name</div>
				    <button>Follow</button>
				  </header>
				</main>
				""");

		assertThat(profile.displayName()).isEqualTo("Meta Name Doctor");
		assertThat(profile.biographyExcerpt())
				.isEqualTo("Seoul clinic biography, not a display name");
	}

	@Test
	void leavesDisplayNameNullAndProfilePartialWhenMetricsImmediatelyFollowUsername() throws Exception {
		InstagramProfileBrowserSnapshot profile = profileFromSyntheticPage(
				"no_display_name",
				"""
				<main>
				  <header>
				    <div>no_display_name</div>
				    <div>게시물 10</div>
				    <div>팔로워 20</div>
				    <div>팔로우 3</div>
				    <div>부산광역시 해운대로 123</div>
				    <button>메시지 보내기</button>
				  </header>
				</main>
				""");

		assertThat(profile.displayName()).isNull();
		assertThat(profile.biographyExcerpt()).isEqualTo("부산광역시 해운대로 123");
		assertThat(profile.isPartial()).isTrue();
	}

	private InstagramBrowserExtractor.PostExtractionResult extractionFromSyntheticPage(
			String finalUrl, String body) throws Exception {
		Document document = parseDocument(body);
		Page page = mock(Page.class);
		when(page.url()).thenReturn(finalUrl);

		List<Element> dialogArticles = articlesWithin(document, "role", "dialog");
		List<Element> mainArticles = articlesWithin(document, "tag", "main");
		List<Element> semanticArticles = uniqueElements(dialogArticles, mainArticles);
		List<Element> mainElements = elementsByTag(document, "main");
		List<Element> roleMainElements = elementsByRole(document, "main");
		List<Element> visibleMainElements = uniqueElements(mainElements, roleMainElements);
		List<Element> articles = elementsByTag(document, "article");
		List<Element> dialogs = elementsByRole(document, "dialog");

		Map<Element, Locator> roots = new IdentityHashMap<>();
		for (Element element : uniqueElements(
				semanticArticles, visibleMainElements, articles, dialogs)) {
			roots.put(element, rootLocator(element));
		}

		Locator semanticArticleLocator = locatorCollection(
				uniqueElements(dialogArticles, mainArticles), roots);
		Locator dialogArticleLocator = locatorCollection(dialogArticles, roots);
		Locator mainArticleLocator = locatorCollection(mainArticles, roots);
		Locator mainElementLocator = locatorCollection(mainElements, roots);
		Locator roleMainLocator = locatorCollection(roleMainElements, roots);
		Locator visibleMainLocator = locatorCollection(visibleMainElements, roots);
		Locator articleLocator = locatorCollection(articles, roots);
		Locator dialogLocator = locatorCollection(dialogs, roots);
		when(page.locator("[role='dialog'] article:visible, main article:visible"))
				.thenReturn(semanticArticleLocator);
		when(page.locator("[role='dialog'] article:visible"))
				.thenReturn(dialogArticleLocator);
		when(page.locator("main article:visible"))
				.thenReturn(mainArticleLocator);
		when(page.locator("main:visible"))
				.thenReturn(mainElementLocator);
		when(page.locator("[role='main']:visible"))
				.thenReturn(roleMainLocator);
		when(page.locator("main:visible, [role='main']:visible"))
				.thenReturn(visibleMainLocator);
		when(page.locator("article:visible"))
				.thenReturn(articleLocator);
		when(page.locator("[role='dialog']:visible"))
				.thenReturn(dialogLocator);

		return extractor.extractPostWithDiagnostic(page);
	}

	private InstagramProfileBrowserSnapshot profileFromSyntheticPage(
			String expectedUsername, String body) throws Exception {
		Document document = parseDocument(body);
		Page page = mock(Page.class);

		List<Element> headers = elementsByTag(document, "header");
		List<Element> mainElements = elementsByTag(document, "main");
		List<Element> headerAnchors = headers.isEmpty()
				? List.of()
				: childElementsByTag(headers.getFirst(), "a");
		Locator headerLocator = elementCollection(headers);
		Locator mainLocator = elementCollection(mainElements);
		Locator headerAnchorLocator = elementCollection(headerAnchors);
		Locator followerAnchorLocator = elementCollection(elementsWithAttributeSuffix(
				headerAnchors, "href", "/followers/"));
		Locator followingAnchorLocator = elementCollection(elementsWithAttributeSuffix(
				headerAnchors, "href", "/following/"));
		Locator ogTitleLocator = elementCollection(metaElements(document, "property", "og:title"));
		Locator ogDescriptionLocator = elementCollection(
				metaElements(document, "property", "og:description"));
		Locator descriptionLocator = elementCollection(metaElements(document, "name", "description"));

		when(page.locator("main header")).thenReturn(headerLocator);
		when(page.locator("main")).thenReturn(mainLocator);
		when(page.locator("main header a[href]")).thenReturn(headerAnchorLocator);
		when(page.locator("main header a[href$='/followers/']")).thenReturn(followerAnchorLocator);
		when(page.locator("main header a[href$='/following/']")).thenReturn(followingAnchorLocator);
		when(page.locator("meta[property='og:title']")).thenReturn(ogTitleLocator);
		when(page.locator("meta[property='og:description']")).thenReturn(ogDescriptionLocator);
		when(page.locator("meta[name='description']")).thenReturn(descriptionLocator);

		return extractor.extractProfile(page, expectedUsername).orElseThrow();
	}

	private Locator elementCollection(List<Element> elements) {
		return locatorCollection(elements.stream().map(this::elementLocator).toList());
	}

	private Locator elementLocator(Element element) {
		Locator locator = mock(Locator.class);
		when(locator.count()).thenReturn(1);
		when(locator.first()).thenReturn(locator);
		when(locator.nth(0)).thenReturn(locator);
		when(locator.isVisible()).thenReturn(true);
		when(locator.innerText()).thenReturn(textOrNull(element));
		when(locator.getAttribute("href")).thenReturn(attributeOrNull(element, "href"));
		when(locator.getAttribute("title")).thenReturn(attributeOrNull(element, "title"));
		when(locator.getAttribute("aria-label")).thenReturn(attributeOrNull(element, "aria-label"));
		when(locator.getAttribute("content")).thenReturn(attributeOrNull(element, "content"));
		return locator;
	}

	private List<Element> childElementsByTag(Element root, String tagName) {
		List<Element> elements = new ArrayList<>();
		NodeList nodes = root.getElementsByTagName(tagName);
		for (int index = 0; index < nodes.getLength(); index++) {
			elements.add((Element) nodes.item(index));
		}
		return elements;
	}

	private List<Element> elementsWithAttributeSuffix(
			List<Element> elements, String attribute, String suffix) {
		return elements.stream()
				.filter(element -> element.getAttribute(attribute).endsWith(suffix))
				.toList();
	}

	private List<Element> metaElements(Document document, String attribute, String expectedValue) {
		return elementsByTag(document, "meta").stream()
				.filter(element -> expectedValue.equalsIgnoreCase(element.getAttribute(attribute)))
				.toList();
	}

	@SafeVarargs
	private final List<Element> uniqueElements(List<Element>... groups) {
		LinkedHashSet<Element> unique = new LinkedHashSet<>();
		for (List<Element> group : groups) {
			unique.addAll(group);
		}
		return List.copyOf(unique);
	}

	private Locator locatorCollection(List<Element> elements, Map<Element, Locator> roots) {
		List<Locator> locators = elements.stream().map(roots::get).toList();
		return locatorCollection(locators);
	}

	private Locator locatorCollection(List<Locator> locators) {
		Locator collection = mock(Locator.class);
		Locator empty = mock(Locator.class);
		when(empty.count()).thenReturn(0);
		when(empty.first()).thenReturn(empty);
		when(collection.count()).thenReturn(locators.size());
		when(collection.first()).thenReturn(locators.isEmpty() ? empty : locators.getFirst());
		for (int index = 0; index < locators.size(); index++) {
			when(collection.nth(index)).thenReturn(locators.get(index));
		}
		return collection;
	}

	private Locator rootLocator(Element rootElement) {
		Locator root = mock(Locator.class);
		when(root.count()).thenReturn(1);
		when(root.first()).thenReturn(root);
		when(root.nth(0)).thenReturn(root);
		when(root.isVisible()).thenReturn(true);
		when(root.innerText()).thenReturn(rootElement.getTextContent());

		List<Locator> links = anchorLocators(rootElement, false);
		List<Locator> semanticLinks = anchorLocators(rootElement, true);
		Locator linkCollection = locatorCollection(links);
		Locator visibleLinkCollection = locatorCollection(links);
		Locator semanticLinkCollection = locatorCollection(semanticLinks);
		when(root.locator("a[href]")).thenReturn(linkCollection);
		when(root.locator("a[href]:visible")).thenReturn(visibleLinkCollection);
		when(root.locator("header a[href], h1 a[href], h2 a[href]"))
				.thenReturn(semanticLinkCollection);
		return root;
	}

	private List<Locator> anchorLocators(Element root, boolean semanticOnly) {
		List<Locator> locators = new ArrayList<>();
		NodeList anchors = root.getElementsByTagName("a");
		for (int index = 0; index < anchors.getLength(); index++) {
			Element anchor = (Element) anchors.item(index);
			if (semanticOnly && !hasSemanticAuthorAncestor(anchor, root)) {
				continue;
			}
			Locator locator = mock(Locator.class);
			when(locator.isVisible()).thenReturn(true);
			when(locator.getAttribute("href")).thenReturn(attributeOrNull(anchor, "href"));
			when(locator.getAttribute("aria-label")).thenReturn(attributeOrNull(anchor, "aria-label"));
			when(locator.getAttribute("title")).thenReturn(attributeOrNull(anchor, "title"));
			when(locator.innerText()).thenReturn(textOrNull(anchor));
			locators.add(locator);
		}
		return locators;
	}

	private List<Element> elementsByTag(Document document, String tagName) {
		List<Element> elements = new ArrayList<>();
		NodeList nodes = document.getElementsByTagName(tagName);
		for (int index = 0; index < nodes.getLength(); index++) {
			elements.add((Element) nodes.item(index));
		}
		return elements;
	}

	private List<Element> elementsByRole(Document document, String role) {
		List<Element> elements = new ArrayList<>();
		NodeList nodes = document.getElementsByTagName("*");
		for (int index = 0; index < nodes.getLength(); index++) {
			Element element = (Element) nodes.item(index);
			if (role.equalsIgnoreCase(element.getAttribute("role"))) {
				elements.add(element);
			}
		}
		return elements;
	}

	private List<Element> articlesWithin(Document document, String ancestorKind, String expected) {
		List<Element> articles = new ArrayList<>();
		for (Element article : elementsByTag(document, "article")) {
			for (Node ancestor = article.getParentNode(); ancestor != null; ancestor = ancestor.getParentNode()) {
				if (!(ancestor instanceof Element element)) {
					continue;
				}
				boolean matches = ancestorKind.equals("tag")
						? expected.equalsIgnoreCase(element.getTagName())
						: expected.equalsIgnoreCase(element.getAttribute(ancestorKind));
				if (matches) {
					articles.add(article);
					break;
				}
			}
		}
		return articles;
	}

	private Document parseDocument(String body) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder().parse(new InputSource(
				new StringReader("<html><body>" + body + "</body></html>")));
	}

	private Optional<InstagramBrowserExtractor.InstagramProfileLink> authorFromSyntheticHtml(
			String body) throws Exception {
		Document document = parseDocument(body);

		Element article = firstSupportedArticle(document);
		if (article == null) {
			return Optional.empty();
		}

		List<InstagramBrowserExtractor.VisibleLink> articleLinks = new ArrayList<>();
		List<InstagramBrowserExtractor.VisibleLink> semanticLinks = new ArrayList<>();
		NodeList anchors = article.getElementsByTagName("a");
		for (int index = 0; index < anchors.getLength(); index++) {
			Element anchor = (Element) anchors.item(index);
			InstagramBrowserExtractor.VisibleLink link = new InstagramBrowserExtractor.VisibleLink(
					attributeOrNull(anchor, "href"),
					textOrNull(anchor),
					attributeOrNull(anchor, "aria-label"),
					attributeOrNull(anchor, "title"));
			articleLinks.add(link);
			if (hasSemanticAuthorAncestor(anchor, article)) {
				semanticLinks.add(link);
			}
		}
		return extractor.findAuthorCandidate(semanticLinks, articleLinks);
	}

	private Element firstSupportedArticle(Document document) {
		NodeList articles = document.getElementsByTagName("article");
		for (int index = 0; index < articles.getLength(); index++) {
			Element article = (Element) articles.item(index);
			for (Node ancestor = article.getParentNode(); ancestor != null; ancestor = ancestor.getParentNode()) {
				if (ancestor instanceof Element element
						&& (element.getTagName().equalsIgnoreCase("main")
								|| "dialog".equalsIgnoreCase(element.getAttribute("role")))) {
					return article;
				}
			}
		}
		return null;
	}

	private boolean hasSemanticAuthorAncestor(Element anchor, Element article) {
		for (Node ancestor = anchor.getParentNode(); ancestor != null && ancestor != article;
				ancestor = ancestor.getParentNode()) {
			if (ancestor instanceof Element element
					&& List.of("header", "h1", "h2").contains(element.getTagName().toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private Locator visibleLink(String href, String innerText) {
		Locator link = mock(Locator.class);
		when(link.isVisible()).thenReturn(true);
		when(link.getAttribute("href")).thenReturn(href);
		when(link.innerText()).thenReturn(innerText);
		return link;
	}

	private String attributeOrNull(Element element, String name) {
		String value = element.getAttribute(name);
		return value.isBlank() ? null : value;
	}

	private String textOrNull(Element element) {
		String value = element.getTextContent().strip();
		return value.isBlank() ? null : value;
	}
}
