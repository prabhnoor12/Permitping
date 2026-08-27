package com.permitping.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Document(long id, String name, String type, String holder, String project,
                       LocalDate expiresOn, String filePath, String notes, long holderProfileId) {
    public Document(long id, String name, String type, String holder, String project, LocalDate expiresOn, String filePath, String notes) {
        this(id, name, type, holder, project, expiresOn, filePath, notes, 0);
    }
    public long daysUntilExpiry(Clock clock) { return ChronoUnit.DAYS.between(LocalDate.now(clock), expiresOn); }
    public ComplianceStatus status(Clock clock) {
        long days = daysUntilExpiry(clock);
        return days < 0 ? ComplianceStatus.EXPIRED : days <= 30 ? ComplianceStatus.EXPIRING_SOON : ComplianceStatus.CURRENT;
    }
    public Document withId(long newId) { return new Document(newId, name, type, holder, project, expiresOn, filePath, notes, holderProfileId); }
}
