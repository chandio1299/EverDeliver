package com.everdeliver.api;

import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public NotificationQueuedResponse sendNotification(@RequestBody NotificationRequest request) {
        return notificationService.enqueue(request);
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotification(@PathVariable UUID id) {
        return notificationService.getById(id);
    }

    @GetMapping
    public List<NotificationResponse> listNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) Integer limit) {
        return notificationService.list(status, since, limit);
    }
}
