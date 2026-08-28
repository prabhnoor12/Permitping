package com.permitping.infrastructure;

import com.permitping.application.ReminderRepository;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteReminderRepository implements ReminderRepository {
    private final Database database;
    public SqliteReminderRepository(Database database) { this.database = database; }
    @Override public List<Integer> enabledThresholds() {
        List<Integer> result = new ArrayList<>();
        try (Connection c=database.connect(); PreparedStatement p=c.prepareStatement("SELECT days_before FROM reminder_settings WHERE enabled=1 ORDER BY days_before DESC"); ResultSet r=p.executeQuery()) { while(r.next()) result.add(r.getInt(1)); }
        catch(SQLException e){throw failure("load settings",e);} return result;
    }
    @Override public void setThresholdEnabled(int daysBeforeExpiry, boolean enabled) {
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO reminder_settings(days_before,enabled) VALUES(?,?) ON CONFLICT(days_before) DO UPDATE SET enabled=excluded.enabled")){p.setInt(1,daysBeforeExpiry);p.setInt(2,enabled?1:0);p.executeUpdate();}catch(SQLException e){throw failure("save settings",e);}
    }
    @Override public boolean wasSent(long documentId, int daysBeforeExpiry) {
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_history WHERE document_id=? AND days_before=? AND sent_at<>''")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);try(ResultSet r=p.executeQuery()){return r.next();}}catch(SQLException e){throw failure("load history",e);}
    }
    @Override public boolean wasSent(long documentId, int daysBeforeExpiry, java.time.LocalDate expiresOn) {
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM reminder_history WHERE document_id=? AND days_before=? AND sent_at<>'' AND expires_on=?")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);p.setString(3,expiresOn.toString());try(ResultSet r=p.executeQuery()){return r.next();}}catch(SQLException e){throw failure("load history",e);}
    }
    @Override public void markSent(long documentId, int daysBeforeExpiry, LocalDateTime sentAt) {
        markSent(documentId, daysBeforeExpiry, null, sentAt);
    }
    @Override public void markSent(long documentId, int daysBeforeExpiry, java.time.LocalDate expiresOn, LocalDateTime sentAt) {
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO reminder_history(document_id,days_before,sent_at,snoozed_until,expires_on) VALUES(?,?,?,NULL,?) ON CONFLICT(document_id,days_before) DO UPDATE SET sent_at=excluded.sent_at,snoozed_until=NULL,expires_on=excluded.expires_on")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);p.setString(3,sentAt.toString());if(expiresOn==null)p.setNull(4,Types.VARCHAR);else p.setString(4,expiresOn.toString());p.executeUpdate();}catch(SQLException e){throw failure("save history",e);}
    }
    @Override public boolean isSnoozed(long documentId,int daysBeforeExpiry){return isSnoozed(documentId,daysBeforeExpiry,null);}
    @Override public boolean isSnoozed(long documentId,int daysBeforeExpiry,java.time.LocalDate expiresOn){String sql=expiresOn==null?"SELECT snoozed_until FROM reminder_history WHERE document_id=? AND days_before=? AND snoozed_until IS NOT NULL":"SELECT snoozed_until FROM reminder_history WHERE document_id=? AND days_before=? AND snoozed_until IS NOT NULL AND expires_on=?";try(Connection c=database.connect();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);if(expiresOn!=null)p.setString(3,expiresOn.toString());try(ResultSet r=p.executeQuery()){return r.next()&&LocalDateTime.parse(r.getString(1)).isAfter(LocalDateTime.now());}}catch(SQLException e){throw failure("load snooze state",e);}}
    @Override public void snooze(long documentId,int daysBeforeExpiry,LocalDateTime until){snooze(documentId,daysBeforeExpiry,null,until);}
    @Override public void snooze(long documentId,int daysBeforeExpiry,java.time.LocalDate expiresOn,LocalDateTime until){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO reminder_history(document_id,days_before,snoozed_until,sent_at,expires_on) VALUES(?,?,?, '', ?) ON CONFLICT(document_id,days_before) DO UPDATE SET snoozed_until=excluded.snoozed_until,sent_at='',expires_on=excluded.expires_on")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);p.setString(3,until.toString());if(expiresOn==null)p.setNull(4,Types.VARCHAR);else p.setString(4,expiresOn.toString());p.executeUpdate();}catch(SQLException e){throw failure("save snooze",e);}}
    private IllegalStateException failure(String action, SQLException e){return new IllegalStateException("Could not "+action+" reminders",e);}
}
