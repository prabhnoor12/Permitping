package com.permitping;

import com.permitping.domain.*;
import com.permitping.infrastructure.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @Test void documentSurvivesSaveReloadAndDelete() throws Exception {
        Path db=Files.createTempFile("permitping-test-", ".db");
        try { var repo=new SqliteDocumentRepository(new Database(db));repo.save(new Document(0,"Annual License","License","Northside Electric","Oak Street",LocalDate.now().plusDays(90),"C:\\docs\\license.pdf","Renew before summer"));var loaded=repo.findAll();assertEquals(1,loaded.size());assertEquals("Northside Electric",loaded.get(0).holder());repo.delete(loaded.get(0).id());assertTrue(repo.findAll().isEmpty()); }
        finally { Files.deleteIfExists(db);Files.deleteIfExists(Path.of(db+"-wal"));Files.deleteIfExists(Path.of(db+"-shm")); }
    }

    @Test void documentMutationsRejectStaleIdsAndInvalidArchiveTransitions() throws Exception {
        Path db = Files.createTempFile("permitping-stale-document-", ".db");
        try {
            var repo = new SqliteDocumentRepository(new Database(db));
            Document missing = new Document(999, "Missing", "License", "Test Co", "Job", LocalDate.now().plusDays(90), null, "");

            assertThrows(IllegalArgumentException.class, () -> repo.save(missing));
            assertThrows(IllegalArgumentException.class, () -> repo.archive(999));
            assertThrows(IllegalArgumentException.class, () -> repo.restore(999));
            assertThrows(IllegalArgumentException.class, () -> repo.delete(999));

            Document saved = repo.save(missing.withId(0));
            repo.archive(saved.id());
            assertThrows(IllegalArgumentException.class, () -> repo.archive(saved.id()));
            repo.restore(saved.id());
            assertThrows(IllegalArgumentException.class, () -> repo.restore(saved.id()));
        } finally { Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm")); }
    }

    @Test void profileSurvivesSaveAndReload() throws Exception {
        Path db=Files.createTempFile("permitping-profile-test-", ".db");
        try {
            var repo = new com.permitping.infrastructure.SqliteProfileRepository(new Database(db));
            repo.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "office@example.com", "555-0100", "Preferred subcontractor"));
            var loaded = repo.findAll();
            assertEquals(1, loaded.size()); assertEquals(ProfileType.COMPANY, loaded.get(0).type()); assertEquals("office@example.com", loaded.get(0).email());
        } finally { Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db+"-wal")); Files.deleteIfExists(Path.of(db+"-shm")); }
    }

    @Test void notificationSubscriptionPersistsCurrentStateAndHistory() throws Exception {
        Path db = Files.createTempFile("permitping-subscription-test-", ".db");
        try {
            Database database = new Database(db);
            var profiles = new SqliteProfileRepository(database);
            profiles.save(new Profile(0, "Consent Co", ProfileType.COMPANY, "crew@example.com", "+15551234567", ""));
            long profileId = profiles.findAll().get(0).id();
            var subscriptions = new SqliteNotificationSubscriptionRepository(database);
            var service = new com.permitping.application.NotificationSubscriptionService(subscriptions, null);

            service.subscribe(profileId, NotificationChannel.EMAIL, "signed form");
            service.unsubscribe(profileId, NotificationChannel.EMAIL, "recipient request");

            assertFalse(service.isSubscribed(profileId, NotificationChannel.EMAIL));
            assertEquals(SubscriptionStatus.UNSUBSCRIBED, subscriptions.find(profileId, NotificationChannel.EMAIL).orElseThrow().status());
            try (Connection c = database.connect(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM notification_subscription_events WHERE profile_id=?")) {
                p.setLong(1, profileId);
                try (ResultSet r = p.executeQuery()) { assertTrue(r.next()); assertEquals(2, r.getInt(1)); }
            }
        } finally { Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm")); }
    }

    @Test void profileDeletionProtectsLinkedDocuments() throws Exception {
        Path db = Files.createTempFile("permitping-profile-delete-", ".db");
        try {
            Database database = new Database(db);
            var profiles = new SqliteProfileRepository(database);
            var documents = new SqliteDocumentRepository(database);
            profiles.save(new Profile(0, "Linked Co", ProfileType.COMPANY, "", "", ""));
            long profileId = profiles.findAll().get(0).id();
            documents.save(new Document(0, "License", "License", "Linked Co", "Job", LocalDate.now().plusDays(90), null, "", profileId));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> profiles.delete(profileId));
            assertTrue(failure.getMessage().contains("linked to documents"));
            assertEquals(1, profiles.findAll().size());
        } finally { Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm")); }
    }

    @Test void profileMutationsRejectStaleIdsAndInvalidArchiveTransitions() throws Exception {
        Path db = Files.createTempFile("permitping-stale-profile-", ".db");
        try {
            var repo = new SqliteProfileRepository(new Database(db));
            Profile missing = new Profile(999, "Missing", ProfileType.COMPANY, "", "", "");

            assertThrows(IllegalArgumentException.class, () -> repo.save(missing));
            assertThrows(IllegalArgumentException.class, () -> repo.archive(999));
            assertThrows(IllegalArgumentException.class, () -> repo.restore(999));
            assertThrows(IllegalArgumentException.class, () -> repo.delete(999));

            repo.save(new Profile(0, "Missing", ProfileType.COMPANY, "", "", ""));
            long id = repo.findAll().get(0).id();
            repo.archive(id);
            assertThrows(IllegalArgumentException.class, () -> repo.archive(id));
            repo.restore(id);
            assertThrows(IllegalArgumentException.class, () -> repo.restore(id));
        } finally { Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm")); }
    }

    @Test void archiveHidesDocumentAndBackupPassesIntegrityCheck() throws Exception {
        Path db=Files.createTempFile("permitping-backup-test-", ".db"); Path backups=Files.createTempDirectory("permitping-backups-");
        try {
            var repo=new SqliteDocumentRepository(new Database(db));
            Document saved=repo.save(new Document(0,"License","License","Test Co","Job",LocalDate.now().plusDays(90),null,""));
            repo.archive(saved.id()); assertTrue(repo.findAll().isEmpty()); assertEquals(1, repo.findArchived().size()); repo.restore(saved.id()); assertEquals(1, repo.findAll().size()); repo.archive(saved.id());
            Path backup=new com.permitping.infrastructure.BackupService(db).createAutomaticBackup(backups, 2);
            assertTrue(Files.isRegularFile(backup));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db+"-wal")); Files.deleteIfExists(Path.of(db+"-shm"));
            try(var files=Files.walk(backups)){files.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}});}
        }
    }

    @Test void assignmentMutationsRejectStaleIds() throws Exception {
        Path db = Files.createTempFile("permitping-stale-assignment-", ".db");
        try {
            Database database = new Database(db);
            var profiles = new SqliteProfileRepository(database);
            var assignments = new SqliteAssignmentRepository(database);
            profiles.save(new Profile(0, "Assignment Co", ProfileType.COMPANY, "", "", ""));
            long profileId = profiles.findAll().get(0).id();
            ProjectAssignment missing = new ProjectAssignment(999, "Job", profileId, AssignmentStatus.PENDING, "");

            assertThrows(IllegalArgumentException.class, () -> assignments.save(missing));
            assertThrows(IllegalArgumentException.class, () -> assignments.delete(999));

            assignments.save(new ProjectAssignment(0, "Job", profileId, AssignmentStatus.PENDING, ""));
            long id = assignments.findAll().get(0).id();
            assignments.delete(id);
            assertThrows(IllegalArgumentException.class, () -> assignments.delete(id));
        } finally { Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm")); }
    }

    @Test void backupBundleIncludesAndRestoresManagedDocuments() throws Exception {
        Path db = Files.createTempFile("permitping-bundle-test-", ".db");
        Path documents = Files.createTempDirectory("permitping-documents-");
        Path backups = Files.createTempDirectory("permitping-bundles-");
        Path legacy = null;
        try {
            var repo = new SqliteDocumentRepository(new Database(db));
            repo.save(new Document(0, "License", "License", "Original Co", "Job", LocalDate.now().plusDays(90), null, ""));
            Path managedFile = documents.resolve("contracts/license.txt");
            Files.createDirectories(managedFile.getParent());
            Files.writeString(managedFile, "original contents");
            var service = new BackupService(db, documents);

            Path backup = service.createAutomaticBackup(backups, 2);
            assertTrue(backup.getFileName().toString().endsWith(".zip"));
            try (ZipFile zip = new ZipFile(backup.toFile())) {
                assertNotNull(zip.getEntry("permitping.db"));
                assertNotNull(zip.getEntry("documents/contracts/license.txt"));
            }

            repo.save(new Document(0, "Changed", "License", "Changed Co", "Job", LocalDate.now().plusDays(90), null, ""));
            Files.writeString(managedFile, "changed contents");
            Path previous = service.restoreBackup(backup);

            assertEquals(1, repo.findAll().size());
            assertEquals("Original Co", repo.findAll().get(0).holder());
            assertEquals("original contents", Files.readString(managedFile));
            assertTrue(Files.isRegularFile(previous));
            try (Stream<Path> paths = Files.list(documents.getParent())) {
                assertTrue(paths.anyMatch(path -> path.getFileName().toString().startsWith(documents.getFileName() + ".before-restore-")));
            }

            legacy = Files.createTempFile("permitping-legacy-backup-", ".db");
            try (ZipFile zip = new ZipFile(backup.toFile()); var input = zip.getInputStream(zip.getEntry("permitping.db"))) {
                Files.copy(input, legacy, StandardCopyOption.REPLACE_EXISTING);
            }
            repo.save(new Document(0, "Changed again", "License", "Changed Again Co", "Job", LocalDate.now().plusDays(90), null, ""));
            Path legacyPrevious = service.restoreBackup(legacy);
            assertEquals(1, repo.findAll().size());
            assertEquals("original contents", Files.readString(managedFile));
            assertTrue(Files.isRegularFile(legacyPrevious));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm"));
            if (legacy != null) Files.deleteIfExists(legacy);
            deleteTree(documents);
            try (Stream<Path> paths = Files.list(documents.getParent())) {
                paths.filter(path -> path.getFileName().toString().startsWith(documents.getFileName() + ".before-restore-")).forEach(this::deleteQuietly);
            }
            deleteTree(backups);
        }
    }

    @Test void backupRejectsZipSlipDocumentEntries() throws Exception {
        Path db = Files.createTempFile("permitping-zip-slip-", ".db");
        Path backup = Files.createTempFile("permitping-malicious-", ".zip");
        Path validBackups = Files.createTempDirectory("permitping-valid-bundle-");
        try {
            Path validBundle = new BackupService(db).createAutomaticBackup(validBackups, 1);
            try (ZipFile source = new ZipFile(validBundle.toFile()); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(backup))) {
                ZipEntry database = source.getEntry("permitping.db");
                output.putNextEntry(new ZipEntry("permitping.db"));
                try (var input = source.getInputStream(database)) { input.transferTo(output); }
                output.closeEntry();
                output.putNextEntry(new ZipEntry("documents/../outside.txt"));
                output.write("unsafe".getBytes());
                output.closeEntry();
            }
            assertThrows(IllegalStateException.class, () -> new BackupService(db).verify(backup));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm")); Files.deleteIfExists(backup);
            deleteTree(validBackups);
        }
    }

    private void deleteTree(Path directory) {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (Exception ignored) { }
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
    }

    @Test void reminderHistoryTracksExpiryAndCompletesSnoozedRows() throws Exception {
        Path db = Files.createTempFile("permitping-reminder-", ".db");
        try {
            Database database = new Database(db);
            var reminders = new SqliteReminderRepository(database);
            LocalDate originalExpiry = LocalDate.of(2026, 9, 5);
            LocalDate changedExpiry = LocalDate.of(2026, 9, 15);

            reminders.snooze(7, 30, originalExpiry, LocalDateTime.now().plusDays(7));
            assertFalse(reminders.wasSent(7, 30, originalExpiry));
            reminders.markSent(7, 30, originalExpiry, LocalDateTime.now());

            assertTrue(reminders.wasSent(7, 30, originalExpiry));
            assertFalse(reminders.isSnoozed(7, 30, originalExpiry));
            assertFalse(reminders.wasSent(7, 30, changedExpiry));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm"));
        }
    }

    @Test void deliveryHistoryDoesNotSuppressAChangedExpiryCycle() throws Exception {
        Path db = Files.createTempFile("permitping-delivery-", ".db");
        try {
            Database database = new Database(db);
            var deliveries = new SqliteReminderDeliveryRepository(database);
            LocalDate originalExpiry = LocalDate.of(2026, 9, 5);
            LocalDate changedExpiry = LocalDate.of(2026, 9, 15);
            deliveries.save(new ReminderDelivery(0, 7, 11, 30, "EMAIL", "crew@example.com", DeliveryStatus.SENT,
                LocalDateTime.now(), "provider-1", "", 1), originalExpiry);

            assertTrue(deliveries.hasSuccessfulDelivery(7, 11, 30, originalExpiry));
            assertFalse(deliveries.hasSuccessfulDelivery(7, 11, 30, changedExpiry));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm"));
        }
    }

    @Test void skippedDeliveryHistoryMatchesOnlyTheSameConfiguration() throws Exception {
        Path db = Files.createTempFile("permitping-skipped-delivery-", ".db");
        try {
            Database database = new Database(db);
            var deliveries = new SqliteReminderDeliveryRepository(database);
            LocalDate expiry = LocalDate.of(2026, 9, 5);
            String reason = "No email address is configured.";
            deliveries.save(new ReminderDelivery(0, 7, 11, 30, "EMAIL", "", DeliveryStatus.SKIPPED,
                LocalDateTime.now(), "", reason, 0), expiry);

            assertTrue(deliveries.hasSkippedDelivery(7, 11, 30, expiry, "", reason));
            assertFalse(deliveries.hasSkippedDelivery(7, 11, 30, expiry, "crew@example.com", reason));
            assertFalse(deliveries.hasSkippedDelivery(7, 11, 30, LocalDate.of(2026, 9, 6), "", reason));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm"));
        }
    }

    @Test void restoreMovesSQLiteSidecarsAlongsideTheDatabaseSafetyCopy() throws Exception {
        Path db = Files.createTempFile("permitping-sidecar-", ".db");
        Path backups = Files.createTempDirectory("permitping-sidecar-backups-");
        try {
            new Database(db);
            BackupService service = new BackupService(db);
            Path backup = service.createAutomaticBackup(backups, 1);
            Path wal = Path.of(db + "-wal");
            Path shm = Path.of(db + "-shm");
            Files.writeString(wal, "old wal");
            Files.writeString(shm, "old shm");

            Path previous = service.restoreBackup(backup);

            assertFalse(Files.exists(wal));
            assertFalse(Files.exists(shm));
            assertEquals("old wal", Files.readString(Path.of(previous + "-wal")));
            assertEquals("old shm", Files.readString(Path.of(previous + "-shm")));
        } finally {
            Files.deleteIfExists(db); Files.deleteIfExists(Path.of(db + "-wal")); Files.deleteIfExists(Path.of(db + "-shm"));
            try (Stream<Path> paths = Files.list(db.getParent())) {
                paths.filter(path -> path.getFileName().toString().startsWith(db.getFileName() + ".before-restore-")).forEach(path -> {
                    deleteTreeQuietly(path);
                    deleteQuietly(path);
                    deleteQuietly(Path.of(path + "-wal"));
                    deleteQuietly(Path.of(path + "-shm"));
                });
            }
            deleteTree(backups);
        }
    }

    private void deleteTreeQuietly(Path directory) {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (Exception ignored) { }
    }
}
