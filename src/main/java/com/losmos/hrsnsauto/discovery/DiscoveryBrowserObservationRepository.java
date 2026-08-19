package com.losmos.hrsnsauto.discovery;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryBrowserObservationRepository extends JpaRepository<DiscoveryBrowserObservation, Long> {

	Optional<DiscoveryBrowserObservation> findOneByDiscoveryItemId(Long discoveryItemId);
}
