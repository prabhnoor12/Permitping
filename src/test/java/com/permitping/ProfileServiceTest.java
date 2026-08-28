package com.permitping;

import com.permitping.application.ProfileRepository;
import com.permitping.application.ProfileService;
import com.permitping.domain.Profile;
import com.permitping.domain.ProfileType;
import com.permitping.domain.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProfileServiceTest {
    private final List<Profile> store = new ArrayList<>();
    private final ProfileService service = new ProfileService(new ProfileRepository() {
        public List<Profile> findAll() { return store.stream().filter(p -> !p.archived()).toList(); }
        public List<Profile> findArchived() { return store.stream().filter(Profile::archived).toList(); }
        public void save(Profile profile) { store.removeIf(p -> p.id() == profile.id() && profile.id() > 0); store.add(profile); }
        public void archive(long id) { replace(id, true); }
        public void restore(long id) { replace(id, false); }
        private void replace(long id, boolean archived) { for (int i=0; i<store.size(); i++) if (store.get(i).id() == id) { Profile p=store.get(i); store.set(i,new Profile(p.id(),p.name(),p.type(),p.email(),p.phone(),p.notes(),archived)); } }
    });

    @Test void rejectsInvalidContactDetails() {
        assertThrows(IllegalArgumentException.class, () -> service.save(new Profile(0,"Acme",ProfileType.COMPANY,"not-an-email","", "")));
        assertThrows(IllegalArgumentException.class, () -> service.save(new Profile(0,"Acme",ProfileType.COMPANY,"","12", "")));
    }

    @Test void rejectsDuplicateNamesCaseInsensitively() {
        service.save(new Profile(0,"Acme",ProfileType.COMPANY,"","", ""));
        assertThrows(IllegalArgumentException.class, () -> service.save(new Profile(0," acme ",ProfileType.COMPANY,"","", "")));
    }

    @Test void preservesNotificationPreferences() {
        service.save(new Profile(0, "Acme", ProfileType.COMPANY, "alerts@example.com", "", "", false, true, NotificationChannel.EMAIL));
        assertEquals(true, store.get(0).notificationsEnabled());
        assertEquals(NotificationChannel.EMAIL, store.get(0).notificationChannel());
    }

    @Test void validatesContactDetailsForSelectedNotificationChannel() {
        assertThrows(IllegalArgumentException.class, () -> service.save(new Profile(0, "Email only", ProfileType.COMPANY, "", "", "", false, true, NotificationChannel.EMAIL)));
        assertThrows(IllegalArgumentException.class, () -> service.save(new Profile(0, "Sms only", ProfileType.COMPANY, "", "", "", false, true, NotificationChannel.SMS)));
        assertThrows(IllegalArgumentException.class, () -> service.save(new Profile(0, "Local phone", ProfileType.COMPANY, "", "555-0100", "", false, true, NotificationChannel.SMS)));
        service.save(new Profile(0, "International phone", ProfileType.COMPANY, "", "+15551234567", "", false, true, NotificationChannel.SMS));
    }

    @Test void archivesProfilesOutOfActiveList() {
        store.add(new Profile(7,"Acme",ProfileType.COMPANY,"","", ""));
        service.archive(7);
        assertEquals(0, service.list().size());
        assertEquals(1, service.archived().size());
    }

    @Test void reusesAnArchivedProfileWhenEnsuringByName() {
        store.add(new Profile(7, "Acme", ProfileType.COMPANY, "", "", "", true));
        service.ensure(" acme ");
        assertEquals(1, service.list().size());
        assertEquals(7, service.list().get(0).id());
    }
}
