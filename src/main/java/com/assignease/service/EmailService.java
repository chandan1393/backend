package com.assignease.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final JavaMailSender mailSender;

    @Async
    public void sendWelcomeAndQueryEmail(String toEmail, String name, String tempPassword, Long queryId) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Welcome to AssignEase – Query Received & Account Created ");

            String loginLink = frontendUrl + "/login";

            message.setText(
                    "Dear " + name + ",\n\n" +

                            "Welcome to AssignEase! 🎉 Your account has been successfully created.\n\n" +

                            "🔐 Login Details:\n" +
                            "Email: " + toEmail + "\n" +
                            "Temporary Password: " + tempPassword + "\n\n" +

                            "👉 Login here: " + loginLink + "\n\n" +

                            "📩 Your query has also been received successfully.\n" +
                            "Query ID: #" + queryId + "\n" +
                            "Our team will get back to you within 24 hours.\n\n" +

                            "⚠️ Please login and change your password immediately for security.\n\n" +

                            "Best regards,\n" +
                            "AssignEase Team"
            );

            mailSender.send(message);
            log.info("Welcome + Query email sent to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send welcome+query email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendAssignmentStatusUpdate(String toEmail, String name, String assignmentTitle, String status) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Assignment Update – " + assignmentTitle);
            message.setText(
                "Dear " + name + ",\n\n" +
                "Your assignment '" + assignmentTitle + "' has been updated.\n" +
                "Current Status: " + status + "\n\n" +
                "Login to your dashboard to view details.\n"
                + frontendUrl + "/dashboard\n\n" +
                "Best regards,\nAssignEase Team"
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send assignment update email: {}", e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Password Reset – AssignEase");
            message.setText(
                    "Dear User,\n\n" +
                            "You requested a password reset. Use the link below:\n\n" +
                            frontendUrl + "/reset-password?token=" + resetToken + "\n\n" +
                            "This link expires in 1 hour. If you didn't request this, ignore this email.\n\n" +
                            "Best regards,\nAssignEase Team"
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
        }
    }

    @Async
    public void sendInstallmentReminder(String toEmail, String name, String courseName,
            int installmentNum, String amount, String dueDate, String stripeLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(toEmail);
            helper.setSubject("⏰ Payment Reminder: Installment #" + installmentNum + " due tomorrow");
            String link = stripeLink != null && !stripeLink.isEmpty() ? stripeLink : "#";
            helper.setText("""
                <html><body style="font-family:Inter,sans-serif;background:#f8fafc;padding:32px">
                <div style="max-width:560px;margin:0 auto;background:white;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08)">
                  <div style="background:linear-gradient(135deg,#f59e0b,#d97706);padding:28px;text-align:center">
                    <h1 style="color:#0f172a;margin:0;font-size:1.4rem">⏰ Payment Due Tomorrow</h1>
                  </div>
                  <div style="padding:32px">
                    <p>Hi <strong>%s</strong>,</p>
                    <p>This is a reminder that your <strong>Installment #%d</strong> for <strong>%s</strong> is due tomorrow.</p>
                    <div style="background:#fffbeb;border:1.5px solid #fde68a;border-radius:12px;padding:20px;margin:20px 0;text-align:center">
                      <div style="font-size:2rem;font-weight:800;color:#0f172a">%s</div>
                      <div style="color:#d97706;font-size:.9rem;margin-top:4px">Due: %s</div>
                    </div>
                    <a href="%s" style="display:block;text-align:center;background:#0d9488;color:white;padding:14px;border-radius:10px;text-decoration:none;font-weight:700;font-size:.95rem">Pay Now via Stripe →</a>
                    <p style="margin-top:20px;font-size:.84rem;color:#64748b">After paying, please upload your receipt in your student dashboard for verification.</p>
                  </div>
                  <div style="background:#f8fafc;padding:16px;text-align:center;font-size:.78rem;color:#94a3b8">
                    This is an automated reminder from AssignEase
                  </div>
                </div></body></html>
                """.formatted(name, installmentNum, courseName, amount, dueDate, link), true);
            mailSender.send(message);
            log.info("Installment reminder sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send installment reminder: {}", e.getMessage());
        }
    }
}