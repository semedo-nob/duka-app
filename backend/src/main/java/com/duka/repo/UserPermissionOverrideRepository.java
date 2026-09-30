package com.duka.repo;

import com.duka.domain.UserPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UserPermissionOverride.Key> {
    List<UserPermissionOverride> findByIdUserId(Long userId);
}
