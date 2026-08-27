package com.permitping.domain;

public record ProjectAssignment(long id, String project, long profileId, AssignmentStatus status, String notes) { }
