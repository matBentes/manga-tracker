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

  public AppSettings updateSettings(Integer pollIntervalMinutes) {
    AppSettings settings = getSettings();
    if (pollIntervalMinutes != null) {
      if (pollIntervalMinutes <= 0) {
        throw new IllegalArgumentException("pollIntervalMinutes must be a positive integer");
      }
      settings.setPollIntervalMinutes(pollIntervalMinutes);
    }
    return appSettingsRepository.save(settings);
  }

  private AppSettings createDefaultSettings() {
    AppSettings defaults = AppSettings.builder().id(SETTINGS_ID).pollIntervalMinutes(30).build();
    return appSettingsRepository.save(defaults);
  }
}
