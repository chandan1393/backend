package com.assignease.service;



import com.assignease.config.RabbitMQConfig;
import com.assignease.dto.EmailRequest;
import com.assignease.enums.EmailStatus;
import com.assignease.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void consume(EmailRequest request) {

        try {
            log.error("wait for 1 min");
            Thread.sleep(60_000); // 60,000 ms = 1 minute
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // good practice
        }


        EmailLog logEntity = new EmailLog();
        logEntity.setToEmail(request.getTo());
        logEntity.setSubject(request.getSubject());
        logEntity.setStatus(EmailStatus.PENDING);
        logEntity.setCreatedAt(LocalDateTime.now());

        emailLogRepository.save(logEntity);

        try {
            emailService.sendEmail(request);
            logEntity.setStatus(EmailStatus.SENT);

        } catch (Exception e) {
            log.error("Email failed: {}", e.getMessage());

            logEntity.setStatus(EmailStatus.FAILED);
            logEntity.setErrorMessage(e.getMessage());
            logEntity.setRetryCount(logEntity.getRetryCount() + 1);

            throw e;
        }

        emailLogRepository.save(logEntity);
    }
}
