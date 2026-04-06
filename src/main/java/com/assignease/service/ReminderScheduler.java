package com.assignease.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final EnrollmentService enrollmentService;

    /** Runs every day at 9 AM to send payment reminders for installments due tomorrow */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        log.info("Running daily payment reminder scheduler...");
        enrollmentService.sendInstallmentReminders();
    }
}
