package com.everdeliver.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRequest {
    private UUID id;
    private Channel channel;
    private String email;
    private String recipient;
    private String phone;
    private String slackWebhookUrl;
    private String webhookUrl;
    private String subject;
    private String message;

    public Channel resolvedChannel() {
        return Channel.orEmail(channel);
    }
}
