package com.everdeliver.worker.delivery;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.HttpStatusMapper;
import com.everdeliver.worker.Redactor;
import com.everdeliver.worker.RetryableDeliveryException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class SlackSender implements ChannelSender {

    private final RestClient deliveryRestClient;

    @Override
    public Channel channel() {
        return Channel.SLACK;
    }

    @Override
    public DeliveryResult send(NotificationRequest request) {
        String url = ChannelSenderRegistry.destination(request);
        String text = slackText(request.getSubject(), request.getMessage());
        post(url, Map.of("text", text));
        return DeliveryResult.none();
    }

    private void post(String url, Map<String, String> payload) {
        try {
            deliveryRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            throw HttpStatusMapper.toDeliveryException(status, "from slack", ex);
        } catch (ResourceAccessException ex) {
            throw new RetryableDeliveryException(Redactor.redact(ex.getMessage()), ex);
        }
    }

    static String slackText(String subject, String message) {
        String body = message == null ? "" : message;
        if (subject == null || subject.isBlank()) {
            return body;
        }
        return subject + "\n" + body;
    }
}
