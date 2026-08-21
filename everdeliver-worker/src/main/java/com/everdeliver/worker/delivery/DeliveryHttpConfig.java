package com.everdeliver.worker.delivery;

import com.everdeliver.worker.ProviderProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DeliveryHttpConfig {

    @Bean
    RestClient deliveryRestClient(ProviderProperties properties) {
        Duration connect = Duration.ofSeconds(properties.getHttp().getConnectTimeoutSeconds());
        Duration read = Duration.ofSeconds(properties.getHttp().getReadTimeoutSeconds());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(connect)
                        .build());
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory).build();
    }
}
