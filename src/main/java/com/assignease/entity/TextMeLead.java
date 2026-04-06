package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "text_me_leads")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TextMeLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phoneNumber; // full number with country code

    private String countryCode;

    @Enumerated(EnumType.STRING)
    private LeadStatus status = LeadStatus.NEW;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime contactedAt;

    public enum LeadStatus {
        NEW, CONTACTED, CONVERTED, CLOSED
    }
}
