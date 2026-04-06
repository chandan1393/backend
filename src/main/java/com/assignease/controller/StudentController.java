package com.assignease.controller;

import com.assignease.dto.AppDTOs;
import com.assignease.entity.*;
import com.assignease.repository.*;
import com.assignease.service.AssignmentService;
import com.assignease.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final AssignmentService assignmentService;
    private final UserService userService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PaymentInstallmentRepository installmentRepository;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(userService.getProfile(ud.getUsername()));
    }

    @GetMapping("/assignments")
    public ResponseEntity<?> getMyAssignments(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(assignmentService.getStudentAssignmentList(ud.getUsername()));
    }

    @PostMapping(value = "/assignments", consumes = "multipart/form-data")
    public ResponseEntity<?> createAssignment(
            @AuthenticationPrincipal UserDetails ud,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "document", required = false) MultipartFile document) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            AppDTOs.AssignmentRequest request = mapper.readValue(dataJson, AppDTOs.AssignmentRequest.class);
            return ResponseEntity.ok(assignmentService.createAssignment(request, ud.getUsername(), document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<?> markAllRead(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        notificationRepository.markAllAsReadForUser(user);
        return ResponseEntity.ok(Map.of("message", "All read"));
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> stats(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        List<PaymentInstallment> installments = installmentRepository.findByEnrollmentStudentId(user.getId());
        double totalPaid = installments.stream()
            .filter(p -> p.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED)
            .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0).sum();
        long confirmedCount = installments.stream()
            .filter(p -> p.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED).count();
        return ResponseEntity.ok(Map.of(
            "unreadNotifications", notificationRepository.countByUserAndReadFalse(user),
            "totalPaid", totalPaid,
            "confirmedInstallments", confirmedCount
        ));
    }

    /**
     * Download payment receipt for a specific INSTALLMENT (not old Razorpay payment)
     */
    @GetMapping("/payment-slip/{installmentId}")
    public ResponseEntity<byte[]> downloadPaymentSlip(
            @PathVariable Long installmentId,
            @AuthenticationPrincipal UserDetails ud) {
        User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        PaymentInstallment inst = installmentRepository.findById(installmentId)
            .orElseThrow(() -> new RuntimeException("Installment not found"));

        // Security: ensure this installment belongs to the requesting student
        if (!inst.getEnrollment().getStudent().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        if (inst.getStatus() != PaymentInstallment.InstallmentStatus.CONFIRMED) {
            return ResponseEntity.badRequest().build();
        }

        String paidAt = inst.getPaidAt() != null
            ? DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").format(inst.getPaidAt())
            : "Confirmed";

        String stripePaymentId = inst.getStripePaymentId() != null ? inst.getStripePaymentId() : "N/A";
        String courseName      = inst.getEnrollment().getCourseName();
        String institution     = inst.getEnrollment().getInstitutionName();
        String studentName     = user.getFullName();
        String studentEmail    = user.getEmail();
        String amount          = String.format("%.2f", inst.getAmount());
        String currency        = inst.getCurrency() != null ? inst.getCurrency() : "USD";
        String installmentNum  = "#" + inst.getInstallmentNumber();
        String dueDate         = inst.getDueDate() != null ? inst.getDueDate().toString() : "N/A";

        String html = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Payment Receipt — EduAssist</title>
  <style>
    *{margin:0;padding:0;box-sizing:border-box}
    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0fdf9;color:#0f172a;padding:40px 20px;min-height:100vh}
    .slip{max-width:600px;margin:0 auto;background:white;border-radius:20px;overflow:hidden;box-shadow:0 8px 40px rgba(13,148,136,.15)}
    .header{background:linear-gradient(135deg,#0d9488,#0369a1);padding:36px 32px 28px;text-align:center}
    .logo-wrap{display:inline-flex;align-items:center;gap:10px;margin-bottom:16px}
    .logo-box{width:42px;height:42px;background:rgba(255,255,255,.2);border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:1.4rem;font-weight:900;color:white}
    .brand-name{font-size:1.4rem;font-weight:800;color:white}
    .header h1{font-size:1rem;color:rgba(255,255,255,.75);font-weight:400;margin-bottom:10px}
    .success-badge{display:inline-flex;align-items:center;gap:6px;background:rgba(255,255,255,.15);border:1px solid rgba(255,255,255,.25);color:white;padding:5px 16px;border-radius:20px;font-size:.8rem;font-weight:700;letter-spacing:.06em}
    .body{padding:32px}
    .amount-block{text-align:center;padding:24px;background:#f0fdf9;border-radius:14px;margin-bottom:24px;border:1.5px solid #99f6e4}
    .amount-label{font-size:.78rem;color:#64748b;text-transform:uppercase;letter-spacing:.08em;margin-bottom:8px}
    .amount-val{font-size:3rem;font-weight:900;color:#0d9488;line-height:1}
    .amount-cur{font-size:1.1rem;font-weight:600;color:#64748b;margin-left:4px}
    .stripe-id{display:inline-block;background:#f8fafc;border:1px solid #e2e8f0;color:#0f172a;padding:6px 14px;border-radius:8px;font-family:monospace;font-size:.82rem;margin-top:8px}
    .details{display:flex;flex-direction:column;gap:0;margin-bottom:24px;border:1.5px solid #e2e8f0;border-radius:12px;overflow:hidden}
    .detail-row{display:flex;justify-content:space-between;align-items:center;padding:12px 16px;border-bottom:1px solid #f1f5f9}
    .detail-row:last-child{border-bottom:none}
    .detail-label{font-size:.8rem;color:#64748b}
    .detail-value{font-size:.87rem;font-weight:600;color:#0f172a;text-align:right}
    .ok-value{color:#0d9488}
    .stripe-branding{display:flex;align-items:center;justify-content:center;gap:8px;background:#f8f9ff;border:1.5px solid #e0e7ff;border-radius:10px;padding:12px;margin-bottom:20px}
    .stripe-logo{font-size:.85rem;font-weight:800;color:#635bff;letter-spacing:-.02em}
    .stripe-text{font-size:.78rem;color:#64748b}
    .footer{padding:20px 32px 28px;text-align:center;background:#f8fafc;border-top:1px solid #e2e8f0}
    .footer p{font-size:.76rem;color:#94a3b8;line-height:1.7}
    .footer strong{color:#0f172a}
    @media print{body{background:white;padding:0}.slip{box-shadow:none;border-radius:0}}
  </style>
</head>
<body>
  <div class="slip">
    <div class="header">
      <div class="logo-wrap">
        <div class="logo-box">E</div>
        <span class="brand-name">EduAssist</span>
      </div>
      <h1>Official Payment Receipt</h1>
      <div class="success-badge">✓ PAYMENT CONFIRMED</div>
    </div>
    <div class="body">
      <div class="amount-block">
        <div class="amount-label">Amount Paid</div>
        <div class="amount-val">%s<span class="amount-cur">%s</span></div>
        <div class="stripe-id">%s</div>
      </div>

      <div class="stripe-branding">
        <span class="stripe-logo">stripe</span>
        <span class="stripe-text">Payment processed securely by Stripe</span>
      </div>

      <div class="details">
        <div class="detail-row"><span class="detail-label">Student Name</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Email</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Course</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Institution</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Installment</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Original Due Date</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Date & Time Paid</span><span class="detail-value">%s</span></div>
        <div class="detail-row"><span class="detail-label">Payment Status</span><span class="detail-value ok-value">✅ Confirmed</span></div>
      </div>
    </div>
    <div class="footer">
      <p>This is a computer-generated receipt. For any queries contact<br><strong>support@eduassist.com</strong></p>
      <p style="margin-top:8px">© 2024 EduAssist. All rights reserved.</p>
    </div>
  </div>
</body>
</html>
""".formatted(amount, currency, stripePaymentId, studentName, studentEmail, courseName, institution, installmentNum, dueDate, paidAt);

        byte[] bytes = html.getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDispositionFormData("attachment",
            "EduAssist_Receipt_Installment_" + installmentId + ".html");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
