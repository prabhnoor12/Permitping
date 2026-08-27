package com.permitping.domain;

import java.time.LocalDateTime;

public record AuditEntry(long id, LocalDateTime occurredAt, String action, String subject) { }
