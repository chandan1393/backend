package com.assignease.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for EduAssist API.
 *
 * Access at: http://localhost:8080/swagger-ui.html
 *
 * HOW TO USE:
 * 1. Expand POST /api/auth/login → "Try it out"
 * 2. Enter email + password → Execute
 * 3. Copy the "token" value from the response
 * 4. Click "Authorize" button (top right of Swagger UI)
 * 5. In the "BearerAuth" field enter: <your-token>
 *    (DO NOT add "Bearer " prefix — Swagger adds it automatically)
 * 6. Click Authorize → Close
 * 7. All subsequent requests will automatically include Authorization: Bearer <token>
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "EduAssist API",
        version     = "1.0.0",
        description = """
            ## EduAssist — Online Class Management Platform API
            
            ### Quick Start
            1. Use **POST /api/auth/login** to get a JWT token
            2. Click the **🔓 Authorize** button at the top
            3. Paste your token (without "Bearer " prefix) and click Authorize
            4. All protected endpoints will now include your token automatically
            
            ### Test Accounts (set up via DataInitializer)
            - **Admin:** admin@eduassist.com / Admin@123
            - **Student:** (register via POST /api/auth/register)
            - **Writer:** (admin creates via POST /api/admin/users)
            
            ### Auth Flow
            `POST /api/auth/login` → copy `token` → Authorize → test all endpoints
            """,
        contact = @Contact(name = "EduAssist Support", email = "support@eduassist.com"),
        license = @License(name = "Private — All Rights Reserved")
    ),
    servers = {
        @Server(url = "http://localhost:8080",  description = "Local Development"),
        @Server(url = "https://your-backend.up.railway.app", description = "Railway Production"),
    },
    security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
    name         = "BearerAuth",
    type         = SecuritySchemeType.HTTP,
    scheme       = "bearer",
    bearerFormat = "JWT",
    in           = SecuritySchemeIn.HEADER,
    description  = """
        Enter your JWT token here (WITHOUT "Bearer " prefix).
        
        Get a token by calling POST /api/auth/login with:
        { "email": "admin@eduassist.com", "password": "Admin@123" }
        
        Then copy the "token" field from the response and paste it here.
        Swagger UI will automatically add "Bearer " when making requests.
        """
)
public class OpenApiConfig {
    // All configuration is via annotations above.
    // springdoc-openapi auto-scans all @RestController classes.
}
