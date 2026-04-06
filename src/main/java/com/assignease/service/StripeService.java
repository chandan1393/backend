package com.assignease.service;

import com.assignease.entity.Enrollment;
import com.assignease.entity.Notification;
import com.assignease.entity.PaymentInstallment;
import com.assignease.entity.User;
import com.assignease.repository.EnrollmentRepository;
import com.assignease.repository.NotificationRepository;
import com.assignease.repository.PaymentInstallmentRepository;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private final PaymentInstallmentRepository installmentRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final NotificationRepository notifRepo;
    private final EmailService emailService;

    /**
     * Create a Stripe Checkout Session for a specific installment.
     * Amount is taken directly from the installment record — no manual input.
     * Returns the Stripe-hosted checkout URL.
     */
    public Map<String, Object> createCheckoutSession(Long installmentId, String studentEmail) throws Exception {
        Stripe.apiKey = stripeSecretKey;

        PaymentInstallment inst = installmentRepo.findById(installmentId)
            .orElseThrow(() -> new RuntimeException("Installment not found"));

        if (inst.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED) {
            throw new RuntimeException("This installment has already been paid.");
        }

        Enrollment enrollment = inst.getEnrollment();
        User student = enrollment.getStudent();

        if (!student.getEmail().equals(studentEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        // Convert amount to cents (Stripe works in smallest currency unit)
        long amountCents = Math.round(inst.getAmount() * 100);

        // Success URL: redirect back to our frontend with session_id for verification
        String successUrl = frontendUrl + "/payment-success?session_id={CHECKOUT_SESSION_ID}&installment_id=" + installmentId;
        String cancelUrl  = frontendUrl + "/payment-success?canceled=true";

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setCustomerEmail(studentEmail)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(inst.getCurrency() != null ? inst.getCurrency().toLowerCase() : "usd")
                            .setUnitAmount(amountCents)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("Installment #" + inst.getInstallmentNumber() + " — " + enrollment.getCourseName())
                                    .setDescription("Online class payment for " + enrollment.getInstitutionName()
                                        + " | Due: " + inst.getDueDate())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            // Store installment ID in metadata for webhook verification
            .putMetadata("installment_id", installmentId.toString())
            .putMetadata("enrollment_id", enrollment.getId().toString())
            .putMetadata("student_email", studentEmail)
            .build();

        Session session = Session.create(params);

        log.info("Stripe session created: {} for installment #{}", session.getId(), installmentId);

        return Map.of(
            "sessionId", session.getId(),
            "checkoutUrl", session.getUrl(),
            "amount", inst.getAmount(),
            "currency", inst.getCurrency() != null ? inst.getCurrency() : "USD",
            "installmentNumber", inst.getInstallmentNumber(),
            "courseName", enrollment.getCourseName()
        );
    }

    /**
     * Called after Stripe redirects back with ?session_id=xxx
     * Retrieves the session, verifies payment_status = "paid", marks installment CONFIRMED.
     */
    public Map<String, Object> verifyAndConfirmPayment(String sessionId, Long installmentId) throws Exception {
        Stripe.apiKey = stripeSecretKey;

        Session session = Session.retrieve(sessionId,
            SessionRetrieveParams.builder().build(), null);

        if (!"paid".equals(session.getPaymentStatus())) {
            throw new RuntimeException("Payment not completed. Status: " + session.getPaymentStatus());
        }

        PaymentInstallment inst = installmentRepo.findById(installmentId)
            .orElseThrow(() -> new RuntimeException("Installment not found"));

        // Idempotency — don't double-confirm
        if (inst.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED) {
            return buildSuccessResponse(inst);
        }

        // Verify the session metadata matches
        String metaInstId = session.getMetadata().get("installment_id");
        if (metaInstId == null || !metaInstId.equals(installmentId.toString())) {
            throw new RuntimeException("Session/installment mismatch");
        }

        // Mark confirmed
        inst.setStatus(PaymentInstallment.InstallmentStatus.CONFIRMED);
        inst.setPaidAt(LocalDateTime.now());
        inst.setStripePaymentId(session.getPaymentIntent());
        installmentRepo.save(inst);

        // Update enrollment status
        Enrollment enrollment = inst.getEnrollment();
        List<PaymentInstallment> all = installmentRepo.findByEnrollmentOrderByInstallmentNumberAsc(enrollment);
        long confirmed = all.stream().filter(p -> p.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED).count();

        if (confirmed >= all.size()) {
            enrollment.setStatus(Enrollment.EnrollmentStatus.FULLY_PAID);
        } else {
            enrollment.setStatus(Enrollment.EnrollmentStatus.PARTIALLY_PAID);
        }
        enrollmentRepo.save(enrollment);

        // Notify student
        User student = enrollment.getStudent();
        Notification notif = Notification.builder()
            .user(student)
            .title("Payment Confirmed ✅")
            .message("Installment #" + inst.getInstallmentNumber() + " of $" + inst.getAmount()
                + " confirmed for '" + enrollment.getCourseName() + "'.")
            .type(Notification.NotificationType.GENERAL)
            .referenceId(enrollment.getId())
            .build();
        notifRepo.save(notif);

        // Notify admins
        // (simplified — notify all admins via email via EmailService if needed)

        log.info("Payment confirmed for installment #{} enrollment #{}", inst.getInstallmentNumber(), enrollment.getId());
        return buildSuccessResponse(inst);
    }

    /** Stripe Webhook — handles async payment events (more reliable than redirect) */
    public void handleWebhook(String payload, String sigHeader) throws Exception {
        Stripe.apiKey = stripeSecretKey;
        // In production: use Stripe.constructEvent(payload, sigHeader, webhookSecret)
        // For now, the redirect-based verification above handles it
        log.info("Webhook received (not processed — using redirect verification)");
    }

    private Map<String, Object> buildSuccessResponse(PaymentInstallment inst) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("confirmed", true);
        resp.put("installmentNumber", inst.getInstallmentNumber());
        resp.put("amount", inst.getAmount());
        resp.put("currency", inst.getCurrency());
        resp.put("paidAt", inst.getPaidAt());
        resp.put("enrollmentId", inst.getEnrollment().getId());
        resp.put("enrollmentStatus", inst.getEnrollment().getStatus());
        return resp;
    }
}
