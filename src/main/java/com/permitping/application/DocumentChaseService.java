package com.permitping.application;

import com.permitping.domain.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Creates deduplicated upload requests and processes their encrypted, bounded delivery outbox. */
public final class DocumentChaseService {
    private static final Duration REQUEST_VALIDITY = Duration.ofDays(14);
    private static final Duration RETRY_COOLDOWN = Duration.ofDays(7);
    private static final int MAX_DELIVERIES_PER_PASS = 200;
    private final AssignmentService assignments;
    private final ProfileService profiles;
    private final DocumentService documents;
    private final RequirementTemplateService templates;
    private final UploadRequestService uploads;
    private final DocumentChaseDeliveryRepository deliveries;
    private final ChaseMessageProtector protector;
    private final EmailSender email;
    private final SmsSender sms;
    private final NotificationSubscriptionService subscriptions;
    private final AuditService audit;
    private final Clock clock;
    private final boolean portalEnabled;

    public DocumentChaseService(AssignmentService assignments, ProfileService profiles, DocumentService documents,
                                RequirementTemplateService templates, UploadRequestService uploads,
                                DocumentChaseDeliveryRepository deliveries, ChaseMessageProtector protector,
                                EmailSender email, SmsSender sms, NotificationSubscriptionService subscriptions,
                                AuditService audit, Clock clock, boolean portalEnabled) {
        this.assignments = assignments; this.profiles = profiles; this.documents = documents; this.templates = templates;
        this.uploads = uploads; this.deliveries = deliveries; this.protector = protector; this.email = email; this.sms = sms;
        this.subscriptions = subscriptions; this.audit = audit; this.clock = clock; this.portalEnabled = portalEnabled;
    }

    public synchronized List<DocumentChaseResult> chaseMissing() {
        List<DocumentChaseResult> results = new ArrayList<>();
        if (!portalEnabled) return results;
        for (ProjectAssignment assignment : assignments.list()) {
            if (assignment.status() != AssignmentStatus.APPROVED) continue;
            Profile profile = profiles.list().stream().filter(value -> value.id() == assignment.profileId() && !value.archived()).findFirst().orElse(null);
            RequirementTemplate template = templateFor(assignment.project());
            if (profile == null || template == null) continue;
            for (String requiredType : template.requiredTypes()) {
                if (hasEvidence(profile, assignment.project(), requiredType)) continue;
                var latest = uploads.latestRequest(profile.id(), assignment.project(), requiredType);
                if (latest.isPresent() && recentlyRequested(latest.get())) continue;
                results.addAll(createAndQueue(profile, assignment.project(), requiredType));
            }
        }
        results.addAll(processPending());
        return results;
    }

    private List<DocumentChaseResult> createAndQueue(Profile profile, String project, String documentType) {
        List<DocumentChaseResult> results = new ArrayList<>();
        List<NotificationChannel> channels = channelsFor(profile);
        if (channels.isEmpty()) {
            results.add(result(profile, project, documentType, 0, NotificationChannel.NONE, ChaseStatus.SKIPPED, "Notifications are disabled, unsubscribed, or missing a contact address."));
            return results;
        }
        UploadInvite invite;
        try {
            invite = uploads.create(profile.id(), project, documentType, REQUEST_VALIDITY);
        } catch (RuntimeException ex) {
            for (NotificationChannel channel : channels) results.add(result(profile, project, documentType, 0, channel, ChaseStatus.FAILED, "Upload request could not be created: " + safeMessage(ex)));
            return results;
        }
        String link = uploads.link(invite);
        for (NotificationChannel channel : channels) {
            String recipient = channel == NotificationChannel.EMAIL ? profile.email() : profile.phone();
            OutgoingMessage message = new OutgoingMessage(recipient, "Action required: " + documentType + " for " + project,
                "PermitPing needs your " + documentType + " for " + project + ". Upload it here: " + link + " (link expires in 14 days.)");
            deliveries.save(new DocumentChaseDelivery(0, invite.request().id(), profile.id(), project, documentType, channel, recipient,
                protector.encrypt(message.recipient() + "\n" + message.subject() + "\n" + message.body()), ChaseDeliveryStatus.PENDING, 0, LocalDateTime.now(clock), null, "", ""));
        }
        if (audit != null) audit.record("DOCUMENT_CHASE_CREATED", "profile=" + profile.id() + ", project=" + project + ", type=" + documentType + ", request=" + invite.request().id());
        return results;
    }

