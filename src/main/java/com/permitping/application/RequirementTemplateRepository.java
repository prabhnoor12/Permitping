package com.permitping.application;

import com.permitping.domain.RequirementTemplate;
import java.util.List;

public interface RequirementTemplateRepository {
    List<RequirementTemplate> findAll();
    Long findTemplateIdForProject(String project);
    void assignToProject(String project, long templateId);
}
