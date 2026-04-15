package com.assignease.service;

import com.assignease.dto.AppDTOs;
import com.assignease.dto.EmailRequest;
import com.assignease.entity.User;
import com.assignease.enums.EmailTemplateName;
import com.assignease.repository.AssignmentRepository;
import com.assignease.repository.UserRepository;
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
public class UserService {

    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailFacadeService emailFacadeService;


    @Value("${app.email.enabled}")
    private boolean emailEnabled;

    public AppDTOs.UserResponse createUser(AppDTOs.UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        String tempPassword = generateTempPassword();

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(tempPassword))
                .role(User.Role.valueOf(request.getRole() != null ? request.getRole() : "ROLE_STUDENT"))
                .enabled(true)
                .firstLogin(true)
                .tempPassword(tempPassword)
                .build();

        user = userRepository.save(user);

        if (emailEnabled) {
            emailFacadeService.sendWelcomeEmail(user.getEmail(), user.getFullName(), tempPassword);
        }
        return mapToUserResponse(user);
    }

    public User createUserFromQuery(String name, String email) {
        if (userRepository.existsByEmail(email)) {
            return userRepository.findByEmail(email).orElseThrow();
        }

        String tempPassword = generateTempPassword();

        User user = User.builder()
                .fullName(name)
                .email(email)
                .password(passwordEncoder.encode(tempPassword))
                .role(User.Role.ROLE_STUDENT)
                .enabled(true)
                .firstLogin(true)
                .tempPassword(tempPassword)
                .build();

        user = userRepository.save(user);
        if (emailEnabled) {
            emailFacadeService.sendWelcomeEmail(user.getEmail(), user.getFullName(), tempPassword);
        }
        return user;
    }

    public List<AppDTOs.UserResponse> getAllStudents() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ROLE_STUDENT)
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public List<AppDTOs.UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public AppDTOs.UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    public AppDTOs.UserResponse updateUser(Long id, AppDTOs.UserCreateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        return mapToUserResponse(userRepository.save(user));
    }

    public void toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    public AppDTOs.UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    private AppDTOs.UserResponse mapToUserResponse(User user) {
        AppDTOs.UserResponse resp = new AppDTOs.UserResponse();
        resp.setId(user.getId());
        resp.setFullName(user.getFullName());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setRole(user.getRole().name());
        resp.setEnabled(user.isEnabled());
        resp.setFirstLogin(user.isFirstLogin());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setAssignmentCount(assignmentRepository.countByStudent(user));
        return resp;
    }

    private String generateTempPassword() {
        return "Ae@" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
