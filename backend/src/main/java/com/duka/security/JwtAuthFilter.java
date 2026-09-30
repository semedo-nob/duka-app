package com.duka.security;

import com.duka.domain.AccountStatus;
import com.duka.domain.BusinessStatus;
import com.duka.domain.PlatformAdmin;
import com.duka.repo.BusinessRepository;
import com.duka.repo.PlatformAdminRepository;
import com.duka.repo.UserAccountRepository;
import com.duka.repo.UserPermissionOverrideRepository;
import com.duka.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserAccountRepository users;
    private final UserPermissionOverrideRepository overrides;
    private final PlatformAdminRepository platformAdmins;
    private final BusinessRepository businesses;
    private final SessionService sessions;
    private final ObjectMapper json;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var decoded = jwt.verify(header.substring(7));
                if ("platform".equals(decoded.getClaim("kind").asString())) {
                    platformAdmins.findById(Long.parseLong(decoded.getSubject())).ifPresent(this::authenticatePlatform);
                } else {
                    String sessionId = decoded.getClaim("sid").asString();
                    if (sessions.active(sessionId)) {
                        users.findById(Long.parseLong(decoded.getSubject())).ifPresent(user -> {
                            if (user.getStatus() != AccountStatus.ACTIVE) {
                                return;
                            }
                            UserPrincipal principal = UserPrincipal.from(user, overrides.findByIdUserId(user.getId()));
                            var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            authentication.setDetails(sessionId);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            sessions.touch(sessionId);
                        });
                    }
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        if (blocked(request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void authenticatePlatform(PlatformAdmin admin) {
        if (!admin.isActive()) {
            return;
        }
        String role = admin.getRole() == null || admin.getRole().isBlank() ? "SUPER_ADMIN" : admin.getRole();
        PlatformPrincipal principal = new PlatformPrincipal(admin.getId(), admin.getName(), role);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                new SimpleGrantedAuthority("ROLE_" + role)
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean blocked(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        var business = businesses.findById(principal.getBusinessId()).orElse(null);
        if (business == null || business.getStatus() == BusinessStatus.DEACTIVATED) {
            SecurityContextHolder.clearContext();
            return false;
        }
        BusinessStatus status = business.getStatus();
        if (status != BusinessStatus.SUSPENDED && status != BusinessStatus.CANCELLED
                && status != BusinessStatus.PENDING_APPROVAL && status != BusinessStatus.PENDING_VERIFICATION
                && status != BusinessStatus.REGISTERED && status != BusinessStatus.CLOSED) {
            return false;
        }
        if (allowedWhilePaused(request)) {
            return false;
        }
        response.setStatus(403);
        response.setContentType("application/json");
        String detail = switch (business.getStatus()) {
            case PENDING_APPROVAL, PENDING_VERIFICATION, REGISTERED ->
                    "This business is waiting for platform approval. You can contact support, but selling and changes stay paused.";
            case SUSPENDED -> "This business is suspended. Selling and changes are paused. You can still export records and contact support.";
            case CLOSED, CANCELLED -> "This business is closed. Records remain available to read. Selling and changes are paused.";
            default -> "This business cannot make changes right now.";
        };
        json.writeValue(response.getWriter(), java.util.Map.of("error", detail));
        return true;
    }

    private static boolean allowedWhilePaused(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/api/account/") || path.startsWith("/api/support");
    }
}
