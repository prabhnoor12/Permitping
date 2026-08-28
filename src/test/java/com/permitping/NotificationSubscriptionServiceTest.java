package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class NotificationSubscriptionServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Test void requiresEvidenceAndKeepsChannelStatesIndependent() {
        MemoryRepository repository = new MemoryRepository();
        NotificationSubscriptionService service = new NotificationSubscriptionService(repository, null, CLOCK);

        assertFalse(service.isSubscribed(7, NotificationChannel.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(7, NotificationChannel.EMAIL, ""));
        service.subscribe(7, NotificationChannel.EMAIL, "signed contractor consent");
        service.unsubscribe(7, NotificationChannel.EMAIL, "recipient requested by phone");

        assertFalse(service.isSubscribed(7, NotificationChannel.EMAIL));
        assertFalse(service.isSubscribed(7, NotificationChannel.SMS));
        assertEquals(SubscriptionStatus.UNSUBSCRIBED, repository.find(7, NotificationChannel.EMAIL).orElseThrow().status());
        assertEquals(2, repository.events);
    }

    @Test void rejectsNonDeliveryChannelsAndOverlongEvidence() {
        NotificationSubscriptionService service = new NotificationSubscriptionService(new MemoryRepository(), null, CLOCK);
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(7, NotificationChannel.NONE, "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(7, NotificationChannel.SMS, "x".repeat(201)));
    }

    private static final class MemoryRepository implements NotificationSubscriptionRepository {
        private final Map<String, NotificationSubscription> values = new HashMap<>();
        int events;
        public Optional<NotificationSubscription> find(long profileId, NotificationChannel channel) { return Optional.ofNullable(values.get(profileId + ":" + channel)); }
        public List<NotificationSubscription> findByProfile(long profileId) { return values.entrySet().stream().filter(e -> e.getKey().startsWith(profileId + ":")).map(Map.Entry::getValue).toList(); }
        public void save(NotificationSubscription subscription) { values.put(subscription.profileId() + ":" + subscription.channel(), subscription); events++; }
    }
}
