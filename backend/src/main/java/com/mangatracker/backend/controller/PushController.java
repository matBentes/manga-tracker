package com.mangatracker.backend.controller;

import com.mangatracker.backend.security.JwtCookieAuthFilter;
import com.mangatracker.backend.service.PushSubscriptionService;
import com.mangatracker.backend.service.VapidKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
@Tag(name = "Push", description = "Web Push VAPID key and subscription operations")
public class PushController {

  private final PushSubscriptionService subscriptionService;
  private final VapidKeys vapidKeys;

  public PushController(PushSubscriptionService subscriptionService, VapidKeys vapidKeys) {
    this.subscriptionService = subscriptionService;
    this.vapidKeys = vapidKeys;
  }

  @GetMapping("/public-key")
  @Operation(summary = "Get the VAPID public key")
  @ApiResponse(responseCode = "200", description = "VAPID public key")
  public Map<String, String> publicKey() {
    return Map.of("publicKey", vapidKeys.getPublicKey());
  }

  @PostMapping("/subscribe")
  @Operation(summary = "Subscribe the current browser to Web Push")
  @SecurityRequirement(name = JwtCookieAuthFilter.COOKIE_NAME)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Subscription registered"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<Void> subscribe(@RequestBody SubscribeRequest request) {
    subscriptionService.subscribe(request.endpoint(), p256dh(request), auth(request));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/unsubscribe")
  @Operation(summary = "Unsubscribe the current browser from Web Push")
  @SecurityRequirement(name = JwtCookieAuthFilter.COOKIE_NAME)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Subscription removed"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
    subscriptionService.unsubscribe(request.endpoint());
    return ResponseEntity.noContent().build();
  }

  private static String p256dh(SubscribeRequest request) {
    return request.keys() == null ? null : request.keys().p256dh();
  }

  private static String auth(SubscribeRequest request) {
    return request.keys() == null ? null : request.keys().auth();
  }

  /** Matches the browser {@code PushSubscription.toJSON()} shape. */
  record SubscribeRequest(
      @Schema(
              description = "Browser push service endpoint",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String endpoint,
      @Schema(
              description = "Browser-generated Web Push encryption keys",
              requiredMode = Schema.RequiredMode.REQUIRED)
          Keys keys) {}

  record Keys(
      @Schema(description = "ECDH public key", requiredMode = Schema.RequiredMode.REQUIRED)
          String p256dh,
      @Schema(description = "Authentication secret", requiredMode = Schema.RequiredMode.REQUIRED)
          String auth) {}

  record UnsubscribeRequest(
      @Schema(
              description = "Browser push service endpoint",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String endpoint) {}
}
