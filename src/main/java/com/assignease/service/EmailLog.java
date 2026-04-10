package com.assignease.service;


import com.assignease.enums.EmailStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String toEmail;
    private String subject;

    @Enumerated(EnumType.STRING)
    private EmailStatus status;

    private int retryCount = 0;

    @Column(length = 2000)
    private String errorMessage;

    private LocalDateTime createdAt;
}
