package com.permitping.domain;

public enum ClearanceStatus {
    CLEARED, AT_RISK, BLOCKED;
    public String label() { return switch (this) { case CLEARED -> "Cleared"; case AT_RISK -> "At Risk"; case BLOCKED -> "Blocked"; }; }
}
