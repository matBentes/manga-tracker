package com.mangatracker.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangatracker.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

class OpenApiDocumentationTest {

  @Test
  void openApiConfig_declaresCookieJwtSecurityScheme() {
    assertThat(OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class)).isNotNull();

    SecurityScheme securityScheme = OpenApiConfig.class.getAnnotation(SecurityScheme.class);
    assertThat(securityScheme).isNotNull();
    assertThat(securityScheme.name()).isEqualTo("auth_token");
    assertThat(securityScheme.paramName()).isEqualTo("auth_token");
  }

  @Test
  void controllers_haveTagsAndOperationsForAllEndpoints() {
    assertThat(AuthController.class.getAnnotation(Tag.class).name()).isEqualTo("Auth");
    assertThat(MangaController.class.getAnnotation(Tag.class).name()).isEqualTo("Manga");
    assertThat(PushController.class.getAnnotation(Tag.class).name()).isEqualTo("Push");

    assertThat(documentedEndpointCount(AuthController.class)).isEqualTo(5);
    assertThat(documentedEndpointCount(MangaController.class)).isEqualTo(9);
    assertThat(documentedEndpointCount(PushController.class)).isEqualTo(3);
  }

  @Test
  void errorResponseSchema_matchesSerializedErrorEnvelope() throws Exception {
    assertThat(ErrorResponse.class).isRecord();
    assertThat(ErrorResponse.class.getRecordComponents())
        .singleElement()
        .satisfies(
            component -> {
              assertThat(component.getName()).isEqualTo("error");
              assertThat(component.getType()).isEqualTo(String.class);
              assertThat(
                      ErrorResponse.class
                          .getDeclaredMethod(component.getName())
                          .getAnnotation(Schema.class))
                  .isNotNull();
            });
    assertThat(new ErrorResponse("not found").error()).isEqualTo("not found");
  }

  @Test
  void authLoginEndpoints_documentRateLimitResponses() throws Exception {
    assertThat(
            responseCodes(
                AuthController.class.getDeclaredMethod(
                    "login", AuthController.LoginRequest.class, HttpServletRequest.class)))
        .contains("429");
    assertThat(
            responseCodes(
                AuthController.class.getDeclaredMethod("demoLogin", HttpServletRequest.class)))
        .contains("429");
  }

  private static long documentedEndpointCount(Class<?> controllerClass) {
    return Arrays.stream(controllerClass.getDeclaredMethods())
        .filter(OpenApiDocumentationTest::isEndpoint)
        .peek(method -> assertThat(method.getAnnotation(Operation.class)).isNotNull())
        .count();
  }

  private static boolean isEndpoint(Method method) {
    return method.isAnnotationPresent(GetMapping.class)
        || method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PatchMapping.class)
        || method.isAnnotationPresent(DeleteMapping.class);
  }

  private static String[] responseCodes(Method method) {
    ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
    assertThat(apiResponses).isNotNull();
    return Arrays.stream(apiResponses.value())
        .map(ApiResponse::responseCode)
        .toArray(String[]::new);
  }
}
