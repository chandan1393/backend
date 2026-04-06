package com.assignease.service;

import com.assignease.entity.Assignment;
import com.assignease.entity.Notification;
import com.assignease.entity.Payment;
import com.assignease.entity.User;
import com.assignease.repository.AssignmentRepository;
import com.assignease.repository.NotificationRepository;
import com.assignease.repository.PaymentRepository;
import com.assignease.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Value("${razorpay.key.id}") private String razorpayKeyId;
    @Value("${razorpay.key.secret}") private String razorpayKeySecret;

    public Map<String, Object> createOrder(Long assignmentId, String studentEmail) throws Exception {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        if (assignment.getPrice() == null || assignment.getPrice() <= 0)
            throw new RuntimeException("Assignment price not set by admin yet. Please wait for a quote.");

        if (Boolean.TRUE.equals(assignment.getPaymentDone()))
            throw new RuntimeException("Payment already completed for this assignment.");

        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        long amountInPaise = (long)(assignment.getPrice() * 100);
        String orderId = createRazorpayOrder(amountInPaise, assignmentId);

        Payment payment = Payment.builder()
                .assignment(assignment).student(student)
                .amount(assignment.getPrice()).razorpayOrderId(orderId)
                .status(Payment.PaymentStatus.PENDING).build();
        paymentRepository.save(payment);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("amount", amountInPaise);
        response.put("currency", "INR");
        response.put("keyId", razorpayKeyId);
        response.put("assignmentTitle", assignment.getTitle());
        response.put("assignmentId", assignmentId);
        return response;
    }

    public Map<String, Object> verifyAndCapture(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) throws Exception {
        // Verify HMAC-SHA256 signature
        String data = razorpayOrderId + "|" + razorpayPaymentId;
        String generated = hmacSHA256(data, razorpayKeySecret);
        if (!generated.equals(razorpaySignature))
            throw new RuntimeException("Payment verification failed — invalid signature");

        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Mark assignment payment done and move to REVIEWING
        Assignment assignment = payment.getAssignment();
        assignment.setPaymentDone(true);
        assignment.setStatus(Assignment.AssignmentStatus.PAYMENT_RECEIVED);
        assignmentRepository.save(assignment);

        // Notify student
        Notification n = Notification.builder()
            .user(assignment.getStudent())
            .title("Payment Confirmed ✅")
            .message("Payment for '" + assignment.getTitle() + "' received. Our team will now begin work.")
            .type(Notification.NotificationType.GENERAL)
            .referenceId(assignment.getId())
            .build();
        notificationRepository.save(n);

        log.info("Payment verified: {} for assignment {}", razorpayPaymentId, assignment.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("assignmentId", payment.getAssignment().getId());
        result.put("message", "Payment successful! Your assignment is now being reviewed.");
        return result;
    }

    private String createRazorpayOrder(long amountInPaise, Long assignmentId) throws Exception {
        String credentials = java.util.Base64.getEncoder()
                .encodeToString((razorpayKeyId + ":" + razorpayKeySecret).getBytes());
        JSONObject req = new JSONObject();
        req.put("amount", amountInPaise); req.put("currency", "INR");
        req.put("receipt", "order_assign_" + assignmentId);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.razorpay.com/v1/orders"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(req.toString()))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body()).getString("id");
    }

    private String hmacSHA256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
