package com.assignease.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="bug_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BugReport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reporter_id") private User reporter;
    private String reporterEmail; // for anonymous reports
    @Enumerated(EnumType.STRING) @Builder.Default private BugSeverity severity = BugSeverity.MEDIUM;
    private String category;   // ui, payment, login, tracker, other
    @Column(length=200) private String title;
    @Column(length=2000) private String description;
    private String pageUrl;    // which page the bug occurred
    private String browserInfo;
    @Enumerated(EnumType.STRING) @Builder.Default private BugStatus status = BugStatus.OPEN;
    private String adminNote;
    @CreationTimestamp private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public enum BugSeverity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum BugStatus { OPEN, IN_PROGRESS, RESOLVED, WONT_FIX }
}
