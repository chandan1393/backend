package com.assignease.repository;

import com.assignease.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /** Fetch PENDING messages to relay — oldest first, limited batch */
    List<OutboxMessage> findTop50ByStatusOrderByCreatedAtAsc(OutboxMessage.OutboxStatus status);

    /** Fetch messages stuck in PUBLISHED for too long (consumer crash recovery) */
    @Query("""
        SELECT m FROM OutboxMessage m
        WHERE m.status = 'PUBLISHED'
          AND m.createdAt < :cutoff
        ORDER BY m.createdAt ASC
        """)
    List<OutboxMessage> findStuckPublished(@Param("cutoff") LocalDateTime cutoff);

    /** Count pending messages (for monitoring / health check) */
    long countByStatus(OutboxMessage.OutboxStatus status);

    /** Delete old sent messages older than N days (cleanup job) */
    @Modifying
    @Query("DELETE FROM OutboxMessage m WHERE m.status = 'SENT' AND m.processedAt < :cutoff")
    int deleteOldSent(@Param("cutoff") LocalDateTime cutoff);
}
