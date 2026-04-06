package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "writers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Writer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    private String bio;

    private String expertise; // comma-separated subjects

    private Double rating = 0.0;

    private Integer completedAssignments = 0;

    private Boolean available = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
