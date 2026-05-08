package com.assignease.service;

import com.assignease.entity.OutboxMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * EmailService sends emails via the Resend HTTP API (https://api.resend.com/emails).
 *
 * WHY HTTP API instead of SMTP:
 *   Railway blocks outbound TCP on ports 465 and 587 (SMTP ports).
 *   Port 443 (HTTPS) is always open. The Resend API runs on 443 so it works
 *   on Railway, Render, Heroku, Fly.io and every other cloud platform.
 *
 * All public methods are @Async — they run on the emailTaskExecutor thread pool
 * defined in AsyncConfig. The HTTP request thread returns immediately.
 *
 * Required Railway variable:
 *   RESEND_API_KEY = re_xxxxxxxxxxxxxxxxxxxx
 */
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final String FROM_ADDRESS   = "noreply@edupilothelp.com";
    private static final String FROM_NAME      = "EduAssist";
    private static final String BRAND_COLOR    = "#0d9488";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public EmailService(ObjectMapper objectMapper) {
        this.objectMapper   = objectMapper;
        this.restTemplate   = new RestTemplate();
    }

    // ── 1. Welcome ───────────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String toEmail, String name, String tempPassword) {
        String subject = "Welcome to EduAssist — Your Account is Ready";
        String body = "<p>Your EduAssist account has been created and is ready to use.</p>"
            + "<div style='background:#f0fdf9;border:1.5px solid #ccfbf1;border-radius:12px;"
            + "padding:20px;margin:20px 0'>"
            + "<p style='margin:0 0 8px'><strong>Email:</strong> " + toEmail + "</p>"
            + "<p style='margin:0'><strong>Temporary Password:</strong> "
            + "<code style='background:#e0fdf4;padding:3px 8px;border-radius:5px;"
            + "font-family:monospace;color:#0f766e'>" + tempPassword + "</code></p>"
            + "</div>"
            + "<p style='color:#64748b'>Please log in and change your password immediately.</p>";
        sendEmail(toEmail, subject, buildHtml("Welcome, " + name + "!", body,
            "Log In Now", frontendUrl + "/login"));
    }

    // ── 2. Query confirmation ─────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendQueryConfirmation(String toEmail, String name, Long queryId) {
        String subject = "We're on it, " + name + " — Your request is being reviewed";
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
            + "<p>Thanks for reaching out. An academic advisor will personally review your "
            + "request and get back to you within <strong>24 hours</strong>.</p>"
            + "<div style='background:#f8fafc;border-left:4px solid #0d9488;"
            + "border-radius:0 10px 10px 0;padding:16px 20px;margin:20px 0'>"
            + "<p style='font-weight:700;color:#0d9488;margin:0 0 10px'>What happens next</p>"
            + "<p style='margin:0 0 6px'>✦ &nbsp;Our team reviews your class details</p>"
            + "<p style='margin:0 0 6px'>✦ &nbsp;We send you a custom price and installment plan</p>"
            + "<p style='margin:0'>✦ &nbsp;You approve the plan — no charge until you do</p>"
            + "</div>"
            + "<p style='color:#94a3b8;font-size:.84rem'>Your login credentials have been sent "
            + "in a separate email so you can track this request from your dashboard.</p>";
        sendEmail(toEmail, subject, buildHtml("Request Received", body,
            "View My Dashboard", frontendUrl + "/dashboard"));
    }

    // ── 3. Password reset ─────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String subject  = "Reset Your EduAssist Password";
        String body = "<p>We received a request to reset the password for your EduAssist account.</p>"
            + "<p>Click the button below. This link expires in <strong>1 hour</strong>.</p>"
            + "<p style='color:#64748b;font-size:.84rem'>If you did not request this, "
            + "you can safely ignore this email.</p>";
        sendEmail(toEmail, subject, buildHtml("Password Reset Request", body,
            "Reset My Password", resetUrl));
    }

    // ── 4. Status update ─────────────────────────────────────────────────────

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
            + "<div style='font-size:1.05rem;font-weight:700;color:#0d9488'>"
            + readable + "</div></div>"
            + "<p style='color:#64748b;font-size:.87rem'>Log in to your dashboard to view "
            + "details and download any available files.</p>";
        sendEmail(toEmail, subject, buildHtml("Class Status Updated", body,
            "Go to Dashboard", frontendUrl + "/dashboard"));
    }

    // ── 5. Payment reminder ───────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendInstallmentReminder(String toEmail, String name, String courseName,
                                        int installmentNum, String amount,
                                        String dueDate, String stripeLink) {
        String payUrl  = (stripeLink != null && !stripeLink.isEmpty())
            ? stripeLink : frontendUrl + "/dashboard";
        String subject = "Payment Reminder: Installment #" + installmentNum + " Due Tomorrow";
        String body = "<p>Hi <strong>" + name + "</strong>,</p>"
            + "<p>Your payment for <strong>" + courseName + "</strong> is due tomorrow.</p>"
            + "<div style='background:#fffbeb;border:1.5px solid #fde68a;border-radius:12px;"
            + "padding:20px;margin:20px 0;text-align:center'>"
            + "<div style='font-size:.78rem;color:#92400e;text-transform:uppercase;"
            + "margin-bottom:8px'>Installment #" + installmentNum + "</div>"
            + "<div style='font-size:2rem;font-weight:800;color:#0f172a'>" + amount + "</div>"
            + "<div style='color:#d97706;margin-top:6px'>Due: " + dueDate + "</div>"
            + "</div>"
            + "<p style='color:#64748b;font-size:.87rem'>After payment, please upload your "
            + "receipt in your student dashboard for quick verification.</p>";
        sendEmail(toEmail, subject, buildHtml("Payment Due Tomorrow", body,
            "Pay Now via Stripe", payUrl));
    }

    // ── 6. Writer assigned ────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWriterAssigned(String toEmail, String studentName, String courseName) {
        String subject = "Your Expert Has Been Assigned — " + courseName;
        String body = "<p>Hi <strong>" + studentName + "</strong>,</p>"
            + "<p>A verified expert has been assigned to your class <strong>"
            + courseName + "</strong> and will begin working immediately.</p>"
            + "<p style='color:#64748b;font-size:.87rem'>All completed work is reviewed by "
            + "our admin team before being made available to you.</p>";
        sendEmail(toEmail, subject, buildHtml("Expert Assigned", body,
            "Track Progress", frontendUrl + "/dashboard"));
    }

    // ── 7. Work delivered ─────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWorkDelivered(String toEmail, String studentName, String courseName) {
        String subject = "Your Work is Ready to Download — " + courseName;
        String body = "<p>Hi <strong>" + studentName + "</strong>,</p>"
            + "<p>Your completed work for <strong>" + courseName
            + "</strong> has been reviewed and approved.</p>"
            + "<p>Your ZIP file is now available for download in your student dashboard.</p>";
        sendEmail(toEmail, subject, buildHtml("Work Delivered", body,
            "Download Now", frontendUrl + "/dashboard?tab=enrollments"));
    }

    // ── 8. Generic notification ───────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendNotification(String toEmail, String subject, String bodyHtml) {
        sendEmail(toEmail, subject, buildHtml("Notification", bodyHtml,
            "Go to Dashboard", frontendUrl + "/dashboard"));
    }

    // ── Shared dispatch — called by EmailConsumer and OutboxRelayJob ──────────

    /**
     * Reads an OutboxMessage and dispatches to the correct send method.
     * NOT @Async — the caller (consumer or relay job) decides threading.
     */
    void dispatchFromOutbox(OutboxMessage msg) throws Exception {
        Map<String, Object> p = parsePayload(msg.getPayloadJson());
        String to = msg.getToEmail();

        switch (msg.getEmailType()) {
            case WELCOME:
                sendWelcomeEmail(to, str(p, "name"), str(p, "tempPassword"));
                break;
            case QUERY_CONFIRMATION:
                sendQueryConfirmation(to, str(p, "name"), toLong(p.get("queryId")));
                break;
            case PASSWORD_RESET:
                sendPasswordResetEmail(to, str(p, "resetToken"));
                break;
            case ASSIGNMENT_STATUS_UPDATE:
                sendAssignmentStatusUpdate(to, str(p, "name"),
                    str(p, "assignmentTitle"), str(p, "status"));
                break;
            case INSTALLMENT_REMINDER:
                sendInstallmentReminder(to, str(p, "name"), str(p, "courseName"),
                    toInt(p.get("installmentNum")), str(p, "amount"),
                    str(p, "dueDate"), str(p, "stripeLink"));
                break;
            case WRITER_ASSIGNED:
                sendWriterAssigned(to, str(p, "studentName"), str(p, "courseName"));
                break;
            case WORK_DELIVERED:
                sendWorkDelivered(to, str(p, "studentName"), str(p, "courseName"));
                break;
            case NOTIFICATION:
                sendNotification(to, str(p, "subject"), str(p, "bodyHtml"));
                break;
            default:
                log.warn("EmailService.dispatchFromOutbox: unknown type {}", msg.getEmailType());
        }
    }

    Map<String, Object> parsePayload(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    // ── Core HTTP send via Resend API ─────────────────────────────────────────

    private void sendEmail(String toEmail, String subject, String htmlBody) {
        if (resendApiKey == null || resendApiKey.isEmpty()) {
            log.warn("RESEND_API_KEY not set — skipping email to={} subject={}", toEmail, subject);
            return;
        }

        try {
            // Build request headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            // Build JSON body for Resend API
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", FROM_NAME + " <" + FROM_ADDRESS + ">");
            payload.put("to",   new String[]{toEmail});
            payload.put("subject", subject);
            payload.put("html", htmlBody);

            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            // POST to Resend API — port 443 HTTPS — works on Railway
            ResponseEntity<String> response = restTemplate.postForEntity(
                RESEND_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent via Resend API to={} subject={}", toEmail, subject);
            } else {
                log.error("Resend API returned {} for to={} subject={}",
                    response.getStatusCode(), toEmail, subject);
            }

        } catch (Exception e) {
            log.error("Email send failed to={} subject={} error={}", toEmail, subject, e.getMessage());
            // Rethrow so EmailConsumer can NACK and trigger retry via DLQ
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    // ── HTML email template ───────────────────────────────────────────────────

    private String buildHtml(String heading, String bodyHtml, String ctaText, String ctaUrl) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            + "<body style='margin:0;padding:0;background:#f1f5f9;"
            + "font-family:Inter,-apple-system,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' style='padding:40px 16px'>"
            + "<tr><td align='center'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' style='max-width:560px'>"
            + "<tr><td style='background:" + BRAND_COLOR + ";border-radius:16px 16px 0 0;"
            + "padding:28px 32px;text-align:center'>"
            + "<div style='font-size:1.1rem;font-weight:700;color:white'>" + FROM_NAME + "</div>"
            + "<div style='font-size:.8rem;color:rgba(255,255,255,.65);margin-top:3px'>"
            + "Academic Support</div></td></tr>"
            + "<tr><td style='background:white;padding:32px;"
            + "border:1px solid #e2e8f0;border-top:none'>"
            + "<h2 style='margin:0 0 20px;font-size:1.1rem;font-weight:700;color:#0f172a'>"
            + heading + "</h2>"
            + "<div style='font-size:.9rem;color:#334155;line-height:1.7'>" + bodyHtml + "</div>"
            + "<div style='margin-top:24px;text-align:center'>"
            + "<a href='" + ctaUrl + "' style='display:inline-block;background:" + BRAND_COLOR
            + ";color:white;padding:12px 28px;border-radius:10px;text-decoration:none;"
            + "font-weight:700;font-size:.9rem'>" + ctaText + " &rarr;</a></div></td></tr>"
            + "<tr><td style='background:#f8fafc;border:1px solid #e2e8f0;border-top:none;"
            + "border-radius:0 0 16px 16px;padding:16px 32px;text-align:center'>"
            + "<p style='margin:0;font-size:.76rem;color:#94a3b8'>"
            + "EduAssist &middot; support&#64;edupilothelp.com<br>"
            + "You are receiving this because you have an account with EduAssist.</p>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatStatus(String status) {
        if (status == null) {
            return "Updated";
        }
        switch (status.toUpperCase()) {
            case "SUBMITTED":           return "Submitted — Under Review";
            case "PAYMENT_PLAN_SENT":   return "Payment Plan Ready";
            case "IN_PROGRESS":         return "In Progress";
            case "DELIVERED":           return "Work Delivered — Download Available";
            case "COMPLETED":           return "Completed";
            case "REVISION_REQUESTED":  return "Revision Requested";
            default:                    return status.replace("_", " ").toLowerCase();
        }
    }

    private String str(Map<String, Object> p, String key) {
        Object value = p.get(key);
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
