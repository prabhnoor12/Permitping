package com.permitping.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UploadAnalysis(long submissionId, List<LocalDate> extractedDates,
                             LocalDate suggestedExpiryDate, List<String> findings,
                             LocalDateTime analyzedAt) {
    public UploadAnalysis {
        extractedDates = extractedDates == null ? List.of() : List.copyOf(extractedDates);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
