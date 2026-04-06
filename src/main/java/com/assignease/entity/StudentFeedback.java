package com.assignease.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="student_feedback")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentFeedback {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_id") private User student;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="enrollment_id") private Enrollment enrollment;
    private Integer rating; // 1-5
    @Column(length=1000) private String comment;
    private Boolean anonymous = false;
    private Boolean adminReviewed = false;
    private Boolean publishedAsTestimonial = false;
    @CreationTimestamp private LocalDateTime createdAt;
}
