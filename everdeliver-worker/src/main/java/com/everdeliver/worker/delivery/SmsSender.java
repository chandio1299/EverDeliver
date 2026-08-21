package com.everdeliver.worker.delivery;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.PermanentDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsSender implements ChannelSender {

    private final TwilioGateway twilioGateway;

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public DeliveryResult send(NotificationRequest request) {
        if (!twilioGateway.isConfigured()) {
            throw new PermanentDeliveryException("Twilio is not configured");
        }
        String to = ChannelSenderRegistry.destination(request);
        String body = request.getMessage() == null ? "" : request.getMessage();
        return twilioGateway.sendSms(to, body);
    }
}
