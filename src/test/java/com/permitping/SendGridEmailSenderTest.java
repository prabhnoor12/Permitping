package com.permitping;

import com.permitping.application.DeliveryResult;
import com.permitping.application.OutgoingMessage;
import com.permitping.infrastructure.SendGridEmailSender;
import com.permitping.infrastructure.TwilioSmsSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SendGridEmailSenderTest {
    @Test void refusesToSendWhenProviderConfigurationIsMissing() {
        DeliveryResult result = new SendGridEmailSender("", "").send(new OutgoingMessage("crew@example.com", "Reminder", "Please renew"));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("not configured"));
    }

    @Test void refusesToSendWhenRecipientIsMissing() {
        DeliveryResult result = new SendGridEmailSender("test-key", "from@example.com").send(new OutgoingMessage("", "Reminder", "Please renew"));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Recipient"));
    }

    @Test void refusesToSendWithoutAnUnsubscribeGroup() {
        DeliveryResult result = new SendGridEmailSender("test-key", "from@example.com", "").send(new OutgoingMessage("crew@example.com", "Reminder", "Please renew"));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("unsubscribe"));
    }

    @Test void refusesToSendSmsWhenProviderConfigurationIsMissing() {
        DeliveryResult result = new TwilioSmsSender("", "", "").send(new OutgoingMessage("+15551234567", "Reminder", "Please renew"));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("SMS delivery is not configured"));
    }
}
