package com.duka.service;

import com.duka.domain.AuditEvent;
import com.duka.repo.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditEventRepository auditEvents;

    public void log(String actor, String action, String detail) {
        log(null, actor, action, detail, "", "", "");
    }

    public void log(Long businessId, String actor, String action, String detail, String entityType, String entityId, String deviceId) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setBusinessId(businessId);
        event.setActor(actor == null || actor.isBlank() ? "System" : actor);
        event.setAction(action);
        event.setDetail(detail == null ? "" : detail);
        event.setEntityType(entityType == null ? "" : entityType);
        event.setEntityId(entityId == null ? "" : entityId);
        event.setDeviceId(deviceId == null ? "" : deviceId);
        auditEvents.save(event);
    }
}
