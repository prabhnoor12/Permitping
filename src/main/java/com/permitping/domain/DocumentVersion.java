package com.permitping.domain;

import java.time.LocalDateTime;
public record DocumentVersion(long id, long documentId, int version, String filePath, LocalDateTime createdAt) { }
