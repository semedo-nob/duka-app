package com.duka.service;

import com.duka.domain.AccountStatus;
import com.duka.domain.UserAccount;
import com.duka.domain.UserSession;
import com.duka.repo.UserAccountRepository;
import com.duka.repo.UserSessionRepository;
import com.duka.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserSessionRepository sessions;
    private final UserAccountRepository users;
    private final JwtService jwt;

    @Transactional
    public String open(UserAccount user, String deviceId, String userAgent) {
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setBusinessId(user.getBusinessId());
        session.setDeviceId(deviceId == null ? "" : deviceId);
        session.setUserAgent(clip(userAgent));
        session.setExpiresAt(Instant.now().plus(jwt.ttlMinutes(), ChronoUnit.MINUTES));
        sessions.save(session);
        user.setLastLoginAt(Instant.now());
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == AccountStatus.LOCKED) {
            user.setStatus(AccountStatus.ACTIVE);
        }
        users.save(user);
        return jwt.issue(user, session.getId());
    }

    @Transactional(readOnly = true)
    public boolean active(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return sessions.findById(sessionId)
                .map(session -> session.getRevokedAt() == null && session.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }

    @Transactional
    public void touch(String sessionId) {
        sessions.findById(sessionId).ifPresent(session -> {
            if (session.getLastSeenAt().isBefore(Instant.now().minusSeconds(60))) {
                session.setLastSeenAt(Instant.now());
            }
        });
    }

    @Transactional
    public void revoke(UserSession session) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
        }
    }

    @Transactional
    public void revokeAll(Long userId) {
        for (UserSession session : sessions.findByUserIdOrderByCreatedAtDesc(userId)) {
            revoke(session);
        }
    }

    @Transactional
    public void revokeOthers(Long userId, String keepSessionId) {
        for (UserSession session : sessions.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (!session.getId().equals(keepSessionId)) {
                revoke(session);
            }
        }
    }

    private static String clip(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() <= 200 ? userAgent : userAgent.substring(0, 200);
    }
}
