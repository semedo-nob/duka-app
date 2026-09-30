package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class DeviceRegistration {
    @Id
    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "installation_id")
    private String installationId;

    @Column(nullable = false)
    private String name = "";

    @Column(name = "app_version", nullable = false)
    private String appVersion = "";

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "pending_sync", nullable = false)
    private int pendingSync;

    @Column(name = "failed_sync", nullable = false)
    private int failedSync;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "oldest_pending_at")
    private Instant oldestPendingAt;

    @Column(name = "last_error", nullable = false)
    private String lastError = "";

    @Column(name = "printer_status", nullable = false)
    private String printerStatus = "";

    @Column(name = "scanner_status", nullable = false)
    private String scannerStatus = "";

    @Column(nullable = false)
    private boolean revoked;
}
