package com.permitping.domain;

import java.util.List;

public record RequirementTemplate(long id, String name, List<String> requiredTypes) {
    public RequirementTemplate {
        requiredTypes = List.copyOf(requiredTypes);
    }
}
