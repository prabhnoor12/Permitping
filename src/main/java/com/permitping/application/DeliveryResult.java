package com.permitping.application;

public record DeliveryResult(boolean successful, String providerMessageId, String errorMessage) {
    public static DeliveryResult sent(String providerMessageId) { return new DeliveryResult(true, providerMessageId, ""); }
    public static DeliveryResult failed(String errorMessage) { return new DeliveryResult(false, "", errorMessage); }
}
