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
        return new OpenAPI().info(new Info().title("Campus Service API").version("v1").description("Bearer JWT authorization. Global ADMIN manages accounts/roles; ACADEMIC_AFFAIRS, CAMPUS_SERVICES and FINANCE operate their department domains with explicit read references. AUDITOR is read-only for audits, reports and approved references. See docs/permissions.md for the full method/path matrix and department ownership."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
