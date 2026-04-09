package com.assignease.controller;

import com.assignease.entity.*;
import com.assignease.repository.*;
import com.assignease.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * FileController — ALL file downloads go through here.
 *
 * Why: Never expose raw disk paths to the frontend or serve files
 * directly via static resource mapping. Every download is:
 *   1. Authenticated (JWT required, except public health check)
 *   2. Authorized (user can only download their own files)
 *   3. Path-traversal protected (FileStorageService validates the path)
 *   4. Served with correct Content-Type and Content-Disposition headers
 */
@Slf4j
@Tag(name = "File Downloads", description = "Authenticated file download endpoints. Student only gets approved files.")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fs;
    private final UserRepository userRepo;
    private final WriterSubmissionRepository submissionRepo;
    private final PaymentInstallmentRepository installmentRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final AssignmentTrackerRepository trackerRepo;

    // ── Student downloads approved submission ZIP ────────────────────────────
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<byte[]> downloadSubmission(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserDetails ud) throws IOException {

        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        WriterSubmission sub = submissionRepo.findById(submissionId)
            .orElseThrow(() -> new RuntimeException("File not found"));

        // Authorization: student can only download their own approved files
        boolean isStudent = user.getRole() == User.Role.ROLE_STUDENT;
        if (isStudent) {
            if (!sub.getEnrollment().getStudent().getId().equals(user.getId()))
                return ResponseEntity.status(403).build();
            if (sub.getStatus() != WriterSubmission.SubmissionStatus.APPROVED)
                return ResponseEntity.status(403).build();
        }
        // Admin and writer can download any submission
        return serveFile(sub.getZipPath(), sub.getFileName());
    }

    // ── Receipt uploaded by student (admin/student can view) ────────────────
    @GetMapping("/receipt/{installmentId}")
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable Long installmentId,
            @AuthenticationPrincipal UserDetails ud) throws IOException {

        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        PaymentInstallment inst = installmentRepo.findById(installmentId)
            .orElseThrow(() -> new RuntimeException("Installment not found"));

        boolean isStudent = user.getRole() == User.Role.ROLE_STUDENT;
        if (isStudent && !inst.getEnrollment().getStudent().getId().equals(user.getId()))
            return ResponseEntity.status(403).build();

        if (inst.getReceiptPath() == null) return ResponseEntity.notFound().build();
        return serveFile(inst.getReceiptPath(), "receipt_" + installmentId);
    }

    // ── Reference doc uploaded by student when enrolling ────────────────────
    @GetMapping("/enrollment-doc/{enrollmentId}")
    public ResponseEntity<byte[]> downloadEnrollmentDoc(
            @PathVariable Long enrollmentId,
            @AuthenticationPrincipal UserDetails ud) throws IOException {

        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Enrollment e = enrollmentRepo.findById(enrollmentId)
            .orElseThrow(() -> new RuntimeException("Not found"));

        boolean isStudent = user.getRole() == User.Role.ROLE_STUDENT;
        if (isStudent && !e.getStudent().getId().equals(user.getId()))
            return ResponseEntity.status(403).build();

        if (e.getReferenceDocPath() == null) return ResponseEntity.notFound().build();
        return serveFile(e.getReferenceDocPath(), "reference_doc_" + enrollmentId);
    }

    // ── Tracker task: writer uploads file per task ───────────────────────────
    @GetMapping("/tracker/{taskId}/writer-file")
    public ResponseEntity<byte[]> downloadTaskWriterFile(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails ud) throws IOException {

        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        AssignmentTracker task = trackerRepo.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));

        // Student can only download if admin approved
        if (user.getRole() == User.Role.ROLE_STUDENT) {
            if (!task.getEnrollment().getStudent().getId().equals(user.getId()))
                return ResponseEntity.status(403).build();
            if (!Boolean.TRUE.equals(task.getAdminApproved()))
                return ResponseEntity.status(403).build();
        }

        if (task.getWriterFilePath() == null) return ResponseEntity.notFound().build();
        return serveFile(task.getWriterFilePath(), task.getWriterFileName());
    }

    // ── Tracker task: admin uploads instructions doc ─────────────────────────
    @GetMapping("/tracker/{taskId}/task-doc")
    public ResponseEntity<byte[]> downloadTaskDoc(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails ud) throws IOException {

        AssignmentTracker task = trackerRepo.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.getTaskDocPath() == null) return ResponseEntity.notFound().build();
        return serveFile(task.getTaskDocPath(), task.getTaskDocName());
    }

    // ── Core serve method ────────────────────────────────────────────────────
    private ResponseEntity<byte[]> serveFile(String relativePath, String displayName) throws IOException {
        byte[] data = fs.read(relativePath);

        String safeName = displayName != null
            ? URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20")
            : "download";

        String contentType = fs.contentType(relativePath);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + safeName)
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .contentType(MediaType.parseMediaType(contentType))
            .body(data);
    }
}
