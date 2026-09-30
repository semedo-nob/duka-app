package com.duka.repo;

import com.duka.domain.StaffInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {
    Optional<StaffInvitation> findByTokenHash(String tokenHash);
}
