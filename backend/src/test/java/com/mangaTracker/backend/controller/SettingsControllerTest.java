package com.mangaTracker.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangaTracker.backend.model.AppSettings;
import com.mangaTracker.backend.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SettingsService settingsService;

  @Test
  void getSettings_returns200WithSettings() throws Exception {
    AppSettings settings = buildSettings();
    when(settingsService.getSettings()).thenReturn(settings);

    mockMvc
        .perform(get("/api/settings").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationEmail").value("user@example.com"))
        .andExpect(jsonPath("$.pollIntervalMinutes").value(30))
        .andExpect(jsonPath("$.emailNotificationsEnabled").value(true));
  }

  @Test
  void updateSettings_returns200_onValidRequest() throws Exception {
    AppSettings updated = buildSettings();
    when(settingsService.updateSettings(any(), any(), any())).thenReturn(updated);

    mockMvc
        .perform(
            put("/api/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"emailNotificationsEnabled\":true,"
                        + "\"notificationEmail\":\"user@example.com\","
                        + "\"pollIntervalMinutes\":30}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationEmail").value("user@example.com"));
  }

  @Test
  void updateSettings_returns400_onInvalidInput() throws Exception {
    when(settingsService.updateSettings(any(), any(), any()))
        .thenThrow(new IllegalArgumentException("pollIntervalMinutes must be > 0"));

    mockMvc
        .perform(
            put("/api/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"emailNotificationsEnabled\":true,"
                        + "\"notificationEmail\":\"user@example.com\","
                        + "\"pollIntervalMinutes\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  private static AppSettings buildSettings() {
    return AppSettings.builder()
        .id(1)
        .emailNotificationsEnabled(true)
        .notificationEmail("user@example.com")
        .pollIntervalMinutes(30)
        .build();
  }
}
