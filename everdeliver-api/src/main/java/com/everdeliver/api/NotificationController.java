package com.everdeliver.api;

import com.everdeliver.common.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    @PostMapping
    public String sendNotification(@RequestBody NotificationRequest request) {
        // We push the message to Kafka. Topic name is "notification-topic"
        kafkaTemplate.send("notification-topic", request);
        return "Notification queued successfully!";
    }
}
