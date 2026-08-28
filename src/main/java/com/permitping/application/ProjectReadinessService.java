package com.permitping.application;

import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import java.time.Clock;
import java.util.*;

public final class ProjectReadinessService {
    private final DocumentService documents;
    private final FileStorage files;
    private final Clock clock;
    private final RequirementTemplateService templates;
    private final AssignmentService assignments;
    private final ClearanceService clearance;

    public ProjectReadinessService(DocumentService documents, FileStorage files) {
        this(documents, files, Clock.systemDefaultZone(), null);
    }

    public ProjectReadinessService(DocumentService documents, FileStorage files, Clock clock) {
        this(documents, files, clock, null);
    }

    public ProjectReadinessService(DocumentService documents, FileStorage files, Clock clock, RequirementTemplateService templates) {
        this(documents, files, clock, templates, null);
    }

    public ProjectReadinessService(DocumentService documents, FileStorage files, Clock clock, RequirementTemplateService templates, AssignmentService assignments) {
        this(documents, files, clock, templates, assignments, null);
    }

    public ProjectReadinessService(DocumentService documents, FileStorage files, Clock clock, RequirementTemplateService templates, AssignmentService assignments, ClearanceService clearance) {
        this.documents = documents; this.files = files; this.clock = clock; this.templates = templates; this.assignments = assignments; this.clearance = clearance;
    }

    public List<ProjectReadiness> list() {
        Map<String, List<Document>> byProject = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        documents.list().stream()
            .filter(d -> d.project() != null && !d.project().isBlank())
            .forEach(d -> {
                String name = d.project().trim();
                String key = name.toLowerCase(Locale.ROOT);
                displayNames.putIfAbsent(key, name);
                byProject.computeIfAbsent(key, ignored -> new ArrayList<>()).add(d);
            });
        if (assignments != null) assignments.list().stream()
            .filter(a -> a.project() != null && !a.project().isBlank())
            .forEach(a -> displayNames.putIfAbsent(a.project().trim().toLowerCase(Locale.ROOT), a.project().trim()));
        displayNames.keySet().forEach(key -> byProject.putIfAbsent(key, new ArrayList<>()));
        List<ProjectReadiness> readiness = byProject.entrySet().stream()
            .map(entry -> assess(displayNames.get(entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparing(ProjectReadiness::status).thenComparing(ProjectReadiness::project))
            .toList();
        if (clearance == null) return readiness;
        Map<String, List<AssignmentClearance>> byAssignmentProject = new HashMap<>();
        clearance.list().forEach(value -> byAssignmentProject.computeIfAbsent(value.assignment().project().trim().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(value));
        return readiness.stream().map(value -> mergeAssignmentClearance(value, byAssignmentProject.getOrDefault(value.project().trim().toLowerCase(Locale.ROOT), List.of()))).toList();
    }

    private ProjectReadiness mergeAssignmentClearance(ProjectReadiness value, List<AssignmentClearance> assignments) {
        if (assignments.isEmpty()) return value;
        ProjectReadinessStatus status = value.status();
        List<String> issues = new ArrayList<>(value.issues());
        for (AssignmentClearance assignment : assignments) {
            if (assignment.clearance() == ClearanceStatus.BLOCKED) status = ProjectReadinessStatus.BLOCKED;
            else if (assignment.clearance() == ClearanceStatus.AT_RISK && status != ProjectReadinessStatus.BLOCKED) status = ProjectReadinessStatus.AT_RISK;
            if (assignment.clearance() != ClearanceStatus.CLEARED) {
                String name = assignment.profile() == null ? "Unknown profile" : assignment.profile().name();
                issues.add(name + " is " + assignment.clearance().label().toLowerCase(Locale.ROOT) + ": " + assignment.issueSummary());
            }
        }
        return new ProjectReadiness(value.project(), status, value.documentCount(), value.templateName(), issues);
    }

    private ProjectReadiness assess(String project, List<Document> projectDocuments) {
        List<String> issues = new ArrayList<>();
        boolean blocked = false, atRisk = false;
        if (projectDocuments.isEmpty() && assignments != null) {
            issues.add("No compliance documents found for this project");
            blocked = true;
        }
        for (Document document : projectDocuments) {
            if (document.status(clock) == ComplianceStatus.EXPIRED) {
                issues.add(document.name() + " expired on " + document.expiresOn()); blocked = true;
            } else if (document.status(clock) == ComplianceStatus.EXPIRING_SOON) {
                issues.add(document.name() + " expires in " + document.daysUntilExpiry(clock) + " days"); atRisk = true;
            }
            if (document.filePath() != null && !document.filePath().isBlank() && !files.exists(document.filePath())) {
                issues.add(document.name() + " file is missing"); blocked = true;
            }
        }
        String templateName = "No template";
        if (templates != null) {
            Long templateId = templates.templateIdFor(project);
            RequirementTemplate template = templates.list().stream().filter(t -> t.id() == (templateId == null ? -1 : templateId)).findFirst().orElse(null);
            if (template != null) {
                templateName = template.name();
                Set<String> presentTypes = projectDocuments.stream().map(Document::type).filter(Objects::nonNull).map(type -> type.trim().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
                template.requiredTypes().stream().filter(type -> !presentTypes.contains(type.trim().toLowerCase(Locale.ROOT))).forEach(type -> issues.add("Missing required " + type));
                if (template.requiredTypes().stream().anyMatch(type -> !presentTypes.contains(type.trim().toLowerCase(Locale.ROOT)))) blocked = true;
            }
        }
        ProjectReadinessStatus status = blocked ? ProjectReadinessStatus.BLOCKED
            : atRisk ? ProjectReadinessStatus.AT_RISK : ProjectReadinessStatus.READY;
        return new ProjectReadiness(project, status, projectDocuments.size(), templateName, issues);
    }
}
