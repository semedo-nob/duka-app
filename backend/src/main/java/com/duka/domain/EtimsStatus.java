package com.duka.domain;

public enum EtimsStatus {
    PENDING,
    ACCEPTED,
    FAILED;

    public String label() {
        return switch (this) {
            case PENDING -> "Pending";
            case ACCEPTED -> "Accepted";
            case FAILED -> "Failed";
        };
    }
}
