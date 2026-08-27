package com.permitping.domain;

public enum AssignmentStatus {
    PENDING, APPROVED, REJECTED, SUSPENDED;
    public String label() { return name().substring(0, 1) + name().substring(1).toLowerCase(); }
}
