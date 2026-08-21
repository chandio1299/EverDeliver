package com.everdeliver.api;

import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationResponse {
    UUID id;
    NotificationStatus status;
    String channel;
    String recipient;
    String subject;
    String body;
    String providerMessageId;
    int retryCount;
    String lastError;
    Instant createdAt;
    Instant updatedAt;
    Instant sentAt;
}
