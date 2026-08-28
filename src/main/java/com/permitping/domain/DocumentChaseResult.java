package com.permitping.domain;

public record DocumentChaseResult(long profileId, String profileName, String project,
                                  String documentType, long uploadRequestId,
                                  NotificationChannel channel, ChaseStatus status,
                                  String detail) { }
