package com.permitping.application;

@FunctionalInterface
public interface SmsSender {
    DeliveryResult send(OutgoingMessage message);
}
