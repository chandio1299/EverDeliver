package com.everdeliver.worker;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "everdeliver.delivery")
public class DeliveryProperties {

    /**
     * When true, every delivery attempt throws a retryable failure (acceptance testing).
     */
    private boolean simulateFailure = false;

    /**
     * When true, every delivery attempt throws a permanent failure (straight to DLQ).
     */
    private boolean simulatePermanentFailure = false;

    /**
     * PROCESSING rows older than this are requeued to QUEUED and republished.
     */
    private Duration processingTimeout = Duration.ofMinutes(5);
}
