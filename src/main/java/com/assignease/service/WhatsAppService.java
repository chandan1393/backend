package com.assignease.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class WhatsAppService {
//edited
    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.from}")
    private String fromNumber;

    @Value("${app.company.name}")
    private String companyName;

    /**
     * Sends a WhatsApp message via Twilio API.
     * Uses Twilio Sandbox for WhatsApp (works in test without approval).
     * For production: register a WhatsApp Business number in Twilio.
     */
    public boolean sendWhatsApp(String toPhone, String message) {
        if (accountSid == null || accountSid.isEmpty() || authToken == null || authToken.isEmpty()) {
            log.warn("Twilio credentials not configured. WhatsApp message NOT sent to {}", toPhone);
            log.info("Would have sent: {}", message);
            return false;
        }

        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

            String body = "From=" + URLEncoder.encode(fromNumber, StandardCharsets.UTF_8)
                    + "&To=" + URLEncoder.encode("whatsapp:" + toPhone, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            String credentials = Base64.getEncoder()
                    .encodeToString((accountSid + ":" + authToken).getBytes());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                log.info("WhatsApp sent successfully to {}", toPhone);
                return true;
            } else {
                log.error("WhatsApp send failed: {} — {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("WhatsApp send error: {}", e.getMessage());
            return false;
        }
    }

    public String buildLeadMessage(String phone, String companyName) {
        return "Hi! 👋 Thank you for your interest in *" + companyName + "*.\n\n"
                + "We received your request from " + phone + ".\n"
                + "Our team will contact you shortly to discuss your assignment requirements.\n\n"
                + "You can also submit a detailed query at: http://localhost:4200\n\n"
                + "Best regards,\n" + companyName + " Team 🎓";
    }
}
