package com.everdeliver.api;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "everdeliver.delivery")
public class ApiDeliveryProperties {

    /**
     * When true, reject slack/webhook URLs whose host resolves to private, loopback, or
     * link-local addresses. Default false so local Compose targets (e.g. echo-server) work.
     */
    private boolean blockPrivateHosts = false;
}
