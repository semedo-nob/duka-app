package com.duka.security;

import lombok.Getter;

@Getter
public class PlatformPrincipal {
    private final Long id;
    private final String name;

    private final String role;

    public PlatformPrincipal(Long id, String name, String role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }
}
