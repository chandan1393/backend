package com.assignease.service;

import com.assignease.entity.OutboxMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper   objectMapper;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private static final String FROM_ADDR   = "noreply@edupilothelp.com";
    private static final String FROM_NAME   = "EduAssist";
    private static final String BRAND_COLOR = "#0d9488";

    // ── 1. Welcome email ──────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String toEmail, String name, String tempPassword) {
        String subject = "Welcome to EduAssist — Your Account is Ready";
        String body = "<p>Your EduAssist account has been created.</p>"
            + "<div style='background:#f0fdf9;border:1.5px solid #ccfbf1;border-radius:12px;padding:20px;margin:20px 0'>"
            + "<p><strong>Email:</strong> " + toEmail + "</p>"
            + "<p><strong>Temporary Password:</strong> <code style='background:#e0fdf4;padding:3px 8px;"
            + "border-radius:5px;font-family:monospace;color:#0f766e'>" + tempPassword + "</code></p>"
            + "</div>"
            + "<p>Please log in and change your password immediately.</p>";
        String html = buildEmail("Welcome, " + name + "!", body, "Log In Now", frontendUrl + "/login");
        send(toEmail, subject, html);
    }

    // ── 2. Query confirmation ─────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendQueryConfirmation(String toEmail, String name, Long queryId) {
        String subject = "We're on it, " + name + " — Your request is being reviewed";
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
            + "<p>Thanks for reaching out to EduAssist. We received your request and an academic "
            + "advisor will personally review it and get back to you within <strong>24 hours</strong>.</p>"
            + "<div style='background:#f8fafc;border-left:4px solid #0d9488;border-radius:0 10px 10px 0;"
            + "padding:16px 20px;margin:20px 0'>"
            + "<p style='font-weight:700;color:#0d9488;margin-bottom:8px'>What happens next</p>"
            + "<p>✦ &nbsp;Our team reviews your class details</p>"
            + "<p>✦ &nbsp;We send you a custom price and installment plan</p>"
            + "<p>✦ &nbsp;You approve the plan — no charge until you do</p>"
            + "</div>"
            + "<p style='color:#94a3b8;font-size:.84rem'>Your login credentials have been sent in a "
            + "separate email so you can track this request from your dashboard.</p>";
        String html = buildEmail("Request Received", body, "View My Dashboard", frontendUrl + "/dashboard");
        send(toEmail, subject, html);
    }

    // ── 3. Password reset ─────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String subject  = "Reset Your EduAssist Password";
        String body = "<p>We received a request to reset the password for your EduAssist account.</p>"
            + "<p>Click the button below to set a new password. This link expires in <strong>1 hour</strong>.</p>"
            + "<p style='color:#64748b;font-size:.84rem'>If you did not request this, "
            + "you can safely ignore this email.</p>";
        String html = buildEmail("Password Reset Request", body, "Reset My Password", resetUrl);
        send(toEmail, subject, html);
    }

    // ── 4. Assignment / class status update ───────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendAssignmentStatusUpdate(String toEmail, String name,
                                           String assignmentTitle, String status) {
        String readable = formatStatus(status);
        String subject  = "Class Update: " + assignmentTitle + " — " + readable;
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
            + "<p>There is an update on your class <strong>" + assignmentTitle + "</strong>.</p>"
            + "<div style='background:#f0fdf9;border:1.5px solid #ccfbf1;border-radius:12px;"
            + "padding:18px;margin:16px 0;text-align:center'>"
            + "<div style='font-size:.78rem;color:#64748b;text-transform:uppercase;"
            + "letter-spacing:.07em;margin-bottom:6px'>Current Status</div>"
            + "<div style='font-size:1.05rem;font-weight:700;color:#0d9488'>" + readable + "</div>"
            + "</div>"
            + "<p style='color:#64748b;font-size:.87rem'>Log in to your dashboard to "
            + "view details and download any available files.</p>";
        String html = buildEmail("Class Status Updated", body, "Go to Dashboard", frontendUrl + "/dashboard");
        send(toEmail, subject, html);
    }

    // ── 5. Installment payment reminder ──────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendInstallmentReminder(String toEmail, String name, String courseName,
                                        int installmentNum, String amount,
                                        String dueDate, String stripeLink) {
        String payLink = (stripeLink != null && !stripeLink.isEmpty())
            ? stripeLink : frontendUrl + "/dashboard";
        String subject = "Payment Reminder: Installment #" + installmentNum + " Due Tomorrow";
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
            + "<p>This is a reminder that your payment for <strong>" + courseName
            + "</strong> is due tomorrow.</p>"
            + "<div style='background:#fffbeb;border:1.5px solid #fde68a;border-radius:12px;"
            + "padding:20px;margin:20px 0;text-align:center'>"
            + "<div style='font-size:.78rem;color:#92400e;text-transform:uppercase;"
            + "letter-spacing:.07em;margin-bottom:8px'>Installment #" + installmentNum + "</div>"
            + "<div style='font-size:2rem;font-weight:800;color:#0f172a'>" + amount + "</div>"
            + "<div style='color:#d97706;font-size:.88rem;margin-top:6px'>Due: " + dueDate + "</div>"
            + "</div>"
            + "<p style='color:#64748b;font-size:.87rem'>After payment, please upload your "
            + "receipt in your student dashboard for quick verification.</p>";
        String html = buildEmail("Payment Due Tomorrow", body, "Pay Now via Stripe", payLink);
        send(toEmail, subject, html);
    }

    // ── 6. Writer assigned ────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWriterAssigned(String toEmail, String studentName, String courseName) {
        String subject = "Your Expert Has Been Assigned — " + courseName;
        String body = "<p>Hi <strong>" + studentName + "</strong>,</p>"
            + "<p>A verified expert has been assigned to your class <strong>"
            + courseName + "</strong>.</p>"
            + "<p>Your expert has been granted access and will begin working on your class "
            + "immediately. You can track progress from your student dashboard.</p>"
            + "<p style='color:#64748b;font-size:.87rem'>All completed work will be reviewed "
            + "by our admin team before being made available to you.</p>";
        String html = buildEmail("Expert Assigned", body, "Track Progress", frontendUrl + "/dashboard");
        send(toEmail, subject, html);
    }

    // ── 7. Work delivered ─────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWorkDelivered(String toEmail, String studentName, String courseName) {
        String subject = "Your Work is Ready to Download — " + courseName;
        String body = "<p>Hi <strong>" + studentName + "</strong>,</p>"
            + "<p>Your completed work for <strong>" + courseName
            + "</strong> has been reviewed and approved by our admin team.</p>"
            + "<p>Your ZIP file is now available for download in your student dashboard.</p>";
        String html = buildEmail("Work Delivered", body,
            "Download Now", frontendUrl + "/dashboard?tab=enrollments");
        send(toEmail, subject, html);
    }

    // ── 8. Generic notification ───────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendNotification(String toEmail, String subject, String bodyHtml) {
        String html = buildEmail("Notification", bodyHtml, "Go to Dashboard", frontendUrl + "/dashboard");
        send(toEmail, subject, html);
    }

    // ── Shared dispatch (used by EmailConsumer and OutboxRelayJob) ────────────

    /**
     * Reads the JSON payload from an outbox row and calls the right send method.
     * Package-private so EmailConsumer and OutboxRelayJob can call it.
     * NOT @Async — callers decide whether to run async or sync.
     */
    void dispatchFromOutbox(OutboxMessage msg) throws Exception {
        Map<String, Object> payload = parsePayload(msg.getPayloadJson());
        String toEmail = msg.getToEmail();

        switch (msg.getEmailType()) {
            case WELCOME:
                sendWelcomeEmail(toEmail, str(payload, "name"), str(payload, "tempPassword"));
                break;
            case QUERY_CONFIRMATION:
                long queryId = toLong(payload.get("queryId"));
                sendQueryConfirmation(toEmail, str(payload, "name"), queryId);
                break;
            case PASSWORD_RESET:
                sendPasswordResetEmail(toEmail, str(payload, "resetToken"));
                break;
            case ASSIGNMENT_STATUS_UPDATE:
                sendAssignmentStatusUpdate(toEmail,
                    str(payload, "name"),
                    str(payload, "assignmentTitle"),
                    str(payload, "status"));
                break;
            case INSTALLMENT_REMINDER:
                int instNum = toInt(payload.get("installmentNum"));
                sendInstallmentReminder(toEmail,
                    str(payload, "name"),
                    str(payload, "courseName"),
                    instNum,
                    str(payload, "amount"),
                    str(payload, "dueDate"),
                    str(payload, "stripeLink"));
                break;
            case WRITER_ASSIGNED:
                sendWriterAssigned(toEmail, str(payload, "studentName"), str(payload, "courseName"));
                break;
            case WORK_DELIVERED:
                sendWorkDelivered(toEmail, str(payload, "studentName"), str(payload, "courseName"));
                break;
            case NOTIFICATION:
                sendNotification(toEmail, str(payload, "subject"), str(payload, "bodyHtml"));
                break;
            default:
                log.warn("EmailService.dispatchFromOutbox: unknown type {}", msg.getEmailType());
        }
    }

    Map<String, Object> parsePayload(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_ADDR, FROM_NAME);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to={} subject={}", toEmail, subject);
        } catch (MessagingException e) {
            log.error("Email failed to={} subject={} error={}", toEmail, subject, e.getMessage());
        } catch (Exception e) {
            log.error("Email unexpected error to={} error={}", toEmail, e.getMessage());
        }
    }

    private String buildEmail(String heading, String bodyHtml, String ctaText, String ctaUrl) {
        return "<!DOCTYPE html><html lang='en'><head>"
            + "<meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "</head>"
            + "<body style='margin:0;padding:0;background:#f1f5f9;"
            + "font-family:Inter,-apple-system,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' style='padding:40px 16px'>"
            + "<tr><td align='center'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' style='max-width:560px'>"

            // Header
            + "<tr><td style='background:" + BRAND_COLOR + ";border-radius:16px 16px 0 0;"
            + "padding:28px 32px;text-align:center'>"
            + "<div style='font-size:1.1rem;font-weight:700;color:white'>" + FROM_NAME + "</div>"
            + "<div style='font-size:.8rem;color:rgba(255,255,255,.65);margin-top:3px'>Academic Support</div>"
            + "</td></tr>"

            // Body
            + "<tr><td style='background:white;padding:32px;border:1px solid #e2e8f0;border-top:none'>"
            + "<h2 style='margin:0 0 20px;font-size:1.1rem;font-weight:700;color:#0f172a'>"
            + heading + "</h2>"
            + "<div style='font-size:.9rem;color:#334155;line-height:1.7'>" + bodyHtml + "</div>"
            + "<div style='margin-top:24px;text-align:center'>"
            + "<a href='" + ctaUrl + "' style='display:inline-block;background:" + BRAND_COLOR
            + ";color:white;padding:12px 28px;border-radius:10px;text-decoration:none;"
            + "font-weight:700;font-size:.9rem'>" + ctaText + " &rarr;</a>"
            + "</div>"
            + "</td></tr>"

            // Footer
            + "<tr><td style='background:#f8fafc;border:1px solid #e2e8f0;border-top:none;"
            + "border-radius:0 0 16px 16px;padding:16px 32px;text-align:center'>"
            + "<p style='margin:0;font-size:.76rem;color:#94a3b8'>"
            + "EduAssist &middot; support&#64;edupilothelp.com<br>"
            + "You are receiving this because you have an account with EduAssist."
            + "</p>"
            + "</td></tr>"

            + "</table></td></tr></table>"
            + "</body></html>";
    }

    private String formatStatus(String status) {
        if (status == null) {
            return "Updated";
        }
        switch (status.toUpperCase()) {
            case "SUBMITTED":          return "Submitted — Under Review";
            case "PAYMENT_PLAN_SENT":  return "Payment Plan Ready";
            case "IN_PROGRESS":        return "In Progress";
            case "DELIVERED":          return "Work Delivered — Download Available";
            case "COMPLETED":          return "Completed";
            case "REVISION_REQUESTED": return "Revision Requested";
            default:                   return status.replace("_", " ").toLowerCase();
        }
    }

    private String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : "";
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 1;
    }
}
