package com.permitping.application;

import com.permitping.domain.AuditEntry;
import java.util.List;

public interface AuditRepository {
    List<AuditEntry> findRecent(int limit);
    void record(String action, String subject, java.time.LocalDateTime occurredAt);
}
