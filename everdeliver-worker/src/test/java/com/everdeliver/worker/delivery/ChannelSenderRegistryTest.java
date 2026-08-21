package com.everdeliver.worker.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.PermanentDeliveryException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelSenderRegistryTest {

    @Test
    void routesByChannelAndFallsBackToEmailRecipient() {
        ChannelSender email = mock(ChannelSender.class);
        when(email.channel()).thenReturn(Channel.EMAIL);
        ChannelSender sms = mock(ChannelSender.class);
        when(sms.channel()).thenReturn(Channel.SMS);

        ChannelSenderRegistry registry = new ChannelSenderRegistry(List.of(email, sms));

        assertThat(registry.require(Channel.EMAIL)).isSameAs(email);
        assertThat(registry.require(null)).isSameAs(email);
        assertThat(registry.require(Channel.SMS)).isSameAs(sms);
        assertThatThrownBy(() -> registry.require(Channel.SLACK))
                .isInstanceOf(PermanentDeliveryException.class);

        NotificationRequest request = new NotificationRequest();
        request.setEmail("legacy@example.com");
        assertThat(ChannelSenderRegistry.destination(request)).isEqualTo("legacy@example.com");
    }
}
