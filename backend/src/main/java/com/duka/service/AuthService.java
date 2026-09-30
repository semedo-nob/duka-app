package com.duka.service;

import com.duka.config.DukaProperties;
import com.duka.config.Phones;
import com.duka.domain.AccountStatus;
import com.duka.domain.Business;
import com.duka.domain.BusinessStatus;
import com.duka.domain.UserAccount;
import com.duka.domain.UserRole;
import com.duka.repo.BusinessRepository;
import com.duka.repo.UserAccountRepository;
import com.duka.security.RolePermissions;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserAccountRepository users;
    private final BusinessRepository businesses;
    private final PasswordEncoder encoder;
    private final DukaProperties properties;
    private final SessionService sessions;
    private final LoginAttemptService attempts;

    public Dto.AuthResponse login(Dto.LoginRequest request, String deviceId, String userAgent) {
        String phone = Phones.normalize(request.phone());
        UserAccount user = users.findByPhone(phone).orElse(null);
        if (user == null) {
            throw new ApiException(401, "Phone or PIN is incorrect");
        }
        if (user.getStatus() == AccountStatus.LOCKED && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(423, "This login is temporarily locked. Try again in a few minutes.");
        }
        if (user.getStatus() == AccountStatus.INVITED) {
            throw new ApiException(401, "Use the invitation code to choose a PIN before logging in.");
        }
        if (user.getStatus() == AccountStatus.DISABLED || user.getStatus() == AccountStatus.DEACTIVATED || !user.isActive()) {
            throw new ApiException(401, "This login is disabled.");
        }
        if (!encoder.matches(request.pin(), user.getPinHash())) {
            attempts.recordFailure(user.getId());
            throw new ApiException(401, "Phone or PIN is incorrect");
        }
        Business business = businesses.findById(user.getBusinessId()).orElseThrow(() -> new ApiException(401, "Phone or PIN is incorrect"));
        if (business.getStatus() == BusinessStatus.DEACTIVATED) {
            throw new ApiException(403, "This business is closed.");
        }
        String token = sessions.open(user, deviceId, userAgent);
        return new Dto.AuthResponse(token, view(users.findById(user.getId()).orElse(user)));
    }

    @Transactional
    public Dto.AuthResponse register(Dto.RegisterRequest request, String deviceId, String userAgent) {
        boolean firstAccount = users.count() == 0;
        if (!properties.getSecurity().isAllowRegistration() && !firstAccount) {
            throw new ApiException(403, "Registration is closed on this server");
        }
        if (request.pin().length() < 4) {
            throw new ApiException(400, "PIN must be at least 4 characters");
        }
        String phone = Phones.normalize(request.phone());
        if (users.findByPhone(phone).isPresent()) {
            throw new ApiException(409, "That phone already has a login");
        }
        Business business = new Business();
        business.setName(request.businessName() == null || request.businessName().isBlank() ? "My Business" : request.businessName().trim());
        business.setBusinessType(request.businessType() == null ? "" : request.businessType().trim());
        business.setCategory(request.category() == null ? "" : request.category().trim());
        business.setEmail(request.email() == null ? "" : request.email().trim());
        business.setPhone(phone);
        business.setStatus(BusinessStatus.PENDING_APPROVAL);
        business.setActive(false);
        business.setStatusReason("Waiting for platform approval");
        business.setCurrency("KES");
        business.setTimezone("Africa/Nairobi");
        businesses.save(business);
        UserAccount user = new UserAccount();
        user.setPhone(phone);
        user.setBusinessId(business.getId());
        user.setName(request.name() == null || request.name().isBlank() ? "Owner" : request.name().trim());
        user.setPinHash(encoder.encode(request.pin()));
        user.setRole(UserRole.OWNER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setActive(true);
        users.saveAndFlush(user);
        return new Dto.AuthResponse(sessions.open(user, deviceId, userAgent), view(user));
    }

    private Dto.UserView view(UserAccount user) {
        var permissions = RolePermissions.defaults(user.getRole()).stream().map(Enum::name).sorted().toList();
        return new Dto.UserView(user.getId(), user.getName(), user.getPhone(), user.getRole().name(), user.getBusinessId(), permissions);
    }
}
