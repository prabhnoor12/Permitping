package com.permitping.infrastructure;

import com.permitping.application.DocumentVersionRepository;
import com.permitping.domain.DocumentVersion;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteDocumentVersionRepository implements DocumentVersionRepository {
    private final Database database;
    public SqliteDocumentVersionRepository(Database database) { this.database=database; }
    public List<DocumentVersion> findByDocument(long documentId){List<DocumentVersion> out=new ArrayList<>();try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT * FROM document_versions WHERE document_id=? ORDER BY version DESC")){p.setLong(1,documentId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new DocumentVersion(r.getLong("id"),r.getLong("document_id"),r.getInt("version"),r.getString("file_path"),LocalDateTime.parse(r.getString("created_at"))));}}catch(SQLException e){throw failure("load",e);}return out;}
    public void save(DocumentVersion version){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO document_versions(document_id,version,file_path,created_at) VALUES(?,?,?,?)")){p.setLong(1,version.documentId());p.setInt(2,version.version());p.setString(3,version.filePath());p.setString(4,version.createdAt().toString());p.executeUpdate();}catch(SQLException e){throw failure("save",e);}}
    private IllegalStateException failure(String action,SQLException e){return new IllegalStateException("Could not "+action+" document versions",e);}
}
