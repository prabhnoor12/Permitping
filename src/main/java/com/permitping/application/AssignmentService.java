package com.permitping.application;

import com.permitping.domain.*;
import java.util.List;

public final class AssignmentService {
    private final AssignmentRepository repository;
    public AssignmentService(AssignmentRepository repository) { this.repository = repository; }
    public List<ProjectAssignment> list() { return repository.findAll(); }
    public void save(ProjectAssignment assignment) {
        if (assignment == null || assignment.project() == null || assignment.project().isBlank()) throw new IllegalArgumentException("Project is required");
        if (assignment.profileId() <= 0) throw new IllegalArgumentException("Profile is required");
        String project = assignment.project().trim();
        if (list().stream().anyMatch(existing -> existing.id() != assignment.id()
            && existing.profileId() == assignment.profileId()
            && existing.project().trim().equalsIgnoreCase(project))) {
            throw new IllegalArgumentException("This profile is already assigned to that project");
        }
        repository.save(new ProjectAssignment(assignment.id(), project, assignment.profileId(), assignment.status() == null ? AssignmentStatus.PENDING : assignment.status(), assignment.notes() == null ? "" : assignment.notes().trim()));
    }
    public void delete(long id) { if (id > 0) repository.delete(id); }
}
