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

    public ProjectReadinessService(DocumentService documents, FileStorage files) {
        this(documents, files, Clock.systemDefaultZone(), null);
    }

    public ProjectReadinessService(DocumentService documents, FileStorage files, Clock clock) {
        this(documents, files, clock, null);
    }

    public ProjectReadinessService(DocumentService documents, FileStorage files, Clock clock, RequirementTemplateService templates) {
        this.documents = documents; this.files = files; this.clock = clock; this.templates = templates;
    }

    public List<ProjectReadiness> list() {
        Map<String, List<Document>> byProject = new LinkedHashMap<>();
        documents.list().stream()
            .filter(d -> d.project() != null && !d.project().isBlank())
            .forEach(d -> byProject.computeIfAbsent(d.project().trim(), ignored -> new ArrayList<>()).add(d));
        return byProject.entrySet().stream()
            .map(entry -> assess(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(ProjectReadiness::status).thenComparing(ProjectReadiness::project))
            .toList();
    }

    private ProjectReadiness assess(String project, List<Document> projectDocuments) {
        List<String> issues = new ArrayList<>();
        boolean blocked = false, atRisk = false;
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
                Set<String> presentTypes = projectDocuments.stream().map(Document::type).filter(Objects::nonNull).map(String::trim).collect(java.util.stream.Collectors.toSet());
                template.requiredTypes().stream().filter(type -> !presentTypes.contains(type)).forEach(type -> issues.add("Missing required " + type));
                if (template.requiredTypes().stream().anyMatch(type -> !presentTypes.contains(type))) blocked = true;
            }
        }
        ProjectReadinessStatus status = blocked ? ProjectReadinessStatus.BLOCKED
            : atRisk ? ProjectReadinessStatus.AT_RISK : ProjectReadinessStatus.READY;
        return new ProjectReadiness(project, status, projectDocuments.size(), templateName, issues);
    }
}
