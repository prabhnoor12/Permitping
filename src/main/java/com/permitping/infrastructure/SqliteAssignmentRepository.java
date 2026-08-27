package com.permitping.infrastructure;

import com.permitping.application.AssignmentRepository;
import com.permitping.domain.*;
import java.sql.*;
import java.util.*;

public final class SqliteAssignmentRepository implements AssignmentRepository {
    private final Database database;
    public SqliteAssignmentRepository(Database database) { this.database = database; }
    @Override public List<ProjectAssignment> findAll() {
        List<ProjectAssignment> assignments = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM project_assignments ORDER BY project_name, id"); ResultSet r = p.executeQuery()) {
            while (r.next()) assignments.add(new ProjectAssignment(r.getLong("id"), r.getString("project_name"), r.getLong("profile_id"), AssignmentStatus.valueOf(r.getString("status")), r.getString("notes")));
        } catch (SQLException e) { throw failure("load", e); }
        return assignments;
    }
    @Override public void save(ProjectAssignment assignment) {
        String sql = assignment.id() == 0 ? "INSERT INTO project_assignments(project_name,profile_id,status,notes) VALUES(?,?,?,?)" : "UPDATE project_assignments SET project_name=?,profile_id=?,status=?,notes=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, assignment.project()); p.setLong(2, assignment.profileId()); p.setString(3, assignment.status().name()); p.setString(4, assignment.notes()); if (assignment.id() != 0) p.setLong(5, assignment.id()); p.executeUpdate();
        } catch (SQLException e) { throw failure("save", e); }
    }
    @Override public void delete(long id) { try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("DELETE FROM project_assignments WHERE id=?")){p.setLong(1,id);p.executeUpdate();}catch(SQLException e){throw failure("delete",e);} }
    private IllegalStateException failure(String action, SQLException e) { return new IllegalStateException("Could not " + action + " assignments", e); }
}
