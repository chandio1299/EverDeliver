package com.everdeliver.api;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "everdeliver.api")
public class ApiCorsProperties {

    /**
     * Browser origins allowed to call the API directly (Vite dev server). Compose nginx
     * same-origin proxy does not need CORS.
     */
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));
}
