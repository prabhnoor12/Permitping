package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class ReminderDeliveryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Test void sendsOnlyToAnOptedInEmailProfileAndMarksItSent() {
        Document document = document(11, 7);
        FakeReminderRepository reminderStore = new FakeReminderRepository();
        FakeDeliveryRepository deliveryStore = new FakeDeliveryRepository();
        List<OutgoingMessage> messages = new ArrayList<>();
        ReminderDeliveryService service = service(document, profile(7, true, NotificationChannel.EMAIL, "crew@example.com"), reminderStore, deliveryStore, messages, DeliveryResult.sent("provider-1"));

        List<ReminderDelivery> result = service.sendPending();

        assertEquals(1, messages.size());
        assertEquals("crew@example.com", messages.get(0).recipient());
        assertEquals(DeliveryStatus.SENT, result.get(0).status());
        assertTrue(reminderStore.sent);
        assertEquals("provider-1", result.get(0).providerMessageId());
    }

    @Test void doesNotSendToAnOptedOutProfile() {
        FakeDeliveryRepository deliveryStore = new FakeDeliveryRepository();
        List<OutgoingMessage> messages = new ArrayList<>();
        List<ReminderDelivery> result = service(document(12, 7), profile(7, false, NotificationChannel.EMAIL, "crew@example.com"), new FakeReminderRepository(), deliveryStore, messages, DeliveryResult.sent("unused")).sendPending();

        assertTrue(messages.isEmpty());
        assertEquals(DeliveryStatus.SKIPPED, result.get(0).status());
        assertEquals(7, result.get(0).profileId());
    }

    @Test void doesNotDuplicateAnUnchangedSkippedDelivery() {
        FakeDeliveryRepository deliveryStore = new FakeDeliveryRepository();
        List<OutgoingMessage> messages = new ArrayList<>();
        ReminderDeliveryService service = service(document(16, 7), profile(7, false, NotificationChannel.EMAIL, "crew@example.com"), new FakeReminderRepository(), deliveryStore, messages, DeliveryResult.sent("unused"));

        assertEquals(1, service.sendPending().size());
        assertTrue(service.sendPending().isEmpty());
        assertEquals(1, deliveryStore.saved.size());
    }

    @Test void failedDeliveryRemainsRetryable() {
        FakeReminderRepository reminderStore = new FakeReminderRepository();
        FakeDeliveryRepository deliveryStore = new FakeDeliveryRepository();
        List<OutgoingMessage> messages = new ArrayList<>();
        List<ReminderDelivery> result = service(document(13, 7), profile(7, true, NotificationChannel.EMAIL, "crew@example.com"), reminderStore, deliveryStore, messages, DeliveryResult.failed("provider unavailable")).sendPending();

        assertEquals(DeliveryStatus.FAILED, result.get(0).status());
        assertFalse(reminderStore.sent);
        assertEquals("provider unavailable", result.get(0).errorMessage());
    }

    @Test void successfulDeliveryIsNotRepeatedWhenHistoryConfirmsIt() {
        FakeReminderRepository reminderStore = new FakeReminderRepository();
        reminderStore.sent = true;
        FakeDeliveryRepository deliveryStore = new FakeDeliveryRepository();
        deliveryStore.successful = true;
        List<OutgoingMessage> messages = new ArrayList<>();

        List<ReminderDelivery> result = service(document(14, 7), profile(7, true, NotificationChannel.EMAIL, "crew@example.com"), reminderStore, deliveryStore, messages, DeliveryResult.sent("unused")).sendPending();

        assertTrue(result.isEmpty());
        assertTrue(messages.isEmpty());
    }

    @Test void repeatedSendProcessingDoesNotSendTheSameReminderTwice() {
        FakeReminderRepository reminderStore = new FakeReminderRepository();
        FakeDeliveryRepository deliveryStore = new FakeDeliveryRepository();
        List<OutgoingMessage> messages = new ArrayList<>();
        ReminderDeliveryService service = service(document(15, 7), profile(7, true, NotificationChannel.EMAIL, "crew@example.com"), reminderStore, deliveryStore, messages, DeliveryResult.sent("provider-2"));

        service.sendPending();
        service.sendPending();

        assertEquals(1, messages.size());
        assertTrue(reminderStore.sent);
    }

    private ReminderDeliveryService service(Document document, Profile profile, FakeReminderRepository reminders,
                                            FakeDeliveryRepository deliveries, List<OutgoingMessage> messages, DeliveryResult response) {
        DocumentService documents = new DocumentService(new DocumentRepository() {
            public List<Document> findAll() { return List.of(document); }
            public Document save(Document value) { return value; }
            public void delete(long id) { }
        }, CLOCK);
        ProfileService profiles = new ProfileService(new ProfileRepository() {
            public List<Profile> findAll() { return List.of(profile); }
            public void save(Profile value) { }
        });
        EmailSender sender = message -> { messages.add(message); return response; };
        return new ReminderDeliveryService(new ReminderService(documents, reminders, CLOCK), profiles, deliveries, sender, CLOCK);
    }

    private Document document(long id, long profileId) { return new Document(id, "Insurance certificate", "Insurance certificate", "Legacy holder", "Project A", LocalDate.of(2026, 9, 2), "", "", profileId); }
    private Profile profile(long id, boolean enabled, NotificationChannel channel, String email) { return new Profile(id, "Northside Electric", ProfileType.COMPANY, email, "", "", false, enabled, channel); }

    private static final class FakeReminderRepository implements ReminderRepository {
        boolean sent;
        public List<Integer> enabledThresholds() { return List.of(7); }
        public void setThresholdEnabled(int days, boolean enabled) { }
        public boolean wasSent(long documentId, int days) { return sent; }
        public void markSent(long documentId, int days, LocalDateTime at) { sent = true; }
    }

    private static final class FakeDeliveryRepository implements ReminderDeliveryRepository {
        boolean successful;
        final List<ReminderDelivery> saved = new ArrayList<>();
        public void save(ReminderDelivery delivery) { saved.add(delivery); }
        public List<ReminderDelivery> recent(int limit) { return saved; }
        public boolean hasSuccessfulDelivery(long documentId, long profileId, int days) { return successful; }
        public boolean hasSkippedDelivery(long documentId, long profileId, int days, LocalDate expiresOn, String recipient, String reason) {
            return saved.stream().anyMatch(delivery -> delivery.documentId() == documentId && delivery.profileId() == profileId
                && delivery.daysBeforeExpiry() == days && delivery.status() == DeliveryStatus.SKIPPED
                && delivery.recipient().equals(recipient) && delivery.errorMessage().equals(reason));
        }
    }
}
