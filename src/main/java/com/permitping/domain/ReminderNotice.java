package com.permitping.domain;

public record ReminderNotice(Document document, int daysBeforeExpiry) {
    public String message() {
        long days = document.daysUntilExpiry(java.time.Clock.systemDefaultZone());
        return document.name() + " for " + document.holder() + (days == 0 ? " expires today" : " expires in " + days + " days");
    }
}
