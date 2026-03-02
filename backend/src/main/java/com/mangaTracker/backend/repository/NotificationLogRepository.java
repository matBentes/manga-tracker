package com.mangaTracker.backend.repository;

import com.mangaTracker.backend.model.NotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

  boolean existsByMangaIdAndChapterNumber(UUID mangaId, int chapterNumber);
}
