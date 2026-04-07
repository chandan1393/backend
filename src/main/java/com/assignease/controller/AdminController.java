package com.assignease.controller;

import com.assignease.dto.AppDTOs;
import com.assignease.service.AssignmentService;
import com.assignease.service.QueryService;
import com.assignease.service.UserService;
import com.assignease.service.WriterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AssignmentService assignmentService;
    private final QueryService queryService;
    private final UserService userService;
    private final WriterService writerService;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() { return ResponseEntity.ok(assignmentService.getAdminStats()); }

    // ── Users
    @GetMapping("/users")   public ResponseEntity<?> getAllUsers()    { return ResponseEntity.ok(userService.getAllUsers()); }
    @GetMapping("/students")public ResponseEntity<?> getAllStudents() { return ResponseEntity.ok(userService.getAllStudents()); }
    @PostMapping("/users")  public ResponseEntity<?> createUser(@Valid @RequestBody AppDTOs.UserCreateRequest req) {
        try { return ResponseEntity.ok(userService.createUser(req)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
    @PutMapping("/users/{id}") public ResponseEntity<?> updateUser(@PathVariable Long id, @Valid @RequestBody AppDTOs.UserCreateRequest req) {
        return ResponseEntity.ok(userService.updateUser(id, req));
    }
    @PatchMapping("/users/{id}/toggle-status") public ResponseEntity<?> toggleUser(@PathVariable Long id) {
        userService.toggleUserStatus(id); return ResponseEntity.ok(Map.of("message", "Status updated"));
    }

    // ── Assignments
    @GetMapping("/assignments") public ResponseEntity<?> getAllAssignments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(assignmentService.getAllAssignments(
            PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/assignments/{id}") public ResponseEntity<?> getAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.getAssignmentById(id));
    }

    /** Update status + price + adminReply */
    @PutMapping("/assignments/{id}/status") public ResponseEntity<?> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AppDTOs.UpdateAssignmentStatusRequest req) {
        try { return ResponseEntity.ok(assignmentService.updateStatus(id, req)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    /** Admin uploads solution directly — immediately visible to student */
    @PostMapping("/assignments/{id}/upload-solution") public ResponseEntity<?> uploadSolution(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try { return ResponseEntity.ok(assignmentService.adminUploadSolution(id, file)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    /** Admin approves writer's uploaded file → makes it visible to student */
    @PostMapping("/assignments/{id}/approve-writer-file") public ResponseEntity<?> approveWriterFile(@PathVariable Long id) {
        try { return ResponseEntity.ok(assignmentService.approveWriterFile(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    // ── Assign writer
    @PostMapping("/assignments/{id}/assign-writer") public ResponseEntity<?> assignWriter(
            @PathVariable Long id, @RequestBody Map<String, Long> body) {
        try { writerService.assignWriterToAssignment(id, body.get("writerUserId")); return ResponseEntity.ok(Map.of("message", "Writer assigned")); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    // ── Writers
    @GetMapping("/writers")           public ResponseEntity<?> getAllWriters()      { return ResponseEntity.ok(writerService.getAllWriters()); }
    @GetMapping("/writers/available") public ResponseEntity<?> getAvailableWriters(){ return ResponseEntity.ok(writerService.getAvailableWriters()); }
    @PostMapping("/writers")          public ResponseEntity<?> createWriter(@RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(writerService.createWriter(body.get("fullName"), body.get("email"), body.get("phone"), body.get("bio"), body.get("expertise"))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    // ── Queries
    @GetMapping("/queries") public ResponseEntity<?> getAllQueries(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(queryService.getAllQueries(PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
    @PostMapping("/queries/{id}/reply") public ResponseEntity<?> replyQuery(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(queryService.replyToQuery(id, body.get("reply")));
    }
    @PatchMapping("/queries/{id}/status") public ResponseEntity<?> updateQueryStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(queryService.updateStatus(id, body.get("status")));
    }
}
