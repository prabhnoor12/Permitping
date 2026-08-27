package com.permitping.application;

import com.permitping.domain.Reminder;
import java.util.List;

public interface ReminderRepository {
    List<Integer> enabledThresholds();
    void setThresholdEnabled(int daysBeforeExpiry, boolean enabled);
    boolean wasSent(long documentId, int daysBeforeExpiry);
    default boolean isSnoozed(long documentId, int daysBeforeExpiry) { return false; }
    default void snooze(long documentId, int daysBeforeExpiry, java.time.LocalDateTime until) { }
    void markSent(long documentId, int daysBeforeExpiry, java.time.LocalDateTime sentAt);
}
