package com.assignease.service;


import com.assignease.dto.EmailRequest;
import com.assignease.entity.EmailTemplate;
import com.assignease.repository.EmailTemplateRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateRepository templateRepository;

    public void sendEmail(EmailRequest request) {

        EmailTemplate template = templateRepository
                .findByNameAndActiveTrue(request.getTemplateName())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        String processedHtml = processTemplate(template.getBody(), request.getVariables());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(request.getTo());
            helper.setSubject(template.getSubject());
            helper.setText(processedHtml, true);

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Email send failed", e);
        }
    }

    private String processTemplate(String body, Map<String, Object> variables) {

        String result = body;

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue().toString());
        }

        return result;
    }
}