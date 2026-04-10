package com.assignease.service;



import com.assignease.dto.EmailRequest;
import com.assignease.enums.EmailTemplateName;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailFacadeService {

    private final EmailProducer emailProducer;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ✅ 1. Welcome Email
    public void sendWelcomeEmail(String email, String name, String password) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", name);
        vars.put("email", email);
        vars.put("password", password);
        vars.put("frontendUrl", frontendUrl);

        send(email, EmailTemplateName.WELCOME_EMAIL, vars);
    }

    // ✅ 2. Query Confirmation
    public void sendQueryConfirmation(String email, String name, Long queryId) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", name);
        vars.put("queryId", queryId);

        send(email, EmailTemplateName.QUERY_CONFIRMATION, vars);
    }

    // ✅ 3. Assignment Status Update
    public void sendAssignmentUpdate(String email, String name, String title, String status) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", name);
        vars.put("assignmentTitle", title);
        vars.put("status", status);
        vars.put("frontendUrl", frontendUrl);

        send(email, EmailTemplateName.ASSIGNMENT_STATUS_UPDATE, vars);
    }

    // ✅ 4. Password Reset
    public void sendPasswordReset(String email, String token) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("token", token);
        vars.put("frontendUrl", frontendUrl);

        send(email, EmailTemplateName.PASSWORD_RESET, vars);
    }

    // ✅ 5. Installment Reminder
    public void sendInstallmentReminder(String email, String name, String course,
                                        int installmentNum, String amount, LocalDate dueDate, String link) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", name);
        vars.put("courseName", course);
        vars.put("installmentNum", installmentNum);
        vars.put("amount", "₹" + amount);
        vars.put("dueDate", dueDate.format(formatter));
        vars.put("paymentLink", link != null ? link : "#");

        send(email, EmailTemplateName.INSTALLMENT_REMINDER, vars);
    }

    // 🔥 COMMON METHOD (single place)
    private void send(String to, EmailTemplateName template, Map<String, Object> vars) {

        EmailRequest request = new EmailRequest();
        request.setTo(to);
        request.setTemplateName(template.name());
        request.setVariables(vars);

        emailProducer.sendEmail(request);
    }
}