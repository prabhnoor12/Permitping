package com.permitping.infrastructure;

import com.permitping.application.NotificationSubscriptionRepository;
import com.permitping.domain.NotificationChannel;
import com.permitping.domain.NotificationSubscription;
import com.permitping.domain.SubscriptionStatus;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteNotificationSubscriptionRepository implements NotificationSubscriptionRepository {
    private final Database database;
    public SqliteNotificationSubscriptionRepository(Database database) { this.database = database; }

    @Override public Optional<NotificationSubscription> find(long profileId, NotificationChannel channel) {
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(
                "SELECT profile_id,channel,status,changed_at,consent_source FROM notification_subscriptions WHERE profile_id=? AND channel=?")) {
            p.setLong(1, profileId); p.setString(2, channel.name());
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(read(r)) : Optional.empty(); }
        } catch (SQLException e) { throw failure("load notification subscription", e); }
    }

    @Override public List<NotificationSubscription> findByProfile(long profileId) {
        List<NotificationSubscription> result = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement(
                "SELECT profile_id,channel,status,changed_at,consent_source FROM notification_subscriptions WHERE profile_id=? ORDER BY channel")) {
            p.setLong(1, profileId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(read(r)); }
        } catch (SQLException e) { throw failure("load notification subscriptions", e); }
        return result;
    }

    @Override public void save(NotificationSubscription subscription) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement current = c.prepareStatement("INSERT INTO notification_subscriptions(profile_id,channel,status,changed_at,consent_source) VALUES(?,?,?,?,?) ON CONFLICT(profile_id,channel) DO UPDATE SET status=excluded.status,changed_at=excluded.changed_at,consent_source=excluded.consent_source");
                 PreparedStatement event = c.prepareStatement("INSERT INTO notification_subscription_events(profile_id,channel,action,changed_at,source) VALUES(?,?,?,?,?)")) {
                current.setLong(1, subscription.profileId()); current.setString(2, subscription.channel().name()); current.setString(3, subscription.status().name()); current.setString(4, subscription.changedAt().toString()); current.setString(5, subscription.consentSource()); current.executeUpdate();
                event.setLong(1, subscription.profileId()); event.setString(2, subscription.channel().name()); event.setString(3, subscription.status().name()); event.setString(4, subscription.changedAt().toString()); event.setString(5, subscription.consentSource()); event.executeUpdate();
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw failure("save notification subscription", e); }
    }

    private NotificationSubscription read(ResultSet r) throws SQLException {
        return new NotificationSubscription(r.getLong("profile_id"), NotificationChannel.valueOf(r.getString("channel")), SubscriptionStatus.valueOf(r.getString("status")), LocalDateTime.parse(r.getString("changed_at")), r.getString("consent_source"));
    }
    private IllegalStateException failure(String action, SQLException e) { return new IllegalStateException("Could not " + action, e); }
}
