package com.permitping.domain;

import java.time.LocalDateTime;

public record DocumentChaseDelivery(long id, long requestId, long profileId, String project,
                                    String documentType, NotificationChannel channel,
                                    String recipient, String encryptedMessage,
                                    ChaseDeliveryStatus status, int attempts,
                                    LocalDateTime nextAttemptAt, LocalDateTime attemptedAt,
                                    String providerMessageId, String errorMessage) { }
