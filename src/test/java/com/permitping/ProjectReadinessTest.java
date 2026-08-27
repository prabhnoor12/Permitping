package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import com.permitping.infrastructure.Database;
import com.permitping.infrastructure.SqliteRequirementTemplateRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProjectReadinessTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Test void projectsAreClassifiedAndExplainTheProblem() throws Exception {
        List<Document> documents = new ArrayList<>(List.of(
            document("Current license", "Ready Job", 90, null),
            document("Insurance", "Risk Job", 10, null),
            document("Expired permit", "Blocked Job", -1, null),
            document("Missing evidence", "Blocked Job", 90, "missing.pdf")
        ));
        DocumentService service = new DocumentService(new InMemoryRepository(documents), CLOCK);
        ProjectReadinessService readiness = new ProjectReadinessService(service,
            new FileStorage(Files.createTempDirectory("permitping-files")), CLOCK);

        Map<String, ProjectReadiness> byProject = new HashMap<>();
        readiness.list().forEach(project -> byProject.put(project.project(), project));

        assertEquals(ProjectReadinessStatus.READY, byProject.get("Ready Job").status());
        assertEquals(ProjectReadinessStatus.AT_RISK, byProject.get("Risk Job").status());
        assertEquals(ProjectReadinessStatus.BLOCKED, byProject.get("Blocked Job").status());
        assertTrue(byProject.get("Blocked Job").issueSummary().contains("expired"));
        assertTrue(byProject.get("Blocked Job").issueSummary().contains("file is missing"));
    }

    @Test void assignedTemplateFlagsMissingRequiredTypes() throws Exception {
        java.nio.file.Path db = Files.createTempFile("permitping-template-", ".db");
        try {
            Database database = new Database(db);
            RequirementTemplateService templates = new RequirementTemplateService(new SqliteRequirementTemplateRepository(database));
            RequirementTemplate template = templates.list().stream().filter(t -> t.name().equals("Electrical subcontractor")).findFirst().orElseThrow();
            templates.assign("Electrical Job", template.id());
            DocumentService documents = new DocumentService(new InMemoryRepository(new ArrayList<>(List.of(
                document("Insurance", "Electrical Job", 90, null)
            ))), CLOCK);
            ProjectReadiness readiness = new ProjectReadinessService(documents,
                new FileStorage(Files.createTempDirectory("permitping-template-files")), CLOCK, templates).list().get(0);

            assertEquals("Electrical subcontractor", readiness.templateName());
            assertEquals(ProjectReadinessStatus.BLOCKED, readiness.status());
            assertTrue(readiness.issueSummary().contains("Missing required License"));
            assertTrue(readiness.issueSummary().contains("Missing required OSHA card"));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(java.nio.file.Path.of(db + "-wal")); Files.deleteIfExists(java.nio.file.Path.of(db + "-shm"));
        }
    }

    private static Document document(String name, String project, long days, String path) {
        return new Document(0, name, "Permit", "Test Holder", project,
            LocalDate.of(2026, 8, 26).plusDays(days), path, "");
    }

    private static final class InMemoryRepository implements DocumentRepository {
        private final List<Document> documents;
        private InMemoryRepository(List<Document> documents) { this.documents = documents; }
        public List<Document> findAll() { return List.copyOf(documents); }
        public Document save(Document document) { documents.add(document); return document; }
        public void delete(long id) { documents.removeIf(document -> document.id() == id); }
    }
}
