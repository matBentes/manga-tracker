package com.mangaTracker.backend.service;

import com.mangaTracker.backend.model.AppSettings;
import com.mangaTracker.backend.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {

  private static final int SETTINGS_ID = 1;

  private final AppSettingsRepository appSettingsRepository;

  public SettingsService(AppSettingsRepository appSettingsRepository) {
    this.appSettingsRepository = appSettingsRepository;
  }

  public AppSettings getSettings() {
    return appSettingsRepository.findById(SETTINGS_ID).orElseGet(this::createDefaultSettings);
  }

  public AppSettings updateSettings(
      Boolean emailNotificationsEnabled, String notificationEmail, Integer pollIntervalMinutes) {
    AppSettings settings = getSettings();
    if (emailNotificationsEnabled != null) {
      settings.setEmailNotificationsEnabled(emailNotificationsEnabled);
    }
    if (notificationEmail != null) {
      if (notificationEmail.isBlank()) {
        throw new IllegalArgumentException("notificationEmail must not be blank");
      }
      settings.setNotificationEmail(notificationEmail);
    }
    if (pollIntervalMinutes != null) {
      if (pollIntervalMinutes <= 0) {
        throw new IllegalArgumentException("pollIntervalMinutes must be a positive integer");
      }
      settings.setPollIntervalMinutes(pollIntervalMinutes);
    }
    return appSettingsRepository.save(settings);
  }

  private AppSettings createDefaultSettings() {
    AppSettings defaults =
        AppSettings.builder()
            .id(SETTINGS_ID)
            .emailNotificationsEnabled(true)
            .notificationEmail("user@localhost")
            .pollIntervalMinutes(30)
            .build();
    return appSettingsRepository.save(defaults);
  }
}
