package com.everdeliver.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationConsumerTest {

    @Test
    void retryTopicDetection() {
        assertThat(NotificationConsumer.isRetryTopic("notification-topic")).isFalse();
        assertThat(NotificationConsumer.isRetryTopic("notification-topic-dlq")).isFalse();
        assertThat(NotificationConsumer.isRetryTopic("notification-topic-retry-5000")).isTrue();
        assertThat(NotificationConsumer.isRetryTopic("notification-topic-retry-120000")).isTrue();
        assertThat(NotificationConsumer.isRetryTopic(null)).isFalse();
    }
}