    private List<DocumentChaseResult> processPending() {
        List<DocumentChaseResult> results = new ArrayList<>(); LocalDateTime now = LocalDateTime.now(clock);
        for (DocumentChaseDelivery delivery : deliveries.pending(now, MAX_DELIVERIES_PER_PASS)) {
            int attempts = delivery.attempts() + 1;
            try {
                OutgoingMessage message = parseMessage(protector.decrypt(delivery.encryptedMessage()));
                DeliveryResult outcome = delivery.channel() == NotificationChannel.EMAIL ? email.send(message) : sms.send(message);
                if (outcome != null && outcome.successful()) {
                    deliveries.save(updated(delivery, ChaseDeliveryStatus.SENT, attempts, now, now, outcome.providerMessageId(), ""));
                    results.add(result(delivery, ChaseStatus.REQUESTED, "Chase message sent to " + delivery.recipient() + "."));
                    if (audit != null) audit.record("DOCUMENT_CHASE_SENT", "delivery=" + delivery.id() + ", request=" + delivery.requestId() + ", channel=" + delivery.channel());
                } else {
                    String error = outcome == null ? "Provider returned no result." : safeMessage(outcome);
                    results.add(failed(delivery, attempts, now, error));
                }
            } catch (RuntimeException ex) { results.add(failed(delivery, attempts, now, "Delivery failed: " + safeMessage(ex))); }
        }
        return results;
    }

    private DocumentChaseResult failed(DocumentChaseDelivery delivery, int attempts, LocalDateTime now, String error) {
        deliveries.save(updated(delivery, ChaseDeliveryStatus.FAILED, attempts, now, now.plus(backoff(attempts)), "", error));
        if (audit != null) audit.record("DOCUMENT_CHASE_FAILED", "delivery=" + delivery.id() + ", attempt=" + attempts + ", error=" + error);
        return result(delivery, ChaseStatus.FAILED, error + (attempts >= 3 ? " No further automatic retries will be attempted." : " Retry is scheduled."));
    }

    private OutgoingMessage parseMessage(String value) {
        String[] parts = value == null ? new String[0] : value.split("\\n", 3);
        if (parts.length != 3 || parts[0].isBlank()) throw new IllegalArgumentException("Invalid chase message payload");
        return new OutgoingMessage(parts[0], parts[1], parts[2]);
    }

    private DocumentChaseDelivery updated(DocumentChaseDelivery delivery, ChaseDeliveryStatus status, int attempts,
                                          LocalDateTime attemptedAt, LocalDateTime nextAttemptAt, String providerId, String error) {
        return new DocumentChaseDelivery(delivery.id(), delivery.requestId(), delivery.profileId(), delivery.project(), delivery.documentType(), delivery.channel(), delivery.recipient(), delivery.encryptedMessage(), status, attempts, nextAttemptAt, attemptedAt, providerId == null ? "" : providerId, error == null ? "" : error);
    }

    private Duration backoff(int attempt) { return switch (attempt) { case 1 -> Duration.ofMinutes(15); case 2 -> Duration.ofHours(1); default -> Duration.ofHours(6); }; }
    private List<NotificationChannel> channelsFor(Profile profile) {
        if (!profile.notificationsEnabled() || profile.notificationChannel() == null) return List.of();
        List<NotificationChannel> channels = new ArrayList<>(); NotificationChannel selected = profile.notificationChannel();
        if ((selected == NotificationChannel.EMAIL || selected == NotificationChannel.EMAIL_AND_SMS) && usable(profile.email()) && subscribed(profile.id(), NotificationChannel.EMAIL)) channels.add(NotificationChannel.EMAIL);
        if ((selected == NotificationChannel.SMS || selected == NotificationChannel.EMAIL_AND_SMS) && usable(profile.phone()) && subscribed(profile.id(), NotificationChannel.SMS)) channels.add(NotificationChannel.SMS);
        return channels;
    }
    private boolean subscribed(long profileId, NotificationChannel channel) { return subscriptions != null && subscriptions.isSubscribed(profileId, channel); }
    private boolean usable(String value) { return value != null && !value.isBlank(); }
    private boolean hasEvidence(Profile profile, String project, String type) { return documents.list().stream().anyMatch(document -> document.holderProfileId() == profile.id() && same(document.project(), project) && same(document.type(), type)); }
    private RequirementTemplate templateFor(String project) { Long id = templates.templateIdFor(project); return id == null ? null : templates.list().stream().filter(template -> template.id() == id).findFirst().orElse(null); }
    private boolean recentlyRequested(UploadRequest request) { LocalDateTime now = LocalDateTime.now(clock); return (request.status() == UploadRequestStatus.OPEN && request.expiresAt().isAfter(now)) || request.createdAt().plus(RETRY_COOLDOWN).isAfter(now); }
    private DocumentChaseResult result(DocumentChaseDelivery delivery, ChaseStatus status, String detail) { return new DocumentChaseResult(delivery.profileId(), "", delivery.project(), delivery.documentType(), delivery.requestId(), delivery.channel(), status, detail); }
    private DocumentChaseResult result(Profile profile, String project, String type, long requestId, NotificationChannel channel, ChaseStatus status, String detail) { return new DocumentChaseResult(profile.id(), profile.name(), project, type, requestId, channel, status, detail); }
    private boolean same(String left, String right) { return left != null && right != null && left.trim().equalsIgnoreCase(right.trim()); }
    private String safeMessage(RuntimeException ex) { return ex.getMessage() == null || ex.getMessage().isBlank() ? "Unknown error." : ex.getMessage(); }
    private String safeMessage(DeliveryResult result) { return result.errorMessage() == null || result.errorMessage().isBlank() ? "Provider rejected the message." : result.errorMessage(); }
}
