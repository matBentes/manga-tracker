package com.mangaTracker.backend.repository;

import com.mangaTracker.backend.model.PushSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

  Optional<PushSubscription> findByEndpoint(String endpoint);

  void deleteByEndpoint(String endpoint);

  boolean existsByEndpoint(String endpoint);
}
