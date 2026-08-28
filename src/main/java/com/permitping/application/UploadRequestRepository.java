package com.permitping.application;

import com.permitping.domain.UploadRequest;
import com.permitping.domain.UploadSubmission;
import java.util.List;
import java.util.Optional;

public interface UploadRequestRepository {
    UploadRequest saveRequest(UploadRequest request);
    Optional<UploadRequest> findRequest(long id);
    Optional<UploadRequest> findOpenByTokenHash(String tokenHash);
    Optional<UploadRequest> findLatest(long profileId, String project, String documentType);
    List<UploadRequest> recentRequests(int limit);
    void revokeRequest(long id);
    void completeRequest(long id);
    UploadSubmission saveSubmission(UploadSubmission submission);
    Optional<UploadSubmission> findSubmission(long id);
    List<UploadSubmission> pendingSubmissions(int limit);
    boolean hasPendingSubmission(long requestId);
    void updateSubmission(UploadSubmission submission);
}
