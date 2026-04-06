package com.assignease.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AuthDTOs {

    @Data
    public static class LoginRequest {
        @Email @NotBlank
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String email;
        private String fullName;
        private String role;
        private boolean firstLogin;
        private Long userId;

        public LoginResponse(String token, String email, String fullName,
                             String role, boolean firstLogin, Long userId) {
            this.token = token;
            this.email = email;
            this.fullName = fullName;
            this.role = role;
            this.firstLogin = firstLogin;
            this.userId = userId;
        }
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        private String newPassword;
    }

    @Data
    public static class ForgotPasswordRequest {
        @Email @NotBlank
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;
        @NotBlank
        private String newPassword;
    }
}
