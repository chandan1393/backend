package com.assignease.repository;


import com.assignease.service.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
}
