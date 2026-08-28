package com.permitping.application;

import com.permitping.domain.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates deduplicated upload requests for missing assigned requirements and delivers one consented chase. */
public final class DocumentChaseService {
    private static final Duration REQUEST_VALIDITY = Duration.ofDays(14);
    private static final Duration RETRY_COOLDOWN = Duration.ofDays(7);
    private final AssignmentService assignments;
    private final ProfileService profiles;
    private final DocumentService documents;
    private final RequirementTemplateService templates;
    private final UploadRequestService uploads;
    private final EmailSender email;
    private final SmsSender sms;
    private final NotificationSubscriptionService subscriptions;
    private final AuditService audit;
    private final Clock clock;
    private final boolean portalEnabled;

    public DocumentChaseService(AssignmentService assignments, ProfileService profiles, DocumentService documents,
                                RequirementTemplateService templates, UploadRequestService uploads,
                                EmailSender email, SmsSender sms, NotificationSubscriptionService subscriptions,
                                AuditService audit, Clock clock, boolean portalEnabled) {
        this.assignments = assignments; this.profiles = profiles; this.documents = documents; this.templates = templates;
        this.uploads = uploads; this.email = email; this.sms = sms; this.subscriptions = subscriptions; this.audit = audit;
        this.clock = clock; this.portalEnabled = portalEnabled;
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
                results.addAll(createAndDeliver(profile, assignment.project(), requiredType));
            }
        }
        return results;
    }

    private List<DocumentChaseResult> createAndDeliver(Profile profile, String project, String documentType) {
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
        for (NotificationChannel channel : channels) results.add(deliver(profile, project, documentType, invite.request().id(), link, channel));
        if (audit != null) audit.record("DOCUMENT_CHASE_CREATED", "profile=" + profile.id() + ", project=" + project + ", type=" + documentType + ", request=" + invite.request().id());
        return results;
    }

    private DocumentChaseResult deliver(Profile profile, String project, String documentType, long requestId, String link, NotificationChannel channel) {
        String recipient = channel == NotificationChannel.EMAIL ? profile.email() : profile.phone();
        OutgoingMessage message = new OutgoingMessage(recipient, "Action required: " + documentType + " for " + project,
            "PermitPing needs your " + documentType + " for " + project + ". Upload it here: " + link + " (link expires in 14 days.)");
        try {
            DeliveryResult delivery = channel == NotificationChannel.EMAIL ? email.send(message) : sms.send(message);
            if (delivery != null && delivery.successful()) return result(profile, project, documentType, requestId, channel, ChaseStatus.REQUESTED, "Upload request sent to " + recipient + ".");
            return result(profile, project, documentType, requestId, channel, ChaseStatus.FAILED, delivery == null ? "Provider returned no result." : safeMessage(delivery));
        } catch (RuntimeException ex) {
            return result(profile, project, documentType, requestId, channel, ChaseStatus.FAILED, "Delivery failed: " + safeMessage(ex));
        }
    }

    private List<NotificationChannel> channelsFor(Profile profile) {
        if (!profile.notificationsEnabled() || profile.notificationChannel() == null) return List.of();
        List<NotificationChannel> channels = new ArrayList<>();
        NotificationChannel selected = profile.notificationChannel();
        if ((selected == NotificationChannel.EMAIL || selected == NotificationChannel.EMAIL_AND_SMS) && usable(profile.email()) && subscribed(profile.id(), NotificationChannel.EMAIL)) channels.add(NotificationChannel.EMAIL);
        if ((selected == NotificationChannel.SMS || selected == NotificationChannel.EMAIL_AND_SMS) && usable(profile.phone()) && subscribed(profile.id(), NotificationChannel.SMS)) channels.add(NotificationChannel.SMS);
        return channels;
    }

    private boolean subscribed(long profileId, NotificationChannel channel) { return subscriptions != null && subscriptions.isSubscribed(profileId, channel); }
    private boolean usable(String value) { return value != null && !value.isBlank(); }
    private boolean hasEvidence(Profile profile, String project, String type) { return documents.list().stream().anyMatch(document -> document.holderProfileId() == profile.id() && same(document.project(), project) && same(document.type(), type)); }
    private RequirementTemplate templateFor(String project) { Long id = templates.templateIdFor(project); return id == null ? null : templates.list().stream().filter(template -> template.id() == id).findFirst().orElse(null); }
    private boolean recentlyRequested(UploadRequest request) { return request.status() == UploadRequestStatus.OPEN && request.expiresAt().isAfter(LocalDateTime.now(clock)) || request.createdAt().plus(RETRY_COOLDOWN).isAfter(LocalDateTime.now(clock)); }
    private DocumentChaseResult result(Profile profile, String project, String type, long requestId, NotificationChannel channel, ChaseStatus status, String detail) { return new DocumentChaseResult(profile.id(), profile.name(), project, type, requestId, channel, status, detail); }
    private boolean same(String left, String right) { return left != null && right != null && left.trim().equalsIgnoreCase(right.trim()); }
    private String safeMessage(RuntimeException ex) { return ex.getMessage() == null || ex.getMessage().isBlank() ? "Unknown error." : ex.getMessage(); }
    private String safeMessage(DeliveryResult result) { return result.errorMessage() == null || result.errorMessage().isBlank() ? "Provider rejected the message." : result.errorMessage(); }
}
