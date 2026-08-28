package com.permitping.domain;

import java.time.LocalDateTime;
import java.util.List;

public record UploadVerification(long submissionId, UploadVerificationStatus status,
                                 List<String> checks, String sha256, LocalDateTime verifiedAt) {
    public UploadVerification {
        checks = checks == null ? List.of() : List.copyOf(checks);
        sha256 = sha256 == null ? "" : sha256;
    }
}
