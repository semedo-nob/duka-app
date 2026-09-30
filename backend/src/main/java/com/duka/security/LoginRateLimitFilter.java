package com.duka.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean limited = "POST".equals(request.getMethod()) && (
                path.equals("/api/auth/login")
                        || path.equals("/api/auth/register")
                        || path.equals("/api/auth/invitations/accept")
                        || path.equals("/api/auth/recovery/request")
                        || path.equals("/api/auth/recovery/complete")
                        || path.equals("/api/platform/login"));
        if (limited && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many attempts. Wait a minute and try again.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key) {
        Instant now = Instant.now();
        Deque<Instant> window = hits.computeIfAbsent(key == null ? "unknown" : key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minusSeconds(60))) {
                window.removeFirst();
            }
            if (window.size() >= 20) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
