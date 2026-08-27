package com.permitping.infrastructure;

import com.permitping.application.RequirementTemplateRepository;
import com.permitping.domain.RequirementTemplate;
import java.sql.*;
import java.util.*;

public final class SqliteRequirementTemplateRepository implements RequirementTemplateRepository {
    private final Database database;
    public SqliteRequirementTemplateRepository(Database database) { this.database = database; }

    @Override public List<RequirementTemplate> findAll() {
        Map<Long, RequirementTemplateBuilder> templates = new LinkedHashMap<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(
                "SELECT t.id, t.name, r.document_type FROM requirement_templates t " +
                "LEFT JOIN template_requirements r ON r.template_id=t.id ORDER BY t.id, r.document_type");
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                RequirementTemplateBuilder builder = templates.get(id);
                if (builder == null) { builder = new RequirementTemplateBuilder(id, rs.getString("name")); templates.put(id, builder); }
                builder.add(rs.getString("document_type"));
            }
        } catch (SQLException e) { throw failure("load", e); }
        return templates.values().stream().map(RequirementTemplateBuilder::build).toList();
    }

    @Override public Long findTemplateIdForProject(String project) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(
                "SELECT template_id FROM project_requirement_templates WHERE project_name=?")) {
            p.setString(1, project); try (ResultSet rs = p.executeQuery()) { return rs.next() ? rs.getLong(1) : null; }
        } catch (SQLException e) { throw failure("load project template", e); }
    }

    @Override public void assignToProject(String project, long templateId) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO project_requirement_templates(project_name,template_id) VALUES(?,?) " +
                "ON CONFLICT(project_name) DO UPDATE SET template_id=excluded.template_id")) {
            p.setString(1, project); p.setLong(2, templateId); p.executeUpdate();
        } catch (SQLException e) { throw failure("assign project template", e); }
    }

    private static final class RequirementTemplateBuilder {
        private final long id; private final String name; private final List<String> types = new ArrayList<>();
        private RequirementTemplateBuilder(long id, String name) { this.id = id; this.name = name; }
        private void add(String type) { if (type != null) types.add(type); }
        private RequirementTemplate build() { return new RequirementTemplate(id, name, types); }
    }
    private IllegalStateException failure(String action, SQLException e) { return new IllegalStateException("Could not " + action + " requirement templates", e); }
}
