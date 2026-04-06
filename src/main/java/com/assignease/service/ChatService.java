package com.assignease.service;

import com.assignease.entity.ChatMessage;
import com.assignease.entity.User;
import com.assignease.repository.ChatMessageRepository;
import com.assignease.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatRepo;
    private final UserRepository userRepository;

    public ChatMessage sendMessage(Long referenceId, String context, String senderEmail, String message) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatMessage msg = ChatMessage.builder()
                .referenceId(referenceId)
                .context(ChatMessage.ChatContext.valueOf(context))
                .sender(sender)
                .message(message.trim())
                .readByRecipient(false)
                .build();

        return chatRepo.save(msg);
    }

    public List<Map<String, Object>> getMessages(Long referenceId, String context) {
        return chatRepo.findByReferenceIdAndContextOrderByCreatedAtAsc(
                referenceId, ChatMessage.ChatContext.valueOf(context))
                .stream()
                .map(m -> Map.of(
                        "id", (Object) m.getId(),
                        "message", (Object) m.getMessage(),
                        "senderName", (Object) m.getSender().getFullName(),
                        "senderRole", (Object) m.getSender().getRole().name(),
                        "senderId", (Object) m.getSender().getId(),
                        "readByRecipient", (Object) m.isReadByRecipient(),
                        "createdAt", (Object) m.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());
    }

    public void markAsRead(Long referenceId, String context, Long userId) {
        chatRepo.markAsRead(referenceId, ChatMessage.ChatContext.valueOf(context), userId);
    }

    public long getUnreadCount(Long referenceId, String context, Long userId) {
        return chatRepo.countByReferenceIdAndContextAndReadByRecipientFalseAndSenderIdNot(
                referenceId, ChatMessage.ChatContext.valueOf(context), userId);
    }
    public Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}

