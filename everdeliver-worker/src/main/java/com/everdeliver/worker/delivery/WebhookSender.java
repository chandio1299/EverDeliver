package com.everdeliver.worker.delivery;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.HttpStatusMapper;
import com.everdeliver.worker.Redactor;
import com.everdeliver.worker.RetryableDeliveryException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class WebhookSender implements ChannelSender {

    private final RestClient deliveryRestClient;

    @Override
    public Channel channel() {
        return Channel.WEBHOOK;
    }

    @Override
    public DeliveryResult send(NotificationRequest request) {
        String url = ChannelSenderRegistry.destination(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", request.getId());
        payload.put("channel", Channel.WEBHOOK.getValue());
        payload.put("recipient", url);
        payload.put("subject", request.getSubject());
        payload.put("message", request.getMessage());

        try {
            deliveryRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw HttpStatusMapper.toDeliveryException(ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        } catch (ResourceAccessException ex) {
            throw new RetryableDeliveryException(Redactor.redact(ex.getMessage()), ex);
        }
        return DeliveryResult.none();
    }
}
