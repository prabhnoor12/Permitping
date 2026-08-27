package com.permitping.infrastructure;

import com.permitping.application.ProfileRepository;
import com.permitping.domain.*;
import java.sql.*;
import java.util.*;

public final class SqliteProfileRepository implements ProfileRepository {
    private final Database database;
    public SqliteProfileRepository(Database database) { this.database = database; }
    @Override public List<Profile> findAll() {
        List<Profile> profiles = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM profiles WHERE archived_at IS NULL ORDER BY name"); ResultSet r = p.executeQuery()) {
            while (r.next()) profiles.add(read(r));
        } catch (SQLException e) { throw failure("load", e); }
        return profiles;
    }
    @Override public List<Profile> findArchived() {
        List<Profile> profiles = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT * FROM profiles WHERE archived_at IS NOT NULL ORDER BY name"); ResultSet r = p.executeQuery()) { while (r.next()) profiles.add(read(r)); }
        catch (SQLException e) { throw failure("load archived", e); } return profiles;
    }
    @Override public void save(Profile profile) {
        String sql = profile.id() == 0 ? "INSERT INTO profiles(name,profile_type,email,phone,notes,notification_enabled,notification_channel) VALUES(?,?,?,?,?,?,?)" : "UPDATE profiles SET name=?,profile_type=?,email=?,phone=?,notes=?,notification_enabled=?,notification_channel=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, profile.name()); p.setString(2, profile.type().name()); p.setString(3, profile.email()); p.setString(4, profile.phone()); p.setString(5, profile.notes()); p.setBoolean(6, profile.notificationsEnabled()); p.setString(7, profile.notificationChannel().name());
            if (profile.id() != 0) p.setLong(8, profile.id()); p.executeUpdate();
        } catch (SQLException e) { throw failure("save", e); }
    }
    @Override public void archive(long id) { updateArchive(id, true); }
    @Override public void restore(long id) { updateArchive(id, false); }
    @Override public void delete(long id) { try (Connection c=database.connect(); PreparedStatement p=c.prepareStatement("DELETE FROM profiles WHERE id=?")) { p.setLong(1,id); p.executeUpdate(); } catch(SQLException e) { throw failure("delete",e); } }
    private void updateArchive(long id, boolean archive) { try(Connection c=database.connect(); PreparedStatement p=c.prepareStatement("UPDATE profiles SET archived_at="+(archive?"CURRENT_TIMESTAMP":"NULL")+" WHERE id=?")){p.setLong(1,id);p.executeUpdate();}catch(SQLException e){throw failure(archive?"archive":"restore",e);} }
    private Profile read(ResultSet r) throws SQLException { return new Profile(r.getLong("id"), r.getString("name"), ProfileType.valueOf(r.getString("profile_type")), r.getString("email"), r.getString("phone"), r.getString("notes"), r.getString("archived_at") != null, r.getBoolean("notification_enabled"), NotificationChannel.valueOf(r.getString("notification_channel"))); }
    private IllegalStateException failure(String action, SQLException e) { return new IllegalStateException("Could not " + action + " profiles", e); }
}
