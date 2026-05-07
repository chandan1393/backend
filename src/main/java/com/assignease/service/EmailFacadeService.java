package com.assignease.service;

import com.assignease.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * EmailFacadeService — the ONE class all business services should call to send emails.
 *
 * Instead of injecting OutboxService directly, callers inject EmailFacadeService.
 * This keeps the public API clean: you just pass the parameters you have,
 * and the facade decides how to route them (always via the outbox).
 *
 * Benefits:
 *  - Single import for all email needs
 *  - Easy to swap underlying delivery strategy without touching callers
 *  - Clear method names that describe intent, not implementation
 *
 * Usage (in any service):
 *   private final EmailFacadeService email;
 *
 *   email.welcomeNewUser(user.getEmail(), user.getFullName(), tempPassword);
 *   email.queryReceived(user.getEmail(), user.getFullName(), query.getId());
 */
@Service
@RequiredArgsConstructor
public class EmailFacadeService {

    private final OutboxService outbox;

    public void welcomeNewUser(String toEmail, String name, String tempPassword) {
        outbox.enqueueWelcomeEmail(toEmail, name, tempPassword);
    }

    public void queryReceived(String toEmail, String name, Long queryId) {
        outbox.enqueueQueryConfirmation(toEmail, name, queryId);
    }

    public void passwordReset(String toEmail, String resetToken) {
        outbox.enqueuePasswordReset(toEmail, resetToken);
    }

    public void classStatusChanged(String toEmail, String name,
                                   String className, String status) {
        outbox.enqueueStatusUpdate(toEmail, name, className, status);
    }

    public void paymentDueTomorrow(String toEmail, String name, String courseName,
                                   int installmentNum, String amount,
                                   String dueDate, String stripeLink) {
        outbox.enqueueInstallmentReminder(toEmail, name, courseName,
            installmentNum, amount, dueDate, stripeLink);
    }

    public void expertAssigned(String toEmail, String studentName, String courseName) {
        outbox.enqueueWriterAssigned(toEmail, studentName, courseName);
    }

    public void workReady(String toEmail, String studentName, String courseName) {
        outbox.enqueueWorkDelivered(toEmail, studentName, courseName);
    }

    public void notify(String toEmail, String subject, String bodyHtml) {
        outbox.enqueueNotification(toEmail, subject, bodyHtml);
    }
}
