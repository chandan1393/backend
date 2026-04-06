package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "testimonials")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Testimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String studentName;

    private String course;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    private String avatar; // initials like "PS"

    private Integer rating = 5;

    private Boolean active = true;

    private Integer displayOrder = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
