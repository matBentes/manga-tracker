package com.mangaTracker.backend.security;

import com.mangaTracker.backend.model.Role;
import java.util.UUID;

/**
 * Immutable principal carried in the Spring Security context after a valid JWT cookie is verified.
 * Holds only what authorization and ownership scoping need: the user id and role.
 */
public record AuthenticatedUser(UUID userId, Role role) {}
