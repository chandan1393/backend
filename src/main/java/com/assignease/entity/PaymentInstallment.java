package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_installments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentInstallment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    private Integer installmentNumber;  // 1, 2, 3...

    @Column(nullable = false)
    private Double amount;

    private String currency = "USD";

    @Column(nullable = false)
    private LocalDate dueDate;          // when payment is due

    // ── Stripe manual payment link ──────────────────────────────────────────
    private String stripePaymentLink;   // admin creates and pastes the Stripe link

    private String stripePaymentId;     // filled when student pays

    // ── Receipt upload by student ───────────────────────────────────────────
    private String receiptPath;         // student uploads payment receipt

    @Enumerated(EnumType.STRING)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Reminder tracking ───────────────────────────────────────────────────
    private Boolean reminderSent = false;  // 1-day-before reminder sent?
    private LocalDateTime reminderSentAt;

    private LocalDateTime paidAt;
    private LocalDateTime receiptUploadedAt;

    @CreationTimestamp private LocalDateTime createdAt;

    public enum InstallmentStatus {
        PENDING,             // Not yet paid
        RECEIPT_UPLOADED,    // Student uploaded receipt — admin verifying
        CONFIRMED,           // Admin confirmed payment
        OVERDUE              // Past due date, not paid
    }
}
