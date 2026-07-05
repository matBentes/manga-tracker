package com.mangatracker.backend.service;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record MangaDexManga(
    @Schema(description = "MangaDex manga identifier") UUID mangaDexId,
    @Schema(description = "Localized title, preferring English") String title,
    @Schema(description = "Localized synopsis, preferring English", nullable = true)
        String description,
    @Schema(description = "MangaDex cover thumbnail URL", nullable = true) String coverImageUrl) {}
