package com.assignease.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_feedbacks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String studentName;

    @Column(length = 120)
    private String course;

    @Column(length = 80)
    private String location;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedbackText;

    @Column(nullable = false)
    private Integer rating;   // 1–5

    @Column(length = 80)
    private String avatar;    // initials e.g. "JW"

    /** true = show on landing page, false = hidden */
    @Column(nullable = false)
    private boolean visible = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
