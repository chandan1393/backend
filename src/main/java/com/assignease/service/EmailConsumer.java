package com.assignease.service;

import com.assignease.config.RabbitMQConfig;
import com.assignease.entity.OutboxMessage;
import com.assignease.entity.OutboxMessage.OutboxStatus;
import com.assignease.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * EmailConsumer — receives message ID from queue, fetches row from DB, sends email.
 *
 * Receives a Long (the outbox message ID), not the full entity.
 * This avoids the "Failed to convert Message content" error caused by
 * trying to deserialize a Hibernate-proxied JPA entity.
 *
 * Flow:
 *   1. OutboxRelayJob publishes msg.getId() (Long) to RabbitMQ
 *   2. This consumer receives the Long
 *   3. Fetches fresh OutboxMessage from DB by ID
 *   4. Dispatches to EmailService method
 *   5. On success: marks row SENT, channel.basicAck()
 *   6. On failure: channel.basicNack() → Spring Retry → DLQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final EmailService            emailService;
    private final OutboxMessageRepository repo;
    private final ObjectMapper            mapper;

    @RabbitListener(
        queues           = RabbitMQConfig.EMAIL_QUEUE,
        containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional
    public void consume(
            Long outboxId,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("EmailConsumer: received outboxId={}", outboxId);

        // Fetch fresh from DB — never trust the queued payload to be current
        OutboxMessage msg = repo.findById(outboxId).orElse(null);

        if (msg == null) {
            log.warn("EmailConsumer: outboxId={} not found in DB — already processed or deleted. ACK.", outboxId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // Already SENT (duplicate delivery) — ack and skip
        if (msg.getStatus() == OutboxStatus.SENT) {
            log.warn("EmailConsumer: outboxId={} already SENT — duplicate delivery. ACK.", outboxId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            Map<String, Object> p = parsePayload(msg.getPayloadJson());
            dispatch(msg.getEmailType(), msg.getToEmail(), p);

            msg.setStatus(OutboxStatus.SENT);
            msg.setProcessedAt(LocalDateTime.now());
            repo.save(msg);

            // ✅ ACK — message successfully processed
            channel.basicAck(deliveryTag, false);
            log.info("EmailConsumer: ACK id={} type={} to={}",
                msg.getId(), msg.getEmailType(), msg.getToEmail());

        } catch (Exception e) {
            log.error("EmailConsumer: FAILED id={} to={}: {}",
                outboxId, msg.getToEmail(), e.getMessage());

            msg.setLastError(e.getMessage());
            repo.save(msg);

            // ❌ NACK — Spring Retry will retry, then DLQ after max attempts
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void dispatch(OutboxMessage.EmailType type, String toEmail,
                          Map<String, Object> p) throws Exception {
        switch (type) {
            case WELCOME ->
                emailService.sendWelcomeEmail(toEmail, str(p,"name"), str(p,"tempPassword"));

            case QUERY_CONFIRMATION ->
                emailService.sendQueryConfirmation(toEmail, str(p,"name"),
                    p.get("queryId") instanceof Number n ? n.longValue() : 0L);

            case PASSWORD_RESET ->
                emailService.sendPasswordResetEmail(toEmail, str(p,"resetToken"));

            case ASSIGNMENT_STATUS_UPDATE ->
                emailService.sendAssignmentStatusUpdate(toEmail, str(p,"name"),
                    str(p,"assignmentTitle"), str(p,"status"));

            case INSTALLMENT_REMINDER ->
                emailService.sendInstallmentReminder(toEmail, str(p,"name"),
                    str(p,"courseName"),
                    p.get("installmentNum") instanceof Number n ? n.intValue() : 1,
                    str(p,"amount"), str(p,"dueDate"), str(p,"stripeLink"));

            case WRITER_ASSIGNED ->
                emailService.sendWriterAssigned(toEmail, str(p,"studentName"), str(p,"courseName"));

            case WORK_DELIVERED ->
                emailService.sendWorkDelivered(toEmail, str(p,"studentName"), str(p,"courseName"));

            case NOTIFICATION ->
                emailService.sendNotification(toEmail, str(p,"subject"), str(p,"bodyHtml"));

            default -> log.warn("EmailConsumer: unknown type {}", type);
        }
    }

    private Map<String, Object> parsePayload(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<>() {});
    }

    private String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v != null ? v.toString() : "";
    }
}
