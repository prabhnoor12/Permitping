package com.permitping.infrastructure;

import com.permitping.application.AuditRepository;
import com.permitping.domain.AuditEntry;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteAuditRepository implements AuditRepository {
    private final Path databasePath;
    public SqliteAuditRepository(Path databasePath) { this.databasePath = databasePath.toAbsolutePath(); initialize(); }
    private Connection connect() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + databasePath); }
    private void initialize() { try (Connection c=connect(); Statement s=c.createStatement()) { s.executeUpdate("CREATE TABLE IF NOT EXISTS audit_history (id INTEGER PRIMARY KEY AUTOINCREMENT, occurred_at TEXT NOT NULL, action TEXT NOT NULL, subject TEXT NOT NULL)"); } catch (SQLException e) { throw new IllegalStateException("Could not initialize audit history", e); } }
    @Override public List<AuditEntry> findRecent(int limit) { List<AuditEntry> result=new ArrayList<>(); try(Connection c=connect();PreparedStatement p=c.prepareStatement("SELECT id,occurred_at,action,subject FROM audit_history ORDER BY id DESC LIMIT ?")){p.setInt(1,Math.max(1,limit));try(ResultSet r=p.executeQuery()){while(r.next())result.add(new AuditEntry(r.getLong(1),LocalDateTime.parse(r.getString(2)),r.getString(3),r.getString(4)));}}catch(SQLException e){throw new IllegalStateException("Could not load audit history",e);}return result; }
    @Override public void record(String action,String subject,LocalDateTime occurredAt){try(Connection c=connect();PreparedStatement p=c.prepareStatement("INSERT INTO audit_history(occurred_at,action,subject) VALUES(?,?,?)")){p.setString(1,occurredAt.toString());p.setString(2,action);p.setString(3,subject);p.executeUpdate();}catch(SQLException e){throw new IllegalStateException("Could not record audit event",e);}}
}
