package com.assignease.dto;

import com.assignease.entity.Assignment;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

public class AppDTOs {

    @Data
    public static class QueryRequest {
        @NotBlank private String name;
        @Email @NotBlank private String email;
        private String phone;
        @NotBlank private String subject;
        @NotBlank private String message;
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

    @Data
    public static class AssignmentRequest {
        @NotBlank private String title;
        @NotBlank private String description;
        @NotBlank private String subject;
        private String level;
        private Integer pages;
        private String wordCount;
        private LocalDateTime deadline;
        private String studentNotes;
        // document upload is handled separately via multipart
    }

    @Data
    public static class AssignmentResponse {
        private Long id;
        private String title;
        private String description;
        private String subject;
        private String level;
        private Integer pages;
        private String wordCount;
        private LocalDateTime deadline;
        private String status;
        private Double price;
        // Student sees filePath only after admin approves writer's upload
        private String filePath;
        // Writer's uploaded file — only admin sees this
        private String writerFilePath;
        private Boolean writerFileApproved;
        // Student uploaded document
        private String studentDocumentPath;
        // Admin reply to student (visible in student dashboard)
        private String adminReply;
        private Boolean paymentDone;
        private String studentName;
        private String studentEmail;
        // Writer sees: no student name/email, only assignment details
        private String assignedWriterName;
        private String adminNotes;
        private String studentNotes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class UpdateAssignmentStatusRequest {
        private String status;
        private String adminNotes;
        private String adminReply;
        private Double price;
    }

    @Data
    public static class UserCreateRequest {
        @NotBlank private String fullName;
        @Email @NotBlank private String email;
        private String phone;
        private String role;
    }

    @Data
    public static class UserResponse {
        private Long id;
        private String fullName;
        private String email;
        private String phone;
        private String role;
        private boolean enabled;
        private boolean firstLogin;
        private LocalDateTime createdAt;
        private long assignmentCount;
    }

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

    @Data
    public static class ContactMessage {
        @NotBlank private String name;
        @Email @NotBlank private String email;
        private String phone;
        @NotBlank private String message;
    }
}
