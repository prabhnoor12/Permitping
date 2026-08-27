package com.permitping.domain;

public enum NotificationChannel {
    NONE("Do not notify"),
    EMAIL("Email"),
    SMS("SMS"),
    EMAIL_AND_SMS("Email and SMS");

    private final String label;
    NotificationChannel(String label) { this.label = label; }
    public String label() { return label; }
}
