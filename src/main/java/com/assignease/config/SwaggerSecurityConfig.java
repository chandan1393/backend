package com.assignease.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;

/**
 * Groups API endpoints into logical sections in Swagger UI.
 * Each group appears as a separate tag filter in the Swagger dropdown.
 */
@Configuration
public class SwaggerSecurityConfig {

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
            .group("1 - Authentication")
            .pathsToMatch("/api/auth/**")
            .build();
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("2 - Public")
            .pathsToMatch("/api/public/**")
            .build();
    }

    @Bean
    public GroupedOpenApi studentApi() {
        return GroupedOpenApi.builder()
            .group("3 - Student")
            .pathsToMatch("/api/student/**", "/api/files/**")
            .build();
    }

    @Bean
    public GroupedOpenApi writerApi() {
        return GroupedOpenApi.builder()
            .group("4 - Writer")
            .pathsToMatch("/api/writer/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("5 - Admin")
            .pathsToMatch("/api/admin/**")
            .build();
    }

    @Bean
    public GroupedOpenApi sharedApi() {
        return GroupedOpenApi.builder()
            .group("6 - Shared (Tracker, Chat, Invitations)")
            .pathsToMatch("/api/tracker/**", "/api/chat/**",
                          "/api/invitations/**", "/api/feedback/**",
                          "/api/events/**", "/api/enrollments/**")
            .build();
    }

    @Bean
    public GroupedOpenApi paymentsApi() {
        return GroupedOpenApi.builder()
            .group("7 - Payments & Stripe")
            .pathsToMatch("/api/stripe/**", "/api/student/installments/**",
                          "/api/student/payment-slip/**")
            .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
            .group("0 - All Endpoints")
            .pathsToMatch("/api/**")
            .build();
    }
}
