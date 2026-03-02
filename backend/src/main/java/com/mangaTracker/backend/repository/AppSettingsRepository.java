package com.mangaTracker.backend.repository;

import com.mangaTracker.backend.model.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, Integer> {
}
