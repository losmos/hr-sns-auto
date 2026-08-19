package com.losmos.hrsnsauto.discovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryItemRepository extends JpaRepository<DiscoveryItem, Long> {

	@EntityGraph(attributePaths = "hashtags")
	List<DiscoveryItem> findAllByOrderByPublishedAtDescIdDesc();

	@EntityGraph(attributePaths = "hashtags")
	List<DiscoveryItem> findAllByReviewStatusOrderByPublishedAtDescIdDesc(DiscoveryReviewStatus reviewStatus);

	@EntityGraph(attributePaths = "hashtags")
	Optional<DiscoveryItem> findOneByInstagramMediaId(String instagramMediaId);

	long countByReviewStatus(DiscoveryReviewStatus reviewStatus);
}
