package com.mangatracker.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
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
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "manga")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Manga {

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Schema(description = "Assigned manga identifier")
  private UUID id;

  @Column(nullable = false)
  @Schema(description = "Manga title")
  private String title;

  @Column(name = "source_url")
  @Schema(description = "Optional read-here URL", nullable = true)
  private String sourceUrl;

  @Column(name = "mangadex_id")
  @Schema(description = "MangaDex manga identifier used for metadata and duplicate detection")
  private UUID mangadexId;

  @Column(name = "current_chapter", nullable = false)
  @Schema(description = "Chapter the user has read up to")
  private int currentChapter;

  @Column(name = "latest_chapter", nullable = false)
  @Schema(description = "Latest known English chapter")
  private int latestChapter;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  @Schema(description = "Cover image URL or data URL", nullable = true)
  private String coverImageUrl;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "reading_status", nullable = false)
  @Schema(description = "User reading status")
  private ReadingStatus readingStatus = ReadingStatus.READING;

  @Column(name = "latest_chapter_at")
  @Schema(description = "Timestamp when the latest chapter was first observed", nullable = true)
  private LocalDateTime latestChapterAt;

  @Column(name = "notifications_enabled", nullable = false)
  @Schema(description = "Whether new chapter notifications are enabled for this manga")
  private boolean notificationsEnabled;

  @Column(name = "last_checked_at")
  @Schema(description = "Timestamp of the latest metadata refresh attempt", nullable = true)
  private LocalDateTime lastCheckedAt;

  @Column(name = "owner_id")
  @Schema(description = "Authenticated owner identifier", nullable = true)
  private UUID ownerId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  @Schema(description = "Creation timestamp")
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  @Schema(description = "Last update timestamp")
  private LocalDateTime updatedAt;
}
