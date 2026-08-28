package com.permitping.application;

import com.permitping.domain.NotificationChannel;
import com.permitping.domain.NotificationSubscription;
import com.permitping.domain.SubscriptionStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public final class NotificationSubscriptionService {
    private static final int MAX_SOURCE_LENGTH = 200;
    private final NotificationSubscriptionRepository repository;
    private final AuditService audit;
    private final Clock clock;

    public NotificationSubscriptionService(NotificationSubscriptionRepository repository, AuditService audit) {
        this(repository, audit, Clock.systemDefaultZone());
    }

    public NotificationSubscriptionService(NotificationSubscriptionRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    public List<NotificationSubscription> list(long profileId) {
        return repository.findByProfile(profileId);
    }

    public boolean isSubscribed(long profileId, NotificationChannel channel) {
        return repository.find(profileId, channel).map(s -> s.status() == SubscriptionStatus.SUBSCRIBED).orElse(false);
    }

    public void subscribe(long profileId, NotificationChannel channel, String source) {
        change(profileId, channel, SubscriptionStatus.SUBSCRIBED, source);
    }

    public void unsubscribe(long profileId, NotificationChannel channel, String source) {
        change(profileId, channel, SubscriptionStatus.UNSUBSCRIBED, source);
    }

    private void change(long profileId, NotificationChannel channel, SubscriptionStatus status, String source) {
        if (profileId <= 0) throw new IllegalArgumentException("A valid profile is required");
        if (channel != NotificationChannel.EMAIL && channel != NotificationChannel.SMS) {
            throw new IllegalArgumentException("Subscriptions can only be managed for email or SMS");
        }
        String normalizedSource = source == null ? "" : source.trim();
        if (normalizedSource.isBlank()) throw new IllegalArgumentException("Record how the recipient requested this change");
        if (normalizedSource.length() > MAX_SOURCE_LENGTH) throw new IllegalArgumentException("Consent source must be 200 characters or fewer");
        NotificationSubscription current = repository.find(profileId, channel).orElse(null);
        if (current != null && current.status() == status && current.consentSource().equals(normalizedSource)) return;
        repository.save(new NotificationSubscription(profileId, channel, status, LocalDateTime.now(clock), normalizedSource));
        if (audit != null) audit.record(status == SubscriptionStatus.SUBSCRIBED ? "NOTIFICATION_SUBSCRIBED" : "NOTIFICATION_UNSUBSCRIBED",
            "profile=" + profileId + ", channel=" + channel.name() + ", source=" + normalizedSource);
    }
}
