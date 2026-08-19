package com.losmos.hrsnsauto.discovery;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class InstagramBrowserEnrichmentService {

	private final InstagramBrowserProperties properties;
	private final InstagramBrowserClient browserClient;
	private final DiscoveryItemRepository itemRepository;
	private final DiscoveryBrowserObservationRepository observationRepository;
	private final Clock clock;
	private final ReentrantLock executionLock = new ReentrantLock(true);

	@Autowired
	public InstagramBrowserEnrichmentService(InstagramBrowserProperties properties,
			InstagramBrowserClient browserClient,
			DiscoveryItemRepository itemRepository,
			DiscoveryBrowserObservationRepository observationRepository) {
		this(properties, browserClient, itemRepository, observationRepository, Clock.systemUTC());
	}

	InstagramBrowserEnrichmentService(InstagramBrowserProperties properties,
			InstagramBrowserClient browserClient,
			DiscoveryItemRepository itemRepository,
			DiscoveryBrowserObservationRepository observationRepository,
			Clock clock) {
		this.properties = properties;
		this.browserClient = browserClient;
		this.itemRepository = itemRepository;
		this.observationRepository = observationRepository;
		this.clock = clock;
	}

	public boolean isEnabled() {
		return properties.isAutomationEnabled();
	}

	public int getDisplayBatchSize() {
		return properties.displayBatchSize();
	}

	public InstagramBrowserSessionResult prepareSession() {
		properties.requireEnabled();
		lockOrThrow();
		try {
			return browserClient.openSession();
		}
		finally {
			executionLock.unlock();
		}
	}

	public BrowserEnrichmentItemResult enrichItem(Long itemId) {
		properties.requireEnabled();
		lockOrThrow();
		try {
			DiscoveryItem item = itemRepository.findById(itemId)
					.orElseThrow(() -> new DiscoveryItemNotFoundException(itemId));
			try {
				return processItem(item);
			}
			catch (RuntimeException exception) {
				throw new InstagramBrowserOperationException(
						InstagramBrowserErrorCode.PERSISTENCE_FAILED,
						"browser observation 저장에 실패함");
			}
		}
		finally {
			executionLock.unlock();
		}
	}

	public BrowserEnrichmentBatchResult enrichNewBatch() {
		properties.requireEnabled();
		int batchSize = properties.validatedBatchSize();
		lockOrThrow();
		try {
			List<DiscoveryItem> items = itemRepository
					.findByReviewStatusAndBrowserObservationIsNullOrderByPublishedAtDescIdDesc(
							DiscoveryReviewStatus.NEW, PageRequest.of(0, batchSize));
			List<BrowserEnrichmentItemResult> results = new ArrayList<>();
			for (DiscoveryItem item : items) {
				BrowserEnrichmentItemResult result;
				try {
					result = processItem(item);
				}
				catch (RuntimeException exception) {
					// 각 save는 독립 repository transaction이므로 앞선 item의 성공 결과는 유지된다.
					result = BrowserEnrichmentItemResult.persistenceFailure(item.getId());
				}
				results.add(result);
				if (result.requiresBatchStop()) {
					// 로그인·challenge·browser 준비 오류는 반복 navigation 없이 즉시 운영자에게 넘긴다.
					break;
				}
			}
			return new BrowserEnrichmentBatchResult(batchSize, results);
		}
		finally {
			executionLock.unlock();
		}
	}

	private BrowserEnrichmentItemResult processItem(DiscoveryItem item) {
		InstagramBrowserEnrichmentResult result;
		try {
			result = browserClient.enrich(item.getPermalink());
		}
		catch (RuntimeException exception) {
			// 예외 전문은 page/session 내용을 포함할 수 있어 저장하지 않고 고정된 원인만 남긴다.
			result = InstagramBrowserEnrichmentResult.failure(
					DiscoveryBrowserObservationStatus.FAILED,
					InstagramBrowserErrorCode.UNEXPECTED_DOM,
					"브라우저 경계에서 예상하지 못한 오류가 발생함");
		}

		DiscoveryBrowserObservation observation = observationRepository
				.findOneByDiscoveryItemId(item.getId())
				.orElseGet(() -> new DiscoveryBrowserObservation(item));
		observation.replaceWith(result, clock.instant());
		observationRepository.saveAndFlush(observation);
		return BrowserEnrichmentItemResult.from(item.getId(), result);
	}

	private void lockOrThrow() {
		if (!executionLock.tryLock()) {
			throw new InstagramBrowserOperationException(
					InstagramBrowserErrorCode.BROWSER_BUSY,
					"다른 Instagram 브라우저 보강 작업이 실행 중임");
		}
	}
}
