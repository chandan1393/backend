package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The assignment or query this chat belongs to
    @Column(nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatContext context; // STUDENT_ADMIN or ADMIN_WRITER

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    private boolean readByRecipient = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum ChatContext {
        STUDENT_ADMIN,  // Between student and admin (for assignment)
        ADMIN_WRITER    // Between admin and writer (for assignment)
    }
}
