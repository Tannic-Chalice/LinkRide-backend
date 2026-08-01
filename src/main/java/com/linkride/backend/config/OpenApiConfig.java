package com.linkride.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata (Phase 5 §7 — see backend/docs/phase-5-platform-hardening.md). This is a
 * mechanically-accurate complement to {@code docs/api.md}, generated from controller/DTO
 * annotations -- it does not replace the narrative documentation there.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI linkRideOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LinkRide API")
                        .description("See docs/api.md for the narrative endpoint reference. "
                                + "This spec is generated directly from the controllers, so field "
                                + "names/types here are authoritative if the two ever disagree.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
