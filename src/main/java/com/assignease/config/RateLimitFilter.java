package com.assignease.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for brute force protection.
 * /api/auth/login: max 5 attempts per IP per minute
 * /api/public/**:  max 30 requests per IP per minute
 */
@Component
public class RateLimitFilter implements Filter {

    // IP → (attempt count, window start time ms)
    private final Map<String, long[]> loginAttempts  = new ConcurrentHashMap<>();
    private final Map<String, long[]> publicRequests = new ConcurrentHashMap<>();

    private static final int LOGIN_MAX   = 5;   // per minute
    private static final int PUBLIC_MAX  = 60;  // per minute
    private static final long WINDOW_MS  = 60_000L;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  hreq  = (HttpServletRequest) req;
        HttpServletResponse hres  = (HttpServletResponse) res;
        String uri = hreq.getRequestURI();
        String ip  = getIp(hreq);

        if (uri.contains("/api/auth/login")) {
            if (isRateLimited(ip, loginAttempts, LOGIN_MAX)) {
                hres.setStatus(429);
                hres.setContentType("application/json");
                hres.getWriter().write("{\"message\":\"Too many login attempts. Please wait 60 seconds before trying again.\"}");
                return;
            }
        } else if (uri.startsWith("/api/public/")) {
            if (isRateLimited(ip, publicRequests, PUBLIC_MAX)) {
                hres.setStatus(429);
                hres.setContentType("application/json");
                hres.getWriter().write("{\"message\":\"Too many requests. Please slow down.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean isRateLimited(String ip, Map<String, long[]> map, int max) {
        long now = System.currentTimeMillis();
        map.compute(ip, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) return new long[]{1, now};
            v[0]++; return v;
        });
        long[] entry = map.get(ip);
        if (now - entry[1] > WINDOW_MS) { map.put(ip, new long[]{1, now}); return false; }
        return entry[0] > max;
    }

    private String getIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
