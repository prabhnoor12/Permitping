package com.permitping.application;

import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import java.time.Clock;
import java.util.*;

public final class ClearanceService {
    private final AssignmentService assignments; private final ProfileService profiles; private final DocumentService documents; private final FileStorage files; private final Clock clock;
    public ClearanceService(AssignmentService assignments, ProfileService profiles, DocumentService documents, FileStorage files) { this(assignments, profiles, documents, files, Clock.systemDefaultZone()); }
    public ClearanceService(AssignmentService assignments, ProfileService profiles, DocumentService documents, FileStorage files, Clock clock) { this.assignments=assignments;this.profiles=profiles;this.documents=documents;this.files=files;this.clock=clock; }
    public List<AssignmentClearance> list() {
        return assignments.list().stream().map(this::assess).toList();
    }
    private AssignmentClearance assess(ProjectAssignment assignment) {
        Profile profile = profiles.list().stream().filter(p -> p.id() == assignment.profileId()).findFirst().orElse(null);
        List<String> issues = new ArrayList<>();
        if (profile == null) issues.add("Assigned profile no longer exists");
        if (assignment.status() != AssignmentStatus.APPROVED) issues.add("Assignment is " + assignment.status().label().toLowerCase(Locale.ROOT));
        var evidence = profile == null ? List.<Document>of() : documents.list().stream().filter(d -> (d.holderProfileId() == profile.id() || (d.holderProfileId() == 0 && same(d.holder(), profile.name()))) && same(d.project(), assignment.project())).toList();
        if (evidence.isEmpty()) issues.add("No compliance documents found for this project");
        boolean blocked = false, atRisk = false;
        for (Document document : evidence) {
            if (document.status(clock) == ComplianceStatus.EXPIRED) { issues.add(document.name() + " is expired"); blocked = true; }
            else if (document.status(clock) == ComplianceStatus.EXPIRING_SOON) { issues.add(document.name() + " expires in " + document.daysUntilExpiry(clock) + " days"); atRisk = true; }
            if (document.filePath() != null && !document.filePath().isBlank() && !files.exists(document.filePath())) { issues.add(document.name() + " file is missing"); blocked = true; }
        }
        if (assignment.status() != AssignmentStatus.APPROVED || profile == null || evidence.isEmpty()) blocked = true;
        return new AssignmentClearance(assignment, profile, blocked ? ClearanceStatus.BLOCKED : atRisk ? ClearanceStatus.AT_RISK : ClearanceStatus.CLEARED, issues);
    }
    private boolean same(String left, String right) { return left != null && right != null && left.trim().equalsIgnoreCase(right.trim()); }
}
