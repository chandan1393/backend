package com.assignease.controller;

import com.assignease.entity.User;
import com.assignease.entity.UserEvent;
import com.assignease.repository.UserEventRepository;
import com.assignease.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final UserEventRepository eventRepo;
    private final UserRepository userRepo;

    @PostMapping("/api/events/track")
    public ResponseEntity<?> track(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud,
            HttpServletRequest req) {
        try {
            UserEvent e = new UserEvent();
            e.setSessionId((String) body.getOrDefault("sessionId", ""));
            e.setEventType((String) body.getOrDefault("eventType", "page_view"));
            e.setPage((String) body.getOrDefault("page", ""));
            e.setElement((String) body.getOrDefault("element", ""));
            e.setMetadata((String) body.getOrDefault("metadata", "{}"));
            e.setUserAgent((String) body.getOrDefault("browserInfo", ""));
            e.setIpAddress(req.getRemoteAddr());
            if (ud != null) {
                userRepo.findByEmail(ud.getUsername()).ifPresent(e::setUser);
            }
            eventRepo.save(e);
        } catch (Exception ex) {
            // never fail the client for analytics
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
