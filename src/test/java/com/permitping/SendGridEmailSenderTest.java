package com.permitping;

import com.permitping.application.DeliveryResult;
import com.permitping.application.OutgoingMessage;
import com.permitping.infrastructure.SendGridEmailSender;
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
}
