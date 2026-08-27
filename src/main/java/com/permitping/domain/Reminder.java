package com.permitping.domain;

import java.time.LocalDateTime;

public record Reminder(long id, long documentId, int daysBeforeExpiry, LocalDateTime sentAt) { }
