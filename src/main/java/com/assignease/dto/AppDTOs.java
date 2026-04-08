package com.assignease.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

public class AppDTOs {

    // ── Student Self-Registration ────────────────────────────────────────────
    @Data
    public static class StudentRegisterRequest {
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 80, message = "Name must be 2–80 characters")
        @Pattern(regexp = "^[a-zA-Z\\s'\\-\\.]+$", message = "Name contains invalid characters")
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 150, message = "Email too long")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8–100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                 message = "Password must contain uppercase, lowercase, and a number")
        private String password;

        @Pattern(regexp = "^[\\+\\d\\s\\-\\.\\(\\)]{0,25}$", message = "Invalid phone number format")
        private String phone;
    }

    // ── Contact / Query Form ─────────────────────────────────────────────────
    @Data
    public static class QueryRequest {
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be 2–80 characters")
        @Pattern(regexp = "^[a-zA-Z\\s'\\-\\.]+$", message = "Name contains invalid characters")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 150)
        private String email;

        @Pattern(regexp = "^[\\+\\d\\s\\-\\.\\(\\)]{0,25}$", message = "Invalid phone format")
        private String phone;

        @NotBlank(message = "Subject is required")
        @Size(max = 100, message = "Subject too long")
        private String subject;

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be 10–2000 characters")
        private String message;
    }

    @Data
    public static class QueryResponse {
        private Long id;
        private String name;
        private String email;
        private String subject;
        private String message;
        private String status;
        private String adminReply;
        private LocalDateTime createdAt;
    }

    // ── Enrollment ────────────────────────────────────────────────────────────
    @Data
    public static class EnrollmentRequest {
        @NotBlank(message = "Course name is required")
        @Size(max = 150, message = "Course name too long")
        private String courseName;

        @NotBlank(message = "Institution name is required")
        @Size(max = 150, message = "Institution name too long")
        private String institutionName;

        @NotBlank(message = "Subject is required")
        @Size(max = 100)
        private String subject;

        @Size(max = 200)
        private String portalUrl;

        @Size(max = 150)
        private String portalUsername;

        @Size(max = 150)
        private String portalPassword;

        @NotNull(message = "Class start date is required")
        private String classStartDate; // YYYY-MM-DD

        @NotNull(message = "Class end date is required")
        private String classEndDate;   // YYYY-MM-DD

        @Size(max = 500)
        private String additionalNotes;
    }

    // ── Admin: Create / Update User ──────────────────────────────────────────
    @Data
    public static class UserCreateRequest {
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 80, message = "Name must be 2–80 characters")
        @Pattern(regexp = "^[a-zA-Z\\s'\\-\\.]+$", message = "Name contains invalid characters")
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 150, message = "Email too long")
        private String email;

        @Pattern(regexp = "^[\\+\\d\\s\\-\\.\\(\\)]{0,25}$", message = "Invalid phone number format")
        private String phone;

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "^(ROLE_STUDENT|ROLE_WRITER|ROLE_ADMIN)$", message = "Role must be ROLE_STUDENT, ROLE_WRITER, or ROLE_ADMIN")
        private String role;

        // Optional — if blank, a temporary password is auto-generated and emailed
        @Size(min = 8, max = 100, message = "Password must be 8–100 characters")
        private String password;
    }

    // ── User Response ─────────────────────────────────────────────────────────
    @Data
    public static class UserResponse {
        private Long id;
        private String fullName;
        private String email;
        private String phone;
        private String role;
        private boolean enabled;
        private boolean firstLogin;
        private java.time.LocalDateTime createdAt;
        private long assignmentCount;
    }

    // ── Assignment Response (returned by AssignmentService) ──────────────────
    @Data
    public static class AssignmentResponse {
        private Long   id;
        private String title;
        private String description;
        private String subject;
        private String level;
        private Integer pages;
        private String wordCount;
        private java.time.LocalDateTime deadline;
        private String status;
        private Double price;

        // Student-uploaded reference document
        // NEVER expose raw disk path — always serve via /api/files/
        private String studentDocumentPath;

        // Approved file path visible to student after approval
        // Served via GET /api/files/submission/{id}
        private String filePath;

        // Writer's uploaded file — admin only
        private String writerFilePath;
        private Boolean writerFileApproved;

        private String adminNotes;
        private String adminReply;
        private String studentNotes;
        private boolean paymentDone;

        // Student info — only included in admin view (isAdmin=true)
        private String studentName;
        private String studentEmail;

        // Writer info
        private String assignedWriterName;

        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.time.LocalDateTime completedAt;
    }

    // ── Admin: Update Assignment Status ──────────────────────────────────────
    @Data
    public static class UpdateAssignmentStatusRequest {
        @Pattern(
            regexp = "^(SUBMITTED|UNDER_REVIEW|IN_PROGRESS|DELIVERED|CANCELLED|REVISION_REQUESTED|PENDING_PAYMENT|PAYMENT_CONFIRMED)?$",
            message = "Invalid status value"
        )
        private String status;

        @Size(max = 2000, message = "Admin notes must be under 2000 characters")
        private String adminNotes;

        @Size(max = 2000, message = "Admin reply must be under 2000 characters")
        private String adminReply;

        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        @DecimalMax(value = "99999.99", message = "Price too large")
        private Double price;
    }

    // ── Contact Form (landing page "Contact Us") ──────────────────────────────
    @Data
    public static class ContactMessage {
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be 2–80 characters")
        @Pattern(regexp = "^[a-zA-Z\\s'\\-\\.]+$", message = "Name contains invalid characters")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 150)
        private String email;

        @Pattern(regexp = "^[\\+\\d\\s\\-\\.\\(\\)]{0,25}$", message = "Invalid phone format")
        private String phone;

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be 10–2000 characters")
        private String message;
    }

    // ── Admin Dashboard Stats ─────────────────────────────────────────────────
    @Data
    public static class DashboardStats {
        private long totalAssignments;
        private long pendingAssignments;
        private long completedAssignments;
        private long inProgressAssignments;
        private long totalStudents;
        private long totalQueries;
        private long pendingQueries;
    }

    // ── Student: Create Assignment Request ────────────────────────────────────
    @Data
    public static class AssignmentRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title too long")
        private String title;

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 2000, message = "Description must be 10–2000 characters")
        private String description;

        @NotBlank(message = "Subject is required")
        @Size(max = 100)
        private String subject;

        @Size(max = 50)
        private String level;     // e.g. "Undergraduate", "Graduate"

        private Integer pages;

        @Size(max = 20)
        private String wordCount;

        private java.time.LocalDateTime deadline;

        @Size(max = 1000)
        private String studentNotes;
    }
}
