package com.assignease.service;

import com.assignease.entity.*;
import com.assignease.repository.WriterSubmissionRepository;
import com.assignease.repository.WriterInvitationRepository;
import com.assignease.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepo;
    private final PaymentInstallmentRepository installmentRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notifRepo;
    private final EmailService emailService;
    private final WriterSubmissionRepository submissionRepo;
    private final WriterInvitationRepository invitationRepo;
    private final WhatsAppService whatsAppService;

    private static final String UPLOAD = "uploads/enrollments/";

    // ── Student: submit enrollment (class details + credentials) ─────────────
    public Map<String, Object> createEnrollment(Map<String, String> data, String studentEmail, MultipartFile doc) throws IOException {
        User student = userRepo.findByEmail(studentEmail).orElseThrow();

        Enrollment e = Enrollment.builder()
            .courseName(data.get("courseName"))
            .institutionName(data.get("institutionName"))
            .subject(data.get("subject"))
            .courseDescription(data.get("courseDescription"))
            .classStartDate(data.containsKey("classStartDate") && !data.get("classStartDate").isEmpty() ? LocalDate.parse(data.get("classStartDate")) : null)
            .classEndDate(data.containsKey("classEndDate") && !data.get("classEndDate").isEmpty() ? LocalDate.parse(data.get("classEndDate")) : null)
            .portalUrl(data.get("portalUrl"))
            .portalUsername(data.get("portalUsername"))
            .portalPassword(data.get("portalPassword"))
            .studentNotes(data.get("studentNotes"))
            .status(Enrollment.EnrollmentStatus.SUBMITTED)
            .student(student)
            .build();

        if (doc != null && !doc.isEmpty()) {
            e.setReferenceDocPath(saveFile(doc, "docs/"));
        }
        e = enrollmentRepo.save(e);

        notifyAdmins("New Enrollment", "Student " + student.getFullName() + " enrolled: " + e.getCourseName(), e.getId(), student);
        return Map.of("id", e.getId(), "message", "Enrollment submitted successfully!");
    }

    // ── Student: get their enrollments ───────────────────────────────────────
    public List<Map<String, Object>> getStudentEnrollments(String email) {
        User student = userRepo.findByEmail(email).orElseThrow();
        return enrollmentRepo.findByStudentOrderByCreatedAtDesc(student).stream()
            .map(e -> toStudentMap(e))
            .collect(Collectors.toList());
    }

    // ── Student: upload payment receipt for an installment ───────────────────
    public Map<String, Object> uploadReceipt(Long installmentId, MultipartFile receipt, String studentEmail) throws IOException {
        PaymentInstallment inst = installmentRepo.findById(installmentId).orElseThrow();
        String path = saveFile(receipt, "receipts/");
        inst.setReceiptPath(path);
        inst.setStatus(PaymentInstallment.InstallmentStatus.RECEIPT_UPLOADED);
        inst.setReceiptUploadedAt(LocalDateTime.now());
        installmentRepo.save(inst);

        // Notify admin
        notifyAdmins("Payment Receipt Uploaded",
            "Student uploaded receipt for installment #" + inst.getInstallmentNumber() + " of enrollment #" + inst.getEnrollment().getId(),
            inst.getEnrollment().getId(), inst.getEnrollment().getStudent());

        return Map.of("message", "Receipt uploaded! Admin will verify and confirm.", "receiptPath", path);
    }

    // ── Admin: get all enrollments ────────────────────────────────────────────
    public Page<Map<String, Object>> getAllEnrollments(Pageable pageable) {
        return enrollmentRepo.findAllByOrderByCreatedAtDesc(pageable).map(e -> toAdminMap(e));
    }

    // ── Admin: update status + price + reply ──────────────────────────────────
    public Map<String, Object> updateEnrollment(Long id, Map<String, Object> data) {
        Enrollment e = enrollmentRepo.findById(id).orElseThrow();
        if (data.containsKey("status"))    e.setStatus(Enrollment.EnrollmentStatus.valueOf((String) data.get("status")));
        if (data.containsKey("adminReply")) e.setAdminReply((String) data.get("adminReply"));
        if (data.containsKey("adminNotes")) e.setAdminNotes((String) data.get("adminNotes"));
        if (data.containsKey("totalPrice")) e.setTotalPrice(Double.valueOf(data.get("totalPrice").toString()));
        e = enrollmentRepo.save(e);

        // Notify student
        createNotif(e.getStudent(), "Enrollment Updated",
            "Your enrollment '" + e.getCourseName() + "' has been updated.", "ENROLLMENT_UPDATED", e.getId());

        return toAdminMap(e);
    }

    // ── Admin: create payment installments ────────────────────────────────────
    public List<Map<String, Object>> createInstallments(Long enrollmentId, List<Map<String, Object>> installmentData) {
        Enrollment enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow();
        // Delete old installments first
        List<PaymentInstallment> existing = installmentRepo.findByEnrollmentOrderByInstallmentNumberAsc(enrollment);
        installmentRepo.deleteAll(existing);

        List<PaymentInstallment> saved = new ArrayList<>();
        for (int i = 0; i < installmentData.size(); i++) {
            Map<String, Object> d = installmentData.get(i);
            PaymentInstallment inst = PaymentInstallment.builder()
                .enrollment(enrollment)
                .installmentNumber(i + 1)
                .amount(Double.valueOf(d.get("amount").toString()))
                .currency(d.getOrDefault("currency", "USD").toString())
                .dueDate(LocalDate.parse(d.get("dueDate").toString()))
                .stripePaymentLink(d.getOrDefault("stripeLink", "").toString())
                .notes(d.getOrDefault("notes", "").toString())
                .status(PaymentInstallment.InstallmentStatus.PENDING)
                .reminderSent(false)
                .build();
            saved.add(installmentRepo.save(inst));
        }

        enrollment.setTotalInstallments(saved.size());
        enrollment.setStatus(Enrollment.EnrollmentStatus.PAYMENT_PLAN_SENT);
        enrollmentRepo.save(enrollment);

        // Notify student
        createNotif(enrollment.getStudent(), "Payment Plan Created 💳",
            "Admin has created a " + saved.size() + "-installment payment plan for '" + enrollment.getCourseName() + "'. Please check and proceed with the first payment.",
            "PAYMENT_PLAN", enrollmentId);

        return saved.stream().map(this::toInstallmentMap).collect(Collectors.toList());
    }

    // ── Admin: update Stripe link for specific installment ────────────────────
    public Map<String, Object> updateInstallmentLink(Long installmentId, String stripeLink) {
        PaymentInstallment inst = installmentRepo.findById(installmentId).orElseThrow();
        inst.setStripePaymentLink(stripeLink);
        return toInstallmentMap(installmentRepo.save(inst));
    }

    // ── Admin: confirm installment payment ────────────────────────────────────
    public Map<String, Object> confirmInstallment(Long installmentId) {
        PaymentInstallment inst = installmentRepo.findById(installmentId).orElseThrow();
        inst.setStatus(PaymentInstallment.InstallmentStatus.CONFIRMED);
        inst.setPaidAt(LocalDateTime.now());
        installmentRepo.save(inst);

        Enrollment enrollment = inst.getEnrollment();

        // Update enrollment status
        List<PaymentInstallment> all = installmentRepo.findByEnrollmentOrderByInstallmentNumberAsc(enrollment);
        long confirmed = all.stream().filter(p -> p.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED).count();

        if (confirmed >= all.size()) {
            enrollment.setStatus(Enrollment.EnrollmentStatus.FULLY_PAID);
        } else {
            enrollment.setStatus(Enrollment.EnrollmentStatus.PARTIALLY_PAID);
        }
        enrollmentRepo.save(enrollment);

        createNotif(enrollment.getStudent(), "Payment Confirmed ✅",
            "Installment #" + inst.getInstallmentNumber() + " of ₹" + inst.getAmount() + " confirmed for '" + enrollment.getCourseName() + "'.",
            "PAYMENT_CONFIRMED", enrollment.getId());

        return Map.of("message", "Payment confirmed", "installment", toInstallmentMap(inst));
    }

    // ── Admin: assign writer (only after first installment paid) ─────────────
    public Map<String, Object> assignWriter(Long enrollmentId, Long writerUserId) {
        Enrollment e = enrollmentRepo.findById(enrollmentId).orElseThrow();
        List<PaymentInstallment> confirmed = installmentRepo.findConfirmedByEnrollment(enrollmentId);
        if (confirmed.isEmpty()) throw new RuntimeException("Cannot assign writer — no payment confirmed yet.");

        User writer = userRepo.findById(writerUserId).orElseThrow(() -> new RuntimeException("Writer not found"));
        e.setAssignedWriter(writer);
        e.setStatus(Enrollment.EnrollmentStatus.IN_PROGRESS);
        e = enrollmentRepo.save(e);

        // Notify writer
        createNotif(writer, "New Class Assigned 📚",
            "You have been assigned to '" + e.getCourseName() + "' from " + e.getClassStartDate() + " to " + e.getClassEndDate() + ". Please login to view details.",
            "WRITER_ASSIGNED", e.getId());

        createNotif(e.getStudent(), "Writer Assigned ✍️",
            "Our expert has been assigned to your class '" + e.getCourseName() + "'. Work is in progress!",
            "WRITER_ASSIGNED", e.getId());

        return Map.of("message", "Writer assigned successfully");
    }

    // ── Writer: get their enrollments (NO student personal details) ───────────
    public List<Map<String, Object>> getWriterEnrollments(String writerEmail) {
        User writer = userRepo.findByEmail(writerEmail).orElseThrow();
        return enrollmentRepo.findByAssignedWriterOrderByCreatedAtDesc(writer).stream()
            .map(e -> toWriterMap(e))
            .collect(Collectors.toList());
    }

    // ── Writer: upload completed assignment ZIP ────────────────────────────────
    public Map<String, Object> writerUploadZip(Long enrollmentId, MultipartFile file,
            String writerEmail, String description) throws IOException {
        Enrollment e = enrollmentRepo.findById(enrollmentId).orElseThrow();
        User writer = userRepo.findByEmail(writerEmail).orElseThrow();

        // Save file
        String path = saveAsZip(file, "solutions/", e.getCourseName());
        String fileName = file.getOriginalFilename();

        // Create submission history record (writer can upload multiple times)
        WriterSubmission submission = WriterSubmission.builder()
            .enrollment(e)
            .writer(writer)
            .zipPath(path)
            .fileName(fileName)
            .description(description != null ? description : "")
            .status(WriterSubmission.SubmissionStatus.PENDING_REVIEW)
            .build();
        submissionRepo.save(submission);

        // Update enrollment's latest zip pointer
        e.setDeliveryZipPath(path);
        e.setWriterFileApproved(false);
        if (e.getStatus() != Enrollment.EnrollmentStatus.IN_PROGRESS) {
            e.setStatus(Enrollment.EnrollmentStatus.IN_PROGRESS);
        }
        enrollmentRepo.save(e);

        notifyAdmins("New Assignment Submission",
            "Writer uploaded new assignment ZIP for: " + e.getCourseName()
            + (description != null && !description.isEmpty() ? " — Note: " + description : ""),
            e.getId(), e.getStudent());
        return Map.of("message", "Assignment uploaded successfully. Admin will review.", "submissionId", submission.getId());
    }

    // ── Admin: approve writer ZIP → available to student ─────────────────────
    public Map<String, Object> approveZip(Long enrollmentId) {
        Enrollment e = enrollmentRepo.findById(enrollmentId).orElseThrow();
        if (e.getDeliveryZipPath() == null) throw new RuntimeException("No ZIP uploaded yet");
        // Enforce full payment before delivery
        List<PaymentInstallment> allInst = installmentRepo.findByEnrollmentOrderByInstallmentNumberAsc(e);
        if (!allInst.isEmpty()) {
            long confirmed = allInst.stream().filter(p -> p.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED).count();
            if (confirmed < allInst.size()) {
                throw new RuntimeException("Cannot deliver — student has " + (allInst.size()-confirmed) + " unpaid installment(s). Full payment required before delivery.");
            }
        }
        e.setWriterFileApproved(true);
        e.setStatus(Enrollment.EnrollmentStatus.DELIVERED);
        e.setCompletedAt(LocalDateTime.now());
        enrollmentRepo.save(e);

        createNotif(e.getStudent(), "Assignments Ready for Download! 🎉",
            "Your completed assignments for '" + e.getCourseName() + "' are ready! Download the ZIP file from your dashboard.",
            "DELIVERED", e.getId());

        // Send email
        emailService.sendAssignmentStatusUpdate(
            e.getStudent().getEmail(), e.getStudent().getFullName(), e.getCourseName(), "DELIVERED");

        return Map.of("message", "Approved! Student can now download.", "enrollment", toAdminMap(e));
    }

    // ── Admin: approve a specific submission → becomes visible to student ────────
    public Map<String, Object> approveSubmission(Long submissionId, String adminNote) {
        WriterSubmission sub = submissionRepo.findById(submissionId)
            .orElseThrow(() -> new RuntimeException("Submission not found"));

        // IMMUTABILITY RULE: once APPROVED, no one can modify it
        if (sub.getStatus() == WriterSubmission.SubmissionStatus.APPROVED) {
            throw new RuntimeException("This submission is already approved and delivered to the student. It cannot be modified.");
        }

        Enrollment enrollment = sub.getEnrollment();

        // Enforce full payment check
        List<PaymentInstallment> allInst = installmentRepo.findByEnrollmentOrderByInstallmentNumberAsc(enrollment);
        if (!allInst.isEmpty()) {
            long confirmed = allInst.stream()
                .filter(p -> p.getStatus() == PaymentInstallment.InstallmentStatus.CONFIRMED).count();
            if (confirmed < allInst.size()) {
                throw new RuntimeException("Cannot approve — student has " + (allInst.size() - confirmed)
                    + " unpaid installment(s). Full payment required before delivery.");
            }
        }

        sub.setStatus(WriterSubmission.SubmissionStatus.APPROVED);
        sub.setReviewedAt(LocalDateTime.now());
        if (adminNote != null && !adminNote.isEmpty()) sub.setAdminNote(adminNote);
        submissionRepo.save(sub);

        // Update enrollment status to show something is available
        enrollment.setStatus(Enrollment.EnrollmentStatus.DELIVERED);
        enrollmentRepo.save(enrollment);

        // Notify student
        long approvedCount = submissionRepo.findApprovedByEnrollment(enrollment.getId()).size();
        createNotif(enrollment.getStudent(), "New Assignment Available! 🎉",
            "Assignment file from your '" + enrollment.getCourseName() + "' class is now available for download. ("
            + approvedCount + " total approved)", "DELIVERED", enrollment.getId());

        emailService.sendAssignmentStatusUpdate(
            enrollment.getStudent().getEmail(), enrollment.getStudent().getFullName(),
            enrollment.getCourseName(), "DELIVERED");

        return Map.of("message", "Submission approved! Student can now download this file.",
            "submissionId", submissionId, "approvedCount", approvedCount);
    }

    // ── Admin: reject a specific submission ─────────────────────────────────────
    public Map<String, Object> rejectSubmission(Long submissionId, String adminNote) {
        WriterSubmission sub = submissionRepo.findById(submissionId)
            .orElseThrow(() -> new RuntimeException("Submission not found"));

        // IMMUTABILITY RULE: once APPROVED, cannot be rejected or changed
        if (sub.getStatus() == WriterSubmission.SubmissionStatus.APPROVED) {
            throw new RuntimeException("This submission is already approved and delivered. It cannot be rejected.");
        }

        if (adminNote == null || adminNote.trim().isEmpty()) {
            throw new RuntimeException("A rejection reason is required.");
        }

        sub.setStatus(WriterSubmission.SubmissionStatus.REJECTED);
        sub.setReviewedAt(LocalDateTime.now());
        if (adminNote != null && !adminNote.isEmpty()) sub.setAdminNote(adminNote);
        submissionRepo.save(sub);

        // Notify writer
        createNotif(sub.getWriter(), "Submission Needs Revision",
            "Your assignment submission for '" + sub.getEnrollment().getCourseName()
            + "' was rejected." + (adminNote != null ? " Reason: " + adminNote : ""),
            "ADMIN_ALERT", sub.getEnrollment().getId());

        return Map.of("message", "Submission rejected. Writer has been notified.");
    }

    // ── Scheduled: send reminders 1 day before due date ──────────────────────
    public void sendInstallmentReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<PaymentInstallment> due = installmentRepo.findInstallmentsDueTomorrow(tomorrow);

        for (PaymentInstallment inst : due) {
            try {
                User student = inst.getEnrollment().getStudent();
                String course = inst.getEnrollment().getCourseName();
                String amount = inst.getCurrency() + " " + inst.getAmount();

                // Email reminder
                emailService.sendInstallmentReminder(student.getEmail(), student.getFullName(), course, inst.getInstallmentNumber(), amount, inst.getDueDate().toString(), inst.getStripePaymentLink());

                // WhatsApp reminder
                String waMsg = "💳 *Payment Reminder — " + course + "*\n\n"
                    + "Hi " + student.getFullName() + "! Your installment #" + inst.getInstallmentNumber()
                    + " of *" + amount + "* is due tomorrow (" + inst.getDueDate() + ").\n\n"
                    + "Pay here: " + (inst.getStripePaymentLink() != null ? inst.getStripePaymentLink() : "Contact admin for link")
                    + "\n\nAfter paying, upload your receipt in your dashboard.";

                if (student.getPhone() != null && !student.getPhone().isEmpty()) {
                    whatsAppService.sendWhatsApp(student.getPhone(), waMsg);
                }

                inst.setReminderSent(true);
                inst.setReminderSentAt(LocalDateTime.now());
                installmentRepo.save(inst);

                createNotif(student, "Payment Due Tomorrow! 💳",
                    "Installment #" + inst.getInstallmentNumber() + " of " + amount + " for '" + course + "' is due tomorrow.",
                    "PAYMENT_REMINDER", inst.getEnrollment().getId());

                log.info("Reminder sent for installment {} of enrollment {}", inst.getId(), inst.getEnrollment().getId());
            } catch (Exception ex) {
                log.error("Failed to send reminder for installment {}: {}", inst.getId(), ex.getMessage());
            }
        }
    }

    public Map<String, Object> getAdminStats() {
        return Map.of(
            "totalEnrollments", enrollmentRepo.count(),
            "submitted", enrollmentRepo.countByStatus(Enrollment.EnrollmentStatus.SUBMITTED),
            "inProgress", enrollmentRepo.countByStatus(Enrollment.EnrollmentStatus.IN_PROGRESS),
            "delivered", enrollmentRepo.countByStatus(Enrollment.EnrollmentStatus.DELIVERED),
            "partiallyPaid", enrollmentRepo.countByStatus(Enrollment.EnrollmentStatus.PARTIALLY_PAID),
            "fullyPaid", enrollmentRepo.countByStatus(Enrollment.EnrollmentStatus.FULLY_PAID)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String saveFile(MultipartFile f, String sub) throws IOException {
        Path dir = Paths.get(UPLOAD + sub);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String name = UUID.randomUUID() + "_" + f.getOriginalFilename();
        Files.write(dir.resolve(name), f.getBytes());
        return UPLOAD + sub + name;
    }

    private String saveAsZip(MultipartFile f, String sub, String title) throws IOException {
        Path dir = Paths.get(UPLOAD + sub);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String safe = title.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String zipName = UUID.randomUUID().toString().substring(0,8) + "_" + safe + "_Assignments.zip";
        Path zipPath = dir.resolve(zipName);
        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry(f.getOriginalFilename() != null ? f.getOriginalFilename() : "assignments.pdf"));
            zos.write(f.getBytes()); zos.closeEntry();
        }
        return UPLOAD + sub + zipName;
    }

    private void notifyAdmins(String title, String msg, Long refId, User ignore) {
        userRepo.findAll().stream().filter(u -> u.getRole() == User.Role.ROLE_ADMIN)
            .forEach(a -> createNotif(a, title, msg, "ADMIN_ALERT", refId));
    }

    private void createNotif(User user, String title, String msg, String type, Long refId) {
        Notification n = Notification.builder().user(user).title(title).message(msg)
            .type(Notification.NotificationType.GENERAL).referenceId(refId).build();
        notifRepo.save(n);
    }

    private Map<String, Object> toStudentMap(Enrollment e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("courseName", e.getCourseName());
        m.put("institutionName", e.getInstitutionName()); m.put("subject", e.getSubject());
        m.put("courseDescription", e.getCourseDescription());
        m.put("classStartDate", e.getClassStartDate()); m.put("classEndDate", e.getClassEndDate());
        m.put("status", e.getStatus()); m.put("totalPrice", e.getTotalPrice());
        m.put("totalInstallments", e.getTotalInstallments());
        m.put("adminReply", e.getAdminReply()); m.put("studentNotes", e.getStudentNotes());
        m.put("referenceDocPath", e.getReferenceDocPath());
        // Include all individually approved submissions for student download
        List<Map<String, Object>> approvedSubs = submissionRepo.findApprovedByEnrollment(e.getId()).stream()
            .map(s -> { Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("id", s.getId()); sm.put("fileName", s.getFileName());
                sm.put("zipPath", s.getZipPath()); sm.put("description", s.getDescription());
                sm.put("uploadedAt", s.getUploadedAt()); sm.put("reviewedAt", s.getReviewedAt());
                return sm; })
            .collect(java.util.stream.Collectors.toList());
        m.put("approvedSubmissions", approvedSubs);
        m.put("hasApprovedFiles", !approvedSubs.isEmpty());
        // Keep legacy single-file field for backward compat
        m.put("deliveryZipPath", !approvedSubs.isEmpty() ? ((Map)approvedSubs.get(0)).get("zipPath") : null);
        m.put("writerFileApproved", !approvedSubs.isEmpty());
        m.put("assignedWriterName", e.getAssignedWriter() != null ? e.getAssignedWriter().getFullName() : null);
        m.put("createdAt", e.getCreatedAt()); m.put("completedAt", e.getCompletedAt());
        m.put("installments", installmentRepo.findByEnrollmentOrderByInstallmentNumberAsc(e).stream().map(this::toInstallmentMap).collect(Collectors.toList()));
        return m;
    }

    private Map<String, Object> toAdminMap(Enrollment e) {
        Map<String, Object> m = new LinkedHashMap<>(toStudentMap(e));
        m.put("portalUrl", e.getPortalUrl()); m.put("portalUsername", e.getPortalUsername());
        m.put("portalPassword", e.getPortalPassword());
        m.put("adminNotes", e.getAdminNotes());
        m.put("deliveryZipPath", e.getDeliveryZipPath()); // admin always sees
        // All submissions for admin review
        List<Map<String, Object>> allSubs = submissionRepo.findByEnrollmentIdOrderByUploadedAtDesc(e.getId()).stream()
            .map(s -> { Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("id", s.getId()); sm.put("fileName", s.getFileName());
                sm.put("zipPath", s.getZipPath()); sm.put("description", s.getDescription());
                sm.put("status", s.getStatus()); sm.put("uploadedAt", s.getUploadedAt());
                sm.put("reviewedAt", s.getReviewedAt()); sm.put("adminNote", s.getAdminNote());
                sm.put("writerName", s.getWriter() != null ? s.getWriter().getFullName() : "");
                return sm; })
            .collect(java.util.stream.Collectors.toList());
        m.put("writerSubmissions", allSubs);
        m.put("studentName", e.getStudent().getFullName());
        m.put("studentEmail", e.getStudent().getEmail());
        m.put("studentPhone", e.getStudent().getPhone());
        return m;
    }

    private Map<String, Object> toWriterMap(Enrollment e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("courseName", e.getCourseName());
        m.put("institutionName", e.getInstitutionName()); m.put("subject", e.getSubject());
        m.put("courseDescription", e.getCourseDescription());
        m.put("classStartDate", e.getClassStartDate()); m.put("classEndDate", e.getClassEndDate());
        m.put("status", e.getStatus()); m.put("studentNotes", e.getStudentNotes());
        // Only show credentials if admin explicitly approved for this writer
        boolean credApproved = invitationRepo.findByEnrollmentIdAndWriterId(e.getId(),
            e.getAssignedWriter() != null ? e.getAssignedWriter().getId() : -1L)
            .map(inv -> Boolean.TRUE.equals(inv.getCredentialsApproved()))
            .orElse(false);
        m.put("credentialsApproved", credApproved);
        m.put("portalUrl",      credApproved ? e.getPortalUrl()      : null);
        m.put("portalUsername", credApproved ? e.getPortalUsername() : null);
        m.put("portalPassword", credApproved ? e.getPortalPassword() : null);
        m.put("referenceDocPath", e.getReferenceDocPath());
        m.put("deliveryZipPath", e.getDeliveryZipPath());
        m.put("writerFileApproved", e.getWriterFileApproved());
        m.put("createdAt", e.getCreatedAt());
        // NO student name/email/phone
        return m;
    }

    private Map<String, Object> toInstallmentMap(PaymentInstallment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("installmentNumber", p.getInstallmentNumber());
        m.put("amount", p.getAmount()); m.put("currency", p.getCurrency());
        m.put("dueDate", p.getDueDate());
        m.put("status", p.getStatus()); m.put("receiptPath", p.getReceiptPath());
        m.put("notes", p.getNotes()); m.put("paidAt", p.getPaidAt());
        m.put("receiptUploadedAt", p.getReceiptUploadedAt()); m.put("reminderSent", p.getReminderSent());
        return m;
    }

    public List<Map<String, Object>> getSubmissionHistory(Long enrollmentId) {
        return submissionRepo.findByEnrollmentIdOrderByUploadedAtDesc(enrollmentId).stream()
            .map(s -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", s.getId());
                m.put("fileName", s.getFileName());
                m.put("zipPath", s.getZipPath());
                m.put("description", s.getDescription());
                m.put("status", s.getStatus());
                m.put("uploadedAt", s.getUploadedAt());
                m.put("reviewedAt", s.getReviewedAt());
                m.put("adminNote", s.getAdminNote());
                m.put("writerName", s.getWriter().getFullName());
                return m;
            })
            .collect(java.util.stream.Collectors.toList());
    }
}
