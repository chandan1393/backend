package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String subject;

    private String level; // Undergraduate, Postgraduate, PhD, etc.

    private Integer pages;

    private String wordCount;

    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status = AssignmentStatus.SUBMITTED;

    private Double price;

    private String filePath; // Admin-approved solution shown to student

    private String writerFilePath; // Writer uploaded file — pending admin approval

    private Boolean writerFileApproved = false; // Admin must approve before student sees it

    private String studentDocumentPath; // Student uploaded requirement document

    private String adminReply; // Admin reply visible to student in their dashboard

    private Boolean paymentDone = false; // True after Razorpay success

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_writer_id")
    private User assignedWriter;

    private String adminNotes;

    private String studentNotes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    public enum AssignmentStatus {
        // Student submitted, awaiting admin review
        SUBMITTED,
        // Admin reviewed, price set — awaiting student payment
        AWAITING_PAYMENT,
        // Payment received — work starts
        PAYMENT_RECEIVED,
        // Assigned to writer, work in progress
        IN_PROGRESS,
        // Writer uploaded file, pending admin review
        UNDER_REVIEW,
        // Admin approved — delivered to student
        DELIVERED,
        // Student requested changes
        REVISION_REQUESTED,
        // All done
        CLOSED
    }
}
