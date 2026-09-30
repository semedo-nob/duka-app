package com.duka.security;

import com.duka.config.Phones;
import com.duka.domain.UserAccount;
import com.duka.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Present so Spring Boot does not invent an in-memory user and a generated password.
 * Checkout login still goes through {@code AuthService} (phone, PIN, JWT). This bean is not exposed as HTTP basic.
 */
@Service
@RequiredArgsConstructor
public class DukaUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) {
        String phone = Phones.normalize(username);
        UserAccount user = users.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown phone"));
        return User.withUsername(user.getPhone())
                .password(user.getPinHash())
                .roles(user.getRole().name())
                .disabled(!user.isActive())
                .build();
    }
}
