package com.assignease.service;

import com.assignease.dto.EmailRequest;
import com.assignease.entity.Assignment;
import com.assignease.entity.Notification;
import com.assignease.entity.User;
import com.assignease.entity.Writer;
import com.assignease.enums.EmailTemplateName;
import com.assignease.repository.AssignmentRepository;
import com.assignease.repository.NotificationRepository;
import com.assignease.repository.UserRepository;
import com.assignease.repository.WriterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WriterService {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final WriterRepository writerRepository;
    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailFacadeService emailFacadeService;

    public Map<String, Object> createWriter(String fullName, String email, String phone, String bio, String expertise) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        String tempPassword = "Wr@" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        User user = User.builder()
                .fullName(fullName).email(email).phone(phone)
                .password(passwordEncoder.encode(tempPassword))
                .role(User.Role.ROLE_WRITER)
                .enabled(true).firstLogin(true).tempPassword(tempPassword)
                .build();
        user = userRepository.save(user);

        Writer writer = Writer.builder()
                .user(user).bio(bio).expertise(expertise).available(true)
                .rating(0.0).completedAssignments(0)
                .build();
        writerRepository.save(writer);


        emailFacadeService.sendWelcomeEmail(user.getEmail(),user.getFullName(),tempPassword);
        return Map.of("message", "Writer created", "email", email, "tempPassword", tempPassword);
    }

    public List<Map<String, Object>> getAllWriters() {
        return writerRepository.findAll().stream().map(w -> Map.of(
                "id", w.getId(),
                "userId", w.getUser().getId(),
                "fullName", (Object) w.getUser().getFullName(),
                "email", (Object) w.getUser().getEmail(),
                "expertise", w.getExpertise() != null ? w.getExpertise() : "",
                "bio", w.getBio() != null ? w.getBio() : "",
                "rating", (Object) w.getRating(),
                "completedAssignments", (Object) w.getCompletedAssignments(),
                "available", (Object) w.getAvailable()
        )).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAvailableWriters() {
        return writerRepository.findByAvailableTrue().stream().map(w -> Map.of(
                "id", w.getId(),
                "userId", w.getUser().getId(),
                "fullName", (Object) w.getUser().getFullName(),
                "expertise", w.getExpertise() != null ? w.getExpertise() : "",
                "rating", (Object) w.getRating(),
                "completedAssignments", (Object) w.getCompletedAssignments()
        )).collect(Collectors.toList());
    }

    public void assignWriterToAssignment(Long assignmentId, Long writerUserId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        // Only allow assigning writer after payment is received
        if (!Boolean.TRUE.equals(assignment.getPaymentDone())) {
            throw new RuntimeException("Cannot assign writer — student payment not received yet.");
        }

        User writer = userRepository.findById(writerUserId)
                .orElseThrow(() -> new RuntimeException("Writer not found"));

        assignment.setAssignedWriter(writer);
        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignmentRepository.save(assignment);

        // Notify writer
        Notification n = Notification.builder()
                .user(writer)
                .title("New Assignment Assigned")
                .message("You have been assigned: " + assignment.getTitle())
                .type(Notification.NotificationType.ASSIGNMENT_CREATED)
                .referenceId(assignmentId)
                .build();
        notificationRepository.save(n);
    }

    public List<Map<String, Object>> getWriterAssignments(String email) {
        User writer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Writer not found"));

        return assignmentRepository.findAll().stream()
                .filter(a -> a.getAssignedWriter() != null && a.getAssignedWriter().getId().equals(writer.getId()))
                .map(a -> Map.of(
                        "id", a.getId(),
                        "title", (Object) a.getTitle(),
                        "subject", (Object) a.getSubject(),
                        "description", (Object) a.getDescription(),
                        "level", a.getLevel() != null ? a.getLevel() : "",
                        "deadline", a.getDeadline() != null ? a.getDeadline().toString() : "",
                        "status", (Object) a.getStatus().name(),
                        "studentName", a.getStudent() != null ? a.getStudent().getFullName() : ""
                )).collect(Collectors.toList());
    }

    public void markAssignmentComplete(Long assignmentId, String writerEmail) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));
        assignment.setStatus(Assignment.AssignmentStatus.UNDER_REVIEW);
        assignmentRepository.save(assignment);

        // Update writer stats
        User writer = userRepository.findByEmail(writerEmail).orElseThrow();
        writerRepository.findByUser(writer).ifPresent(w -> {
            w.setCompletedAssignments(w.getCompletedAssignments() + 1);
            writerRepository.save(w);
        });

        // Notify student
        Notification n = Notification.builder()
                .user(assignment.getStudent())
                .title("Assignment Completed")
                .message("Your assignment '" + assignment.getTitle() + "' has been completed!")
                .type(Notification.NotificationType.ASSIGNMENT_COMPLETED)
                .referenceId(assignmentId)
                .build();
        notificationRepository.save(n);
    }
}
