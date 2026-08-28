package com.permitping.application;

import com.permitping.domain.Document;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

public final class DocumentService {
    private final DocumentRepository repository;
    private final Clock clock;
    public DocumentService(DocumentRepository repository) { this(repository, Clock.systemDefaultZone()); }
    public DocumentService(DocumentRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public List<Document> list() { return repository.findAll(); }
    public Document save(Document d) {
        if (d == null || d.name()==null || d.name().isBlank()) throw new IllegalArgumentException("Document name is required");
        if (d.type()==null || d.type().isBlank()) throw new IllegalArgumentException("Document type is required");
        if (d.holder()==null || d.holder().isBlank()) throw new IllegalArgumentException("Holder / company is required");
        if (d.expiresOn()==null) throw new IllegalArgumentException("Expiration date is required");
        Document normalized = new Document(d.id(), d.name().trim(), d.type().trim(), d.holder().trim(), d.project() == null ? "" : d.project().trim(), d.expiresOn(), d.filePath() == null ? "" : d.filePath().trim(), d.notes() == null ? "" : d.notes().trim(), d.holderProfileId());
        if (isDuplicate(normalized)) throw new IllegalArgumentException("A document with this name, holder, and project already exists");
        return repository.save(normalized);
    }
    public boolean isDuplicate(Document candidate) {
        if (candidate == null) return false;
        return list().stream().anyMatch(existing -> (candidate.id() == 0 || existing.id() != candidate.id())
            && existing.name().trim().equalsIgnoreCase(candidate.name().trim())
            && existing.holder().trim().equalsIgnoreCase(candidate.holder().trim())
            && Objects.equals(existing.project() == null ? "" : existing.project().trim(), candidate.project() == null ? "" : candidate.project().trim()));
    }
    public List<Document> archived() { return repository.findArchived(); }
    public void delete(long id) { if (id > 0) repository.delete(id); }
    public void archive(long id) { if (id > 0) repository.archive(id); }
    public void restore(long id) {
        if (id <= 0) return;
        Document archived = archived().stream().filter(document -> document.id() == id).findFirst().orElse(null);
        if (archived != null && isDuplicate(archived)) {
            throw new IllegalArgumentException("Cannot restore this document because an active document with the same name, holder, and project already exists");
        }
        repository.restore(id);
    }
    public List<Document> search(String query, String status) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return list().stream().filter(d -> q.isBlank() || String.join(" ", safe(d.name()), safe(d.type()), safe(d.holder()), safe(d.project())).toLowerCase(Locale.ROOT).contains(q))
            .filter(d -> status == null || status.startsWith("All") || d.status(clock).label().equalsIgnoreCase(status)).collect(Collectors.toList());
    }
    public long count() { return list().size(); }
    public long countBy(com.permitping.domain.ComplianceStatus status) { return list().stream().filter(d -> d.status(clock)==status).count(); }
    private static String safe(String s) { return s == null ? "" : s; }
}
