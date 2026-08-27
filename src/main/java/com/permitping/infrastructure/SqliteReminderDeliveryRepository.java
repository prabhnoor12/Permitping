package com.permitping.infrastructure;

import com.permitping.application.ReminderDeliveryRepository;
import com.permitping.domain.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class SqliteReminderDeliveryRepository implements ReminderDeliveryRepository {
    private final Database database;
    public SqliteReminderDeliveryRepository(Database database) { this.database = database; }
    @Override public void save(ReminderDelivery d) {
        String sql = "INSERT INTO reminder_deliveries(document_id,profile_id,days_before,channel,recipient,status,attempted_at,provider_message_id,error_message,attempts) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection c=database.connect(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,d.documentId()); p.setLong(2,d.profileId()); p.setInt(3,d.daysBeforeExpiry()); p.setString(4,d.channel()); p.setString(5,d.recipient()); p.setString(6,d.status().name()); p.setString(7,d.attemptedAt().toString()); p.setString(8,d.providerMessageId()); p.setString(9,d.errorMessage()); p.setInt(10,d.attempts()); p.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Could not save reminder delivery", e); }
    }
    @Override public List<ReminderDelivery> recent(int limit) {
        List<ReminderDelivery> result=new ArrayList<>();
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT * FROM reminder_deliveries ORDER BY attempted_at DESC LIMIT ?")){p.setInt(1,Math.max(1,limit));try(ResultSet r=p.executeQuery()){while(r.next())result.add(read(r));}}
        catch(SQLException e){throw new IllegalStateException("Could not load reminder deliveries",e);} return result;
    }
    @Override public boolean hasSuccessfulDelivery(long documentId,long profileId,int daysBeforeExpiry){
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_deliveries WHERE document_id=? AND profile_id=? AND days_before=? AND status='SENT' LIMIT 1")){p.setLong(1,documentId);p.setLong(2,profileId);p.setInt(3,daysBeforeExpiry);return p.executeQuery().next();}
        catch(SQLException e){throw new IllegalStateException("Could not check reminder delivery",e);}
    }
    private ReminderDelivery read(ResultSet r)throws SQLException{return new ReminderDelivery(r.getLong("id"),r.getLong("document_id"),r.getLong("profile_id"),r.getInt("days_before"),r.getString("channel"),r.getString("recipient"),DeliveryStatus.valueOf(r.getString("status")),LocalDateTime.parse(r.getString("attempted_at")),r.getString("provider_message_id"),r.getString("error_message"),r.getInt("attempts"));}
}
