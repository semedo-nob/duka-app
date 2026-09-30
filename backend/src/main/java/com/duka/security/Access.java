package com.duka.security;

import com.duka.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Access {
    private Access() {}

    public static boolean has(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (permission.name().equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static void require(Permission permission) {
        if (!has(permission)) {
            throw new ApiException(403, "You don't have permission to do that");
        }
    }
}
