package com.assignease.controller;

import com.assignease.entity.*;
import com.assignease.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final StudentFeedbackRepository feedbackRepo;
    private final BugReportRepository bugRepo;
    private final UserEventRepository eventRepo;
    private final UserRepository userRepo;
    private final EnrollmentRepository enrollmentRepo;

    // ── FEEDBACK ─────────────────────────────────────────────────────────────
    @PostMapping("/api/feedback")
    public ResponseEntity<?> submitFeedback(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            User student = userRepo.findByEmail(ud.getUsername()).orElseThrow();
            int rating = Integer.parseInt(body.get("rating").toString());
            if (rating < 1 || rating > 5) return ResponseEntity.badRequest().body(Map.of("message","Rating must be 1-5"));
            Long enrollmentId = body.get("enrollmentId") != null ? Long.parseLong(body.get("enrollmentId").toString()) : null;
            Enrollment enrollment = (enrollmentId != null) ? enrollmentRepo.findById(enrollmentId).orElse(null) : null;

            StudentFeedback fb = StudentFeedback.builder()
                .student(student).enrollment(enrollment).rating(rating)
                .comment((String) body.getOrDefault("comment", ""))
                .anonymous(Boolean.TRUE.equals(body.get("anonymous"))).build();
            feedbackRepo.save(fb);
            return ResponseEntity.ok(Map.of("message","Thank you for your feedback!"));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/api/admin/feedback")
    public ResponseEntity<?> getFeedback() {
        return ResponseEntity.ok(feedbackRepo.findAll().stream().map(f -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", f.getId()); m.put("rating", f.getRating()); m.put("comment", f.getComment());
            m.put("anonymous", f.getAnonymous()); m.put("createdAt", f.getCreatedAt());
            m.put("adminReviewed", f.getAdminReviewed());
            m.put("studentName", Boolean.TRUE.equals(f.getAnonymous()) ? "Anonymous" : f.getStudent().getFullName());
            m.put("courseName", f.getEnrollment() != null ? f.getEnrollment().getCourseName() : null);
            return m;
        }).collect(Collectors.toList()));
    }

    // ── BUG REPORTS ──────────────────────────────────────────────────────────
    @PostMapping("/api/bug-report")
    public ResponseEntity<?> submitBug(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            User reporter = (ud != null) ? userRepo.findByEmail(ud.getUsername()).orElse(null) : null;
            String email = (ud != null) ? ud.getUsername() : (String) body.getOrDefault("email", "anonymous");

            BugReport bug = BugReport.builder()
                .reporter(reporter).reporterEmail(email)
                .title((String) body.get("title"))
                .description((String) body.get("description"))
                .category((String) body.getOrDefault("category","other"))
                .severity(BugReport.BugSeverity.valueOf(
                    ((String) body.getOrDefault("severity","MEDIUM")).toUpperCase()))
                .pageUrl((String) body.getOrDefault("pageUrl",""))
                .browserInfo((String) body.getOrDefault("browserInfo","")).build();
            bugRepo.save(bug);
            return ResponseEntity.ok(Map.of("message","Bug report submitted. Thank you!"));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/api/admin/bug-reports")
    public ResponseEntity<?> getBugReports() {
        return ResponseEntity.ok(bugRepo.findAll().stream().map(b -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",b.getId()); m.put("title",b.getTitle()); m.put("description",b.getDescription());
            m.put("category",b.getCategory()); m.put("severity",b.getSeverity());
            m.put("status",b.getStatus()); m.put("pageUrl",b.getPageUrl());
            m.put("reporterEmail",b.getReporterEmail()); m.put("createdAt",b.getCreatedAt());
            m.put("adminNote",b.getAdminNote());
            return m;
        }).sorted((a,b2) -> b2.get("createdAt").toString().compareTo(a.get("createdAt").toString()))
          .collect(Collectors.toList()));
    }

    @PatchMapping("/api/admin/bug-reports/{id}")
    public ResponseEntity<?> updateBug(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return bugRepo.findById(id).map(b -> {
            if (body.containsKey("status")) b.setStatus(BugReport.BugStatus.valueOf((String)body.get("status")));
            if (body.containsKey("adminNote")) b.setAdminNote((String)body.get("adminNote"));
            if (b.getStatus()==BugReport.BugStatus.RESOLVED) b.setResolvedAt(LocalDateTime.now());
            bugRepo.save(b);
            return ResponseEntity.ok(Map.of("message","Updated"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── EVENT TRACKING ────────────────────────────────────────────────────────
    @PostMapping("/api/events/track")
    public ResponseEntity<?> trackEvent(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud,
            jakarta.servlet.http.HttpServletRequest req) {
        try {
            User user = (ud != null) ? userRepo.findByEmail(ud.getUsername()).orElse(null) : null;
            String xff = req.getHeader("X-Forwarded-For");
            String ip  = xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
            UserEvent ev = UserEvent.builder()
                .user(user)
                .sessionId((String) body.getOrDefault("sessionId",""))
                .eventType((String) body.getOrDefault("eventType","page_view"))
                .page((String) body.getOrDefault("page",""))
                .element((String) body.getOrDefault("element",""))
                .metadata((String) body.getOrDefault("metadata","{}"))
                .ipAddress(ip)
                .userAgent(req.getHeader("User-Agent")).build();
            eventRepo.save(ev);
            return ResponseEntity.ok(Map.of("tracked", true));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("tracked", false)); }
    }

    @GetMapping("/api/admin/analytics")
    public ResponseEntity<?> getAnalytics() {
        long total   = eventRepo.count();
        long pageViews = eventRepo.findAll().stream().filter(e->"page_view".equals(e.getEventType())).count();
        long logins    = eventRepo.findAll().stream().filter(e->"login".equals(e.getEventType())).count();
        long payments  = eventRepo.findAll().stream().filter(e->e.getEventType()!=null&&e.getEventType().startsWith("payment")).count();
        return ResponseEntity.ok(Map.of("totalEvents",total,"pageViews",pageViews,"logins",logins,"paymentEvents",payments));
    }
}
