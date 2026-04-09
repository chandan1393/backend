package com.assignease.controller;

import com.assignease.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@Tag(name = "Enrollments", description = "Student enrollment management, installments, submissions")
@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // ── Student endpoints ──────────────────────────────────────────────────
    @PostMapping("/api/student/enrollments")
    public ResponseEntity<?> createEnrollment(
            @RequestParam Map<String, String> data,
            @RequestParam(required = false) MultipartFile document,
            @AuthenticationPrincipal UserDetails ud) {
        try { return ResponseEntity.ok(enrollmentService.createEnrollment(data, ud.getUsername(), document)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/api/student/enrollments")
    public ResponseEntity<?> getMyEnrollments(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(enrollmentService.getStudentEnrollments(ud.getUsername()));
    }

    @PostMapping("/api/student/installments/{id}/receipt")
    public ResponseEntity<?> uploadReceipt(@PathVariable Long id,
            @RequestParam("receipt") MultipartFile receipt,
            @AuthenticationPrincipal UserDetails ud) {
        try { return ResponseEntity.ok(enrollmentService.uploadReceipt(id, receipt, ud.getUsername())); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    // ── Admin endpoints ────────────────────────────────────────────────────
    @GetMapping("/api/admin/enrollments")
    public ResponseEntity<?> getAllEnrollments(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(enrollmentService.getAllEnrollments(PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/api/admin/enrollments/{id}")
    public ResponseEntity<?> getEnrollment(@PathVariable Long id) {
        try { return ResponseEntity.ok(enrollmentService.getAllEnrollments(PageRequest.of(0,1000,Sort.by("createdAt").descending())).getContent().stream().filter(e->e.get("id").equals(id)).findFirst().orElseThrow()); }
        catch (Exception e) { return ResponseEntity.notFound().build(); }
    }

    @PutMapping("/api/admin/enrollments/{id}")
    public ResponseEntity<?> updateEnrollment(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        try { return ResponseEntity.ok(enrollmentService.updateEnrollment(id, data)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/api/admin/enrollments/{id}/installments")
    public ResponseEntity<?> createInstallments(@PathVariable Long id, @RequestBody List<Map<String, Object>> installments) {
        try { return ResponseEntity.ok(enrollmentService.createInstallments(id, installments)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PatchMapping("/api/admin/installments/{id}/link")
    public ResponseEntity<?> updateStripeLink(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(enrollmentService.updateInstallmentLink(id, body.get("stripeLink")));
    }

    @PostMapping("/api/admin/installments/{id}/confirm")
    public ResponseEntity<?> confirmInstallment(@PathVariable Long id) {
        try { return ResponseEntity.ok(enrollmentService.confirmInstallment(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/api/admin/enrollments/{id}/assign-writer")
    public ResponseEntity<?> assignWriter(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        try { return ResponseEntity.ok(enrollmentService.assignWriter(id, body.get("writerUserId"))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/api/admin/enrollments/{id}/approve-zip")
    public ResponseEntity<?> approveZip(@PathVariable Long id) {
        try { return ResponseEntity.ok(enrollmentService.approveZip(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/api/admin/enrollments/stats")
    public ResponseEntity<?> stats() { return ResponseEntity.ok(enrollmentService.getAdminStats()); }

    // ── Writer endpoints ───────────────────────────────────────────────────
    @GetMapping("/api/writer/enrollments")
    public ResponseEntity<?> getWriterEnrollments(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(enrollmentService.getWriterEnrollments(ud.getUsername()));
    }

    @PostMapping("/api/writer/enrollments/{id}/upload-zip")
    public ResponseEntity<?> writerUploadZip(@PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false, defaultValue = "") String description,
            @AuthenticationPrincipal UserDetails ud) {
        try { return ResponseEntity.ok(enrollmentService.writerUploadZip(id, file, ud.getUsername(), description)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/api/writer/enrollments/{id}/submissions")
    public ResponseEntity<?> getSubmissions(@PathVariable Long id) {
        return ResponseEntity.ok(enrollmentService.getSubmissionHistory(id));
    }

    @GetMapping("/api/admin/enrollments/{id}/submissions")
    public ResponseEntity<?> getSubmissionsAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(enrollmentService.getSubmissionHistory(id));
    }

    @PostMapping("/api/admin/submissions/{submissionId}/approve")
    public ResponseEntity<?> approveSubmission(
            @PathVariable Long submissionId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String note = body != null ? body.getOrDefault("adminNote", "") : "";
            return ResponseEntity.ok(enrollmentService.approveSubmission(submissionId, note));
        } catch (Exception e) { return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage())); }
    }

    @PostMapping("/api/admin/submissions/{submissionId}/reject")
    public ResponseEntity<?> rejectSubmission(
            @PathVariable Long submissionId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String note = body != null ? body.getOrDefault("adminNote", "") : "";
            return ResponseEntity.ok(enrollmentService.rejectSubmission(submissionId, note));
        } catch (Exception e) { return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage())); }
    }

}
