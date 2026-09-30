package com.duka.security;

import com.duka.domain.UserAccount;
import com.duka.domain.UserPermissionOverride;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String name;
    private final String phone;
    private final String role;
    private final Long businessId;
    private final boolean active;
    private final Set<Permission> permissions;

    public UserPrincipal(Long id, String name, String phone, String role, Long businessId, boolean active, Set<Permission> permissions) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.businessId = businessId;
        this.active = active;
        this.permissions = Set.copyOf(permissions);
    }

    public static UserPrincipal from(UserAccount user, List<UserPermissionOverride> overrides) {
        EnumSet<Permission> permissions = EnumSet.copyOf(RolePermissions.defaults(user.getRole()));
        if (overrides != null) {
            for (UserPermissionOverride override : overrides) {
                Permission permission;
                try {
                    permission = Permission.valueOf(override.getId().getPermission());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (override.isGranted()) {
                    permissions.add(permission);
                } else {
                    permissions.remove(permission);
                }
            }
        }
        return new UserPrincipal(user.getId(), user.getName(), user.getPhone(), user.getRole().name(), user.getBusinessId(), user.isActive(), permissions);
    }

    public boolean allows(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (Permission permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission.name()));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
