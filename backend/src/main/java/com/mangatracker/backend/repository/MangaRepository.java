package com.mangatracker.backend.repository;

import com.mangatracker.backend.model.Manga;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface MangaRepository extends JpaRepository<Manga, UUID> {

  List<Manga> findAllByOrderByUpdatedAtDesc();

  /** Owner-scoped listing: only the given user's manga, newest-updated first. */
  List<Manga> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

  /** Owner-scoped fetch: returns the manga only if it belongs to the given user. */
  Optional<Manga> findByIdAndOwnerId(UUID id, UUID ownerId);

  /** Owner-scoped duplicate check: a user may track a MangaDex title another user tracks. */
  boolean existsByMangadexIdAndOwnerId(UUID mangadexId, UUID ownerId);

  /** Owner-scoped count. Used by the demo startup seed to fill an empty demo library. */
  long countByOwnerId(UUID ownerId);

  /** Deletes every manga owned by the given user. Used by the demo nightly reset. */
  @Modifying
  void deleteByOwnerId(UUID ownerId);
}
