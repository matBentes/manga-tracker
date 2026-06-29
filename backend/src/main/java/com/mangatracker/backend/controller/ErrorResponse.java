package com.mangatracker.backend.controller;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Shared error response body returned by the API")
public record ErrorResponse(@Schema(description = "Human-readable error message") String error) {}
