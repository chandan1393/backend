package com.assignease.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="user_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
    private String sessionId;
    private String eventType;  // page_view, button_click, form_submit, payment_start, payment_success, payment_fail, login, logout, download
    private String page;
    private String element;    // button id / section name
    @Column(length=1000) private String metadata; // JSON string of extra data
    private String ipAddress;
    private String userAgent;
    @CreationTimestamp private LocalDateTime createdAt;
}
