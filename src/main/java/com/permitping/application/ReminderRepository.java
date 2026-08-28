package com.permitping.application;

import com.permitping.domain.Reminder;
import java.util.List;

public interface ReminderRepository {
    List<Integer> enabledThresholds();
    void setThresholdEnabled(int daysBeforeExpiry, boolean enabled);
    boolean wasSent(long documentId, int daysBeforeExpiry);
    default boolean wasSent(long documentId, int daysBeforeExpiry, java.time.LocalDate expiresOn) {
        return wasSent(documentId, daysBeforeExpiry);
    }
    default boolean isSnoozed(long documentId, int daysBeforeExpiry) { return false; }
    default boolean isSnoozed(long documentId, int daysBeforeExpiry, java.time.LocalDate expiresOn) {
        return isSnoozed(documentId, daysBeforeExpiry);
    }
    default void snooze(long documentId, int daysBeforeExpiry, java.time.LocalDateTime until) { }
    default void snooze(long documentId, int daysBeforeExpiry, java.time.LocalDate expiresOn, java.time.LocalDateTime until) {
        snooze(documentId, daysBeforeExpiry, until);
    }
    void markSent(long documentId, int daysBeforeExpiry, java.time.LocalDateTime sentAt);
    default void markSent(long documentId, int daysBeforeExpiry, java.time.LocalDate expiresOn, java.time.LocalDateTime sentAt) {
        markSent(documentId, daysBeforeExpiry, sentAt);
    }
}
