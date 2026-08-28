package com.permitping.application;

import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

public final class UploadRequestService {
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx");
    private final UploadRequestRepository repository;
    private final ProfileService profiles;
    private final DocumentService documents;
    private final FileStorage files;
    private final Clock clock;
    private final AuditService audit;
    private final UploadVerificationService verification;
    private final UploadContentAnalysisService analysis;
    private final SecureRandom random = new SecureRandom();

    public UploadRequestService(UploadRequestRepository repository, ProfileService profiles, DocumentService documents, FileStorage files) {
        this(repository, profiles, documents, files, Clock.systemDefaultZone(), null);
    }
    public UploadRequestService(UploadRequestRepository repository, ProfileService profiles, DocumentService documents, FileStorage files, Clock clock) {
        this(repository, profiles, documents, files, clock, null);
    }
    public UploadRequestService(UploadRequestRepository repository, ProfileService profiles, DocumentService documents, FileStorage files, Clock clock, AuditService audit) {
        this.repository = repository; this.profiles = profiles; this.documents = documents; this.files = files; this.clock = clock; this.audit = audit; this.verification = new UploadVerificationService(files, clock); this.analysis = new UploadContentAnalysisService(files, clock);
    }

    public UploadInvite create(long profileId, String project, String documentType, Duration validity) {
        if (profileId <= 0) throw new IllegalArgumentException("A valid subcontractor profile is required");
        if (profiles.list().stream().noneMatch(profile -> profile.id() == profileId)) throw new IllegalArgumentException("Subcontractor profile not found");
        String normalizedProject = normalize(project, "Project");
        String normalizedType = normalize(documentType, "Document type");
        if (validity == null || validity.isNegative() || validity.isZero() || validity.toDays() > 90) throw new IllegalArgumentException("Upload request validity must be between 1 and 90 days");
        String token = randomToken();
        LocalDateTime now = LocalDateTime.now(clock);
        UploadRequest saved = repository.saveRequest(new UploadRequest(0, profileId, normalizedProject, normalizedType, hash(token), UploadRequestStatus.OPEN, now, now.plus(validity)));
        if (audit != null) audit.record("UPLOAD_REQUEST_CREATED", "profile=" + profileId + ", project=" + normalizedProject + ", type=" + normalizedType);
        return new UploadInvite(saved, token);
    }

