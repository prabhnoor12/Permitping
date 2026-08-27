package com.permitping.domain;

public enum ComplianceStatus {
    CURRENT, EXPIRING_SOON, EXPIRED;

    public String label() { return name().replace('_', ' '); }
}
