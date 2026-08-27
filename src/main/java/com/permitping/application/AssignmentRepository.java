package com.permitping.application;

import com.permitping.domain.ProjectAssignment;
import java.util.List;

public interface AssignmentRepository { List<ProjectAssignment> findAll(); void save(ProjectAssignment assignment); default void delete(long id) { } }
