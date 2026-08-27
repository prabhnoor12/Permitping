package com.permitping.domain;

public record Profile(long id, String name, ProfileType type, String email, String phone, String notes, boolean archived, boolean notificationsEnabled, NotificationChannel notificationChannel) {
    public Profile(long id, String name, ProfileType type, String email, String phone, String notes) {
        this(id, name, type, email, phone, notes, false, false, NotificationChannel.NONE);
    }
    public Profile(long id, String name, ProfileType type, String email, String phone, String notes, boolean archived) {
        this(id, name, type, email, phone, notes, archived, false, NotificationChannel.NONE);
    }
}
