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
        save(d, null);
    }
    @Override public void save(ReminderDelivery d, java.time.LocalDate expiresOn) {
        String sql = "INSERT INTO reminder_deliveries(document_id,profile_id,days_before,channel,recipient,status,attempted_at,provider_message_id,error_message,attempts,expires_on) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c=database.connect(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,d.documentId()); p.setLong(2,d.profileId()); p.setInt(3,d.daysBeforeExpiry()); p.setString(4,d.channel()); p.setString(5,d.recipient()); p.setString(6,d.status().name()); p.setString(7,d.attemptedAt().toString()); p.setString(8,d.providerMessageId()); p.setString(9,d.errorMessage()); p.setInt(10,d.attempts()); if (expiresOn == null) p.setNull(11, Types.VARCHAR); else p.setString(11, expiresOn.toString()); p.executeUpdate();
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
    @Override public boolean hasSuccessfulDelivery(long documentId,long profileId,int daysBeforeExpiry,java.time.LocalDate expiresOn){
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_deliveries WHERE document_id=? AND profile_id=? AND days_before=? AND status='SENT' AND expires_on=? LIMIT 1")){p.setLong(1,documentId);p.setLong(2,profileId);p.setInt(3,daysBeforeExpiry);p.setString(4,expiresOn.toString());return p.executeQuery().next();}
        catch(SQLException e){throw new IllegalStateException("Could not check reminder delivery",e);}
    }
    @Override public boolean hasSuccessfulDelivery(long documentId,long profileId,int daysBeforeExpiry,java.time.LocalDate expiresOn,String channel){
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_deliveries WHERE document_id=? AND profile_id=? AND days_before=? AND status='SENT' AND expires_on=? AND channel=? LIMIT 1")){p.setLong(1,documentId);p.setLong(2,profileId);p.setInt(3,daysBeforeExpiry);p.setString(4,expiresOn.toString());p.setString(5,channel);return p.executeQuery().next();}
        catch(SQLException e){throw new IllegalStateException("Could not check reminder delivery",e);}
    }
    @Override public boolean hasSkippedDelivery(long documentId,long profileId,int daysBeforeExpiry,java.time.LocalDate expiresOn,String recipient,String reason){
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_deliveries WHERE document_id=? AND profile_id=? AND days_before=? AND status='SKIPPED' AND expires_on=? AND recipient=? AND error_message=? LIMIT 1")){p.setLong(1,documentId);p.setLong(2,profileId);p.setInt(3,daysBeforeExpiry);p.setString(4,expiresOn.toString());p.setString(5,recipient);p.setString(6,reason);return p.executeQuery().next();}
        catch(SQLException e){throw new IllegalStateException("Could not check skipped reminder delivery",e);}
    }
    @Override public boolean hasSkippedDelivery(long documentId,long profileId,int daysBeforeExpiry,java.time.LocalDate expiresOn,String channel,String recipient,String reason){
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_deliveries WHERE document_id=? AND profile_id=? AND days_before=? AND status='SKIPPED' AND expires_on=? AND channel=? AND recipient=? AND error_message=? LIMIT 1")){p.setLong(1,documentId);p.setLong(2,profileId);p.setInt(3,daysBeforeExpiry);p.setString(4,expiresOn.toString());p.setString(5,channel);p.setString(6,recipient);p.setString(7,reason);return p.executeQuery().next();}
        catch(SQLException e){throw new IllegalStateException("Could not check skipped reminder delivery",e);}
    }
    private ReminderDelivery read(ResultSet r)throws SQLException{return new ReminderDelivery(r.getLong("id"),r.getLong("document_id"),r.getLong("profile_id"),r.getInt("days_before"),r.getString("channel"),r.getString("recipient"),DeliveryStatus.valueOf(r.getString("status")),LocalDateTime.parse(r.getString("attempted_at")),r.getString("provider_message_id"),r.getString("error_message"),r.getInt("attempts"));}
}
