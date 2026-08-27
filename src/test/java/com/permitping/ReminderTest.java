package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReminderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Test void reminderUsesThresholdsAndHistoryInsteadOfExactDates() {
        List<Reminder> sent = new ArrayList<>();
        ReminderRepository reminders = new ReminderRepository() {
            public List<Integer> enabledThresholds() { return List.of(90, 30, 7); }
            public void setThresholdEnabled(int days, boolean enabled) { }
            public boolean wasSent(long documentId, int days) { return sent.stream().anyMatch(r -> r.documentId() == documentId && r.daysBeforeExpiry() == days); }
            public void markSent(long documentId, int days, LocalDateTime at) { sent.add(new Reminder(sent.size() + 1, documentId, days, at)); }
        };
        Document document = new Document(1, "Insurance", "Insurance certificate", "Northside Electric", "Oak Street", LocalDate.of(2026, 9, 5), null, "");
        DocumentService documents = new DocumentService(new DocumentRepository() { public List<Document> findAll(){return List.of(document);} public Document save(Document d){return d;} public void delete(long id){} }, CLOCK);
        ReminderService service = new ReminderService(documents, reminders, CLOCK);

        ReminderNotice notice = service.pending().get(0);
        assertEquals(30, notice.daysBeforeExpiry());
        service.markSent(notice);
        assertTrue(service.pending().isEmpty());
    }

    @Test void snoozedReminderIsNotPending() {
        ReminderNotice notice = new ReminderNotice(new Document(2, "Permit", "Permit", "Acme", "Job", LocalDate.of(2026, 9, 5), "", ""), 30);
        ReminderRepository reminders = new ReminderRepository() {
            LocalDateTime snoozedUntil;
            public List<Integer> enabledThresholds() { return List.of(30); }
            public void setThresholdEnabled(int days, boolean enabled) { }
            public boolean wasSent(long documentId, int days) { return false; }
            public boolean isSnoozed(long documentId, int days) { return snoozedUntil != null && snoozedUntil.isAfter(LocalDateTime.now(CLOCK)); }
            public void snooze(long documentId, int days, LocalDateTime until) { snoozedUntil = until; }
            public void markSent(long documentId, int days, LocalDateTime at) { }
        };
        DocumentService documents = new DocumentService(new DocumentRepository() { public List<Document> findAll(){return List.of(notice.document());} public Document save(Document d){return d;} public void delete(long id){} }, CLOCK);
        ReminderService service = new ReminderService(documents, reminders, CLOCK);
        service.snooze(notice, 7);
        assertTrue(service.pending().isEmpty());
    }

    @Test void aReminderStaysPendingUntilDeliveryIsExplicitlyMarkedSent() {
        Document document = new Document(3, "Permit", "Permit", "Acme", "Job", LocalDate.of(2026, 9, 5), "", "");
        ReminderRepository reminders = new ReminderRepository() {
            public List<Integer> enabledThresholds() { return List.of(30); }
            public void setThresholdEnabled(int days, boolean enabled) { }
            public boolean wasSent(long documentId, int days) { return false; }
            public void markSent(long documentId, int days, LocalDateTime at) { }
        };
        DocumentService documents = new DocumentService(new DocumentRepository() { public List<Document> findAll(){return List.of(document);} public Document save(Document d){return d;} public void delete(long id){} }, CLOCK);
        ReminderService service = new ReminderService(documents, reminders, CLOCK);
        assertFalse(service.pending().isEmpty());
    }
}
