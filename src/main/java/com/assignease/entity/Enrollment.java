package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "enrollments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Enrollment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Class Details ───────────────────────────────────────────────────────
    @Column(nullable = false)
    private String courseName;       // e.g. "MBA Marketing - Semester 3"

    @Column(nullable = false)
    private String institutionName;  // e.g. "University of Delhi"

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String courseDescription; // what topics, syllabus etc.

    private LocalDate classStartDate;
    private LocalDate classEndDate;

    // ── Login Credentials (encrypted at rest ideally) ───────────────────────
    private String portalUrl;         // LMS/portal URL
    private String portalUsername;    // student's login
    private String portalPassword;    // student's password (stored securely)

    // ── Additional Info ─────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String studentNotes;      // special instructions

    private String referenceDocPath;  // student uploaded syllabus/rubric

    // ── Pricing & Payment ───────────────────────────────────────────────────
    private Double totalPrice;        // admin sets this
    private Integer totalInstallments; // how many installments admin creates

    @Column(columnDefinition = "TEXT")
    private String adminReply;        // admin message visible to student

    @Column(columnDefinition = "TEXT")
    private String adminNotes;        // internal notes

    // ── Status ──────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status = EnrollmentStatus.SUBMITTED;

    // ── Relations ────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_writer_id")
    private User assignedWriter;

    @OneToMany(mappedBy = "enrollment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PaymentInstallment> installments = new ArrayList<>();

    // ── Delivery ─────────────────────────────────────────────────────────────
    private String deliveryZipPath;   // writer uploads ZIP of all completed assignments

    private Boolean writerFileApproved = false;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp  private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public enum EnrollmentStatus {
        SUBMITTED,           // Student submitted class details
        UNDER_REVIEW,        // Admin is reviewing
        PAYMENT_PLAN_SENT,   // Admin set price + installments
        PARTIALLY_PAID,      // At least 1 installment paid
        FULLY_PAID,          // All installments paid
        IN_PROGRESS,         // Writer assigned and working
        UNDER_ADMIN_REVIEW,  // Writer uploaded, admin reviewing
        DELIVERED,           // Admin approved — student downloads
        REVISION_REQUESTED,  // Student requested changes
        COMPLETED            // All done
    }
}
