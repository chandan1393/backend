package com.assignease.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * EmailService — all methods are @Async.
 *
 * Every public method returns void and runs on the "emailTaskExecutor"
 * thread pool defined in AsyncConfig. The HTTP request thread returns
 * immediately; email is sent in the background.
 *
 * Failures are caught, logged, and silently swallowed — email delivery
 * is best-effort and must never cause an API response to fail.
 *
 * Resend SMTP setup:
 *   host: smtp.resend.com
 *   port: 465 (SSL) or 587 (TLS)
 *   username: resend  (literal string)
 *   password: re_xxxx  (your Resend API key)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${spring.mail.username:noreply@edupilothelp.com}")
    private String fromEmail;

    private static final String FROM_NAME  = "EduAssist";
    private static final String FROM_ADDR  = "noreply@edupilothelp.com";
    private static final String BRAND      = "EduAssist";
    private static final String BRAND_COLOR = "#0d9488";

    // ── 1. Welcome / Account Created ─────────────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String toEmail, String name, String tempPassword) {
        String subject = "Welcome to EduAssist — Your Account is Ready";
        String html = baseTemplate(
            "👋 Welcome, " + name + "!",
            """
            <p style="margin:0 0 16px">Your EduAssist account has been created and is ready to use.</p>
            <div style="background:#f0fdf9;border:1.5px solid #ccfbf1;border-radius:12px;padding:20px;margin:20px 0">
              <p style="margin:0 0 8px;font-size:.84rem;color:#64748b;text-transform:uppercase;letter-spacing:.05em;font-weight:700">Your Login Details</p>
              <p style="margin:0 0 6px"><strong>Email:</strong> %s</p>
              <p style="margin:0"><strong>Temporary Password:</strong> <code style="background:#e0fdf4;padding:3px 8px;border-radius:5px;font-family:monospace;color:#0f766e">%s</code></p>
            </div>
            <p style="margin:0 0 20px;color:#64748b;font-size:.9rem">⚠️ Please log in and change your password immediately for your security.</p>
            """.formatted(toEmail, tempPassword),
            "Log In Now →",
            frontendUrl + "/login"
        );
        send(toEmail, subject, html);
    }

    // ── 2. Query Confirmation ─────────────────────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendQueryConfirmation(String toEmail, String name, Long queryId) {
        String subject = "We're on it, " + name + " — Your request is being reviewed";
        String html = baseTemplate(
            "Request Received",
            """
            <p style="margin:0 0 16px">Hi <strong>%s</strong>,</p>
            <p style="margin:0 0 16px">
              Thanks for reaching out to EduAssist. We've received your request and an academic
              advisor will personally review it and get back to you within <strong>24 hours</strong>.
            </p>
            <div style="background:#f8fafc;border-left:4px solid #0d9488;border-radius:0 10px 10px 0;padding:16px 20px;margin:20px 0">
              <div style="font-size:.76rem;font-weight:700;color:#0d9488;text-transform:uppercase;letter-spacing:.08em;margin-bottom:6px">What happens next</div>
              <p style="margin:0 0 8px;font-size:.88rem;color:#334155">
                ✦ &nbsp;Our team reviews your class details
              </p>
              <p style="margin:0 0 8px;font-size:.88rem;color:#334155">
                ✦ &nbsp;We send you a custom price and installment plan
              </p>
              <p style="margin:0;font-size:.88rem;color:#334155">
                ✦ &nbsp;You approve the plan — no charge until you do
              </p>
            </div>
            <p style="margin:0;font-size:.84rem;color:#94a3b8">
              Your login credentials have been sent in a separate email. You can use your dashboard
              to track updates on this request anytime.
            </p>
            """.formatted(name),
            "View My Dashboard →",
            frontendUrl + "/dashboard"
        );
        send(toEmail, subject, html);
    }

    // ── 3. Password Reset ─────────────────────────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String subject  = "Reset Your EduAssist Password";
        String html = baseTemplate(
            "Password Reset Request",
            """
            <p style="margin:0 0 16px">We received a request to reset the password for your EduAssist account associated with this email.</p>
            <p style="margin:0 0 20px">Click the button below to set a new password. This link expires in <strong>1 hour</strong>.</p>
            <p style="margin:20px 0 0;font-size:.84rem;color:#64748b">If you didn't request this, you can safely ignore this email. Your password won't change.</p>
            """,
            "Reset My Password →",
            resetUrl
        );
        send(toEmail, subject, html);
    }

    // ── 4. Assignment / Class Status Update ───────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendAssignmentStatusUpdate(String toEmail, String name,
                                           String assignmentTitle, String status) {
        String subject = "Class Update: " + assignmentTitle + " — " + formatStatus(status);
        String html = baseTemplate(
            "Class Status Updated",
            """
            <p style="margin:0 0 14px">Hi <strong>%s</strong>,</p>
            <p style="margin:0 0 16px">There's an update on your class <strong>%s</strong>.</p>
            <div style="background:#f0fdf9;border:1.5px solid #ccfbf1;border-radius:12px;padding:18px;margin:16px 0;text-align:center">
              <div style="font-size:.78rem;color:#64748b;text-transform:uppercase;letter-spacing:.07em;margin-bottom:6px">Current Status</div>
              <div style="font-size:1.05rem;font-weight:700;color:#0d9488">%s</div>
            </div>
            <p style="margin:0;font-size:.87rem;color:#64748b">Log in to your dashboard to view details and download any available files.</p>
            """.formatted(name, assignmentTitle, formatStatus(status)),
            "Go to Dashboard →",
            frontendUrl + "/dashboard"
        );
        send(toEmail, subject, html);
    }

    // ── 5. Payment Installment Reminder ──────────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendInstallmentReminder(String toEmail, String name, String courseName,
                                        int installmentNum, String amount, String dueDate,
                                        String stripeLink) {
        String subject = "⏰ Payment Reminder: Installment #" + installmentNum + " Due Tomorrow";
        String payLink = (stripeLink != null && !stripeLink.isBlank()) ? stripeLink
                                                                        : frontendUrl + "/dashboard";
        String html = baseTemplate(
            "Payment Due Tomorrow ⏰",
            """
            <p style="margin:0 0 14px">Hi <strong>%s</strong>,</p>
            <p style="margin:0 0 16px">This is a reminder that your payment for <strong>%s</strong> is due tomorrow.</p>
            <div style="background:#fffbeb;border:1.5px solid #fde68a;border-radius:12px;padding:20px;margin:20px 0;text-align:center">
              <div style="font-size:.78rem;color:#92400e;text-transform:uppercase;letter-spacing:.07em;margin-bottom:8px">Installment #%d</div>
              <div style="font-size:2rem;font-weight:800;color:#0f172a">%s</div>
              <div style="color:#d97706;font-size:.88rem;margin-top:6px">Due: %s</div>
            </div>
            <p style="margin:0 0 16px;font-size:.87rem;color:#64748b">After payment, please upload your receipt in your student dashboard for quick verification.</p>
            """.formatted(name, courseName, installmentNum, amount, dueDate),
            "Pay Now via Stripe →",
            payLink
        );
        send(toEmail, subject, html);
    }

    // ── 6. Writer Assigned Notification ──────────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendWriterAssigned(String toEmail, String studentName, String courseName) {
        String subject = "Your Expert Has Been Assigned — " + courseName;
        String html = baseTemplate(
            "Expert Assigned ✍️",
            """
            <p style="margin:0 0 14px">Hi <strong>%s</strong>,</p>
            <p style="margin:0 0 16px">Great news! A verified expert writer has been assigned to your class <strong>%s</strong>.</p>
            <p style="margin:0 0 16px">Your expert has been granted access and will begin working on your class immediately. You can track progress from your student dashboard.</p>
            <p style="margin:0;font-size:.87rem;color:#64748b">All completed work will be reviewed by our admin team before being packaged and made available to you.</p>
            """.formatted(studentName, courseName),
            "Track Progress →",
            frontendUrl + "/dashboard"
        );
        send(toEmail, subject, html);
    }

    // ── 7. Work Delivered / Download Ready ───────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendWorkDelivered(String toEmail, String studentName, String courseName) {
        String subject = "✅ Your Work is Ready to Download — " + courseName;
        String html = baseTemplate(
            "Work Delivered ✅",
            """
            <p style="margin:0 0 14px">Hi <strong>%s</strong>,</p>
            <p style="margin:0 0 16px">Your completed work for <strong>%s</strong> has been reviewed and approved by our admin team.</p>
            <p style="margin:0 0 20px">Your ZIP file is now available for download in your student dashboard.</p>
            """.formatted(studentName, courseName),
            "Download Now →",
            frontendUrl + "/dashboard?tab=enrollments"
        );
        send(toEmail, subject, html);
    }

    // ── 8. Generic notification (fallback) ───────────────────────────────────
    @Async("emailTaskExecutor")
    public void sendNotification(String toEmail, String subject, String bodyHtml) {
        String html = baseTemplate("Notification", bodyHtml, "Go to Dashboard →", frontendUrl + "/dashboard");
        send(toEmail, subject, html);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(FROM_ADDR, FROM_NAME);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent → {} | {}", to, subject);
        } catch (Exception e) {
            log.error("Email failed → {} | {} | {}", to, subject, e.getMessage());
        }
    }

    /**
     * Single-column HTML email template.
     * Consistent with EduAssist brand — teal header, clean white body, dark footer.
     */
    private String baseTemplate(String heading, String bodyContent,
                                 String ctaText, String ctaUrl) {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>%s</title></head>
        <body style="margin:0;padding:0;background:#f1f5f9;font-family:Inter,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif">
          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:40px 16px">
            <tr><td align="center">
              <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px">

                <!-- Header -->
                <tr><td style="background:%s;border-radius:16px 16px 0 0;padding:28px 32px;text-align:center">
                  <div style="font-size:1.15rem;font-weight:800;color:white;letter-spacing:-.02em">%s</div>
                  <div style="font-size:.8rem;color:rgba(255,255,255,.65);margin-top:3px">Academic Support</div>
                </td></tr>

                <!-- Body -->
                <tr><td style="background:white;padding:32px 32px 24px;border-left:1px solid #e2e8f0;border-right:1px solid #e2e8f0">
                  <h2 style="margin:0 0 20px;font-size:1.15rem;font-weight:700;color:#0f172a;letter-spacing:-.02em">%s</h2>
                  <div style="font-size:.92rem;color:#334155;line-height:1.72">%s</div>
                  <!-- CTA -->
                  <table width="100%%" cellpadding="0" cellspacing="0" style="margin-top:24px">
                    <tr><td align="center">
                      <a href="%s" style="display:inline-block;background:%s;color:white;padding:13px 28px;border-radius:10px;text-decoration:none;font-weight:700;font-size:.93rem;letter-spacing:-.01em">%s</a>
                    </td></tr>
                  </table>
                </td></tr>

                <!-- Footer -->
                <tr><td style="background:#f8fafc;border:1px solid #e2e8f0;border-top:none;border-radius:0 0 16px 16px;padding:18px 32px;text-align:center">
                  <p style="margin:0;font-size:.78rem;color:#94a3b8">
                    This email was sent by <strong style="color:#64748b">EduAssist</strong> · support&#64;edupilothelp.com<br>
                    You're receiving this because you have an account with EduAssist.
                  </p>
                </td></tr>

              </table>
            </td></tr>
          </table>
        </body></html>
        """.formatted(heading, BRAND_COLOR, BRAND, heading, bodyContent, ctaUrl, BRAND_COLOR, ctaText);
    }

    private String formatStatus(String status) {
        if (status == null) return "Updated";
        return switch (status.toUpperCase()) {
            case "SUBMITTED"        -> "Submitted — Under Review";
            case "PAYMENT_PLAN_SENT"-> "Payment Plan Ready";
            case "IN_PROGRESS"      -> "In Progress";
            case "DELIVERED"        -> "Work Delivered — Download Available";
            case "COMPLETED"        -> "Completed";
            case "REVISION_REQUESTED" -> "Revision Requested";
            default -> status.replace("_", " ").toLowerCase();
        };
    }
}
