package com.mangaTracker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSettings {

  @Id
  private Integer id;

  @Column(name = "email_notifications_enabled", nullable = false)
  private boolean emailNotificationsEnabled;

  @Column(name = "notification_email", nullable = false)
  private String notificationEmail;

  @Column(name = "poll_interval_minutes", nullable = false)
  private int pollIntervalMinutes;
}
