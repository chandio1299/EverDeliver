package com.everdeliver.worker.delivery;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.PermanentDeliveryException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ChannelSenderRegistry {

    private final Map<Channel, ChannelSender> senders;

    public ChannelSenderRegistry(List<ChannelSender> senders) {
        Map<Channel, ChannelSender> mapped = new EnumMap<>(Channel.class);
        for (ChannelSender sender : senders) {
            ChannelSender previous = mapped.put(sender.channel(), sender);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ChannelSender for " + sender.channel());
            }
        }
        this.senders = Map.copyOf(mapped);
    }

    public ChannelSender require(Channel channel) {
        Channel resolved = Channel.orEmail(channel);
        ChannelSender sender = senders.get(resolved);
        if (sender == null) {
            throw new PermanentDeliveryException("No sender configured for channel " + resolved.getValue());
        }
        return sender;
    }

    public static String destination(NotificationRequest request) {
        if (request.getRecipient() != null && !request.getRecipient().isBlank()) {
            return request.getRecipient().trim();
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return request.getEmail().trim();
        }
        throw new PermanentDeliveryException("Notification is missing a recipient");
    }
}
