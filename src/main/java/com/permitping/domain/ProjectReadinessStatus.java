package com.permitping.domain;

public enum ProjectReadinessStatus {
    READY, AT_RISK, BLOCKED;

    public String label() {
        return switch (this) {
            case READY -> "Ready";
            case AT_RISK -> "At Risk";
            case BLOCKED -> "Blocked";
        };
    }
}
