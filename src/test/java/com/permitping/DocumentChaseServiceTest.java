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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class DocumentChaseServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;

    @Test void createsRequestsAndSendsOnlyOnceForMissingTemplateRequirements() {
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
        UploadRequestService uploads = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), new FileStorage(temp.resolve("files")), CLOCK);
        DocumentChaseService chasing = new DocumentChaseService(assignments, profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), templates, uploads,
            message -> { sent.add(message); return DeliveryResult.sent("email-1"); }, message -> DeliveryResult.failed("unexpected SMS"), subscriptions, null, CLOCK, true);

        List<DocumentChaseResult> first = chasing.chaseMissing();
        List<DocumentChaseResult> second = chasing.chaseMissing();

        assertEquals(3, first.size());
        assertTrue(first.stream().allMatch(value -> value.status() == ChaseStatus.REQUESTED));
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
        UploadRequestService uploads = new UploadRequestService(new SqliteUploadRequestRepository(database), profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), new FileStorage(temp.resolve("files")), CLOCK);
        DocumentChaseService chasing = new DocumentChaseService(assignments, profiles, new DocumentService(new SqliteDocumentRepository(database), CLOCK), templates, uploads,
            message -> DeliveryResult.sent("email-1"), message -> DeliveryResult.failed("unexpected SMS"), subscriptions, null, CLOCK, true);

        List<DocumentChaseResult> results = chasing.chaseMissing();

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(value -> value.status() == ChaseStatus.SKIPPED));
        assertTrue(uploads.recentRequests(20).isEmpty());
    }
}
