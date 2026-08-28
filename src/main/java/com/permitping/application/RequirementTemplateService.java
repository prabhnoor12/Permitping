package com.permitping.application;

import com.permitping.domain.RequirementTemplate;
import java.util.List;

public final class RequirementTemplateService {
    private final RequirementTemplateRepository repository;

    public RequirementTemplateService(RequirementTemplateRepository repository) { this.repository = repository; }
    public List<RequirementTemplate> list() { return repository.findAll(); }
    public Long templateIdFor(String project) { return repository.findTemplateIdForProject(project == null ? "" : project.trim()); }
    public void assign(String project, long templateId) {
        if (project == null || project.isBlank() || templateId <= 0) return;
        repository.assignToProject(project.trim(), templateId);
    }
}
