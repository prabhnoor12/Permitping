package com.permitping.application;

import com.permitping.domain.ReminderDelivery;
import java.util.List;

public interface ReminderDeliveryRepository {
    void save(ReminderDelivery delivery);
    default void save(ReminderDelivery delivery, java.time.LocalDate expiresOn) { save(delivery); }
    List<ReminderDelivery> recent(int limit);
    boolean hasSuccessfulDelivery(long documentId, long profileId, int daysBeforeExpiry);
    default boolean hasSuccessfulDelivery(long documentId, long profileId, int daysBeforeExpiry, java.time.LocalDate expiresOn) {
        return hasSuccessfulDelivery(documentId, profileId, daysBeforeExpiry);
    }
    default boolean hasSuccessfulDelivery(long documentId, long profileId, int daysBeforeExpiry, java.time.LocalDate expiresOn, String channel) {
        return hasSuccessfulDelivery(documentId, profileId, daysBeforeExpiry, expiresOn);
    }
    default boolean hasSkippedDelivery(long documentId, long profileId, int daysBeforeExpiry, java.time.LocalDate expiresOn, String recipient, String reason) {
        return false;
    }
    default boolean hasSkippedDelivery(long documentId, long profileId, int daysBeforeExpiry, java.time.LocalDate expiresOn, String channel, String recipient, String reason) {
        return hasSkippedDelivery(documentId, profileId, daysBeforeExpiry, expiresOn, recipient, reason);
    }
}
