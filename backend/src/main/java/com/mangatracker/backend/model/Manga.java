package com.mangatracker.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
  @Schema(description = "Title scraped from the source page")
  private String title;

  @Column(name = "source_url", nullable = false)
  @Schema(description = "Original source URL used for scraping and duplicate detection")
  private String sourceUrl;

  @Column(name = "current_chapter", nullable = false)
  @Schema(description = "Chapter the user has read up to")
  private int currentChapter;

  @Column(name = "latest_chapter", nullable = false)
  @Schema(description = "Latest chapter found by the scraper")
  private int latestChapter;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  @Schema(description = "Cover image URL or data URL scraped from the source page", nullable = true)
  private String coverImageUrl;

  @Column(name = "latest_chapter_at")
  @Schema(description = "Timestamp when the latest chapter was first observed", nullable = true)
  private LocalDateTime latestChapterAt;

  @Column(name = "notifications_enabled", nullable = false)
  @Schema(description = "Whether new chapter notifications are enabled for this manga")
  private boolean notificationsEnabled;

  @Column(name = "last_checked_at")
  @Schema(description = "Timestamp of the latest scrape attempt", nullable = true)
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
