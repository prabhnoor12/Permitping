package com.permitping.application;

import com.permitping.domain.*;
import java.util.List;

public final class ProfileService {
    private final ProfileRepository repository;
    public ProfileService(ProfileRepository repository) { this.repository = repository; }
    public List<Profile> list() { return repository.findAll(); }
    public List<Profile> archived() { return repository.findArchived(); }
    public void save(Profile profile) {
        validate(profile);
        String email = profile.email() == null ? "" : profile.email().trim();
        String phone = profile.phone() == null ? "" : profile.phone().trim();
        repository.save(new Profile(profile.id(), profile.name().trim(), profile.type() == null ? ProfileType.COMPANY : profile.type(), email, phone, profile.notes() == null ? "" : profile.notes().trim(), profile.archived(), profile.notificationsEnabled(), profile.notificationChannel() == null ? NotificationChannel.NONE : profile.notificationChannel()));
    }
    public void validate(Profile profile) {
        if (profile == null || profile.name() == null || profile.name().isBlank()) throw new IllegalArgumentException("Profile name is required");
        String email = profile.email() == null ? "" : profile.email().trim();
        if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address");
        String phone = profile.phone() == null ? "" : profile.phone().trim();
        if (!phone.isBlank() && !phone.matches("^[+0-9() .-]{7,}$")) throw new IllegalArgumentException("Enter a valid phone number");
        NotificationChannel channel = profile.notificationChannel() == null ? NotificationChannel.NONE : profile.notificationChannel();
        if (profile.notificationsEnabled() && (channel == NotificationChannel.EMAIL || channel == NotificationChannel.EMAIL_AND_SMS) && email.isBlank()) throw new IllegalArgumentException("An email address is required for the selected notification channel");
        if (profile.notificationsEnabled() && (channel == NotificationChannel.SMS || channel == NotificationChannel.EMAIL_AND_SMS) && phone.isBlank()) throw new IllegalArgumentException("A phone number is required for the selected notification channel");
        if (profile.notificationsEnabled() && (channel == NotificationChannel.SMS || channel == NotificationChannel.EMAIL_AND_SMS) && !phone.matches("\\+[1-9]\\d{7,14}")) throw new IllegalArgumentException("Use an SMS phone number in international format, for example +15551234567");
        if (java.util.stream.Stream.concat(list().stream(), archived().stream()).anyMatch(existing -> (profile.id() == 0 || existing.id() != profile.id()) && existing.name().equalsIgnoreCase(profile.name().trim()))) throw new IllegalArgumentException("A profile with this name already exists");
    }
    public void ensure(String name) {
        if (name == null || name.isBlank()) return;
        String normalized = name.trim();
        if (list().stream().anyMatch(profile -> profile.name().equalsIgnoreCase(normalized))) return;
        var archivedMatch = archived().stream().filter(profile -> profile.name().equalsIgnoreCase(normalized)).findFirst();
        if (archivedMatch.isPresent()) { restore(archivedMatch.get().id()); return; }
        save(new Profile(0, normalized, ProfileType.COMPANY, "", "", ""));
    }
    public void archive(long id) { if (id > 0) repository.archive(id); }
    public void restore(long id) { if (id > 0) repository.restore(id); }
    public void delete(long id) { if (id > 0) repository.delete(id); }
}
