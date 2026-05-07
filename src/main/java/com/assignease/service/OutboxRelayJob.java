package com.assignease.service;

import com.assignease.config.RabbitMQConfig;
import com.assignease.entity.OutboxMessage;
import com.assignease.entity.OutboxMessage.OutboxStatus;
import com.assignease.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OutboxRelayJob — polls DB for PENDING outbox rows and publishes to RabbitMQ.
 *
 * KEY FIX: publishes only the message ID (Long), NOT the full OutboxMessage entity.
 *
 * Why only the ID?
 *   Publishing a JPA entity causes "Failed to convert Message content" because
 *   Jackson cannot serialize Hibernate proxies, lazy collections, and internal
 *   Hibernate state attached to the entity. The entity is NOT a plain POJO.
 *
 *   The consumer re-fetches the full row from the DB by ID. This also ensures
 *   the consumer always has the freshest data, not a stale snapshot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private static final int MAX_RETRY     = 5;
    private static final int STUCK_MINUTES = 5;

    private final OutboxMessageRepository repo;
    private final RabbitTemplate          rabbit;

    // ── Main relay: every 30 seconds ─────────────────────────────────────────
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    @Transactional
    public void relayPending() {
        List<OutboxMessage> pending = repo.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("OutboxRelay: processing {} pending message(s)", pending.size());
        int published = 0, failed = 0;

        for (OutboxMessage msg : pending) {
            try {
                publishId(msg);
                msg.setStatus(OutboxStatus.PUBLISHED);
                repo.save(msg);
                published++;
            } catch (AmqpException e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setLastError(e.getMessage());
                if (msg.getRetryCount() >= MAX_RETRY) {
                    msg.setStatus(OutboxStatus.FAILED);
                    failed++;
                    log.error("OutboxRelay: FAILED after {} retries → id={} to={}",
                        MAX_RETRY, msg.getId(), msg.getToEmail());
                } else {
                    log.warn("OutboxRelay: AMQP error (retry {}/{}) → id={}: {}",
                        msg.getRetryCount(), MAX_RETRY, msg.getId(), e.getMessage());
                }
                repo.save(msg);
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setLastError(e.getMessage());
                repo.save(msg);
                log.error("OutboxRelay: unexpected error id={}: {}", msg.getId(), e.getMessage());
            }
        }

        if (published > 0 || failed > 0)
            log.info("OutboxRelay: published={} failed={}", published, failed);
    }

    // ── Recovery: re-queue stuck PUBLISHED rows every 5 minutes ──────────────
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void recoverStuck() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_MINUTES);
        List<OutboxMessage> stuck = repo.findStuckPublished(cutoff);
        if (stuck.isEmpty()) return;

        log.warn("OutboxRelay: recovering {} stuck PUBLISHED message(s)", stuck.size());
        stuck.forEach(msg -> {
            msg.setStatus(OutboxStatus.PENDING);
            msg.setLastError("Stuck recovery at " + LocalDateTime.now());
            repo.save(msg);
        });
    }

    // ── Cleanup: delete SENT rows older than 7 days at 2 AM ──────────────────
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupSent() {
        int deleted = repo.deleteOldSent(LocalDateTime.now().minusDays(7));
        if (deleted > 0) log.info("OutboxRelay: cleaned {} old SENT records", deleted);
    }

    // ── Publish only the ID — consumer fetches full row from DB ──────────────
    private void publishId(OutboxMessage msg) {
        String routingKey = "email." + msg.getEmailType().name().toLowerCase();
        // Send just the Long ID — avoids Hibernate proxy serialization errors
        rabbit.convertAndSend(RabbitMQConfig.EMAIL_EXCHANGE, routingKey, msg.getId());
    }
}
