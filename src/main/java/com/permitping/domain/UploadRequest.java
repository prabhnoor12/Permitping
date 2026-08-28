package com.permitping.domain;

import java.time.LocalDateTime;

public record UploadRequest(long id, long profileId, String project, String documentType, String tokenHash,
                            UploadRequestStatus status, LocalDateTime createdAt, LocalDateTime expiresAt) { }
