package com.permitping.domain;

import java.util.List;

public record ProjectReadiness(String project, ProjectReadinessStatus status,
                               int documentCount, String templateName, List<String> issues) {
    public ProjectReadiness {
        issues = List.copyOf(issues);
    }

    public String issueSummary() {
        return issues.isEmpty() ? "No issues" : String.join("; ", issues);
    }
}
