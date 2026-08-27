package com.permitping;

import com.permitping.domain.Document;
import com.permitping.domain.Profile;
import com.permitping.domain.ProfileType;
import com.permitping.infrastructure.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @Test void documentSurvivesSaveReloadAndDelete() throws Exception {
        Path db=Files.createTempFile("permitping-test-", ".db");
        try { var repo=new SqliteDocumentRepository(new Database(db));repo.save(new Document(0,"Annual License","License","Northside Electric","Oak Street",LocalDate.now().plusDays(90),"C:\\docs\\license.pdf","Renew before summer"));var loaded=repo.findAll();assertEquals(1,loaded.size());assertEquals("Northside Electric",loaded.get(0).holder());repo.delete(loaded.get(0).id());assertTrue(repo.findAll().isEmpty()); }
        finally { Files.deleteIfExists(db);Files.deleteIfExists(Path.of(db+"-wal"));Files.deleteIfExists(Path.of(db+"-shm")); }
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
}
