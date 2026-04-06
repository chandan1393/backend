package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "writer_invitations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WriterInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id", nullable = false)
    private User writer;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    private String message;        // admin's note to writer
    private String writerNote;     // writer's acceptance/decline note

    @CreationTimestamp private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    // Admin grants credential access only after accepting + admin approval
    @Builder.Default
    private Boolean credentialsApproved = false;
    private LocalDateTime credentialsApprovedAt;

    public enum InvitationStatus {
        PENDING,    // sent, not yet responded
        ACCEPTED,   // writer accepted
        DECLINED    // writer declined
    }
}
