package com.assignease.controller;

import com.assignease.service.StripeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeController {

    private final StripeService stripeService;

    /**
     * POST /api/stripe/create-session/{installmentId}
     * Creates a Stripe Checkout Session for the exact installment amount.
     * Returns { sessionId, checkoutUrl, amount, currency, ... }
     */
    @PostMapping("/create-session/{installmentId}")
    public ResponseEntity<?> createSession(
            @PathVariable Long installmentId,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            return ResponseEntity.ok(stripeService.createCheckoutSession(installmentId, ud.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * POST /api/stripe/verify-session
     * Called after Stripe redirects back with session_id.
     * Verifies payment_status=paid, confirms installment, updates enrollment.
     * Body: { sessionId, installmentId }
     */
    @PostMapping("/verify-session")
    public ResponseEntity<?> verifySession(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            String sessionId    = (String) body.get("sessionId");
            Long installmentId  = Long.valueOf(body.get("installmentId").toString());
            return ResponseEntity.ok(stripeService.verifyAndConfirmPayment(sessionId, installmentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * GET /api/stripe/session-status/{sessionId}
     * Poll-able endpoint to check if a session is paid.
     */
    @GetMapping("/session-status/{sessionId}")
    public ResponseEntity<?> sessionStatus(
            @PathVariable String sessionId,
            @RequestParam Long installmentId,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            return ResponseEntity.ok(stripeService.verifyAndConfirmPayment(sessionId, installmentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "confirmed", false));
        }
    }

    /** Stripe Webhook — for async payment events */
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sig) {
        try {
            stripeService.handleWebhook(payload, sig);
            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
