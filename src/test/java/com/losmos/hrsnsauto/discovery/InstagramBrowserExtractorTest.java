package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
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

		when(page.locator("[role='dialog'] article:visible, main article:visible"))
				.thenReturn(containers);
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
		assertThat(containerWait.getValue().timeout).isEqualTo(4_000);

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
						true, 30, 5, List.of("one", "two", "three", "four"));

		assertThat(diagnostic.compactSummary())
				.isEqualTo("postContainer=found, articleLinks=30, profileLinks=5, candidates=one,two,three");
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
	}

	private Optional<InstagramBrowserExtractor.InstagramProfileLink> authorFromSyntheticHtml(
			String body) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setExpandEntityReferences(false);
		Document document = factory.newDocumentBuilder().parse(new InputSource(
				new StringReader("<html><body>" + body + "</body></html>")));

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
