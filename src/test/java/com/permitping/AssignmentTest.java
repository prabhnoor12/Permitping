package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Test void approvedAssignmentIsClearedOnlyByCurrentProjectEvidence() throws Exception {
        Profile profile = new Profile(1, "Northside Electric", ProfileType.COMPANY, "", "", "");
        List<ProjectAssignment> assignments = new ArrayList<>(List.of(new ProjectAssignment(1, "Oak Street", 1, AssignmentStatus.APPROVED, "")));
        List<Document> documents = new ArrayList<>(List.of(new Document(1, "License", "License", profile.name(), "Oak Street", LocalDate.of(2026, 11, 24), null, "")));
        ClearanceService service = new ClearanceService(new AssignmentService(new AssignmentRepository() { public List<ProjectAssignment> findAll(){return assignments;} public void save(ProjectAssignment value){assignments.add(value);} }), new ProfileService(new ProfileRepository() { public List<Profile> findAll(){return List.of(profile);} public void save(Profile value){} }), new DocumentService(new DocumentRepository() { public List<Document> findAll(){return documents;} public Document save(Document value){return value;} public void delete(long id){} }, CLOCK), new FileStorage(Files.createTempDirectory("permitping-clearance")), CLOCK);

        AssignmentClearance result = service.list().get(0);
        assertEquals(ClearanceStatus.CLEARED, result.clearance());

        assignments.set(0, new ProjectAssignment(1, "Oak Street", 1, AssignmentStatus.PENDING, "Awaiting review"));
        assertEquals(ClearanceStatus.BLOCKED, service.list().get(0).clearance());
        assertTrue(service.list().get(0).issueSummary().contains("pending"));
    }

    @Test void projectTemplateRulesBlockAContractorWithIncompleteEvidence() throws Exception {
        Profile profile = new Profile(1, "Northside Electric", ProfileType.COMPANY, "", "", "");
        List<ProjectAssignment> assignments = new ArrayList<>(List.of(new ProjectAssignment(1, "Oak Street", 1, AssignmentStatus.APPROVED, "")));
        List<Document> documents = new ArrayList<>(List.of(new Document(1, "License", "License", profile.name(), "Oak Street", LocalDate.of(2026, 11, 24), null, "")));
        RequirementTemplateService templates = new RequirementTemplateService(new RequirementTemplateRepository() {
            public List<RequirementTemplate> findAll() { return List.of(new RequirementTemplate(9, "Electrical", List.of("License", "Insurance certificate"))); }
            public Long findTemplateIdForProject(String project) { return 9L; }
            public void assignToProject(String project, long templateId) { }
        });
        ClearanceService service = new ClearanceService(new AssignmentService(new AssignmentRepository() { public List<ProjectAssignment> findAll(){return assignments;} public void save(ProjectAssignment value){assignments.add(value);} }), new ProfileService(new ProfileRepository() { public List<Profile> findAll(){return List.of(profile);} public void save(Profile value){} }), new DocumentService(new DocumentRepository() { public List<Document> findAll(){return documents;} public Document save(Document value){return value;} public void delete(long id){} }, CLOCK), new FileStorage(Files.createTempDirectory("permitping-template-clearance")), CLOCK, templates);

        AssignmentClearance result = service.list().get(0);

        assertEquals(ClearanceStatus.BLOCKED, result.clearance());
        assertTrue(result.issueSummary().contains("Missing required Insurance certificate"));
    }
}
