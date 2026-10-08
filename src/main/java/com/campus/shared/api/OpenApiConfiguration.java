package com.campus.shared.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI campusServiceOpenApi() {
        return new OpenAPI().info(new Info().title("Campus Service API").version("v1").description("Bearer JWT authorization. Global ADMIN manages accounts/roles; functional operators access their own domains with explicit read references. AUDIT_VIEWER and REPORTING_VIEWER are read-only. See docs/permissions.md for the full method/path matrix and scope limits."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
