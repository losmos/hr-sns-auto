package com.losmos.hrsnsauto.discovery;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiscoveryService {

	private static final int HASHTAG_MAX_LENGTH = 100;
	private static final Comparator<DiscoveryItem> INBOX_ORDER = Comparator
			.comparingInt((DiscoveryItem item) -> reviewOrder(item.getReviewStatus()))
			.thenComparing(DiscoveryItem::getPublishedAt, Comparator.reverseOrder())
			.thenComparing(DiscoveryItem::getId, Comparator.nullsLast(Comparator.reverseOrder()));

	private final DiscoveryHashtagRepository hashtagRepository;
	private final DiscoveryItemRepository itemRepository;
	private final MetaInstagramClient metaInstagramClient;
	private final Clock clock;

	@Autowired
	public DiscoveryService(DiscoveryHashtagRepository hashtagRepository,
			DiscoveryItemRepository itemRepository, MetaInstagramClient metaInstagramClient) {
		this(hashtagRepository, itemRepository, metaInstagramClient, Clock.systemUTC());
	}

	DiscoveryService(DiscoveryHashtagRepository hashtagRepository,
			DiscoveryItemRepository itemRepository, MetaInstagramClient metaInstagramClient, Clock clock) {
		this.hashtagRepository = hashtagRepository;
		this.itemRepository = itemRepository;
		this.metaInstagramClient = metaInstagramClient;
		this.clock = clock;
	}

	public List<DiscoveryHashtag> findAllHashtags() {
		return hashtagRepository.findAllByOrderByEnabledDescCreatedAtAscIdAsc();
	}

	public List<DiscoveryItem> findItems(DiscoveryReviewStatus reviewStatus) {
		if (reviewStatus != null) {
			return itemRepository.findAllByReviewStatusOrderByPublishedAtDescIdDesc(reviewStatus);
		}
		List<DiscoveryItem> items = new ArrayList<>(itemRepository.findAllByOrderByPublishedAtDescIdDesc());
		items.sort(INBOX_ORDER);
		return items;
	}

	public DiscoveryInboxCounts getInboxCounts() {
		return new DiscoveryInboxCounts(
				itemRepository.countByReviewStatus(DiscoveryReviewStatus.NEW),
				itemRepository.countByReviewStatus(DiscoveryReviewStatus.OPENED),
				itemRepository.countByReviewStatus(DiscoveryReviewStatus.DISMISSED));
	}

	@Transactional
	public DiscoveryHashtag addHashtag(String input) {
		String normalizedKeyword = normalizeHashtag(input);
		if (hashtagRepository.existsByKeyword(normalizedKeyword)) {
			throw new DuplicateDiscoveryHashtagException(normalizedKeyword);
		}

		try {
			// 애플리케이션 검사 이후의 동시 등록도 DB unique 제약으로 막고 동일한 form 오류로 반환한다.
			return hashtagRepository.saveAndFlush(new DiscoveryHashtag(normalizedKeyword));
		}
		catch (DataIntegrityViolationException exception) {
			throw new DuplicateDiscoveryHashtagException(normalizedKeyword, exception);
		}
	}

	@Transactional
	public void setHashtagEnabled(Long hashtagId, boolean enabled) {
		DiscoveryHashtag hashtag = hashtagRepository.findById(hashtagId)
				.orElseThrow(() -> new DiscoveryHashtagNotFoundException(hashtagId));
		hashtag.setEnabled(enabled);
		hashtagRepository.save(hashtag);
	}

	@Transactional
	public DiscoverySyncResult syncRecentMedia() {
		// 설정 검사는 network와 DB 변경 전에 한 번 수행해 config 오류가 partial sync를 만들지 않게 한다.
		metaInstagramClient.validateConfiguration();
		List<DiscoveryHashtag> enabledHashtags = hashtagRepository.findAllByEnabledTrueOrderByIdAsc();
		List<HashtagSyncResult> results = new ArrayList<>();
		Instant observedAt = clock.instant();

		for (DiscoveryHashtag hashtag : enabledHashtags) {
			try {
				String hashtagId = metaInstagramClient.lookupHashtagId(hashtag.getKeyword());
				List<InstagramMedia> recentMedia = metaInstagramClient.getRecentMedia(hashtagId);
				int createdCount = storeObservations(hashtag, recentMedia, observedAt);
				// hashtag 단위 flush로 성공 결과의 제약 위반을 성공으로 잘못 보고하지 않는다.
				itemRepository.flush();
				results.add(HashtagSyncResult.success(hashtag.getKeyword(), recentMedia.size(), createdCount));
			}
			catch (MetaInstagramException exception) {
				// client exception message는 token과 raw payload가 제거된 운영자용 정보만 담는다.
				results.add(HashtagSyncResult.failure(hashtag.getKeyword(), exception.getMessage()));
			}
		}
		return new DiscoverySyncResult(results);
	}

	@Transactional
	public URI markOpened(Long itemId) {
		DiscoveryItem item = getItem(itemId);
		item.markOpened();
		itemRepository.save(item);
		return URI.create(item.getPermalink());
	}

	@Transactional
	public void dismiss(Long itemId) {
		DiscoveryItem item = getItem(itemId);
		item.dismiss();
		itemRepository.save(item);
	}

	private int storeObservations(DiscoveryHashtag hashtag, List<InstagramMedia> mediaList, Instant observedAt) {
		int createdCount = 0;
		for (InstagramMedia media : mediaList) {
			DiscoveryItem item = itemRepository.findOneByInstagramMediaId(media.mediaId()).orElse(null);
			if (item == null) {
				item = new DiscoveryItem(media, hashtag, observedAt);
				createdCount++;
			}
			else {
				item.observe(media, hashtag, observedAt);
			}
			itemRepository.save(item);
		}
		return createdCount;
	}

	private DiscoveryItem getItem(Long itemId) {
		return itemRepository.findById(itemId)
				.orElseThrow(() -> new DiscoveryItemNotFoundException(itemId));
	}

	private String normalizeHashtag(String input) {
		if (input == null) {
			throw new InvalidDiscoveryHashtagException("hashtag를 입력한다");
		}
		String normalized = input.strip();
		while (normalized.startsWith("#")) {
			normalized = normalized.substring(1).stripLeading();
		}
		normalized = normalized.strip().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			throw new InvalidDiscoveryHashtagException("# 기호를 제외한 hashtag를 입력한다");
		}
		if (normalized.length() > HASHTAG_MAX_LENGTH) {
			throw new InvalidDiscoveryHashtagException("hashtag는 100자 이내로 입력한다");
		}
		return normalized;
	}

	private static int reviewOrder(DiscoveryReviewStatus reviewStatus) {
		return switch (reviewStatus) {
			case NEW -> 0;
			case OPENED -> 1;
			case DISMISSED -> 2;
		};
	}
}
