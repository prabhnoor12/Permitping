package com.permitping.infrastructure;

import com.permitping.application.DeliveryResult;
import com.permitping.application.OutgoingMessage;
import com.permitping.application.SmsSender;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sends SMS through Twilio when explicitly configured with environment variables. */
public final class TwilioSmsSender implements SmsSender {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern SID = Pattern.compile("\\\"sid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final HttpClient client;

    public TwilioSmsSender() {
        this(System.getenv("PERMITPING_TWILIO_ACCOUNT_SID"),
                System.getenv("PERMITPING_TWILIO_AUTH_TOKEN"),
                System.getenv("PERMITPING_TWILIO_FROM_NUMBER"));
    }

    public TwilioSmsSender(String accountSid, String authToken, String fromNumber) {
        this(accountSid, authToken, fromNumber, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    TwilioSmsSender(String accountSid, String authToken, String fromNumber, HttpClient client) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.client = client;
    }

    @Override public DeliveryResult send(OutgoingMessage message) {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank() || fromNumber == null || fromNumber.isBlank()) {
            return DeliveryResult.failed("SMS delivery is not configured. Set PERMITPING_TWILIO_ACCOUNT_SID, PERMITPING_TWILIO_AUTH_TOKEN, and PERMITPING_TWILIO_FROM_NUMBER.");
        }
        if (!accountSid.matches("[A-Za-z0-9]{2,64}")) return DeliveryResult.failed("Twilio account SID is invalid.");
        if (message == null || message.recipient() == null || message.recipient().isBlank()) return DeliveryResult.failed("Recipient phone number is missing.");
        if (message.body() == null || message.body().isBlank()) return DeliveryResult.failed("SMS message is empty.");
        String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        String form = form("To", message.recipient()) + "&" + form("From", fromNumber) + "&" + form("Body", message.body());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return DeliveryResult.failed("SMS provider returned HTTP " + response.statusCode());
            Matcher matcher = SID.matcher(response.body() == null ? "" : response.body());
            return DeliveryResult.sent(matcher.find() ? matcher.group(1) : "twilio");
        } catch (Exception ex) {
            return DeliveryResult.failed("SMS delivery failed: " + ex.getMessage());
        }
    }

    private String form(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
