package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class InstagramBrowserEnrichmentServiceTest {

	@Mock
	private InstagramBrowserClient browserClient;

	@Mock
	private DiscoveryItemRepository itemRepository;

	@Mock
	private DiscoveryBrowserObservationRepository observationRepository;

	private InstagramBrowserProperties properties;
	private InstagramBrowserEnrichmentService service;

	@BeforeEach
	void setUp() {
		properties = new InstagramBrowserProperties();
		service = new InstagramBrowserEnrichmentService(
				properties,
				browserClient,
				itemRepository,
				observationRepository,
				Clock.fixed(Instant.parse("2026-08-19T12:34:56Z"), ZoneOffset.UTC));
	}

	@Test
	void disabledAutomationRejectsOperatorActionWithoutOpeningBrowser() {
		assertThatThrownBy(() -> service.enrichItem(1L))
				.isInstanceOf(InstagramBrowserOperationException.class)
				.hasMessageContaining("비활성");

		verifyNoInteractions(browserClient, itemRepository, observationRepository);
	}

	@Test
	void batchUsesConfiguredLimitProcessesSequentiallyAndPersistsEachOutcome() {
		properties.setAutomationEnabled(true);
		properties.setBatchSize(2);
		DiscoveryItem first = item(1L, "https://www.instagram.com/p/newer/");
		DiscoveryItem second = item(2L, "https://www.instagram.com/reel/older/");
		when(itemRepository.findByReviewStatusAndBrowserObservationIsNullOrderByPublishedAtDescIdDesc(
				any(DiscoveryReviewStatus.class), any(Pageable.class)))
				.thenReturn(List.of(first, second));
		when(observationRepository.findOneByDiscoveryItemId(1L)).thenReturn(Optional.empty());
		when(observationRepository.findOneByDiscoveryItemId(2L)).thenReturn(Optional.empty());
		when(observationRepository.saveAndFlush(any(DiscoveryBrowserObservation.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		InstagramBrowserEnrichmentResult firstResult = successResult(false);
		InstagramBrowserEnrichmentResult secondResult = InstagramBrowserEnrichmentResult.failure(
				DiscoveryBrowserObservationStatus.FAILED,
				InstagramBrowserErrorCode.POST_UNAVAILABLE,
				"Instagram 게시물을 사용할 수 없거나 삭제됨");
		when(browserClient.enrich(first.getPermalink())).thenReturn(firstResult);
		when(browserClient.enrich(second.getPermalink())).thenReturn(secondResult);

		BrowserEnrichmentBatchResult result = service.enrichNewBatch();

		assertThat(result.getRequestedCount()).isEqualTo(2);
		assertThat(result.getProcessedCount()).isEqualTo(2);
		assertThat(result.getSuccessCount()).isEqualTo(1);
		assertThat(result.getFailedCount()).isEqualTo(1);
		ArgumentCaptor<DiscoveryReviewStatus> status = ArgumentCaptor.forClass(DiscoveryReviewStatus.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(itemRepository)
				.findByReviewStatusAndBrowserObservationIsNullOrderByPublishedAtDescIdDesc(
						status.capture(), pageable.capture());
		assertThat(status.getValue()).isEqualTo(DiscoveryReviewStatus.NEW);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
		InOrder callOrder = inOrder(browserClient);
		callOrder.verify(browserClient).enrich(first.getPermalink());
		callOrder.verify(browserClient).enrich(second.getPermalink());
		verify(observationRepository, org.mockito.Mockito.times(2))
				.saveAndFlush(any(DiscoveryBrowserObservation.class));
	}

	@Test
	void batchStopsAfterLoginRequiredWithoutTouchingRemainingItem() {
		properties.setAutomationEnabled(true);
		DiscoveryItem first = item(1L, "https://www.instagram.com/p/newer/");
		DiscoveryItem second = org.mockito.Mockito.mock(DiscoveryItem.class);
		when(second.getPermalink()).thenReturn("https://www.instagram.com/p/older/");
		when(itemRepository.findByReviewStatusAndBrowserObservationIsNullOrderByPublishedAtDescIdDesc(
				any(DiscoveryReviewStatus.class), any(Pageable.class)))
				.thenReturn(List.of(first, second));
		when(observationRepository.findOneByDiscoveryItemId(1L)).thenReturn(Optional.empty());
		when(observationRepository.saveAndFlush(any(DiscoveryBrowserObservation.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(browserClient.enrich(first.getPermalink())).thenReturn(InstagramBrowserEnrichmentResult.failure(
				DiscoveryBrowserObservationStatus.LOGIN_REQUIRED,
				InstagramBrowserErrorCode.LOGIN_REQUIRED,
				"Instagram 로그인이 필요함"));

		BrowserEnrichmentBatchResult result = service.enrichNewBatch();

		assertThat(result.getProcessedCount()).isEqualTo(1);
		assertThat(result.isStoppedForOperatorAction()).isTrue();
		verify(browserClient, never()).enrich(second.getPermalink());
	}

	private InstagramBrowserEnrichmentResult successResult(boolean partial) {
		return InstagramBrowserEnrichmentResult.success(
				new InstagramPostBrowserSnapshot(
						"doctor.one", "https://www.instagram.com/doctor.one/", null, null, null),
				new InstagramProfileBrowserSnapshot(
						partial ? null : "닥터 원", 100L, 50L, 20L, null, false, false));
	}

	private DiscoveryItem item(Long id, String permalink) {
		DiscoveryItem item = org.mockito.Mockito.mock(DiscoveryItem.class);
		when(item.getId()).thenReturn(id);
		when(item.getPermalink()).thenReturn(permalink);
		return item;
	}
}
