package com.permitping.infrastructure;

import com.permitping.application.UploadRequestRepository;
import com.permitping.domain.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteUploadRequestRepository implements UploadRequestRepository {
    private final Database database;
    public SqliteUploadRequestRepository(Database database) { this.database = database; }

    @Override public UploadRequest saveRequest(UploadRequest request) {
        String sql = request.id() == 0 ? "INSERT INTO upload_requests(profile_id,project,document_type,token_hash,status,created_at,expires_at) VALUES(?,?,?,?,?,?,?)" : "UPDATE upload_requests SET profile_id=?,project=?,document_type=?,token_hash=?,status=?,created_at=?,expires_at=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(sql, request.id() == 0 ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            p.setLong(1, request.profileId()); p.setString(2, request.project()); p.setString(3, request.documentType()); p.setString(4, request.tokenHash()); p.setString(5, request.status().name()); p.setString(6, request.createdAt().toString()); p.setString(7, request.expiresAt().toString()); if (request.id() != 0) p.setLong(8, request.id());
            if (p.executeUpdate() != 1) throw new IllegalArgumentException("Upload request not found");
            if (request.id() == 0) try (ResultSet keys = p.getGeneratedKeys()) { if (keys.next()) return new UploadRequest(keys.getLong(1), request.profileId(), request.project(), request.documentType(), request.tokenHash(), request.status(), request.createdAt(), request.expiresAt()); }
            return request;
        } catch (SQLException e) { throw failure("save upload request", e); }
    }

    @Override public Optional<UploadRequest> findRequest(long id) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM upload_requests WHERE id=?")) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(readRequest(r)) : Optional.empty(); } }
        catch (SQLException e) { throw failure("load upload request", e); }
    }

    @Override public Optional<UploadRequest> findOpenByTokenHash(String tokenHash) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM upload_requests WHERE token_hash=? AND status='OPEN'")) { p.setString(1, tokenHash); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(readRequest(r)) : Optional.empty(); } }
        catch (SQLException e) { throw failure("find upload request", e); }
    }

    @Override public List<UploadRequest> recentRequests(int limit) {
        List<UploadRequest> result = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM upload_requests ORDER BY created_at DESC LIMIT ?")) { p.setInt(1, limit); try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(readRequest(r)); } }
        catch (SQLException e) { throw failure("load upload requests", e); } return result;
    }

    @Override public void revokeRequest(long id) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("UPDATE upload_requests SET status='REVOKED' WHERE id=? AND status='OPEN'")) { p.setLong(1, id); if (p.executeUpdate() != 1) throw new IllegalArgumentException("Upload request not found or already revoked"); }
        catch (SQLException e) { throw failure("revoke upload request", e); }
    }

    @Override public void completeRequest(long id) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("UPDATE upload_requests SET status='COMPLETED' WHERE id=? AND status='OPEN'")) { p.setLong(1, id); if (p.executeUpdate() != 1) throw new IllegalArgumentException("Upload request not found or already closed"); }
        catch (SQLException e) { throw failure("complete upload request", e); }
    }

    @Override public UploadSubmission saveSubmission(UploadSubmission submission) {
        String sql = submission.id() == 0 ? "INSERT INTO upload_submissions(request_id,original_filename,content_type,size_bytes,file_path,submitted_at,status,review_notes,reviewed_at,reviewed_by,document_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)" : "UPDATE upload_submissions SET request_id=?,original_filename=?,content_type=?,size_bytes=?,file_path=?,submitted_at=?,status=?,review_notes=?,reviewed_at=?,reviewed_by=?,document_id=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(sql, submission.id() == 0 ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            bindSubmission(p, submission); if (submission.id() != 0) p.setLong(12, submission.id()); if (p.executeUpdate() != 1) throw new IllegalArgumentException("Upload submission not found");
            if (submission.id() == 0) try (ResultSet keys = p.getGeneratedKeys()) { if (keys.next()) return withId(submission, keys.getLong(1)); }
            return submission;
        } catch (SQLException e) { throw failure("save upload submission", e); }
    }

    @Override public Optional<UploadSubmission> findSubmission(long id) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM upload_submissions WHERE id=?")) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(readSubmission(r)) : Optional.empty(); } }
        catch (SQLException e) { throw failure("load upload submission", e); }
    }

    @Override public List<UploadSubmission> pendingSubmissions(int limit) {
        List<UploadSubmission> result = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM upload_submissions WHERE status='PENDING' ORDER BY submitted_at ASC LIMIT ?")) { p.setInt(1, limit); try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(readSubmission(r)); } }
        catch (SQLException e) { throw failure("load pending uploads", e); } return result;
    }

    @Override public boolean hasPendingSubmission(long requestId) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT 1 FROM upload_submissions WHERE request_id=? AND status='PENDING' LIMIT 1")) { p.setLong(1, requestId); return p.executeQuery().next(); }
        catch (SQLException e) { throw failure("check pending upload", e); }
    }

    @Override public void updateSubmission(UploadSubmission submission) { saveSubmission(submission); }

    private void bindSubmission(PreparedStatement p, UploadSubmission s) throws SQLException { p.setLong(1, s.requestId()); p.setString(2, s.originalFilename()); p.setString(3, s.contentType()); p.setLong(4, s.sizeBytes()); p.setString(5, s.filePath()); p.setString(6, s.submittedAt().toString()); p.setString(7, s.status().name()); p.setString(8, s.reviewNotes()); if (s.reviewedAt() == null) p.setNull(9, Types.VARCHAR); else p.setString(9, s.reviewedAt().toString()); p.setString(10, s.reviewedBy()); p.setLong(11, s.documentId()); }
    private UploadRequest readRequest(ResultSet r) throws SQLException { return new UploadRequest(r.getLong("id"), r.getLong("profile_id"), r.getString("project"), r.getString("document_type"), r.getString("token_hash"), UploadRequestStatus.valueOf(r.getString("status")), LocalDateTime.parse(r.getString("created_at")), LocalDateTime.parse(r.getString("expires_at"))); }
    private UploadSubmission readSubmission(ResultSet r) throws SQLException { String reviewedAt = r.getString("reviewed_at"); return new UploadSubmission(r.getLong("id"), r.getLong("request_id"), r.getString("original_filename"), r.getString("content_type"), r.getLong("size_bytes"), r.getString("file_path"), LocalDateTime.parse(r.getString("submitted_at")), UploadSubmissionStatus.valueOf(r.getString("status")), r.getString("review_notes"), reviewedAt == null ? null : LocalDateTime.parse(reviewedAt), r.getString("reviewed_by"), r.getLong("document_id")); }
    private UploadSubmission withId(UploadSubmission s, long id) { return new UploadSubmission(id, s.requestId(), s.originalFilename(), s.contentType(), s.sizeBytes(), s.filePath(), s.submittedAt(), s.status(), s.reviewNotes(), s.reviewedAt(), s.reviewedBy(), s.documentId()); }
    private IllegalStateException failure(String action, SQLException e) { return new IllegalStateException("Could not " + action, e); }
}
