package com.duka.domain;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_permission_overrides")
@Getter
@Setter
@NoArgsConstructor
public class UserPermissionOverride {
    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private boolean granted;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements java.io.Serializable {
        @Column(name = "user_id")
        private Long userId;
        @Column(name = "permission")
        private String permission;
    }
}
