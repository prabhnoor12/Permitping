package com.permitping;

import com.permitping.application.AssignmentRepository;
import com.permitping.application.AssignmentService;
import com.permitping.domain.AssignmentStatus;
import com.permitping.domain.ProjectAssignment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AssignmentServiceTest {
    @Test void deletesAnAssignmentById() {
        List<ProjectAssignment> store = new ArrayList<>(List.of(new ProjectAssignment(4,"Oak Street",2,AssignmentStatus.PENDING,"")));
        AssignmentService service = new AssignmentService(new AssignmentRepository() {
            public List<ProjectAssignment> findAll() { return store; }
            public void save(ProjectAssignment assignment) { store.add(assignment); }
            public void delete(long id) { store.removeIf(value -> value.id() == id); }
        });
        service.delete(4);
        assertTrue(store.isEmpty());
    }

    @Test void rejectsDuplicateProfileProjectAssignments() {
        List<ProjectAssignment> store = new ArrayList<>(List.of(new ProjectAssignment(4, "Oak Street", 2, AssignmentStatus.PENDING, "")));
        AssignmentService service = new AssignmentService(new AssignmentRepository() {
            public List<ProjectAssignment> findAll() { return store; }
            public void save(ProjectAssignment assignment) { store.add(assignment); }
        });
        assertThrows(IllegalArgumentException.class, () -> service.save(new ProjectAssignment(0, " oak street ", 2, AssignmentStatus.PENDING, "")));
    }
}
