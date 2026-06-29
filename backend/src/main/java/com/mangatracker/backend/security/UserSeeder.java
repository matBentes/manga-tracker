package com.mangatracker.backend.security;

import com.mangatracker.backend.model.AppUser;
import com.mangatracker.backend.model.Role;
import com.mangatracker.backend.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the two fixed accounts (owner, demo) on startup from environment-provided passwords. Skips
 * an account (with a warning) when its password env var is unset, so the app still boots in dev
 * without secrets. Passwords are BCrypt-hashed; raw values are never logged. Existing accounts are
 * left untouched (idempotent — no password rotation here).
 */
@Component
@Order(0) // must run before DemoResetJob (@Order(1)) so the demo account exists for library seeding
public class UserSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(UserSeeder.class);

  private static final String OWNER_USERNAME = "owner";
  private static final String DEMO_USERNAME = "demo";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final String ownerPassword;
  private final String demoPassword;

  public UserSeeder(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.auth.owner-password:}") String ownerPassword,
      @Value("${app.auth.demo-password:}") String demoPassword) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.ownerPassword = ownerPassword;
    this.demoPassword = demoPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    seed(OWNER_USERNAME, ownerPassword, Role.OWNER);
    seed(DEMO_USERNAME, demoPassword, Role.DEMO);
  }

  private void seed(String username, String rawPassword, Role role) {
    if (rawPassword == null || rawPassword.isBlank()) {
      LOG.warn(
          "Skipping seed of '{}' account: password env var is not set. Set it to enable this login.",
          username);
      return;
    }
    if (appUserRepository.findByUsername(username).isPresent()) {
      LOG.info("Account '{}' already exists; leaving it unchanged.", username);
      return;
    }
    AppUser user =
        AppUser.builder()
            .username(username)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .build();
    appUserRepository.save(user);
    LOG.info("Seeded '{}' account with role {}.", username, role);
  }
}
