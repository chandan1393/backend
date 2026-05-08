package com.assignease.service;

import com.assignease.config.RabbitMQConfig;
import com.assignease.entity.OutboxMessage;
import com.assignease.entity.OutboxMessage.OutboxStatus;
import com.assignease.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OutboxRelayJob polls the outbox_messages table every 30 seconds.
 *
 * Two delivery paths:
 *   1. RabbitMQ UP   -> publishId() -> queue -> EmailConsumer -> send
 *   2. RabbitMQ DOWN -> dispatchFromOutbox() -> EmailService directly
 *
 * The AtomicBoolean rabbitAvailable tracks which path to use.
 * It flips automatically when publish fails or succeeds.
 *
 * Either way the row gets marked SENT in the DB — no data loss.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private static final int MAX_RETRY     = 5;
    private static final int STUCK_MINUTES = 5;

    private final OutboxMessageRepository repo;
    private final RabbitTemplate          rabbit;
    private final EmailService            emailService;

    // true = use RabbitMQ, false = send directly
    // Flips automatically when publish fails or succeeds
    private final AtomicBoolean rabbitAvailable = new AtomicBoolean(true);

    // ── Main relay — runs every 30 seconds ───────────────────────────────────

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    @Transactional
    public void relayPending() {
        List<OutboxMessage> pending = repo.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        String path = rabbitAvailable.get() ? "RabbitMQ" : "DIRECT (RabbitMQ down)";
        log.info("OutboxRelay: processing {} message(s) via {}", pending.size(), path);

        int sent   = 0;
        int failed = 0;

        for (OutboxMessage msg : pending) {
            boolean success;
            if (rabbitAvailable.get()) {
                success = tryViaRabbit(msg);
            } else {
                success = tryDirect(msg);
            }

            if (success) {
                sent++;
            } else {
                failed++;
            }
        }

        log.info("OutboxRelay: done sent={} failed={} rabbit={}", sent, failed, rabbitAvailable.get());
    }

    // ── Path A: publish ID to RabbitMQ queue ─────────────────────────────────

    private boolean tryViaRabbit(OutboxMessage msg) {
        try {
            String routingKey = "email." + msg.getEmailType().name().toLowerCase();
            rabbit.convertAndSend(RabbitMQConfig.EMAIL_EXCHANGE, routingKey, msg.getId());

            msg.setStatus(OutboxStatus.PUBLISHED);
            repo.save(msg);
            rabbitAvailable.set(true);
            return true;

        } catch (Exception e) {
            // RabbitMQ went down — switch to direct path
            log.warn("OutboxRelay: RabbitMQ unavailable (id={}), switching to direct. error={}",
                msg.getId(), e.getMessage());
            rabbitAvailable.set(false);
            return tryDirect(msg);
        }
    }

    // ── Path B: call EmailService directly when RabbitMQ is down ─────────────

    private boolean tryDirect(OutboxMessage msg) {
        try {
            // dispatchFromOutbox is NOT @Async here — we need it to finish
            // so we can mark the row SENT in the same transaction
            emailService.dispatchFromOutbox(msg);

            msg.setStatus(OutboxStatus.SENT);
            msg.setProcessedAt(LocalDateTime.now());
            repo.save(msg);

            log.info("OutboxRelay: DIRECT sent id={} type={} to={}",
                msg.getId(), msg.getEmailType(), msg.getToEmail());
            return true;

        } catch (Exception e) {
            incrementRetry(msg, e.getMessage());
            return false;
        }
    }

    // ── Recovery: reset stuck PUBLISHED rows every 5 minutes ─────────────────

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void recoverStuck() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_MINUTES);
        List<OutboxMessage> stuck = repo.findStuckPublished(cutoff);

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("OutboxRelay: recovering {} stuck PUBLISHED message(s)", stuck.size());

        for (OutboxMessage msg : stuck) {
            msg.setStatus(OutboxStatus.PENDING);
            msg.setLastError("Stuck recovery at " + LocalDateTime.now());
            repo.save(msg);
        }
    }

    // ── Cleanup: delete SENT rows older than 7 days — runs daily at 2 AM ─────

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupSent() {
        int deleted = repo.deleteOldSent(LocalDateTime.now().minusDays(7));
        if (deleted > 0) {
            log.info("OutboxRelay: deleted {} old SENT records", deleted);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void incrementRetry(OutboxMessage msg, String errorMessage) {
        msg.setRetryCount(msg.getRetryCount() + 1);
        msg.setLastError(truncate(errorMessage, 490));

        if (msg.getRetryCount() >= MAX_RETRY) {
            msg.setStatus(OutboxStatus.FAILED);
            log.error("OutboxRelay: FAILED after {} retries id={} to={}",
                MAX_RETRY, msg.getId(), msg.getToEmail());
        } else {
            log.warn("OutboxRelay: retry {}/{} id={} error={}",
                msg.getRetryCount(), MAX_RETRY, msg.getId(), errorMessage);
        }

        repo.save(msg);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
