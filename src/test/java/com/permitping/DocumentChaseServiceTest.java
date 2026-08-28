package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import com.permitping.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class DocumentChaseServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;

    @Test void chasesWhenRequiredEvidenceIsExpiredOrItsFileIsMissing() {
        Database database = new Database(temp.resolve("permitping.db"));
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "office@example.com", "", "", false, true, NotificationChannel.EMAIL));
        long profileId = profiles.list().get(0).id();
        AssignmentService assignments = new AssignmentService(new SqliteAssignmentRepository(database));
        assignments.save(new ProjectAssignment(0, "Oak Street", profileId, AssignmentStatus.APPROVED, ""));
        RequirementTemplateService templates = new RequirementTemplateService(new SqliteRequirementTemplateRepository(database));
        templates.assign("Oak Street", templates.list().stream().filter(value -> value.name().equals("Electrical subcontractor")).findFirst().orElseThrow().id());
        NotificationSubscriptionService subscriptions = new NotificationSubscriptionService(new SqliteNotificationSubscriptionRepository(database), null, CLOCK);
        subscriptions.subscribe(profileId, NotificationChannel.EMAIL, "signed-form");
        List<OutgoingMessage> sent = new ArrayList<>();
        FileStorage files = new FileStorage(temp.resolve("files"));
        DocumentService documents = new DocumentService(new SqliteDocumentRepository(database), CLOCK);
        documents.save(new Document(0, "Expired OSHA card", "OSHA card", "Northside Electric", "Oak Street", LocalDate.of(2026, 8, 25), "", "", profileId));
        documents.save(new Document(0, "Unusable license", "License", "Northside Electric", "Oak Street", LocalDate.of(2027, 8, 26), temp.resolve("missing-license.pdf").toString(), "", profileId));
        UploadRequestService uploads = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, documents, files, CLOCK);
        DocumentChaseService chasing = new DocumentChaseService(assignments, profiles, documents, templates, uploads,
            files, new SqliteDocumentChaseDeliveryRepository(database), new ChaseMessageProtector("test-key"),
            message -> { sent.add(message); return DeliveryResult.sent("email-1"); }, message -> DeliveryResult.failed("unexpected SMS"), subscriptions, null, CLOCK, true);

        List<DocumentChaseResult> first = chasing.chaseMissing();
        List<DocumentChaseResult> second = chasing.chaseMissing();

        assertEquals(3, first.size());
        assertTrue(first.stream().allMatch(value -> value.status() == ChaseStatus.REQUESTED));
        assertTrue(first.stream().anyMatch(value -> value.documentType().equals("OSHA card")));
        assertTrue(first.stream().anyMatch(value -> value.documentType().equals("License")));
        assertTrue(first.stream().anyMatch(value -> value.documentType().equals("Insurance certificate")));
        assertEquals(3, sent.size());
        assertTrue(sent.get(0).body().contains("/upload/"));
        assertTrue(second.isEmpty());
        assertEquals(3, uploads.recentRequests(20).size());
    }

    @Test void doesNotCreateARequestWithoutExplicitSubscription() {
        Database database = new Database(temp.resolve("permitping.db"));
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "office@example.com", "", "", false, true, NotificationChannel.EMAIL));
        long profileId = profiles.list().get(0).id();
        AssignmentService assignments = new AssignmentService(new SqliteAssignmentRepository(database));
        assignments.save(new ProjectAssignment(0, "Oak Street", profileId, AssignmentStatus.APPROVED, ""));
        RequirementTemplateService templates = new RequirementTemplateService(new SqliteRequirementTemplateRepository(database));
        templates.assign("Oak Street", templates.list().stream().filter(value -> value.name().equals("Electrical subcontractor")).findFirst().orElseThrow().id());
        NotificationSubscriptionService subscriptions = new NotificationSubscriptionService(new SqliteNotificationSubscriptionRepository(database), null, CLOCK);
        FileStorage files = new FileStorage(temp.resolve("files"));
        UploadRequestService uploads = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), files, CLOCK);
        DocumentChaseService chasing = new DocumentChaseService(assignments, profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), templates, uploads,
            files, new SqliteDocumentChaseDeliveryRepository(database), new ChaseMessageProtector("test-key"),
            message -> DeliveryResult.sent("email-1"), message -> DeliveryResult.failed("unexpected SMS"), subscriptions, null, CLOCK, true);

        List<DocumentChaseResult> results = chasing.chaseMissing();

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(value -> value.status() == ChaseStatus.SKIPPED));
        assertTrue(uploads.recentRequests(20).isEmpty());
    }

    @Test void retriesTransientProviderFailuresFromThePersistentOutbox() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-26T00:00:00Z"));
        Database database = new Database(temp.resolve("retry.db"));
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(database));
        profiles.save(new Profile(0, "Northside Electric", ProfileType.COMPANY, "office@example.com", "", "", false, true, NotificationChannel.EMAIL));
        long profileId = profiles.list().get(0).id();
        AssignmentService assignments = new AssignmentService(new SqliteAssignmentRepository(database));
        assignments.save(new ProjectAssignment(0, "Oak Street", profileId, AssignmentStatus.APPROVED, ""));
        RequirementTemplateService templates = new RequirementTemplateService(new SqliteRequirementTemplateRepository(database));
        templates.assign("Oak Street", templates.list().stream().filter(value -> value.name().equals("Electrical subcontractor")).findFirst().orElseThrow().id());
        NotificationSubscriptionService subscriptions = new NotificationSubscriptionService(new SqliteNotificationSubscriptionRepository(database), null, clock);
        subscriptions.subscribe(profileId, NotificationChannel.EMAIL, "signed-form");
        DocumentService documents = new DocumentService(new SqliteDocumentRepository(database), clock);
        FileStorage files = new FileStorage(temp.resolve("retry-files"));
        UploadRequestService uploads = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, documents, files, clock);
        AtomicInteger providerCalls = new AtomicInteger();
        DocumentChaseService chasing = new DocumentChaseService(assignments, profiles, documents, templates, uploads,
            files, new SqliteDocumentChaseDeliveryRepository(database), new ChaseMessageProtector("test-key"),
            message -> providerCalls.getAndIncrement() == 0
                ? DeliveryResult.failed("temporary provider outage")
                : DeliveryResult.sent("email-" + providerCalls.get()),
            message -> DeliveryResult.failed("unexpected SMS"), subscriptions, null, clock, true);

        List<DocumentChaseResult> first = chasing.chaseMissing();

        assertEquals(3, first.size());
        assertEquals(1, first.stream().filter(value -> value.status() == ChaseStatus.FAILED).count());
        assertEquals(2, first.stream().filter(value -> value.status() == ChaseStatus.REQUESTED).count());
        assertEquals(3, providerCalls.get());

        clock.advance(Duration.ofMinutes(16));
        List<DocumentChaseResult> retry = chasing.chaseMissing();

        assertEquals(1, retry.size());
        assertEquals(ChaseStatus.REQUESTED, retry.get(0).status());
        assertEquals(4, providerCalls.get());
        assertTrue(new SqliteDocumentChaseDeliveryRepository(database).pending(clock.instant().atZone(ZoneOffset.UTC).toLocalDateTime().plusDays(1), 20).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
