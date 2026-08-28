package com.permitping.application;

import com.permitping.domain.Document;
import java.time.Clock;
import com.permitping.domain.ReminderNotice;
import java.util.*;

public final class ReminderService {
    private final DocumentService documents;
    private final Clock clock;
    private final ReminderRepository repository;
    public ReminderService(DocumentService documents, ReminderRepository repository) { this(documents, repository, Clock.systemDefaultZone()); }
    public ReminderService(DocumentService documents, ReminderRepository repository, Clock clock) { this.documents=documents; this.repository=repository; this.clock=clock; }
    public List<Integer> enabledThresholds() { return repository.enabledThresholds(); }
    public void setThresholdEnabled(int daysBeforeExpiry, boolean enabled) { if (daysBeforeExpiry >= 0) repository.setThresholdEnabled(daysBeforeExpiry, enabled); }
    public List<ReminderNotice> pending() {
        List<ReminderNotice> notices = new ArrayList<>();
        List<Integer> thresholds = repository.enabledThresholds();
        for (Document document : documents.list()) {
            long days = document.daysUntilExpiry(clock);
            if (thresholds.isEmpty()) continue;
            int threshold = days < 0 ? 0 : thresholds.stream()
                .filter(value -> days <= value)
                .min(Integer::compareTo)
                .orElse(-1);
            if (threshold >= 0 && !repository.wasSent(document.id(), threshold, document.expiresOn())
                && !repository.isSnoozed(document.id(), threshold, document.expiresOn())) {
                notices.add(new ReminderNotice(document, threshold));
            }
        }
        return notices;
    }
    public void markSent(ReminderNotice notice) { repository.markSent(notice.document().id(), notice.daysBeforeExpiry(), notice.document().expiresOn(), java.time.LocalDateTime.now(clock)); }
    public void snooze(ReminderNotice notice, int days) { if (notice != null && days > 0) repository.snooze(notice.document().id(), notice.daysBeforeExpiry(), notice.document().expiresOn(), java.time.LocalDateTime.now(clock).plusDays(days)); }
    public List<Document> dueForReminder(int daysBeforeExpiry) {
        return documents.list().stream().filter(d -> d.daysUntilExpiry(clock) >= 0 && d.daysUntilExpiry(clock) <= daysBeforeExpiry).toList();
    }
}
