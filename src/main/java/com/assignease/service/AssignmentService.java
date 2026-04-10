package com.assignease.service;

import com.assignease.dto.AppDTOs;
import com.assignease.dto.EmailRequest;
import com.assignease.entity.Assignment;
import com.assignease.entity.Notification;
import com.assignease.entity.User;
import com.assignease.enums.EmailTemplateName;
import com.assignease.repository.AssignmentRepository;
import com.assignease.repository.NotificationRepository;
import com.assignease.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.assignease.config.InputSanitizer;
import com.assignease.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {


    private final AssignmentRepository assignmentRepository;
    private final FileStorageService fileStorage;
    private final InputSanitizer sanitizer;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailFacadeService emailFacadeService;

    private static final String UPLOAD_DIR = "uploads/assignments/";

    // ── Student: create assignment (with optional document) ────────────────────
    public AppDTOs.AssignmentResponse createAssignment(AppDTOs.AssignmentRequest request,
                                                        String studentEmail,
                                                        MultipartFile document) throws IOException {
        User student = userRepository.findByEmail(studentEmail)
            .orElseThrow(() -> new RuntimeException("Student not found"));

        Assignment assignment = Assignment.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .subject(request.getSubject())
            .level(request.getLevel())
            .pages(request.getPages())
            .wordCount(request.getWordCount())
            .deadline(request.getDeadline())
            .studentNotes(request.getStudentNotes())
            .status(Assignment.AssignmentStatus.SUBMITTED)
            .paymentDone(false)
            .writerFileApproved(false)
            .student(student)
            .build();

        // Save student uploaded document if provided
        if (document != null && !document.isEmpty()) {
            String docPath = fileStorage.save(document, "assignments/student-docs");
            assignment.setStudentDocumentPath(docPath);
        }

        assignment = assignmentRepository.save(assignment);

        notifyAdmins("New Assignment Submitted",
            "Student " + student.getFullName() + " submitted: " + assignment.getTitle(),
            assignment.getId());

        return mapToResponse(assignment, false);
    }

    public AppDTOs.AssignmentResponse getAssignmentById(Long id) {
        Assignment a = assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));
        return mapToResponse(a, true);
    }

    // ── Student: view their own assignments ───────────────────────────────────
    public List<AppDTOs.AssignmentResponse> getStudentAssignmentList(String email) {
        User student = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        return assignmentRepository.findByStudent(student).stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(a -> mapToResponse(a, false))
            .collect(Collectors.toList());
    }

    // ── Admin: view all assignments (with student details) ────────────────────
    public Page<AppDTOs.AssignmentResponse> getAllAssignments(Pageable pageable) {
        return assignmentRepository.findAllOrderByCreatedAtDesc(pageable)
            .map(a -> mapToResponse(a, true));
    }

    // ── Admin: update status, price, admin reply ───────────────────────────────
    public AppDTOs.AssignmentResponse updateStatus(Long id, AppDTOs.UpdateAssignmentStatusRequest request) {
        Assignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));

        if (request.getStatus() != null)
            assignment.setStatus(Assignment.AssignmentStatus.valueOf(request.getStatus()));
        if (request.getAdminNotes() != null) assignment.setAdminNotes(sanitizer.sanitize(request.getAdminNotes(), 2000));
        if (request.getAdminReply() != null) assignment.setAdminReply(sanitizer.sanitize(request.getAdminReply(), 2000));
        if (request.getPrice() != null) assignment.setPrice(request.getPrice());

        if (assignment.getStatus() == Assignment.AssignmentStatus.UNDER_REVIEW ||
            assignment.getStatus() == Assignment.AssignmentStatus.DELIVERED) {
            assignment.setCompletedAt(LocalDateTime.now());
        }

        assignment = assignmentRepository.save(assignment);

        // Notify student
        String notifMsg = "Your assignment '" + assignment.getTitle() + "' has been updated";
        if (request.getAdminReply() != null) notifMsg += ". Admin reply: " + request.getAdminReply();
        createNotification(assignment.getStudent(), "Assignment Updated", notifMsg,
            Notification.NotificationType.ASSIGNMENT_UPDATED, id);

        emailFacadeService.sendAssignmentUpdate(assignment.getStudent().getEmail(),
                assignment.getStudent().getFullName(),assignment.getTitle(),assignment.getStatus().name());

        return mapToResponse(assignment, true);
    }

    // ── Admin: approve payment (called after Razorpay verify) — now handled in PaymentService directly

    // ── Writer: upload solution file ───────────────────────────────────────────
    public AppDTOs.AssignmentResponse writerUploadSolution(Long id, MultipartFile file, String writerEmail) throws IOException {
        Assignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));

        // Verify writer is assigned to this
        User writer = userRepository.findByEmail(writerEmail)
            .orElseThrow(() -> new RuntimeException("Writer not found"));
        if (assignment.getAssignedWriter() == null ||
            !assignment.getAssignedWriter().getId().equals(writer.getId())) {
            throw new RuntimeException("You are not assigned to this assignment");
        }

        String path = fileStorage.saveAsZip(file, "assignments/writer-solutions", assignment.getTitle());
        assignment.setWriterFilePath(path);
        assignment.setWriterFileApproved(false); // Admin must approve first
        assignment.setStatus(Assignment.AssignmentStatus.UNDER_REVIEW);
        assignment = assignmentRepository.save(assignment);

        // Notify admins
        notifyAdmins("Writer Uploaded Solution",
            "Writer " + writer.getFullName() + " uploaded solution for: " + assignment.getTitle(),
            id);

        return mapToResponse(assignment, false); // writer view — no student details
    }

    // ── Admin: approve writer's file → makes it visible to student ────────────
    public AppDTOs.AssignmentResponse approveWriterFile(Long id) throws IOException {
        Assignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));

        if (assignment.getWriterFilePath() == null) {
            throw new RuntimeException("No writer file to approve");
        }

        assignment.setFilePath(assignment.getWriterFilePath()); // copy to student-visible field
        assignment.setWriterFileApproved(true);
        assignment.setStatus(Assignment.AssignmentStatus.DELIVERED);
        assignment.setCompletedAt(LocalDateTime.now());
        assignment = assignmentRepository.save(assignment);

        createNotification(assignment.getStudent(),
            "Assignment Delivered! 🎉",
            "Your assignment '" + assignment.getTitle() + "' is ready. Download it from your dashboard.",
            Notification.NotificationType.ASSIGNMENT_COMPLETED, id);

        emailFacadeService.sendAssignmentUpdate(assignment.getStudent().getEmail(),
                assignment.getStudent().getFullName(),assignment.getTitle(),"DELIVERED");
        return mapToResponse(assignment, true);
    }

    // ── Admin: upload solution directly (bypasses writer) ─────────────────────
    public AppDTOs.AssignmentResponse adminUploadSolution(Long id, MultipartFile file) throws IOException {
        Assignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));

        String path = fileStorage.saveAsZip(file, "assignments/admin-solutions", assignment.getTitle());
        assignment.setFilePath(path);
        assignment.setWriterFileApproved(true);
        assignment.setStatus(Assignment.AssignmentStatus.DELIVERED);
        assignment.setCompletedAt(LocalDateTime.now());
        assignment = assignmentRepository.save(assignment);

        createNotification(assignment.getStudent(),
            "Assignment Delivered! 🎉",
            "Your assignment '" + assignment.getTitle() + "' is ready for download.",
            Notification.NotificationType.ASSIGNMENT_COMPLETED, id);

        return mapToResponse(assignment, true);
    }

    public AppDTOs.DashboardStats getAdminStats() {
        AppDTOs.DashboardStats stats = new AppDTOs.DashboardStats();
        stats.setTotalAssignments(assignmentRepository.count());
        stats.setPendingAssignments(assignmentRepository.countByStatus(Assignment.AssignmentStatus.SUBMITTED));
        stats.setCompletedAssignments(assignmentRepository.countByStatus(Assignment.AssignmentStatus.DELIVERED));
        stats.setInProgressAssignments(assignmentRepository.countByStatus(Assignment.AssignmentStatus.IN_PROGRESS));
        stats.setTotalStudents(userRepository.countByRole(User.Role.ROLE_STUDENT));
        return stats;
    }

    // ── Writer: get only their assigned assignments (NO student personal data) ─
    public List<AppDTOs.AssignmentResponse> getWriterAssignments(String writerEmail) {
        User writer = userRepository.findByEmail(writerEmail)
            .orElseThrow(() -> new RuntimeException("Writer not found"));
        return assignmentRepository.findAll().stream()
            .filter(a -> a.getAssignedWriter() != null &&
                         a.getAssignedWriter().getId().equals(writer.getId()))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(a -> mapToWriterResponse(a)) // writer-safe response, NO student info
            .collect(Collectors.toList());
    }

    // ── Writer: mark complete ─────────────────────────────────────────────────
    public void writerMarkComplete(Long id, String writerEmail) {
        Assignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));
        assignment.setStatus(Assignment.AssignmentStatus.UNDER_REVIEW);
        assignmentRepository.save(assignment);
        notifyAdmins("Writer Marked Complete", "Assignment '" + assignment.getTitle() + "' marked complete by writer", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String saveFile(MultipartFile file, String subDir) throws IOException {
        Path dir = Paths.get(UPLOAD_DIR + subDir);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path path = dir.resolve(filename);
        Files.write(path, file.getBytes());
        return UPLOAD_DIR + subDir + filename;
    }

    /**
     * Saves an uploaded file wrapped in a ZIP archive.
     * The student always downloads a .zip file regardless of what was uploaded.
     */
    private String saveAsZip(MultipartFile file, String subDir, String assignmentTitle) throws IOException {
        Path dir = Paths.get(UPLOAD_DIR + subDir);
        if (!Files.exists(dir)) Files.createDirectories(dir);

        // Clean title for filename use
        String safeTitle = assignmentTitle.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String zipFilename = UUID.randomUUID().toString().substring(0, 8) + "_" + safeTitle + "_Solution.zip";
        Path zipPath = dir.resolve(zipFilename);

        // Original file entry name inside the zip
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "solution.pdf";

        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry(originalName);
            zos.putNextEntry(entry);
            zos.write(file.getBytes());
            zos.closeEntry();
        }

        return UPLOAD_DIR + subDir + zipFilename;
    }

    private void notifyAdmins(String title, String message, Long refId) {
        userRepository.findAll().stream()
            .filter(u -> u.getRole() == User.Role.ROLE_ADMIN)
            .forEach(admin -> createNotification(admin, title, message,
                Notification.NotificationType.ASSIGNMENT_CREATED, refId));
    }

    private void createNotification(User user, String title, String message,
                                    Notification.NotificationType type, Long refId) {
        Notification n = Notification.builder()
            .user(user).title(title).message(message).type(type).referenceId(refId).build();
        notificationRepository.save(n);
    }

    /** Full response for admin — includes student details, writer file path */
    private AppDTOs.AssignmentResponse mapToResponse(Assignment a, boolean isAdmin) {
        AppDTOs.AssignmentResponse r = new AppDTOs.AssignmentResponse();
        r.setId(a.getId()); r.setTitle(a.getTitle()); r.setDescription(a.getDescription());
        r.setSubject(a.getSubject()); r.setLevel(a.getLevel()); r.setPages(a.getPages());
        r.setWordCount(a.getWordCount()); r.setDeadline(a.getDeadline());
        r.setStatus(a.getStatus().name()); r.setPrice(a.getPrice());
        r.setAdminNotes(a.getAdminNotes()); r.setAdminReply(a.getAdminReply());
        r.setStudentNotes(a.getStudentNotes()); r.setCreatedAt(a.getCreatedAt());
        r.setUpdatedAt(a.getUpdatedAt()); r.setCompletedAt(a.getCompletedAt());
        r.setPaymentDone(a.getPaymentDone() != null && a.getPaymentDone());
        r.setWriterFileApproved(a.getWriterFileApproved() != null && a.getWriterFileApproved());
        r.setStudentDocumentPath(a.getStudentDocumentPath());

        // filePath shown to student only AFTER admin approval
        if (a.getWriterFileApproved() != null && a.getWriterFileApproved()) {
            r.setFilePath(a.getFilePath());
        } else if (isAdmin) {
            r.setFilePath(a.getFilePath()); // admin always sees it
        }

        // writer file — only admin sees
        if (isAdmin) r.setWriterFilePath(a.getWriterFilePath());

        if (a.getStudent() != null) {
            r.setStudentName(a.getStudent().getFullName());
            r.setStudentEmail(a.getStudent().getEmail());
        }
        if (a.getAssignedWriter() != null) r.setAssignedWriterName(a.getAssignedWriter().getFullName());
        return r;
    }

    /** Writer-safe response — NO student name/email */
    private AppDTOs.AssignmentResponse mapToWriterResponse(Assignment a) {
        AppDTOs.AssignmentResponse r = new AppDTOs.AssignmentResponse();
        r.setId(a.getId()); r.setTitle(a.getTitle()); r.setDescription(a.getDescription());
        r.setSubject(a.getSubject()); r.setLevel(a.getLevel()); r.setPages(a.getPages());
        r.setWordCount(a.getWordCount()); r.setDeadline(a.getDeadline());
        r.setStatus(a.getStatus().name()); r.setStudentNotes(a.getStudentNotes());
        r.setCreatedAt(a.getCreatedAt()); r.setWriterFilePath(a.getWriterFilePath());
        r.setWriterFileApproved(a.getWriterFileApproved() != null && a.getWriterFileApproved());
        // Writer CAN see student's reference document — helps understand the task
        r.setStudentDocumentPath(a.getStudentDocumentPath());
        // NO studentName, NO studentEmail — privacy protected
        return r;
    }
}
