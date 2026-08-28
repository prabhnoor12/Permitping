package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import com.permitping.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

final class UploadRequestServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;

    @Test void tokenIsHashedAndRequestsExpireOrRevoke() throws Exception {
        Path db = temp.resolve("permitping.db"); Database database = new Database(db);
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "", "", ""));
        long profileId = profiles.list().get(0).id();
        UploadRequestService service = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), new FileStorage(temp.resolve("files")), CLOCK);

        UploadInvite invite = service.create(profileId, "Oak Street", "License", Duration.ofDays(1));

        assertNotEquals(invite.token(), invite.request().tokenHash());
        assertTrue(service.findByToken(invite.token()).isPresent());
        service.revoke(invite.request().id());
        assertTrue(service.findByToken(invite.token()).isEmpty());
        UploadInvite expiring = service.create(profileId, "Oak Street", "Permit", Duration.ofDays(1));
        UploadRequestService afterExpiry = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), new FileStorage(temp.resolve("files")), Clock.offset(CLOCK, Duration.ofDays(2)));
        assertTrue(afterExpiry.findByToken(expiring.token()).isEmpty());
    }

    @Test void uploadRemainsPendingUntilReviewerAcceptsIt() throws Exception {
        Path db = temp.resolve("permitping.db"); Database database = new Database(db);
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "", "", ""));
        long profileId = profiles.list().get(0).id();
        DocumentService documents = new DocumentService(new SqliteDocumentRepository(database), CLOCK);
        UploadRequestService service = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, documents, new FileStorage(temp.resolve("files")), CLOCK);
        UploadInvite invite = service.create(profileId, "Oak Street", "License", Duration.ofDays(30));
        Path source = temp.resolve("license.pdf"); Files.writeString(source, "test document");

        UploadSubmission submission = service.submit(invite.token(), "../../license.pdf", "application/pdf", source);

        assertEquals(UploadSubmissionStatus.PENDING, submission.status());
        assertTrue(documents.list().isEmpty());
        Document accepted = service.accept(submission.id(), "reviewer", "Northside license", LocalDate.of(2027, 1, 1), "Verified by office");
        assertEquals(profileId, accepted.holderProfileId());
        assertEquals("Oak Street", accepted.project());
        assertEquals(UploadSubmissionStatus.ACCEPTED, new SqliteUploadRequestRepository(database).findSubmission(submission.id()).orElseThrow().status());
        assertEquals(1, documents.list().size());
        assertThrows(IllegalArgumentException.class, () -> service.accept(submission.id(), "reviewer", "Duplicate", LocalDate.of(2027, 1, 1), ""));
    }
}
