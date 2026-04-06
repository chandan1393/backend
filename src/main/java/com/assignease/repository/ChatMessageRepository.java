package com.assignease.repository;

import com.assignease.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByReferenceIdAndContextOrderByCreatedAtAsc(
            Long referenceId, ChatMessage.ChatContext context);

    long countByReferenceIdAndContextAndReadByRecipientFalseAndSenderIdNot(
            Long referenceId, ChatMessage.ChatContext context, Long senderId);

    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.readByRecipient = true WHERE m.referenceId = :refId AND m.context = :ctx AND m.sender.id <> :userId")
    void markAsRead(Long refId, ChatMessage.ChatContext ctx, Long userId);
}
