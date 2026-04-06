package com.assignease.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import java.util.TimeZone;

/**
 * Forces the JVM to run in UTC.
 * 
 * WHY: Spring Boot/Hibernate use the JVM timezone when storing LocalDateTime.
 * By forcing UTC here, all stored timestamps are UTC regardless of the server's
 * OS timezone (whether server is in India, USA, etc.).
 *
 * HOW timezone display works for users:
 * - Backend stores: "2025-06-15T09:30:00.000Z" (UTC)
 * - Angular DatePipe formats using the USER'S browser timezone automatically
 * - A US student sees: "Jun 15, 2025, 5:30 AM" (EDT = UTC-4)
 * - An Indian admin sees: "Jun 15, 2025, 3:00 PM" (IST = UTC+5:30)
 * - Same UTC timestamp, different display — seamless, no leakage
 *
 * DATE-ONLY fields (dueDate, classStartDate etc.) use LocalDate = no timezone.
 * These are agreed calendar dates ("due by June 15") that mean the same day
 * everywhere regardless of timezone.
 */
@Configuration
public class TimezoneConfig {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
