package com.permitping.infrastructure;

import com.permitping.application.ReminderRepository;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteReminderRepository implements ReminderRepository {
    private final Database database;
    public SqliteReminderRepository(Database database) { this.database = database; try(Connection c=database.connect();Statement s=c.createStatement()){try{s.executeUpdate("ALTER TABLE reminder_history ADD COLUMN snoozed_until TEXT");}catch(SQLException ignored){}}catch(SQLException e){throw failure("initialize history",e);} }
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
    @Override public void markSent(long documentId, int daysBeforeExpiry, LocalDateTime sentAt) {
        try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO reminder_history(document_id,days_before,sent_at) VALUES(?,?,?)")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);p.setString(3,sentAt.toString());p.executeUpdate();}catch(SQLException e){throw failure("save history",e);}
    }
    @Override public boolean isSnoozed(long documentId,int daysBeforeExpiry){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("SELECT snoozed_until FROM reminder_history WHERE document_id=? AND days_before=? AND snoozed_until IS NOT NULL")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);try(ResultSet r=p.executeQuery()){return r.next()&&LocalDateTime.parse(r.getString(1)).isAfter(LocalDateTime.now());}}catch(SQLException e){throw failure("load snooze state",e);}}
    @Override public void snooze(long documentId,int daysBeforeExpiry,LocalDateTime until){try(Connection c=database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO reminder_history(document_id,days_before,snoozed_until,sent_at) VALUES(?,?,?,?) ON CONFLICT(document_id,days_before) DO UPDATE SET snoozed_until=excluded.snoozed_until")){p.setLong(1,documentId);p.setInt(2,daysBeforeExpiry);p.setString(3,until.toString());p.setString(4,"");p.executeUpdate();}catch(SQLException e){throw failure("save snooze",e);}}
    private IllegalStateException failure(String action, SQLException e){return new IllegalStateException("Could not "+action+" reminders",e);}
}
