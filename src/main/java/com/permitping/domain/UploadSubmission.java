package com.permitping.domain;

import java.time.LocalDateTime;

public record UploadSubmission(long id, long requestId, String originalFilename, String contentType,
                               long sizeBytes, String filePath, LocalDateTime submittedAt,
                               UploadSubmissionStatus status, String reviewNotes, LocalDateTime reviewedAt,
                               String reviewedBy, long documentId) { }
