package com.duka.repo;

import com.duka.domain.DeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRegistrationRepository extends JpaRepository<DeviceRegistration, String> {
    List<DeviceRegistration> findByBusinessId(Long businessId);
}
