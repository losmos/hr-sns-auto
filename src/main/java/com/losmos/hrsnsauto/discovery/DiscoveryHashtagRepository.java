package com.losmos.hrsnsauto.discovery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryHashtagRepository extends JpaRepository<DiscoveryHashtag, Long> {

	List<DiscoveryHashtag> findAllByOrderByEnabledDescCreatedAtAscIdAsc();

	List<DiscoveryHashtag> findAllByEnabledTrueOrderByIdAsc();

	boolean existsByKeyword(String keyword);
}
