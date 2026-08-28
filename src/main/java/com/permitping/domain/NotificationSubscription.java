package com.permitping.domain;

import java.time.LocalDateTime;

public record NotificationSubscription(long profileId, NotificationChannel channel,
                                       SubscriptionStatus status, LocalDateTime changedAt,
                                       String consentSource) { }
