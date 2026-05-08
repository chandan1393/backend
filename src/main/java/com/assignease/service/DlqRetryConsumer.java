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
 * DlqRetryConsumer reads from the Dead-Letter Queue (DLQ).
 *
 * When EmailConsumer fails all Spring Retry attempts, the message
 * lands in the DLQ. This consumer checks how many times it has
 * already been replayed and decides:
 *
 *   Under DLQ_MAX_ATTEMPTS:
 *     - Increment x-dlq-retry-count header
 *     - Re-publish message ID to main exchange
 *     - ACK the DLQ message
 *
 *   Over DLQ_MAX_ATTEMPTS:
 *     - Mark the DB row as FAILED
 *     - NACK to the final dead queue (permanent failure)
 *
 * Uses dlqListenerContainerFactory (no Spring Retry) to avoid
 * infinite loops where DLQ retries trigger more DLQ retries.
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

        // Read how many DLQ retries have already happened
        Map<String, Object> headers = rawMessage.getMessageProperties().getHeaders();
        Object countValue = headers.get(RabbitMQConfig.DLQ_RETRY_HEADER);
        int dlqRetryCount = 0;
        if (countValue instanceof Number) {
            dlqRetryCount = ((Number) countValue).intValue();
        }

        log.warn("DlqRetryConsumer: id={} dlqRetry={}/{}",
            outboxId, dlqRetryCount + 1, RabbitMQConfig.DLQ_MAX_ATTEMPTS);

        if (dlqRetryCount < RabbitMQConfig.DLQ_MAX_ATTEMPTS) {
            replayToMainQueue(outboxId, headers, dlqRetryCount, channel, deliveryTag);
        } else {
            permanentlyFail(outboxId, channel, deliveryTag);
        }
    }

    private void replayToMainQueue(Long outboxId, Map<String, Object> originalHeaders,
                                   int currentCount, Channel channel, long deliveryTag)
            throws IOException {

        // Build new headers with incremented counter
        Map<String, Object> newHeaders = new HashMap<>(originalHeaders);
        newHeaders.put(RabbitMQConfig.DLQ_RETRY_HEADER, currentCount + 1);

        // Re-publish the ID (not the entity) to the main exchange
        rabbit.convertAndSend(
            RabbitMQConfig.EMAIL_EXCHANGE,
            "email.retry",
            outboxId,
            message -> {
                message.getMessageProperties().setHeaders(newHeaders);
                return message;
            }
        );

        log.info("DlqRetryConsumer: re-published id={} (dlqRetry {} of {})",
            outboxId, currentCount + 1, RabbitMQConfig.DLQ_MAX_ATTEMPTS);

        // ACK the DLQ message — it has been re-queued in main queue
        channel.basicAck(deliveryTag, false);
    }

    private void permanentlyFail(Long outboxId, Channel channel, long deliveryTag)
            throws IOException {

        log.error("DlqRetryConsumer: PERMANENTLY FAILED id={} after {} DLQ retries. "
            + "Sending to dead queue. Check outbox_messages table for details.",
            outboxId, RabbitMQConfig.DLQ_MAX_ATTEMPTS);

        // Mark the DB row as FAILED for visibility
        repo.findById(outboxId).ifPresent(msg -> {
            msg.setStatus(OutboxStatus.FAILED);
            msg.setProcessedAt(LocalDateTime.now());
            msg.setLastError("Exhausted " + RabbitMQConfig.DLQ_MAX_ATTEMPTS
                + " DLQ retries. Failed at: " + LocalDateTime.now());
            repo.save(msg);
        });

        // NACK without requeue — goes to final dead queue (eduassist.email.queue.dead)
        channel.basicNack(deliveryTag, false, false);
    }
}
