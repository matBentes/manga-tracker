package com.mangaTracker.backend.controller;

import com.mangaTracker.backend.model.AppSettings;
import com.mangaTracker.backend.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

  private final SettingsService settingsService;

  public SettingsController(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @GetMapping
  public AppSettings getSettings() {
    return settingsService.getSettings();
  }

  @PutMapping
  public AppSettings updateSettings(@RequestBody UpdateSettingsRequest request) {
    return settingsService.updateSettings(
        request.emailNotificationsEnabled(),
        request.notificationEmail(),
        request.pollIntervalMinutes());
  }

  record UpdateSettingsRequest(
      Boolean emailNotificationsEnabled, String notificationEmail, Integer pollIntervalMinutes) {}
}
