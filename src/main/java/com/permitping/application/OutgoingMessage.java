package com.permitping.application;

public record OutgoingMessage(String recipient, String subject, String body) { }
