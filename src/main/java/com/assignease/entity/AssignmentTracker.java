package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_trackers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssignmentTracker {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    private String assignmentTitle;
    private String description;
    private String assignmentType;    // quiz, essay, project, discussion, exam
    private LocalDate dueDate;
    private LocalDate uploadDeadline; // when writer should upload by

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TrackerStatus status = TrackerStatus.PENDING;

    // Writer uploads a file for this specific task
    private String writerFilePath;
    private String writerFileName;
    private LocalDateTime writerUploadedAt;
    private String writerNote;        // writer's note about this upload

    // Admin downloads & approves — then student can download
    @Builder.Default
    private Boolean adminApproved = false;
    private LocalDateTime adminApprovedAt;
    private String adminNote;

    // Reference doc provided by admin for this task (instructions, rubric, etc.)
    private String taskDocPath;
    private String taskDocName;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp  private LocalDateTime updatedAt;

    public enum TrackerStatus {
        PENDING,      // not uploaded yet
        UPLOADED,     // writer uploaded file, pending admin review
        APPROVED,     // admin approved — student can download
        REJECTED,     // admin rejected writer's upload
        MISSED        // due date passed without upload
    }
}
