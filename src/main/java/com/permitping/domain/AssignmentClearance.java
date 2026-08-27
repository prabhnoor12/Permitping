package com.permitping.domain;

import java.util.List;

public record AssignmentClearance(ProjectAssignment assignment, Profile profile, ClearanceStatus clearance, List<String> issues) {
    public AssignmentClearance { issues = List.copyOf(issues); }
    public String issueSummary() { return issues.isEmpty() ? "No issues" : String.join("; ", issues); }
}
