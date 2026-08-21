package com.everdeliver.worker.delivery;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;

public interface ChannelSender {

    Channel channel();

    DeliveryResult send(NotificationRequest request);
}
