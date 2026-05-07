package com.assignease.service;

import com.assignease.config.RabbitMQConfig;
import com.assignease.entity.OutboxMessage;
import com.assignease.entity.OutboxMessage.OutboxStatus;
import com.assignease.repository.OutboxMessageRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DlqRetryConsumer — reads failed message IDs from the DLQ and replays them.
 *
 * Receives a Long (outbox message ID) — same as EmailConsumer.
 * Checks x-dlq-retry-count header, either re-publishes to main queue
 * or permanently marks as FAILED after DLQ_MAX_ATTEMPTS.
 *
 * Uses dlqListenerContainerFactory (no Spring Retry interceptor)
 * to avoid infinite retry loops.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlqRetryConsumer {

    private final RabbitTemplate          rabbit;
    private final OutboxMessageRepository repo;

    @RabbitListener(
        queues           = RabbitMQConfig.EMAIL_DLQ,
        containerFactory = "dlqListenerContainerFactory"
    )
    @Transactional
    public void retryFromDlq(
            Long outboxId,
            Message rawMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Map<String, Object> headers = rawMessage.getMessageProperties().getHeaders();
        int dlqRetryCount = headers.getOrDefault(RabbitMQConfig.DLQ_RETRY_HEADER, 0)
            instanceof Number n ? n.intValue() : 0;

        log.warn("DlqRetryConsumer: outboxId={} dlqRetry={}/{}",
            outboxId, dlqRetryCount + 1, RabbitMQConfig.DLQ_MAX_ATTEMPTS);

        if (dlqRetryCount < RabbitMQConfig.DLQ_MAX_ATTEMPTS) {

            // Build new headers with incremented counter
            Map<String, Object> newHeaders = new HashMap<>(headers);
            newHeaders.put(RabbitMQConfig.DLQ_RETRY_HEADER, dlqRetryCount + 1);

            // Re-publish the ID (not the entity) back to main exchange
            rabbit.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                "email.retry",
                outboxId,
                outMsg -> {
                    outMsg.getMessageProperties().setHeaders(newHeaders);
                    return outMsg;
                }
            );

            log.info("DlqRetryConsumer: re-published outboxId={} (dlqRetry {} of {})",
                outboxId, dlqRetryCount + 1, RabbitMQConfig.DLQ_MAX_ATTEMPTS);

            // ✅ ACK the DLQ message
            channel.basicAck(deliveryTag, false);

        } else {

            // All DLQ retries exhausted — mark FAILED permanently
            log.error("DlqRetryConsumer: PERMANENTLY FAILED outboxId={} — exhausted {} DLQ retries",
                outboxId, RabbitMQConfig.DLQ_MAX_ATTEMPTS);

            repo.findById(outboxId).ifPresent(msg -> {
                msg.setStatus(OutboxStatus.FAILED);
                msg.setProcessedAt(LocalDateTime.now());
                msg.setLastError("Exhausted " + RabbitMQConfig.DLQ_MAX_ATTEMPTS
                    + " DLQ retries at " + LocalDateTime.now());
                repo.save(msg);
            });

            // ❌ NACK to final dead queue
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
