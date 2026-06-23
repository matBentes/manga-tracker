package com.mangaTracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.model.AppSettings;
import com.mangaTracker.backend.repository.AppSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

  @Mock private AppSettingsRepository appSettingsRepository;

  @InjectMocks private SettingsService settingsService;

  @Test
  void getSettings_returnsExistingSettings() {
    AppSettings existing = buildSettings(30);
    when(appSettingsRepository.findById(1)).thenReturn(Optional.of(existing));

    AppSettings result = settingsService.getSettings();

    assertThat(result).isSameAs(existing);
  }

  @Test
  void getSettings_createsDefaultSettings_whenNotFound() {
    when(appSettingsRepository.findById(1)).thenReturn(Optional.empty());
    when(appSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    AppSettings result = settingsService.getSettings();

    assertThat(result.getId()).isEqualTo(1);
    assertThat(result.getPollIntervalMinutes()).isEqualTo(30);
    verify(appSettingsRepository).save(any(AppSettings.class));
  }

  @Test
  void updateSettings_updatesPollIntervalMinutes() {
    AppSettings settings = buildSettings(30);
    when(appSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
    when(appSettingsRepository.save(settings)).thenReturn(settings);

    AppSettings result = settingsService.updateSettings(60);

    assertThat(result.getPollIntervalMinutes()).isEqualTo(60);
  }

  @Test
  void updateSettings_throwsIllegalArgument_whenPollIntervalNotPositive() {
    AppSettings settings = buildSettings(30);
    when(appSettingsRepository.findById(1)).thenReturn(Optional.of(settings));

    assertThatThrownBy(() -> settingsService.updateSettings(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pollIntervalMinutes");
  }

  @Test
  void updateSettings_noOp_whenParamNull() {
    AppSettings settings = buildSettings(30);
    when(appSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
    when(appSettingsRepository.save(settings)).thenReturn(settings);

    AppSettings result = settingsService.updateSettings(null);

    assertThat(result).isSameAs(settings);
  }

  private static AppSettings buildSettings(int pollInterval) {
    return AppSettings.builder().id(1).pollIntervalMinutes(pollInterval).build();
  }
}
