package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    private boolean read = false;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private Long referenceId; // assignment id or query id

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum NotificationType {
        ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED, ASSIGNMENT_COMPLETED,
        QUERY_REPLY, QUERY_CREATED, GENERAL
    }
}
