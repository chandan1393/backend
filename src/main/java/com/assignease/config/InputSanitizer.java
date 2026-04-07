package com.assignease.config;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * InputSanitizer — prevents XSS and SQL injection at the application level.
 *
 * SQL Injection: Already handled by Spring Data JPA / JPQL with named parameters.
 * All repositories use parameter binding, never string concatenation.
 * Native queries are forbidden (no nativeQuery=true in this codebase).
 *
 * XSS: All string inputs from users are HTML-escaped before being stored,
 * preventing script injection in rendered output.
 */
@Component
public class InputSanitizer {

    /** HTML-escape a string to prevent XSS. Null-safe. */
    public String sanitize(String input) {
        if (input == null) return null;
        return HtmlUtils.htmlEscape(input.trim());
    }

    /** Sanitize and truncate to max length. */
    public String sanitize(String input, int maxLength) {
        String s = sanitize(input);
        if (s == null) return null;
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    /** Only allow digits, spaces, hyphens, parentheses in phone numbers. */
    public String sanitizePhone(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[^0-9\\+\\-\\s\\(\\)]", "").trim();
    }

    /** Only allow safe characters in names (letters, spaces, hyphens, apostrophes). */
    public String sanitizeName(String name) {
        if (name == null) return null;
        return name.replaceAll("[^\\p{L}\\p{M}\\s\\-\\']", "").trim();
    }

    /** Strip dangerous patterns that could indicate injection attempts. */
    public boolean containsSuspiciousContent(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("<script") || lower.contains("javascript:") ||
               lower.contains("on error=") || lower.contains("onerror=") ||
               lower.contains("onload=") || lower.contains("drop table") ||
               lower.contains("delete from") || lower.contains("insert into") ||
               lower.contains("union select") || lower.contains("exec(") ||
               lower.contains("execute(") || lower.contains("xp_cmd");
    }
}
