package com.mangaTracker.backend.repository;

import com.mangaTracker.backend.model.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByUsername(String username);
}
