package com.assignease.service;

import com.assignease.entity.OutboxMessage;
import com.assignease.entity.OutboxMessage.EmailType;
import com.assignease.entity.OutboxMessage.OutboxStatus;
import com.assignease.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * OutboxService — the PRODUCER side of the outbox pattern.
 *
 * Business logic calls these methods (within the same DB transaction
 * as the main operation). The outbox row is committed to the DB atomically
 * with the business data. If RabbitMQ is down, no data is lost — the relay
 * job will pick it up later.
 *
 * Usage:
 *   // Inside EnrollmentService (same @Transactional scope):
 *   outboxService.enqueueWelcomeEmail(email, name, password);
 *   // HTTP response returns immediately — email sends in background
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxMessageRepository repo;
    private final ObjectMapper            mapper;

    // ── Enqueue methods (called by business services) ─────────────────────────

    @Transactional
    public void enqueueWelcomeEmail(String toEmail, String name, String tempPassword) {
        save(EmailType.WELCOME, toEmail, Map.of(
            "name", name, "tempPassword", tempPassword
        ));
    }

    @Transactional
    public void enqueueQueryConfirmation(String toEmail, String name, Long queryId) {
        save(EmailType.QUERY_CONFIRMATION, toEmail, Map.of(
            "name", name, "queryId", queryId
        ));
    }

    @Transactional
    public void enqueuePasswordReset(String toEmail, String resetToken) {
        save(EmailType.PASSWORD_RESET, toEmail, Map.of(
            "resetToken", resetToken
        ));
    }

    @Transactional
    public void enqueueStatusUpdate(String toEmail, String name,
                                    String assignmentTitle, String status) {
        save(EmailType.ASSIGNMENT_STATUS_UPDATE, toEmail, Map.of(
            "name", name, "assignmentTitle", assignmentTitle, "status", status
        ));
    }

    @Transactional
    public void enqueueInstallmentReminder(String toEmail, String name, String courseName,
                                           int installmentNum, String amount,
                                           String dueDate, String stripeLink) {
        save(EmailType.INSTALLMENT_REMINDER, toEmail, Map.of(
            "name", name, "courseName", courseName,
            "installmentNum", installmentNum, "amount", amount,
            "dueDate", dueDate, "stripeLink", stripeLink != null ? stripeLink : ""
        ));
    }

    @Transactional
    public void enqueueWriterAssigned(String toEmail, String studentName, String courseName) {
        save(EmailType.WRITER_ASSIGNED, toEmail, Map.of(
            "studentName", studentName, "courseName", courseName
        ));
    }

    @Transactional
    public void enqueueWorkDelivered(String toEmail, String studentName, String courseName) {
        save(EmailType.WORK_DELIVERED, toEmail, Map.of(
            "studentName", studentName, "courseName", courseName
        ));
    }

    @Transactional
    public void enqueueNotification(String toEmail, String subject, String bodyHtml) {
        save(EmailType.NOTIFICATION, toEmail, Map.of(
            "subject", subject, "bodyHtml", bodyHtml
        ));
    }

    // ── Internal helper ───────────────────────────────────────────────────────

    private void save(EmailType type, String toEmail, Map<String, Object> payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            OutboxMessage msg = OutboxMessage.builder()
                .emailType(type)
                .toEmail(toEmail)
                .payloadJson(json)
                .status(OutboxStatus.PENDING)
                .build();
            repo.save(msg);
            log.debug("Outbox: queued {} → {}", type, toEmail);
        } catch (Exception e) {
            // JSON serialisation failure is a programming error — log and swallow
            // so the main business transaction is not rolled back for email
            log.error("Outbox: failed to save message type={} to={}: {}", type, toEmail, e.getMessage());
        }
    }
}
