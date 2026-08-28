package com.permitping.application;

import com.permitping.domain.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ReminderDeliveryService {
    private final ReminderService reminders;
    private final ProfileService profiles;
    private final ReminderDeliveryRepository deliveries;
    private final EmailSender emailSender;
    private final Clock clock;

    public ReminderDeliveryService(ReminderService reminders, ProfileService profiles,
                                   ReminderDeliveryRepository deliveries, EmailSender emailSender) {
        this(reminders, profiles, deliveries, emailSender, Clock.systemDefaultZone());
    }
    public ReminderDeliveryService(ReminderService reminders, ProfileService profiles,
                                   ReminderDeliveryRepository deliveries, EmailSender emailSender, Clock clock) {
        this.reminders = reminders; this.profiles = profiles; this.deliveries = deliveries;
        this.emailSender = emailSender; this.clock = clock;
    }
    public synchronized List<ReminderDelivery> sendPending() {
        List<ReminderDelivery> results = new ArrayList<>();
        for (ReminderNotice notice : reminders.pending()) {
            Document document = notice.document();
            Profile profile = profiles.list().stream().filter(p -> p.id() == document.holderProfileId()).findFirst().orElse(null);
            if (profile == null || !profile.notificationsEnabled() || profile.notificationChannel() != NotificationChannel.EMAIL && profile.notificationChannel() != NotificationChannel.EMAIL_AND_SMS) {
                String reason = "Notifications are disabled or no email channel is configured.";
                if (!deliveries.hasSkippedDelivery(document.id(), profile == null ? 0 : profile.id(), notice.daysBeforeExpiry(), document.expiresOn(), recipient(profile), reason)) results.add(record(document, profile, notice, DeliveryStatus.SKIPPED, "", reason, 0));
                continue;
            }
            if (profile.email() == null || profile.email().isBlank()) {
                String reason = "No email address is configured.";
                if (!deliveries.hasSkippedDelivery(document.id(), profile.id(), notice.daysBeforeExpiry(), document.expiresOn(), recipient(profile), reason)) results.add(record(document, profile, notice, DeliveryStatus.SKIPPED, "", reason, 0));
                continue;
            }
            if (deliveries.hasSuccessfulDelivery(document.id(), profile.id(), notice.daysBeforeExpiry(), document.expiresOn())) continue;
            OutgoingMessage message = new OutgoingMessage(profile.email(), "PermitPing reminder: " + document.name(), notice.message(clock));
            DeliveryResult result;
            try { result = emailSender.send(message); } catch (RuntimeException ex) { result = DeliveryResult.failed(ex.getMessage()); }
            ReminderDelivery delivery = record(document, profile, notice, result.successful() ? DeliveryStatus.SENT : DeliveryStatus.FAILED, result.providerMessageId(), result.errorMessage(), 1);
            results.add(delivery);
            if (result.successful()) reminders.markSent(notice);
        }
        return results;
    }
    private ReminderDelivery record(Document document, Profile profile, ReminderNotice notice, DeliveryStatus status, String providerId, String error, int attempts) {
        ReminderDelivery delivery = new ReminderDelivery(0, document.id(), profile == null ? 0 : profile.id(), notice.daysBeforeExpiry(), "EMAIL", recipient(profile), status, LocalDateTime.now(clock), providerId == null ? "" : providerId, error == null ? "" : error, attempts);
        deliveries.save(delivery, document.expiresOn()); return delivery;
    }
    private String recipient(Profile profile) { return profile == null || profile.email() == null ? "" : profile.email(); }
}
