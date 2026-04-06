package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "writer_submissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WriterSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id", nullable = false)
    private User writer;

    private String zipPath;
    private String fileName;
    private String description;   // writer's note for this submission

    @Enumerated(EnumType.STRING)
    private SubmissionStatus status = SubmissionStatus.PENDING_REVIEW;

    @CreationTimestamp
    private LocalDateTime uploadedAt;

    private LocalDateTime reviewedAt;
    private String adminNote;

    public enum SubmissionStatus {
        PENDING_REVIEW,   // admin hasn't reviewed yet
        APPROVED,         // delivered to student
        REJECTED          // admin rejected, writer must re-upload
    }
}
