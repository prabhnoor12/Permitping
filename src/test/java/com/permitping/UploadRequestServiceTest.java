package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import com.permitping.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
        Path source = temp.resolve("license.pdf"); Files.write(source, "%PDF-1.7\nPermitPing test document\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

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

    @Test void suspiciousFileIsHeldForReviewAndCannotBecomeEvidence() throws Exception {
        Path db = temp.resolve("permitping.db"); Database database = new Database(db);
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "", "", ""));
        long profileId = profiles.list().get(0).id();
        DocumentService documents = new DocumentService(new SqliteDocumentRepository(database), CLOCK);
        UploadRequestService service = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, documents, new FileStorage(temp.resolve("files")), CLOCK);
        UploadInvite invite = service.create(profileId, "Oak Street", "License", Duration.ofDays(30));
        Path source = temp.resolve("license.pdf"); Files.writeString(source, "not actually a PDF");

        UploadSubmission submission = service.submit(invite.token(), "license.pdf", "application/pdf", source);

        assertEquals(UploadVerificationStatus.NEEDS_REVIEW, service.verify(submission).status());
        assertThrows(IllegalArgumentException.class, () -> service.accept(submission.id(), "reviewer", "Northside license", LocalDate.of(2027, 1, 1), ""));
        assertTrue(documents.list().isEmpty());
        assertEquals(UploadSubmissionStatus.PENDING, new SqliteUploadRequestRepository(database).findSubmission(submission.id()).orElseThrow().status());
    }

    @Test void suggestsExpiryDateFromPdfTextButLeavesAcceptanceToReviewer() throws Exception {
        Path db = temp.resolve("permitping.db"); Database database = new Database(db);
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "", "", ""));
        long profileId = profiles.list().get(0).id();
        UploadRequestService service = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), new FileStorage(temp.resolve("files")), CLOCK);
        UploadInvite invite = service.create(profileId, "Oak Street", "License", Duration.ofDays(30));
        Path source = temp.resolve("license.pdf"); Files.writeString(source, "%PDF-1.7\nExpiration date: 2027-04-30\n");

        UploadAnalysis analysis = service.analyze(service.submit(invite.token(), "license.pdf", "application/pdf", source));

        assertEquals(List.of(LocalDate.of(2027, 4, 30)), analysis.extractedDates());
        assertEquals(LocalDate.of(2027, 4, 30), analysis.suggestedExpiryDate());
        assertTrue(analysis.findings().stream().anyMatch(value -> value.contains("reviewer confirmation")));
    }

    @Test void extractsExpiryDateFromDocxXmlWithinArchiveLimits() throws Exception {
        Path db = temp.resolve("permitping.db"); Database database = new Database(db);
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "", "", ""));
        long profileId = profiles.list().get(0).id();
        UploadRequestService service = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), new FileStorage(temp.resolve("files")), CLOCK);
        UploadInvite invite = service.create(profileId, "Oak Street", "License", Duration.ofDays(30));
        Path source = temp.resolve("license.docx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document><w:t>Valid until 31 December 2027</w:t></w:document>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        UploadAnalysis analysis = service.analyze(service.submit(invite.token(), "license.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", source));

        assertEquals(List.of(LocalDate.of(2027, 12, 31)), analysis.extractedDates());
        assertEquals(LocalDate.of(2027, 12, 31), analysis.suggestedExpiryDate());
    }
}
