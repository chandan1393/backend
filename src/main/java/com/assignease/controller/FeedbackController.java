package com.assignease.controller;

import com.assignease.entity.StudentFeedback;
import com.assignease.repository.StudentFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final StudentFeedbackRepository repo;

    /** Public: landing page reads visible feedback */
    @GetMapping("/api/public/feedback")
    public ResponseEntity<?> getPublic() {
        return ResponseEntity.ok(repo.findByVisibleTrueOrderByCreatedAtDesc());
    }

    /** Admin: all feedback */
    @GetMapping("/api/admin/feedback")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /** Admin: create */
    @PostMapping("/api/admin/feedback")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        StudentFeedback f = new StudentFeedback();
        apply(f, body);
        return ResponseEntity.ok(repo.save(f));
    }

    /** Admin: update */
    @PutMapping("/api/admin/feedback/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        StudentFeedback f = repo.findById(id).orElseThrow();
        apply(f, body);
        return ResponseEntity.ok(repo.save(f));
    }

    /** Admin: toggle visibility */
    @PatchMapping("/api/admin/feedback/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        StudentFeedback f = repo.findById(id).orElseThrow();
        f.setVisible(!f.isVisible());
        repo.save(f);
        return ResponseEntity.ok(Map.of("visible", f.isVisible(), "id", id));
    }

    /** Admin: delete */
    @DeleteMapping("/api/admin/feedback/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    private void apply(StudentFeedback f, Map<String, Object> b) {
        if (b.containsKey("studentName"))  f.setStudentName((String) b.get("studentName"));
        if (b.containsKey("course"))       f.setCourse((String) b.get("course"));
        if (b.containsKey("location"))     f.setLocation((String) b.get("location"));
        if (b.containsKey("feedbackText")) f.setFeedbackText((String) b.get("feedbackText"));
        if (b.containsKey("rating"))       f.setRating(((Number) b.get("rating")).intValue());
        if (b.containsKey("avatar"))       f.setAvatar((String) b.get("avatar"));
        if (b.containsKey("visible"))      f.setVisible((Boolean) b.get("visible"));
        // auto-generate avatar initials from name if not provided
        if ((f.getAvatar() == null || f.getAvatar().isBlank()) && f.getStudentName() != null) {
            String[] parts = f.getStudentName().trim().split("\\s+");
            f.setAvatar(parts.length >= 2
                ? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
                : f.getStudentName().substring(0, Math.min(2, f.getStudentName().length())).toUpperCase());
        }
    }
}
