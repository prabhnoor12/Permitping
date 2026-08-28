package com.permitping.infrastructure;

import com.permitping.application.DocumentChaseDeliveryRepository;
import com.permitping.domain.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class SqliteDocumentChaseDeliveryRepository implements DocumentChaseDeliveryRepository {
    private final Database database;
    public SqliteDocumentChaseDeliveryRepository(Database database) { this.database = database; }

    @Override public DocumentChaseDelivery save(DocumentChaseDelivery delivery) {
        String sql = delivery.id() == 0 ? "INSERT INTO document_chase_deliveries(request_id,profile_id,project,document_type,channel,recipient,encrypted_message,status,attempts,next_attempt_at,attempted_at,provider_message_id,error_message) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)" : "UPDATE document_chase_deliveries SET request_id=?,profile_id=?,project=?,document_type=?,channel=?,recipient=?,encrypted_message=?,status=?,attempts=?,next_attempt_at=?,attempted_at=?,provider_message_id=?,error_message=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(sql, delivery.id() == 0 ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            bind(p, delivery); if (delivery.id() != 0) p.setLong(14, delivery.id()); if (p.executeUpdate() != 1) throw new IllegalArgumentException("Chase delivery not found");
            if (delivery.id() == 0) try (ResultSet keys = p.getGeneratedKeys()) { if (keys.next()) return withId(delivery, keys.getLong(1)); }
            return delivery;
        } catch (SQLException e) { throw failure("save chase delivery", e); }
    }

    @Override public List<DocumentChaseDelivery> pending(LocalDateTime now, int limit) {
        List<DocumentChaseDelivery> result = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM document_chase_deliveries WHERE (status='PENDING' OR (status='FAILED' AND attempts < 3)) AND next_attempt_at<=? ORDER BY next_attempt_at ASC,id ASC LIMIT ?")) {
            p.setString(1, now.toString()); p.setInt(2, limit); try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(read(r)); }
        } catch (SQLException e) { throw failure("load pending chase deliveries", e); }
        return result;
    }

    private void bind(PreparedStatement p, DocumentChaseDelivery d) throws SQLException { p.setLong(1,d.requestId());p.setLong(2,d.profileId());p.setString(3,d.project());p.setString(4,d.documentType());p.setString(5,d.channel().name());p.setString(6,d.recipient());p.setString(7,d.encryptedMessage());p.setString(8,d.status().name());p.setInt(9,d.attempts());p.setString(10,d.nextAttemptAt().toString());if(d.attemptedAt()==null)p.setNull(11,Types.VARCHAR);else p.setString(11,d.attemptedAt().toString());p.setString(12,d.providerMessageId());p.setString(13,d.errorMessage()); }
    private DocumentChaseDelivery read(ResultSet r) throws SQLException { String attempted=r.getString("attempted_at");return new DocumentChaseDelivery(r.getLong("id"),r.getLong("request_id"),r.getLong("profile_id"),r.getString("project"),r.getString("document_type"),NotificationChannel.valueOf(r.getString("channel")),r.getString("recipient"),r.getString("encrypted_message"),ChaseDeliveryStatus.valueOf(r.getString("status")),r.getInt("attempts"),LocalDateTime.parse(r.getString("next_attempt_at")),attempted==null?null:LocalDateTime.parse(attempted),r.getString("provider_message_id"),r.getString("error_message")); }
    private DocumentChaseDelivery withId(DocumentChaseDelivery d,long id){return new DocumentChaseDelivery(id,d.requestId(),d.profileId(),d.project(),d.documentType(),d.channel(),d.recipient(),d.encryptedMessage(),d.status(),d.attempts(),d.nextAttemptAt(),d.attemptedAt(),d.providerMessageId(),d.errorMessage());}
    private IllegalStateException failure(String action,SQLException e){return new IllegalStateException("Could not "+action,e);}
}
