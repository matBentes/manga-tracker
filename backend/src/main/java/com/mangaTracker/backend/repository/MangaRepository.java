package com.mangaTracker.backend.repository;

import com.mangaTracker.backend.model.Manga;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MangaRepository extends JpaRepository<Manga, UUID> {

  List<Manga> findAllByOrderByUpdatedAtDesc();

  boolean existsBySourceUrl(String sourceUrl);
}
