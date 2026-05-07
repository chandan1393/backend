package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * OutboxMessage — the "transactional outbox" pattern.
 *
 * WHY THIS EXISTS:
 * Without an outbox, a common failure scenario is:
 *   1. Business logic completes (enrollment created, payment confirmed)
 *   2. Publish to RabbitMQ — RABBITMQ IS DOWN → exception
 *   3. Email never sent, data is in DB but notification is lost
 *
 * With the outbox pattern:
 *   1. Business logic AND outbox row are written in ONE DB transaction
 *   2. If RabbitMQ is down, the row stays PENDING in the DB
 *   3. A @Scheduled poller retries PENDING rows every 30 seconds
 *   4. When RabbitMQ comes back up, the message is published and sent
 *   5. Zero message loss — guaranteed at-least-once delivery
 *
 * Flow:
 *   Service → save OutboxMessage(PENDING) → DB transaction commits
 *   OutboxRelayJob (scheduler) → polls PENDING rows → publishes to RabbitMQ
 *   EmailConsumer → receives from queue → sends email via Resend
 *   OutboxRelayJob → marks row SENT
 */
@Entity
@Table(
    name = "outbox_messages",
    indexes = {
        @Index(name = "idx_outbox_status",      columnList = "status"),
        @Index(name = "idx_outbox_created_at",   columnList = "created_at"),
        @Index(name = "idx_outbox_status_created", columnList = "status,created_at")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email type — maps to EmailService method to call */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmailType emailType;

    /** Recipient email address */
    @Column(nullable = false, length = 200)
    private String toEmail;

    /**
     * Payload serialized as JSON.
     * Contains all parameters needed to send the email
     * (name, courseName, amount, resetToken, etc.)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    /** Processing status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    /** How many times publishing has been attempted */
    @Builder.Default
    @Column(name = "retry_count")
    private int retryCount = 0;

    /** Last error message (for debugging) */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum OutboxStatus {
        PENDING,    // waiting to be published to RabbitMQ
        PUBLISHED,  // published to RabbitMQ, consumer will send
        SENT,       // email successfully sent by consumer
        FAILED      // max retries exceeded — needs manual review
    }

    public enum EmailType {
        WELCOME,
        QUERY_CONFIRMATION,
        PASSWORD_RESET,
        ASSIGNMENT_STATUS_UPDATE,
        INSTALLMENT_REMINDER,
        WRITER_ASSIGNED,
        WORK_DELIVERED,
        NOTIFICATION
    }
}