    public Optional<UploadRequest> findByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return repository.findOpenByTokenHash(hash(token.trim())).filter(request -> request.expiresAt().isAfter(LocalDateTime.now(clock)));
    }

    public UploadSubmission submit(String token, String originalFilename, String contentType, Path source) throws IOException {
        UploadRequest request = findByToken(token).orElseThrow(() -> new IllegalArgumentException("This upload link is invalid, expired, or revoked"));
        if (repository.hasPendingSubmission(request.id())) throw new IllegalArgumentException("This upload request already has a document waiting for review");
        validateFile(originalFilename, source);
        Path imported = null;
        try {
            imported = files.importFile(source);
            return repository.saveSubmission(new UploadSubmission(0, request.id(), safeFilename(originalFilename), contentType == null ? "" : contentType, Files.size(source), imported.toString(), LocalDateTime.now(clock), UploadSubmissionStatus.PENDING, "", null, "", 0));
        } catch (RuntimeException | IOException ex) {
            if (imported != null) try { files.deleteManagedFile(imported); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    public List<UploadSubmission> pendingSubmissions(int limit) { return repository.pendingSubmissions(Math.max(1, Math.min(limit, 500))); }
    public List<UploadRequest> recentRequests(int limit) { return repository.recentRequests(Math.max(1, Math.min(limit, 500))); }
    public UploadRequest requestFor(UploadSubmission submission) { return repository.findRequest(submission.requestId()).orElseThrow(() -> new IllegalArgumentException("Upload request not found")); }
    public UploadVerification verify(UploadSubmission submission) { return verification.verify(submission); }
    public UploadAnalysis analyze(UploadSubmission submission) { return analysis.analyze(submission); }
    public void revoke(long requestId) { if (requestId > 0) { repository.revokeRequest(requestId); if (audit != null) audit.record("UPLOAD_REQUEST_REVOKED", "request=" + requestId); } }

    public void reject(long submissionId, String reviewer, String reason) {
        UploadSubmission submission = pending(submissionId);
        String normalizedReviewer = normalize(reviewer, "Reviewer"); String normalizedReason = normalize(reason, "Rejection reason");
        repository.updateSubmission(new UploadSubmission(submission.id(), submission.requestId(), submission.originalFilename(), submission.contentType(), submission.sizeBytes(), submission.filePath(), submission.submittedAt(), UploadSubmissionStatus.REJECTED, normalizedReason, LocalDateTime.now(clock), normalizedReviewer, 0));
        if (audit != null) audit.record("UPLOAD_REJECTED", "submission=" + submissionId + ", reviewer=" + normalizedReviewer);
    }

    public Document accept(long submissionId, String reviewer, String documentName, LocalDate expiresOn, String notes) {
        UploadSubmission submission = pending(submissionId);
        UploadVerification result = verify(submission);
        if (result.status() != UploadVerificationStatus.VERIFIED) throw new IllegalArgumentException("Upload cannot be accepted until automatic verification passes: " + String.join(" ", result.checks()));
        UploadRequest request = repository.findRequest(submission.requestId()).orElseThrow(() -> new IllegalArgumentException("Upload request not found"));
        Profile profile = profiles.list().stream().filter(value -> value.id() == request.profileId()).findFirst().orElseThrow(() -> new IllegalArgumentException("Subcontractor profile no longer exists"));
        if (expiresOn == null) throw new IllegalArgumentException("Expiration date is required before accepting an upload");
        Document saved = null;
        try {
            saved = documents.save(new Document(0, normalize(documentName, "Document name"), request.documentType(), profile.name(), request.project(), expiresOn, submission.filePath(), notes == null ? "" : notes.trim(), profile.id()));
            String normalizedReviewer = normalize(reviewer, "Reviewer");
            repository.updateSubmission(new UploadSubmission(submission.id(), submission.requestId(), submission.originalFilename(), submission.contentType(), submission.sizeBytes(), submission.filePath(), submission.submittedAt(), UploadSubmissionStatus.ACCEPTED, "", LocalDateTime.now(clock), normalizedReviewer, saved.id()));
            repository.completeRequest(request.id());
            if (audit != null) audit.record("UPLOAD_ACCEPTED", "submission=" + submissionId + ", document=" + saved.id() + ", reviewer=" + normalizedReviewer);
            return saved;
        } catch (RuntimeException ex) {
            if (saved != null) {
                try { documents.delete(saved.id()); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
                try { repository.updateSubmission(submission); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            }
            throw ex;
        }
    }

    public String link(UploadInvite invite) {
        String base = System.getenv("PERMITPING_UPLOAD_BASE_URL");
        if (base == null || base.isBlank()) base = "http://127.0.0.1:8765";
        return base.replaceAll("/+$", "") + "/upload/" + invite.token();
    }

    private UploadSubmission pending(long id) {
        UploadSubmission submission = repository.findSubmission(id).orElseThrow(() -> new IllegalArgumentException("Upload submission not found"));
        if (submission.status() != UploadSubmissionStatus.PENDING) throw new IllegalArgumentException("This upload has already been reviewed");
        return submission;
    }
    private void validateFile(String filename, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Uploaded file is missing");
        if (Files.size(source) <= 0 || Files.size(source) > MAX_UPLOAD_BYTES) throw new IOException("Upload must be between 1 byte and 10 MB");
        String safe = safeFilename(filename);
        int dot = safe.lastIndexOf('.');
        if (dot <= 0 || !ALLOWED_EXTENSIONS.contains(safe.substring(dot + 1).toLowerCase(Locale.ROOT))) throw new IOException("Use a PDF, image, or Word document");
    }
    private String safeFilename(String value) { try { String name = value == null ? "upload" : Path.of(value.replace('\\', '/')).getFileName().toString().trim(); if (name.isBlank() || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("A safe filename is required"); return name.length() > 180 ? name.substring(name.length() - 180) : name; } catch (InvalidPathException ex) { throw new IllegalArgumentException("A safe filename is required"); } }
    private String normalize(String value, String label) { String normalized = value == null ? "" : value.trim(); if (normalized.isBlank()) throw new IllegalArgumentException(label + " is required"); if (normalized.length() > 200) throw new IllegalArgumentException(label + " must be 200 characters or fewer"); return normalized; }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String token) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); } }
}
