package com.assignease.controller;

import com.assignease.dto.AppDTOs;
import com.assignease.entity.TextMeLead;
import com.assignease.service.QueryService;
import com.assignease.service.SiteService;
import com.assignease.service.WhatsAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.assignease.config.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name = "Public", description = "No authentication required — landing page forms, config, testimonials")
@SecurityRequirements
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final QueryService queryService;
    private final SiteService siteService;
    private final WhatsAppService whatsAppService;
    private final InputSanitizer sanitizer;

    @PostMapping("/query")
    public ResponseEntity<?> submitQuery(@Valid @RequestBody AppDTOs.QueryRequest request) {
        try {
            AppDTOs.QueryResponse response = queryService.submitQuery(request);
            return ResponseEntity.ok(Map.of("message", "Query submitted! Login credentials sent to your email.", "queryId", response.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/contact")
    public ResponseEntity<?> contactMessage(@Valid @RequestBody AppDTOs.ContactMessage message) {
        AppDTOs.QueryRequest req = new AppDTOs.QueryRequest();
        req.setName(message.getName()); req.setEmail(message.getEmail());
        req.setPhone(message.getPhone()); req.setSubject("Contact Message"); req.setMessage(message.getMessage());
        try {
            queryService.submitQuery(req);
            return ResponseEntity.ok(Map.of("message", "Message received! We'll get back to you soon."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/text-me")
    public ResponseEntity<?> textMe(@RequestBody Map<String, String> body) {
        String phone = sanitizer.sanitizePhone(body.get("phone"));
        String countryCode = sanitizer.sanitize(body.getOrDefault("countryCode", ""), 10);
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number is required"));
        }
        if (sanitizer.containsSuspiciousContent(phone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid input detected."));
        }
        try {
            TextMeLead lead = siteService.saveTextMeLead(phone.trim(), countryCode);
            String companyName = siteService.getConfig("company_name");
            if (companyName == null || companyName.isEmpty()) companyName = "AssignEase";
            String message = whatsAppService.buildLeadMessage(phone.trim(), companyName);
            whatsAppService.sendWhatsApp(phone.trim(), message);
            return ResponseEntity.ok(Map.of("message", "Thanks! Our team will WhatsApp you shortly.", "leadId", lead.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<?> getPublicConfig() {
        Map<String, String> c = siteService.getAllConfig();
        return ResponseEntity.ok(Map.of(
            "company_name",    c.getOrDefault("company_name", "AssignEase"),
            "company_tagline", c.getOrDefault("company_tagline", "Academic Excellence Delivered"),
            "company_email",   c.getOrDefault("company_email", "support@assignease.com"),
            "company_phone",   c.getOrDefault("company_phone", "+91 98765 43210"),
            "company_address", c.getOrDefault("company_address", "New Delhi, India"),
            "whatsapp_number", c.getOrDefault("whatsapp_number", "+91 98765 43210")
        ));
    }

    @GetMapping("/testimonials")
    public ResponseEntity<?> getTestimonials() {
        return ResponseEntity.ok(siteService.getActiveTestimonials());
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "API is running"));
    }
}
