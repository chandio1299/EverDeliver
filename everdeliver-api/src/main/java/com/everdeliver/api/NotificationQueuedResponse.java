package com.everdeliver.api;

import com.everdeliver.persistence.NotificationStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationQueuedResponse {
    private UUID id;
    private NotificationStatus status;
}
