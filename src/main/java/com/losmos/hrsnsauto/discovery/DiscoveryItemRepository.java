package com.losmos.hrsnsauto.discovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryItemRepository extends JpaRepository<DiscoveryItem, Long> {

	@EntityGraph(attributePaths = { "hashtags", "browserObservation" })
	List<DiscoveryItem> findAllByOrderByPublishedAtDescIdDesc();

	@EntityGraph(attributePaths = { "hashtags", "browserObservation" })
	List<DiscoveryItem> findAllByReviewStatusOrderByPublishedAtDescIdDesc(DiscoveryReviewStatus reviewStatus);

	@EntityGraph(attributePaths = { "hashtags", "browserObservation" })
	Optional<DiscoveryItem> findOneByInstagramMediaId(String instagramMediaId);

	List<DiscoveryItem> findByReviewStatusAndBrowserObservationIsNullOrderByPublishedAtDescIdDesc(
			DiscoveryReviewStatus reviewStatus, Pageable pageable);

	long countByReviewStatus(DiscoveryReviewStatus reviewStatus);
}
