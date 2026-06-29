package com.mangatracker.backend.config;

import com.mangatracker.backend.security.JwtCookieAuthFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "MangaTracker API",
            version = "v1",
            description =
                "Generated API reference. Authentication uses the auth_token httpOnly cookie. "
                    + "POST, PATCH, and DELETE requests also require X-XSRF-TOKEN from "
                    + "GET /api/auth/csrf; Swagger Try it out returns 403 without it."))
@SecurityScheme(
    name = JwtCookieAuthFilter.COOKIE_NAME,
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = JwtCookieAuthFilter.COOKIE_NAME)
public class OpenApiConfig {}
