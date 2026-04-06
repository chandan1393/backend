package com.assignease.controller;

import com.assignease.entity.Testimonial;
import com.assignease.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/site")
@RequiredArgsConstructor
public class SiteAdminController {

    private final SiteService siteService;

    // ── Config ───────────────────────────────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(siteService.getAllConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<?> updateConfigs(@RequestBody Map<String, String> configs) {
        siteService.updateConfigs(configs);
        return ResponseEntity.ok(Map.of("message", "Configuration updated successfully"));
    }

    @PostMapping("/config/{key}")
    public ResponseEntity<?> setConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        siteService.setConfig(key, body.get("value"));
        return ResponseEntity.ok(Map.of("message", "Config updated"));
    }

    // ── Testimonials ─────────────────────────────────────────────────────────

    @GetMapping("/testimonials")
    public ResponseEntity<?> getAllTestimonials() {
        return ResponseEntity.ok(siteService.getAllTestimonials());
    }

    @PostMapping("/testimonials")
    public ResponseEntity<?> createTestimonial(@RequestBody Testimonial t) {
        return ResponseEntity.ok(siteService.saveTestimonial(t));
    }

    @PutMapping("/testimonials/{id}")
    public ResponseEntity<?> updateTestimonial(@PathVariable Long id, @RequestBody Testimonial t) {
        t.setId(id);
        return ResponseEntity.ok(siteService.saveTestimonial(t));
    }

    @PatchMapping("/testimonials/{id}/toggle")
    public ResponseEntity<?> toggleTestimonial(@PathVariable Long id) {
        return ResponseEntity.ok(siteService.toggleTestimonialActive(id));
    }

    @DeleteMapping("/testimonials/{id}")
    public ResponseEntity<?> deleteTestimonial(@PathVariable Long id) {
        siteService.deleteTestimonial(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // ── Text Me Leads ────────────────────────────────────────────────────────

    @GetMapping("/leads")
    public ResponseEntity<?> getAllLeads() {
        return ResponseEntity.ok(siteService.getAllLeads());
    }

    @GetMapping("/leads/new")
    public ResponseEntity<?> getNewLeads() {
        return ResponseEntity.ok(siteService.getNewLeads());
    }

    @PatchMapping("/leads/{id}/status")
    public ResponseEntity<?> updateLeadStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(siteService.updateLeadStatus(id, body.get("status"), body.get("notes")));
    }
}
