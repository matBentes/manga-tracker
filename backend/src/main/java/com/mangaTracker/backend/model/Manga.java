package com.mangaTracker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "manga")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Manga {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String title;

  @Column(name = "source_url", nullable = false, unique = true)
  private String sourceUrl;

  @Column(name = "current_chapter", nullable = false)
  private int currentChapter;

  @Column(name = "latest_chapter", nullable = false)
  private int latestChapter;

  @Column(name = "notifications_enabled", nullable = false)
  private boolean notificationsEnabled;

  @Column(name = "last_checked_at")
  private LocalDateTime lastCheckedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
