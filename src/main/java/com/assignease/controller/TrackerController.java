package com.assignease.controller;

import com.assignease.entity.*;
import com.assignease.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class TrackerController {

    private final AssignmentTrackerRepository trackerRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final UserRepository userRepo;
    private final WriterInvitationRepository invitationRepo;
    private final NotificationRepository notifRepo;

    private static final String UPLOAD_BASE = "uploads/tracker/";

    // ── CREATE TASK (admin OR writer) ────────────────────────────────────────
    @PostMapping("/api/tracker/enrollments/{enrollmentId}/tasks")
    public ResponseEntity<?> createTask(
            @PathVariable Long enrollmentId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            Enrollment e = enrollmentRepo.findById(enrollmentId).orElseThrow();
            User creator = userRepo.findByEmail(ud.getUsername()).orElseThrow();

            AssignmentTracker task = AssignmentTracker.builder()
                .enrollment(e).createdBy(creator)
                .assignmentTitle((String) body.get("assignmentTitle"))
                .description((String) body.getOrDefault("description", ""))
                .assignmentType((String) body.getOrDefault("assignmentType", "assignment"))
                .dueDate(LocalDate.parse((String) body.get("dueDate")))
                .uploadDeadline(body.get("uploadDeadline") != null && !((String)body.get("uploadDeadline")).isEmpty()
                    ? LocalDate.parse((String) body.get("uploadDeadline")) : null)
                .build();
            trackerRepo.save(task);

            // Notify the other party
            if (creator.getRole() == User.Role.ROLE_ADMIN && e.getAssignedWriter() != null) {
                save(e.getAssignedWriter(), "New Task Added 📋",
                    "Task '" + task.getAssignmentTitle() + "' added to " + e.getCourseName()
                    + " — due " + task.getDueDate(), e.getId());
            } else if (creator.getRole() == User.Role.ROLE_WRITER) {
                notifyAdmins("Writer Added Task 📋",
                    creator.getFullName() + " added task '" + task.getAssignmentTitle()
                    + "' to " + e.getCourseName(), e);
            }
            return ResponseEntity.ok(toMap(task, true));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── GET TASKS FOR AN ENROLLMENT ──────────────────────────────────────────
    @GetMapping("/api/tracker/enrollments/{enrollmentId}/tasks")
    public ResponseEntity<?> getTasks(
            @PathVariable Long enrollmentId,
            @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean isStudent = user.getRole() == User.Role.ROLE_STUDENT;
        return ResponseEntity.ok(
            trackerRepo.findByEnrollmentIdOrderByDueDateAsc(enrollmentId).stream()
                .map(t -> toMap(t, !isStudent))   // students only see approved files
                .collect(Collectors.toList())
        );
    }

    // ── GET MY TASKS (all dashboards) ────────────────────────────────────────
    @GetMapping("/api/tracker/my-tasks")
    public ResponseEntity<?> getMyTasks(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        List<AssignmentTracker> tasks;
        if (user.getRole() == User.Role.ROLE_STUDENT)
            tasks = trackerRepo.findByStudentId(user.getId());
        else if (user.getRole() == User.Role.ROLE_WRITER)
            tasks = trackerRepo.findByWriterId(user.getId());
        else
            tasks = trackerRepo.findDueBetween(LocalDate.now().minusDays(7), LocalDate.now().plusDays(30));
        boolean isStudent = user.getRole() == User.Role.ROLE_STUDENT;
        return ResponseEntity.ok(tasks.stream().map(t -> toMap(t, !isStudent)).collect(Collectors.toList()));
    }

    // ── UPDATE TASK (admin OR writer) ────────────────────────────────────────
    @PutMapping("/api/tracker/tasks/{taskId}")
    public ResponseEntity<?> updateTask(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            AssignmentTracker t = trackerRepo.findById(taskId).orElseThrow();
            if (body.containsKey("assignmentTitle")) t.setAssignmentTitle((String) body.get("assignmentTitle"));
            if (body.containsKey("description"))     t.setDescription((String) body.get("description"));
            if (body.containsKey("assignmentType"))  t.setAssignmentType((String) body.get("assignmentType"));
            if (body.containsKey("dueDate") && body.get("dueDate") != null)
                t.setDueDate(LocalDate.parse((String) body.get("dueDate")));
            if (body.containsKey("uploadDeadline") && body.get("uploadDeadline") != null
                    && !((String)body.get("uploadDeadline")).isEmpty())
                t.setUploadDeadline(LocalDate.parse((String) body.get("uploadDeadline")));
            if (body.containsKey("status"))          t.setStatus(AssignmentTracker.TrackerStatus.valueOf((String) body.get("status")));
            trackerRepo.save(t);
            return ResponseEntity.ok(toMap(t, true));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── DELETE TASK ──────────────────────────────────────────────────────────
    @DeleteMapping("/api/tracker/tasks/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId) {
        trackerRepo.deleteById(taskId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ── WRITER UPLOADS FILE FOR A TASK ────────────────────────────────────────
    @PostMapping("/api/tracker/tasks/{taskId}/upload")
    public ResponseEntity<?> writerUpload(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "note", required = false, defaultValue = "") String note,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            AssignmentTracker task = trackerRepo.findById(taskId).orElseThrow();
            String path = saveFile(file, taskId);
            task.setWriterFilePath(path);
            task.setWriterFileName(file.getOriginalFilename());
            task.setWriterUploadedAt(LocalDateTime.now());
            task.setWriterNote(note);
            task.setStatus(AssignmentTracker.TrackerStatus.UPLOADED);
            task.setAdminApproved(false);
            trackerRepo.save(task);

            // Notify admins
            notifyAdmins("Task File Uploaded 📤",
                "Writer uploaded file for task '" + task.getAssignmentTitle()
                + "' in " + task.getEnrollment().getCourseName(), task.getEnrollment());

            return ResponseEntity.ok(toMap(task, true));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── ADMIN UPLOADS TASK INSTRUCTIONS / REFERENCE DOC ──────────────────────
    @PostMapping("/api/tracker/tasks/{taskId}/upload-doc")
    public ResponseEntity<?> adminUploadDoc(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            AssignmentTracker task = trackerRepo.findById(taskId).orElseThrow();
            String path = saveFile(file, taskId);
            task.setTaskDocPath(path);
            task.setTaskDocName(file.getOriginalFilename());
            trackerRepo.save(task);

            // Notify writer
            if (task.getEnrollment().getAssignedWriter() != null) {
                save(task.getEnrollment().getAssignedWriter(), "Task Document Added 📎",
                    "Instructions added for task '" + task.getAssignmentTitle() + "'",
                    task.getEnrollment().getId());
            }
            return ResponseEntity.ok(toMap(task, true));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── ADMIN APPROVES TASK FILE → STUDENT CAN DOWNLOAD ─────────────────────
    @PostMapping("/api/tracker/tasks/{taskId}/approve")
    public ResponseEntity<?> approveTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            AssignmentTracker task = trackerRepo.findById(taskId).orElseThrow();
            if (task.getWriterFilePath() == null)
                return ResponseEntity.badRequest().body(Map.of("message", "No file uploaded for this task yet."));
            task.setAdminApproved(true);
            task.setAdminApprovedAt(LocalDateTime.now());
            task.setStatus(AssignmentTracker.TrackerStatus.APPROVED);
            if (body != null) task.setAdminNote(body.getOrDefault("note", ""));
            trackerRepo.save(task);

            // Notify student
            save(task.getEnrollment().getStudent(), "Assignment Ready 🎉",
                "Task '" + task.getAssignmentTitle() + "' from "
                + task.getEnrollment().getCourseName() + " is ready to download!",
                task.getEnrollment().getId());
            return ResponseEntity.ok(toMap(task, true));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── ADMIN REJECTS TASK FILE ───────────────────────────────────────────────
    @PostMapping("/api/tracker/tasks/{taskId}/reject")
    public ResponseEntity<?> rejectTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            AssignmentTracker task = trackerRepo.findById(taskId).orElseThrow();
            task.setStatus(AssignmentTracker.TrackerStatus.REJECTED);
            task.setAdminApproved(false);
            if (body != null) task.setAdminNote(body.getOrDefault("note", ""));
            trackerRepo.save(task);

            if (task.getEnrollment().getAssignedWriter() != null) {
                save(task.getEnrollment().getAssignedWriter(), "Task Rejected ❌",
                    "Your file for '" + task.getAssignmentTitle() + "' was rejected. "
                    + (body != null ? body.getOrDefault("note", "") : ""),
                    task.getEnrollment().getId());
            }
            return ResponseEntity.ok(Map.of("message", "Task rejected."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── DOWNLOAD FILE (writer file or task doc) ───────────────────────────────
    @GetMapping("/api/tracker/tasks/{taskId}/download-writer-file")
    public ResponseEntity<byte[]> downloadWriterFile(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails ud) throws IOException {
        AssignmentTracker task = trackerRepo.findById(taskId).orElseThrow();
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

        // Student can only download if admin approved
        if (user.getRole() == User.Role.ROLE_STUDENT && !Boolean.TRUE.equals(task.getAdminApproved()))
            return ResponseEntity.status(403).build();
        if (task.getWriterFilePath() == null)
            return ResponseEntity.notFound().build();

        byte[] data = Files.readAllBytes(Paths.get(task.getWriterFilePath()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + task.getWriterFileName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }

    @GetMapping("/api/tracker/tasks/{taskId}/download-doc")
    public ResponseEntity<byte[]> downloadTaskDoc(@PathVariable Long taskId) throws IOException {
        AssignmentTracker task = trackerRepo.findById(taskId).orElseThrow();
        if (task.getTaskDocPath() == null) return ResponseEntity.notFound().build();
        byte[] data = Files.readAllBytes(Paths.get(task.getTaskDocPath()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + task.getTaskDocName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }

    // ── INVITATIONS (unchanged) ───────────────────────────────────────────────
    @PostMapping("/api/invitations/enrollments/{enrollmentId}/invite/{writerId}")
    public ResponseEntity<?> inviteWriter(@PathVariable Long enrollmentId, @PathVariable Long writerId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            Enrollment e = enrollmentRepo.findById(enrollmentId).orElseThrow();
            User writer = userRepo.findById(writerId).orElseThrow();
            Optional<WriterInvitation> existing = invitationRepo.findByEnrollmentIdAndWriterId(enrollmentId, writerId);
            if (existing.isPresent() && existing.get().getStatus() == WriterInvitation.InvitationStatus.PENDING)
                return ResponseEntity.badRequest().body(Map.of("message", "Writer already invited."));
            String msg = body != null ? body.getOrDefault("message", "") : "";
            WriterInvitation inv = WriterInvitation.builder().enrollment(e).writer(writer).message(msg).build();
            invitationRepo.save(inv);
            save(writer, "Class Invitation 📨",
                "You've been invited to handle '" + e.getCourseName() + "' at " + e.getInstitutionName()
                + ". Accept or decline in your dashboard.", e.getId());
            return ResponseEntity.ok(Map.of("message", "Invitation sent!", "invitationId", inv.getId()));
        } catch (Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @GetMapping("/api/invitations/enrollments/{enrollmentId}")
    public ResponseEntity<?> getEnrollmentInvitations(@PathVariable Long enrollmentId) {
        return ResponseEntity.ok(invitationRepo.findByEnrollmentIdOrderByCreatedAtDesc(enrollmentId).stream().map(this::toInvMap).collect(Collectors.toList()));
    }

    @GetMapping("/api/invitations/my")
    public ResponseEntity<?> getMyInvitations(@AuthenticationPrincipal UserDetails ud) {
        User writer = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(invitationRepo.findByWriterIdOrderByCreatedAtDesc(writer.getId()).stream().map(this::toInvMap).collect(Collectors.toList()));
    }

    @PostMapping("/api/invitations/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            WriterInvitation inv = invitationRepo.findById(id).orElseThrow();
            if (!inv.getWriter().getEmail().equals(ud.getUsername())) return ResponseEntity.status(403).build();
            inv.setStatus(WriterInvitation.InvitationStatus.ACCEPTED);
            inv.setRespondedAt(LocalDateTime.now());
            if (body != null) inv.setWriterNote(body.getOrDefault("note", ""));
            invitationRepo.save(inv);
            notifyAdmins("Writer Accepted ✅", inv.getWriter().getFullName() + " accepted invitation for '"
                + inv.getEnrollment().getCourseName() + "'.", inv.getEnrollment());
            return ResponseEntity.ok(Map.of("message", "Accepted!"));
        } catch (Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @PostMapping("/api/invitations/{id}/decline")
    public ResponseEntity<?> decline(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            WriterInvitation inv = invitationRepo.findById(id).orElseThrow();
            inv.setStatus(WriterInvitation.InvitationStatus.DECLINED);
            inv.setRespondedAt(LocalDateTime.now());
            if (body != null) inv.setWriterNote(body.getOrDefault("note", ""));
            invitationRepo.save(inv);
            return ResponseEntity.ok(Map.of("message", "Declined."));
        } catch (Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @PostMapping("/api/invitations/{id}/approve-credentials")
    public ResponseEntity<?> approveCredentials(@PathVariable Long id) {
        try {
            WriterInvitation inv = invitationRepo.findById(id).orElseThrow();
            if (inv.getStatus() != WriterInvitation.InvitationStatus.ACCEPTED)
                return ResponseEntity.badRequest().body(Map.of("message", "Writer must accept first."));
            inv.setCredentialsApproved(true);
            inv.setCredentialsApprovedAt(LocalDateTime.now());
            invitationRepo.save(inv);
            save(inv.getWriter(), "Portal Access Unlocked 🔓",
                "You now have access to portal credentials for '" + inv.getEnrollment().getCourseName() + "'.",
                inv.getEnrollment().getId());
            return ResponseEntity.ok(Map.of("message", "Credentials approved."));
        } catch (Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @PutMapping("/api/enrollments/{enrollmentId}/credentials")
    public ResponseEntity<?> updateCreds(@PathVariable Long enrollmentId, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            Enrollment e = enrollmentRepo.findById(enrollmentId).orElseThrow();
            if (body.containsKey("portalUrl"))      e.setPortalUrl(body.get("portalUrl"));
            if (body.containsKey("portalUsername")) e.setPortalUsername(body.get("portalUsername"));
            if (body.containsKey("portalPassword")) e.setPortalPassword(body.get("portalPassword"));
            enrollmentRepo.save(e);
            if (e.getAssignedWriter() != null)
                save(e.getAssignedWriter(), "Portal Credentials Updated 🔄",
                    "Credentials changed for '" + e.getCourseName() + "'.", e.getId());
            return ResponseEntity.ok(Map.of("message", "Credentials updated."));
        } catch (Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private String saveFile(MultipartFile f, Long taskId) throws IOException {
        Path dir = Paths.get(UPLOAD_BASE + taskId + "/");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().substring(0, 8) + "_" + f.getOriginalFilename();
        Files.write(dir.resolve(name), f.getBytes());
        return UPLOAD_BASE + taskId + "/" + name;
    }

    private Map<String, Object> toMap(AssignmentTracker t, boolean includeFiles) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("enrollmentId", t.getEnrollment().getId());
        m.put("courseName", t.getEnrollment().getCourseName());
        m.put("institutionName", t.getEnrollment().getInstitutionName());
        m.put("assignmentTitle", t.getAssignmentTitle());
        m.put("description", t.getDescription());
        m.put("assignmentType", t.getAssignmentType());
        m.put("dueDate", t.getDueDate());
        m.put("uploadDeadline", t.getUploadDeadline());
        m.put("status", t.getStatus());
        m.put("adminApproved", t.getAdminApproved());
        m.put("adminNote", t.getAdminNote());
        m.put("adminApprovedAt", t.getAdminApprovedAt());
        m.put("createdBy", t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : "");
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        // Task reference doc (everyone can see)
        m.put("hasTaskDoc", t.getTaskDocPath() != null);
        m.put("taskDocName", t.getTaskDocName());
        // Writer file — student sees only if approved, others always see metadata
        m.put("hasWriterFile", t.getWriterFilePath() != null);
        m.put("writerFileName", t.getWriterFileName());
        m.put("writerUploadedAt", t.getWriterUploadedAt());
        m.put("writerNote", t.getWriterNote());
        // canDownload = approved OR not student
        m.put("canDownloadWriterFile", includeFiles && t.getWriterFilePath() != null);
        return m;
    }

    private Map<String, Object> toInvMap(WriterInvitation inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId()); m.put("enrollmentId", inv.getEnrollment().getId());
        m.put("courseName", inv.getEnrollment().getCourseName());
        m.put("institutionName", inv.getEnrollment().getInstitutionName());
        m.put("status", inv.getStatus()); m.put("message", inv.getMessage());
        m.put("writerNote", inv.getWriterNote()); m.put("writerId", inv.getWriter().getId());
        m.put("writerName", inv.getWriter().getFullName()); m.put("writerEmail", inv.getWriter().getEmail());
        m.put("credentialsApproved", inv.getCredentialsApproved());
        m.put("credentialsApprovedAt", inv.getCredentialsApprovedAt());
        m.put("createdAt", inv.getCreatedAt()); m.put("respondedAt", inv.getRespondedAt());
        return m;
    }

    private void save(User u, String title, String msg, Long refId) {
        notifRepo.save(Notification.builder().user(u).title(title).message(msg)
            .type(Notification.NotificationType.GENERAL).referenceId(refId).build());
    }

    private void notifyAdmins(String title, String msg, Enrollment e) {
        userRepo.findAll().stream().filter(u -> u.getRole() == User.Role.ROLE_ADMIN)
            .forEach(a -> save(a, title, msg, e.getId()));
    }
}
