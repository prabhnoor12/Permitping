package com.permitping.application;

import com.permitping.domain.NotificationChannel;
import com.permitping.domain.NotificationSubscription;
import java.util.List;
import java.util.Optional;

public interface NotificationSubscriptionRepository {
    Optional<NotificationSubscription> find(long profileId, NotificationChannel channel);
    List<NotificationSubscription> findByProfile(long profileId);
    void save(NotificationSubscription subscription);
}
