package com.assignease.controller;

import com.assignease.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** Send a message — context: STUDENT_ADMIN or ADMIN_WRITER */
    @PostMapping("/{assignmentId}/{context}")
    public ResponseEntity<?> sendMessage(
            @PathVariable Long assignmentId,
            @PathVariable String context,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            var msg = chatService.sendMessage(assignmentId, context, ud.getUsername(), body.get("message"));
            return ResponseEntity.ok(Map.of("id", msg.getId(), "message", "Message sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Get messages for an assignment chat */
    @GetMapping("/{assignmentId}/{context}")
    public ResponseEntity<?> getMessages(
            @PathVariable Long assignmentId,
            @PathVariable String context) {
        return ResponseEntity.ok(chatService.getMessages(assignmentId, context));
    }

    /** Mark messages as read */
    @PostMapping("/{assignmentId}/{context}/read")
    public ResponseEntity<?> markRead(
            @PathVariable Long assignmentId,
            @PathVariable String context,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            // Get user id from email
            var userId = chatService.getUserId(ud.getUsername());
            chatService.markAsRead(assignmentId, context, userId);
            return ResponseEntity.ok(Map.of("message", "Marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Unread count for a chat */
    @GetMapping("/{assignmentId}/{context}/unread")
    public ResponseEntity<?> unreadCount(
            @PathVariable Long assignmentId,
            @PathVariable String context,
            @AuthenticationPrincipal UserDetails ud) {
        try {
            var userId = chatService.getUserId(ud.getUsername());
            long count = chatService.getUnreadCount(assignmentId, context, userId);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
