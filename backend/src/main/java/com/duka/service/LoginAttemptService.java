package com.duka.service;

import com.duka.domain.AccountStatus;
import com.duka.domain.UserAccount;
import com.duka.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    static final int LOCK_AFTER = 5;

    private final UserAccountRepository users;
    private final AuditService audit;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        UserAccount user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        audit.log(user.getBusinessId(), user.getName(), "LOGIN_FAILED", "Failed sign-in", "USER", user.getId().toString(), "");
        if (user.getFailedAttempts() >= LOCK_AFTER && user.getStatus() == AccountStatus.ACTIVE) {
            user.setStatus(AccountStatus.LOCKED);
            user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            audit.log(user.getBusinessId(), user.getName(), "ACCOUNT_LOCKED", "Too many failed sign-ins", "USER", user.getId().toString(), "");
        }
        users.save(user);
    }
}
