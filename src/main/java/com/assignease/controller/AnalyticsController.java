package com.assignease.controller;

import com.assignease.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Transactional
public class AnalyticsController {

    private final UserEventRepository eventRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final UserRepository userRepo;
    private final PaymentInstallmentRepository installmentRepo;

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics() {
        try {
            // Total counts
            long totalUsers       = userRepo.count();
            long totalEnrollments = enrollmentRepo.count();

            // Page view counts by event type
            Map<String, Long> eventCounts = new LinkedHashMap<>();
            eventRepo.findAll().forEach(e -> {
                String key = e.getEventType() != null ? e.getEventType() : "unknown";
                eventCounts.merge(key, 1L, Long::sum);
            });

            // Enrollment status breakdown
            Map<String, Long> enrollmentByStatus = new LinkedHashMap<>();
            enrollmentRepo.findAll().forEach(e -> {
                String status = e.getStatus() != null ? e.getStatus().name() : "UNKNOWN";
                enrollmentByStatus.merge(status, 1L, Long::sum);
            });

            // Payment totals
            long[] paymentTotals = {0L, 0L};
            installmentRepo.findAll().forEach(p -> {
                if (p.getStatus() != null && p.getStatus().name().equals("CONFIRMED")) {
                    paymentTotals[0]++;
                    paymentTotals[1] += p.getAmount() != null ? p.getAmount().longValue() : 0;
                }
            });

            // Top pages from events
            Map<String, Long> pageViews = new LinkedHashMap<>();
            eventRepo.findAll().stream()
                .filter(e -> "page_view".equals(e.getEventType()) && e.getPage() != null && !e.getPage().isBlank())
                .forEach(e -> pageViews.merge(e.getPage(), 1L, Long::sum));

            // Sort page views descending
            List<Map<String, Object>> topPages = pageViews.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("page", entry.getKey()); m.put("views", entry.getValue()); return m; })
                .collect(java.util.stream.Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalUsers", totalUsers);
            result.put("totalEnrollments", totalEnrollments);
            result.put("totalEvents", eventRepo.count());
            result.put("eventCounts", eventCounts);
            result.put("enrollmentByStatus", enrollmentByStatus);
            result.put("paymentsConfirmed", paymentTotals[0]);
            result.put("revenueCollected", paymentTotals[1]);
            result.put("topPages", topPages);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "totalUsers", 0, "totalEnrollments", 0, "totalEvents", 0,
                "eventCounts", Map.of(), "enrollmentByStatus", Map.of(),
                "paymentsConfirmed", 0, "revenueCollected", 0, "topPages", List.of(),
                "error", e.getMessage()
            ));
        }
    }
}
