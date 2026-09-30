package com.duka.repo;

import com.duka.domain.Installation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationRepository extends JpaRepository<Installation, String> {
}
