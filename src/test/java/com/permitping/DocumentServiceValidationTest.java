package com.permitping;

import com.permitping.application.DocumentRepository;
import com.permitping.application.DocumentService;
import com.permitping.domain.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class DocumentServiceValidationTest {
    private final DocumentService service = new DocumentService(new DocumentRepository() {
        public List<Document> findAll() { return List.of(); }
        public List<Document> findArchived() { return List.of(); }
        public Document save(Document document) { return document; }
        public void delete(long id) { }
        public void archive(long id) { }
        public void restore(long id) { }
    });

    @Test void rejectsBlankDocumentName() {
        assertThrows(IllegalArgumentException.class, () -> service.save(document(" ", "License", "Acme")));
    }

    @Test void rejectsMissingHolder() {
        assertThrows(IllegalArgumentException.class, () -> service.save(document("License", "License", "")));
    }

    @Test void rejectsMissingExpirationDate() {
        Document invalid = new Document(0, "License", "License", "Acme", "Job", null, "", "");
        assertThrows(IllegalArgumentException.class, () -> service.save(invalid));
    }

    @Test void rejectsDuplicateDocumentsAtTheServiceBoundary() {
        List<Document> store = new java.util.ArrayList<>();
        DocumentRepository repository = new DocumentRepository() {
            public List<Document> findAll() { return store; }
            public List<Document> findArchived() { return List.of(); }
            public Document save(Document document) { store.add(document); return document; }
            public void delete(long id) { }
            public void archive(long id) { }
            public void restore(long id) { }
        };
        DocumentService guarded = new DocumentService(repository);
        guarded.save(document("Annual license", "License", "Acme"));
        assertThrows(IllegalArgumentException.class, () -> guarded.save(document(" annual license ", "License", " acme ")));
        assertEquals(1, store.size());
    }

    @Test void searchesAcrossDocumentHolderAndProject() {
        List<Document> store = List.of(
            new Document(1, "Annual license", "License", "Northside Electric", "Oak Street", LocalDate.of(2026, 9, 10), "", ""),
            new Document(2, "Insurance", "Insurance certificate", "Southside Plumbing", "Maple Street", LocalDate.of(2026, 12, 10), "", ""));
        DocumentService searchable = new DocumentService(new DocumentRepository() {
            public List<Document> findAll() { return store; }
            public Document save(Document value) { return value; }
            public void delete(long id) { }
        }, Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
        assertEquals(1, searchable.search("northside", "All").size());
        assertEquals(1, searchable.search("maple", "All").size());
        assertEquals(1, searchable.search("", "Current").size());
    }

    private Document document(String name, String type, String holder) {
        return new Document(0, name, type, holder, "Job", LocalDate.now().plusDays(30), "", "");
    }
}
