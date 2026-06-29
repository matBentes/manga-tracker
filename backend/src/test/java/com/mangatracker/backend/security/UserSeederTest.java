package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangatracker.backend.model.AppUser;
import com.mangatracker.backend.model.Role;
import com.mangatracker.backend.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

  @Mock private AppUserRepository appUserRepository;

  @Test
  void run_seedsBothAccounts_withBcryptHashes_whenPasswordsProvided() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    when(appUserRepository.findByUsername(any())).thenReturn(Optional.empty());
    UserSeeder seeder = new UserSeeder(appUserRepository, encoder, "owner-pw", "demo-pw");

    seeder.run(null);

    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    verify(appUserRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    AppUser owner =
        captor.getAllValues().stream()
            .filter(u -> u.getRole() == Role.OWNER)
            .findFirst()
            .orElseThrow();
    AppUser demo =
        captor.getAllValues().stream()
            .filter(u -> u.getRole() == Role.DEMO)
            .findFirst()
            .orElseThrow();
    assertThat(owner.getUsername()).isEqualTo("owner");
    assertThat(demo.getUsername()).isEqualTo("demo");
    // Hash is BCrypt, not the raw password.
    assertThat(owner.getPasswordHash()).isNotEqualTo("owner-pw").startsWith("$2");
    assertThat(encoder.matches("owner-pw", owner.getPasswordHash())).isTrue();
  }

  @Test
  void run_skipsAccount_whenPasswordBlank() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    when(appUserRepository.findByUsername("owner")).thenReturn(Optional.empty());
    UserSeeder seeder = new UserSeeder(appUserRepository, encoder, "owner-pw", "  ");

    seeder.run(null);

    // Only owner saved; demo skipped because its password is blank.
    verify(appUserRepository, org.mockito.Mockito.times(1)).save(any());
  }

  @Test
  void run_skipsAllAccounts_whenNoPasswordsProvided() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    UserSeeder seeder = new UserSeeder(appUserRepository, encoder, "", "");

    seeder.run(null);

    verify(appUserRepository, never()).save(any());
  }

  @Test
  void run_isIdempotent_leavesExistingAccountUnchanged() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    when(appUserRepository.findByUsername("owner"))
        .thenReturn(Optional.of(AppUser.builder().username("owner").role(Role.OWNER).build()));
    UserSeeder seeder = new UserSeeder(appUserRepository, encoder, "owner-pw", "");

    seeder.run(null);

    verify(appUserRepository, never()).save(any());
  }
}
