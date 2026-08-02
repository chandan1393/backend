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

    /** Sent when an admin creates an order on a student's behalf. */
    public void orderCreatedForStudent(String toEmail, String name, String title,
                                       Double price, java.time.LocalDateTime deadline) {
        StringBuilder b = new StringBuilder();
        b.append("<p>Hi ").append(name == null ? "there" : name).append(",</p>");
        b.append("<p>We've set up a new order on your account:</p>");
        b.append("<p><strong>").append(title).append("</strong></p>");
        b.append("<ul>");
        if (price != null && price > 0) {
            b.append("<li>Price: <strong>$").append(String.format("%.2f", price)).append("</strong></li>");
        }
        if (deadline != null) {
            b.append("<li>Deadline: ").append(deadline.toLocalDate()).append("</li>");
        }
        b.append("</ul>");
        if (price != null && price > 0) {
            b.append("<p>Log in to your dashboard to review the details and complete payment.</p>");
        } else {
            b.append("<p>Log in to your dashboard to review the details. We'll confirm pricing shortly.</p>");
        }
        b.append("<p>— The EduPilotHelp Team</p>");
        notify(toEmail, "Your new order: " + title, b.toString());
    }
}
