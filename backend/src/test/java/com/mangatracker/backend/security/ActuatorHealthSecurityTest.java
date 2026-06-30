package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.DefaultHealthContributorRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthEndpointWebExtension;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.StatusAggregator;

class ActuatorHealthSecurityTest {

  @Test
  void anonymousHealthRequest_returnsStatusOnly() throws IOException {
    Show configuredShowDetails = readConfiguredShowDetails();
    HealthEndpointWebExtension extension = healthEndpointWebExtension(configuredShowDetails);

    WebEndpointResponse<HealthComponent> response =
        extension.health(ApiVersion.V3, WebServerNamespace.SERVER, SecurityContext.NONE);

    HealthComponent body = response.getBody();
    assertThat(body.getStatus().getCode()).isEqualTo("UP");
    if (body instanceof Health health) {
      assertThat(health.getDetails()).isNullOrEmpty();
    } else {
      assertThat((CompositeHealth) body)
          .satisfies(health -> assertThat(health.getDetails()).isNullOrEmpty());
    }
  }

  private static Show readConfiguredShowDetails() throws IOException {
    Properties properties = new Properties();
    try (InputStream input =
        ActuatorHealthSecurityTest.class.getResourceAsStream("/application.properties")) {
      properties.load(input);
    }
    String value = properties.getProperty("management.endpoint.health.show-details");
    return Show.valueOf(value.replace('-', '_').toUpperCase());
  }

  private static HealthEndpointWebExtension healthEndpointWebExtension(Show showDetails) {
    DefaultHealthContributorRegistry registry =
        new DefaultHealthContributorRegistry(
            Map.<String, HealthContributor>of(
                "db",
                (HealthIndicator) () -> Health.up().withDetail("database", "PostgreSQL").build(),
                "diskSpace",
                (HealthIndicator) () -> Health.up().withDetail("free", 1024).build()));
    HealthEndpointGroup group = healthEndpointGroup(showDetails);
    return new HealthEndpointWebExtension(
        registry, HealthEndpointGroups.of(group, Map.of()), Duration.ZERO);
  }

  private static HealthEndpointGroup healthEndpointGroup(Show showDetails) {
    return new HealthEndpointGroup() {
      @Override
      public boolean isMember(String name) {
        return true;
      }

      @Override
      public boolean showComponents(SecurityContext securityContext) {
        return showDetails.isShown(securityContext, java.util.Set.of());
      }

      @Override
      public boolean showDetails(SecurityContext securityContext) {
        return showDetails.isShown(securityContext, java.util.Set.of());
      }

      @Override
      public StatusAggregator getStatusAggregator() {
        return StatusAggregator.getDefault();
      }

      @Override
      public HttpCodeStatusMapper getHttpCodeStatusMapper() {
        return HttpCodeStatusMapper.DEFAULT;
      }

      @Override
      public org.springframework.boot.actuate.health.AdditionalHealthEndpointPath
          getAdditionalPath() {
        return null;
      }
    };
  }
}
