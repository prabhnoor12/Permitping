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
    private final SmsSender smsSender;
    private final Clock clock;

    public ReminderDeliveryService(ReminderService reminders, ProfileService profiles,
                                   ReminderDeliveryRepository deliveries, EmailSender emailSender) {
        this(reminders, profiles, deliveries, emailSender, null, Clock.systemDefaultZone());
    }
    public ReminderDeliveryService(ReminderService reminders, ProfileService profiles,
                                   ReminderDeliveryRepository deliveries, EmailSender emailSender, SmsSender smsSender) {
        this(reminders, profiles, deliveries, emailSender, smsSender, Clock.systemDefaultZone());
    }
    public ReminderDeliveryService(ReminderService reminders, ProfileService profiles,
                                   ReminderDeliveryRepository deliveries, EmailSender emailSender, Clock clock) {
        this(reminders, profiles, deliveries, emailSender, null, clock);
    }
    public ReminderDeliveryService(ReminderService reminders, ProfileService profiles,
                                   ReminderDeliveryRepository deliveries, EmailSender emailSender, SmsSender smsSender, Clock clock) {
        this.reminders = reminders; this.profiles = profiles; this.deliveries = deliveries;
        this.emailSender = emailSender; this.smsSender = smsSender; this.clock = clock;
    }
    public synchronized List<ReminderDelivery> sendPending() {
        List<ReminderDelivery> results = new ArrayList<>();
        for (ReminderNotice notice : reminders.pending()) {
            Document document = notice.document();
            Profile profile = profiles.list().stream().filter(p -> p.id() == document.holderProfileId()).findFirst().orElse(null);
            NotificationChannel channel = profile == null ? null : profile.notificationChannel();
            if (profile == null || !profile.notificationsEnabled() || channel == null || channel == NotificationChannel.NONE) {
                String reason = profile == null ? "No notification profile is linked." : "Notifications are disabled or no delivery channel is configured.";
                if (!deliveries.hasSkippedDelivery(document.id(), profile == null ? 0 : profile.id(), notice.daysBeforeExpiry(), document.expiresOn(), "NONE", "", reason)) results.add(record(document, profile, notice, "NONE", "", DeliveryStatus.SKIPPED, "", reason, 0));
                continue;
            }
            boolean complete = true;
            if (channel == NotificationChannel.EMAIL || channel == NotificationChannel.EMAIL_AND_SMS) {
                ChannelResult email = deliver(document, profile, notice, "EMAIL", profile.email(), emailSender, "No email address is configured.");
                if (email.delivery() != null) results.add(email.delivery());
                complete &= email.successful();
            }
            if (channel == NotificationChannel.SMS || channel == NotificationChannel.EMAIL_AND_SMS) {
                ChannelResult sms = deliver(document, profile, notice, "SMS", profile.phone(), smsSender, "No phone number is configured.");
                if (sms.delivery() != null) results.add(sms.delivery());
                complete &= sms.successful();
            }
            if (complete) reminders.markSent(notice);
        }
        return results;
    }
    private ChannelResult deliver(Document document, Profile profile, ReminderNotice notice, String channel, String recipient, Object sender, String missingRecipientReason) {
        if (deliveries.hasSuccessfulDelivery(document.id(), profile.id(), notice.daysBeforeExpiry(), document.expiresOn(), channel)) return new ChannelResult(true, null);
        if (recipient == null || recipient.isBlank()) {
            if (!deliveries.hasSkippedDelivery(document.id(), profile.id(), notice.daysBeforeExpiry(), document.expiresOn(), channel, "", missingRecipientReason)) {
                return new ChannelResult(false, record(document, profile, notice, channel, "", DeliveryStatus.SKIPPED, "", missingRecipientReason, 0));
            }
            return new ChannelResult(false, null);
        }
        DeliveryResult result;
        try {
            result = sender instanceof EmailSender email ? email.send(new OutgoingMessage(recipient, "PermitPing reminder: " + document.name(), notice.message(clock)))
                    : sender instanceof SmsSender sms ? sms.send(new OutgoingMessage(recipient, "PermitPing reminder: " + document.name(), notice.message(clock)))
                    : DeliveryResult.failed(channel + " delivery is not configured.");
        } catch (RuntimeException ex) { result = DeliveryResult.failed(ex.getMessage()); }
        return new ChannelResult(result.successful(), record(document, profile, notice, channel, recipient, result.successful() ? DeliveryStatus.SENT : DeliveryStatus.FAILED, result.providerMessageId(), result.errorMessage(), 1));
    }
    private ReminderDelivery record(Document document, Profile profile, ReminderNotice notice, String channel, String recipient, DeliveryStatus status, String providerId, String error, int attempts) {
        ReminderDelivery delivery = new ReminderDelivery(0, document.id(), profile == null ? 0 : profile.id(), notice.daysBeforeExpiry(), channel, recipient == null ? "" : recipient, status, LocalDateTime.now(clock), providerId == null ? "" : providerId, error == null ? "" : error, attempts);
        deliveries.save(delivery, document.expiresOn()); return delivery;
    }
    private record ChannelResult(boolean successful, ReminderDelivery delivery) { }
}
