package com.permitping.infrastructure;

import com.permitping.application.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Sends email through SendGrid when explicitly configured with environment variables. */
public final class SendGridEmailSender implements EmailSender {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final String apiKey;
    private final String from;
    private final String unsubscribeGroupId;
    private final HttpClient client;
    public SendGridEmailSender() { this(System.getenv("PERMITPING_SENDGRID_API_KEY"), System.getenv("PERMITPING_FROM_EMAIL"), System.getenv("PERMITPING_SENDGRID_UNSUBSCRIBE_GROUP_ID")); }
    public SendGridEmailSender(String apiKey, String from) { this(apiKey, from, System.getenv("PERMITPING_SENDGRID_UNSUBSCRIBE_GROUP_ID")); }
    public SendGridEmailSender(String apiKey, String from, String unsubscribeGroupId) { this(apiKey, from, unsubscribeGroupId, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()); }
    SendGridEmailSender(String apiKey, String from, HttpClient client) { this(apiKey, from, System.getenv("PERMITPING_SENDGRID_UNSUBSCRIBE_GROUP_ID"), client); }
    SendGridEmailSender(String apiKey, String from, String unsubscribeGroupId, HttpClient client) { this.apiKey=apiKey; this.from=from; this.unsubscribeGroupId=unsubscribeGroupId; this.client=client; }
    @Override public DeliveryResult send(OutgoingMessage message) {
        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank()) return DeliveryResult.failed("Email delivery is not configured. Set PERMITPING_SENDGRID_API_KEY and PERMITPING_FROM_EMAIL.");
        if (message == null || message.recipient() == null || message.recipient().isBlank()) return DeliveryResult.failed("Recipient email is missing.");
        if (unsubscribeGroupId == null || unsubscribeGroupId.isBlank()) return DeliveryResult.failed("Email unsubscribe is not configured. Set PERMITPING_SENDGRID_UNSUBSCRIBE_GROUP_ID to a SendGrid unsubscribe group ID.");
        int groupId;
        try { groupId = Integer.parseInt(unsubscribeGroupId.trim()); } catch (NumberFormatException ex) { return DeliveryResult.failed("SendGrid unsubscribe group ID is invalid."); }
        if (groupId <= 0) return DeliveryResult.failed("SendGrid unsubscribe group ID is invalid.");
        String content = (message.body() == null ? "" : message.body()) + "\n\nTo stop receiving these reminders, unsubscribe here: <%asm_group_unsubscribe_url%>";
        String body = "{\"personalizations\":[{\"to\":[{\"email\":\""+json(message.recipient())+"\"}]}],\"from\":{\"email\":\""+json(from)+"\"},\"subject\":\""+json(message.subject())+"\",\"content\":[{\"type\":\"text/plain\",\"value\":\""+json(content)+"\"}],\"asm\":{\"group_id\":"+groupId+"}}";
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.sendgrid.com/v3/mail/send")).timeout(REQUEST_TIMEOUT).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            return response.statusCode()>=200&&response.statusCode()<300 ? DeliveryResult.sent(response.headers().firstValue("X-Message-Id").orElse("sendgrid")) : DeliveryResult.failed("Email provider returned HTTP "+response.statusCode());
        } catch (Exception ex) { return DeliveryResult.failed("Email delivery failed: "+ex.getMessage()); }
    }
    private String json(String value) { return (value==null?"":value).replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n"); }
}
