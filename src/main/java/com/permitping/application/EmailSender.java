package com.permitping.application;

public interface EmailSender {
    DeliveryResult send(OutgoingMessage message);
}
