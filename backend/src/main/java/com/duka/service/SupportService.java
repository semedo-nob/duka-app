package com.duka.service;

import com.duka.domain.SupportCase;
import com.duka.domain.SupportMessage;
import com.duka.repo.SupportCaseRepository;
import com.duka.repo.SupportMessageRepository;
import com.duka.security.PlatformPrincipal;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupportService {
    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final AuditService audit;

    public static final List<String> CATEGORIES = List.of(
            "SUBSCRIPTION", "BILLING", "PAYMENT", "TECHNICAL", "ACCOUNT", "DEVICE", "PRINTER", "SCANNER", "M-PESA", "ETIMS", "SYNC", "OFFLINE", "OTHER");

    @Transactional
    public Map<String, Object> open(Dto.SupportRequest request, UserPrincipal user) {
        SupportCase supportCase = new SupportCase();
        supportCase.setBusinessId(user.getBusinessId());
        supportCase.setOpenedBy(user.getId());
        supportCase.setCategory(category(request.category(), request.topic()));
        supportCase.setTopic(request.topic().trim());
        supportCase.setSummary(request.message().trim());
        supportCase.setStatus("OPEN");
        supportCase.setPriority(priority(request.priority()));
        cases.save(supportCase);
        add(supportCase.getId(), "CUSTOMER", user.getName(), request.message().trim());
        String context = diagnostics(request.diagnostics());
        if (!context.isBlank()) {
            add(supportCase.getId(), "CONTEXT", "Device", context);
        }
        audit.log(user.getBusinessId(), user.getName(), "SUPPORT_TICKET_CREATED", supportCase.getCategory(), "SUPPORT", supportCase.getId().toString(), "");
        return detail(supportCase);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UserPrincipal user) {
        return cases.findByBusinessIdOrderByUpdatedAtDesc(user.getBusinessId()).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id, UserPrincipal user) {
        SupportCase supportCase = cases.findById(id).orElseThrow(() -> new ApiException(404, "Support case not found"));
        if (!user.getBusinessId().equals(supportCase.getBusinessId())) {
            throw new ApiException(404, "Support case not found");
        }
        return detail(supportCase);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> platformGet(long id) {
        SupportCase supportCase = cases.findById(id).orElseThrow(() -> new ApiException(404, "Support case not found"));
        return detail(supportCase);
    }

    @Transactional
    public Map<String, Object> reply(long id, String message, PlatformPrincipal admin) {
        SupportCase supportCase = cases.findById(id).orElseThrow(() -> new ApiException(404, "Support case not found"));
        add(supportCase.getId(), "PLATFORM", admin.getName(), message.trim());
        supportCase.setStatus("WAITING_FOR_CUSTOMER");
        supportCase.setUpdatedAt(Instant.now());
        audit.log(supportCase.getBusinessId(), admin.getName(), "SUPPORT_REPLY_SENT", requestTopic(supportCase), "SUPPORT", String.valueOf(id), "");
        return detail(supportCase);
    }

    @Transactional
    public Map<String, Object> customerReply(long id, String message, UserPrincipal user) {
        SupportCase supportCase = cases.findById(id).orElseThrow(() -> new ApiException(404, "Support case not found"));
        if (!user.getBusinessId().equals(supportCase.getBusinessId())) {
            throw new ApiException(404, "Support case not found");
        }
        add(supportCase.getId(), "CUSTOMER", user.getName(), message.trim());
        supportCase.setStatus("IN_PROGRESS");
        supportCase.setUpdatedAt(Instant.now());
        return detail(supportCase);
    }

    @Transactional
    public Map<String, Object> setStatus(long id, String status, PlatformPrincipal admin) {
        if (!List.of("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED").contains(status)) {
            throw new ApiException(400, "Unknown support status");
        }
        SupportCase supportCase = cases.findById(id).orElseThrow(() -> new ApiException(404, "Support case not found"));
        supportCase.setStatus(status);
        supportCase.setUpdatedAt(Instant.now());
        audit.log(supportCase.getBusinessId(), admin.getName(), "updated support case " + id + " to " + status, supportCase.getTopic(), "SUPPORT", String.valueOf(id), "");
        return detail(supportCase);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forBusiness(long businessId) {
        return cases.findByBusinessIdOrderByUpdatedAtDesc(businessId).stream().map(this::detail).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> all() {
        return cases.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .map(this::summary)
                .toList();
    }

    private void add(Long caseId, String kind, String name, String body) {
        SupportMessage message = new SupportMessage();
        message.setCaseId(caseId);
        message.setAuthorKind(kind);
        message.setAuthorName(name);
        message.setBody(body);
        messages.save(message);
    }

    private Map<String, Object> summary(SupportCase supportCase) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", supportCase.getId());
        body.put("category", supportCase.getCategory());
        body.put("topic", supportCase.getTopic());
        body.put("summary", supportCase.getSummary());
        body.put("status", supportCase.getStatus());
        body.put("priority", supportCase.getPriority() == null ? "NORMAL" : supportCase.getPriority());
        body.put("businessId", supportCase.getBusinessId());
        body.put("updatedAt", supportCase.getUpdatedAt());
        return body;
    }

    private Map<String, Object> detail(SupportCase supportCase) {
        Map<String, Object> body = summary(supportCase);
        body.put("messages", messages.findByCaseIdOrderByCreatedAtAsc(supportCase.getId()).stream().map(message -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("authorKind", message.getAuthorKind());
            row.put("authorName", message.getAuthorName());
            row.put("body", message.getBody());
            row.put("at", message.getCreatedAt());
            return row;
        }).toList());
        return body;
    }

    private static String requestTopic(SupportCase supportCase) {
        return supportCase.getTopic();
    }

    private static String priority(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if (List.of("LOW", "NORMAL", "HIGH", "URGENT").contains(value)) {
            return value;
        }
        return "NORMAL";
    }

    private static String diagnostics(java.util.Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(entry.getKey().trim()).append(": ").append(entry.getValue().trim());
        }
        String text = body.toString();
        return text.length() <= 2000 ? text : text.substring(0, 2000);
    }

    private static String category(String raw, String topic) {
        String value = raw == null || raw.isBlank() ? topic : raw;
        String normalized = value == null ? "" : value.trim().toUpperCase().replace(' ', '_').replace('/', '_');
        if ("SYNC_OFFLINE".equals(normalized)) {
            normalized = "SYNC";
        }
        if (!CATEGORIES.contains(normalized)) {
            return "OTHER";
        }
        return normalized;
    }
}
