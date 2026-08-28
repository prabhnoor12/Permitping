package com.permitping.domain;

public record ReminderNotice(Document document, int daysBeforeExpiry) {
    public String message() {
        return message(java.time.Clock.systemDefaultZone());
    }
    public String message(java.time.Clock clock) {
        long days = document.daysUntilExpiry(clock);
        if (days < 0) {
            long elapsed = Math.abs(days);
            return document.name() + " for " + document.holder() + " expired " + elapsed + (elapsed == 1 ? " day" : " days") + " ago";
        }
        return document.name() + " for " + document.holder() + (days == 0 ? " expires today" : " expires in " + days + " days");
    }
}
