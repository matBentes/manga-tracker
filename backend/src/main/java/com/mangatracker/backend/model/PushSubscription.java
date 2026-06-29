package com.mangatracker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "push_subscription")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription {

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(name = "endpoint", nullable = false, unique = true, columnDefinition = "TEXT")
  private String endpoint;

  @Column(name = "p256dh", nullable = false, columnDefinition = "TEXT")
  private String p256dh;

  @Column(name = "auth", nullable = false, columnDefinition = "TEXT")
  private String auth;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
