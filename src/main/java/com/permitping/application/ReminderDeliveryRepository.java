package com.permitping.application;

import com.permitping.domain.ReminderDelivery;
import java.util.List;

public interface ReminderDeliveryRepository {
    void save(ReminderDelivery delivery);
    List<ReminderDelivery> recent(int limit);
    boolean hasSuccessfulDelivery(long documentId, long profileId, int daysBeforeExpiry);
}
