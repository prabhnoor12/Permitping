package com.permitping.domain;

import java.time.LocalDateTime;

public record ReminderDelivery(long id, long documentId, long profileId, int daysBeforeExpiry,
                               String channel, String recipient, DeliveryStatus status,
                               LocalDateTime attemptedAt, String providerMessageId, String errorMessage,
                               int attempts) { }
