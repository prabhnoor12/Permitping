package com.permitping.infrastructure;

import com.permitping.application.DocumentRepository;
import com.permitping.domain.Document;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class SqliteDocumentRepository implements DocumentRepository {
    private final Database database;
    public SqliteDocumentRepository(Database database) { this.database=database; }
    @Override public List<Document> findAll(){List<Document> out=new ArrayList<>();try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT * FROM documents WHERE archived_at IS NULL ORDER BY expires_on ASC");ResultSet r=p.executeQuery()){while(r.next())out.add(read(r));}catch(SQLException e){throw failure("load",e);}return out;}
    @Override public List<Document> findArchived(){List<Document> out=new ArrayList<>();try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT * FROM documents WHERE archived_at IS NOT NULL ORDER BY archived_at DESC");ResultSet r=p.executeQuery()){while(r.next())out.add(read(r));}catch(SQLException e){throw failure("load archived",e);}return out;}
    @Override public Document save(Document d){String sql=d.id()==0?"INSERT INTO documents(name,type,holder,project,expires_on,file_path,notes,holder_profile_id) VALUES(?,?,?,?,?,?,?,?)":"UPDATE documents SET name=?,type=?,holder=?,project=?,expires_on=?,file_path=?,notes=?,holder_profile_id=? WHERE id=?";try(Connection c=database.connect();PreparedStatement p=c.prepareStatement(sql,d.id()==0?Statement.RETURN_GENERATED_KEYS:Statement.NO_GENERATED_KEYS)){bind(p,d);if(d.id()!=0)p.setLong(9,d.id());int affected=p.executeUpdate();if(d.id()!=0&&affected!=1)throw new IllegalArgumentException("Document not found");if(d.id()==0)try(ResultSet keys=p.getGeneratedKeys()){if(keys.next())return d.withId(keys.getLong(1));}return d;}catch(SQLException e){throw failure("save",e);}}
    @Override public void delete(long id){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("DELETE FROM documents WHERE id=?")){p.setLong(1,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Document not found");}catch(SQLException e){throw failure("delete",e);}}
    @Override public void archive(long id){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("UPDATE documents SET archived_at=CURRENT_TIMESTAMP WHERE id=? AND archived_at IS NULL")){p.setLong(1,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Document not found or already archived");}catch(SQLException e){throw failure("archive",e);}}
    @Override public void restore(long id){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("UPDATE documents SET archived_at=NULL WHERE id=? AND archived_at IS NOT NULL")){p.setLong(1,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Document not found or already active");}catch(SQLException e){throw failure("restore",e);}}
  private Document read(ResultSet r)throws SQLException{return new Document(r.getLong("id"),r.getString("name"),r.getString("type"),r.getString("holder"),r.getString("project"),LocalDate.parse(r.getString("expires_on")),r.getString("file_path"),r.getString("notes"),r.getLong("holder_profile_id"));}
  private void bind(PreparedStatement p,Document d)throws SQLException{p.setString(1,d.name());p.setString(2,d.type());p.setString(3,d.holder());p.setString(4,d.project());p.setString(5,d.expiresOn().toString());p.setString(6,d.filePath());p.setString(7,d.notes());p.setLong(8,d.holderProfileId());}
    private IllegalStateException failure(String action,SQLException e){return new IllegalStateException("Could not "+action+" documents",e);}
}
