package com.assignease.controller;

import com.assignease.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/writer")
@RequiredArgsConstructor
public class WriterController {

    private final AssignmentService assignmentService;

    /** Writer sees their assignments — NO student personal details */
    @GetMapping("/assignments")
    public ResponseEntity<?> getMyAssignments(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(assignmentService.getWriterAssignments(ud.getUsername()));
    }

    /** Writer uploads solution — goes to admin for approval first */
    @PostMapping("/assignments/{id}/upload-solution")
    public ResponseEntity<?> uploadSolution(@PathVariable Long id,
                                             @RequestParam("file") MultipartFile file,
                                             @AuthenticationPrincipal UserDetails ud) {
        try { return ResponseEntity.ok(assignmentService.writerUploadSolution(id, file, ud.getUsername())); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    /** Writer marks assignment complete (without file) */
    @PostMapping("/assignments/{id}/complete")
    public ResponseEntity<?> markComplete(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails ud) {
        try { assignmentService.writerMarkComplete(id, ud.getUsername()); return ResponseEntity.ok(Map.of("message", "Marked complete")); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
}
