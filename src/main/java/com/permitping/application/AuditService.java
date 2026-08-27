package com.permitping.application;

import com.permitping.domain.AuditEntry;
import java.time.LocalDateTime;
import java.util.List;

public final class AuditService {
    private final AuditRepository repository;
    public AuditService(AuditRepository repository) { this.repository = repository; }
    public List<AuditEntry> recent() { return repository.findRecent(200); }
    public void record(String action, String subject) { if (action != null && !action.isBlank()) repository.record(action.trim(), subject == null ? "" : subject.trim(), LocalDateTime.now()); }
}
