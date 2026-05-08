package com.assignease.service;

import com.assignease.config.RabbitMQConfig;
import com.assignease.entity.OutboxMessage;
import com.assignease.entity.OutboxMessage.OutboxStatus;
import com.assignease.repository.OutboxMessageRepository;
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

/**
 * EmailConsumer listens to the main RabbitMQ email queue.
 *
 * Receives the outbox message ID (Long), looks it up in the DB,
 * then calls EmailService.dispatchFromOutbox() to send the email.
 *
 * Manual ACK:
 *   - Only ACKs after the email is sent and DB row is updated to SENT
 *   - If the app crashes before ACK, RabbitMQ re-delivers to next consumer
 *   - basicNack(requeue=false) on failure -> Spring Retry -> DLQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final EmailService            emailService;
    private final OutboxMessageRepository repo;

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

        // Fetch fresh from DB — never trust queued data to be current
        OutboxMessage msg = repo.findById(outboxId).orElse(null);

        if (msg == null) {
            log.warn("EmailConsumer: id={} not found in DB, already deleted. ACK.", outboxId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // Guard against duplicate delivery from RabbitMQ
        if (msg.getStatus() == OutboxStatus.SENT) {
            log.warn("EmailConsumer: id={} already SENT, skipping duplicate. ACK.", outboxId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            // Shared dispatch logic — same method used by OutboxRelayJob direct path
            emailService.dispatchFromOutbox(msg);

            // Mark SENT in DB
            msg.setStatus(OutboxStatus.SENT);
            msg.setProcessedAt(LocalDateTime.now());
            repo.save(msg);

            // ACK — broker removes the message permanently
            channel.basicAck(deliveryTag, false);
            log.info("EmailConsumer: ACK id={} type={} to={}",
                msg.getId(), msg.getEmailType(), msg.getToEmail());

        } catch (Exception e) {
            log.error("EmailConsumer: FAILED id={} to={} error={}",
                outboxId, msg.getToEmail(), e.getMessage());

            // Save the error for debugging
            msg.setLastError(e.getMessage());
            repo.save(msg);

            // NACK — Spring Retry interceptor will retry up to 3 times,
            // then RejectAndDontRequeueRecoverer sends it to the DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
