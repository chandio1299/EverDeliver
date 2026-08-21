package com.everdeliver.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "everdeliver.providers")
public class ProviderProperties {

    private SendGrid sendgrid = new SendGrid();
    private Twilio twilio = new Twilio();
    private Http http = new Http();

    @Getter
    @Setter
    public static class SendGrid {
        private String apiKey = "";
        private String fromEmail = "";
        private String baseUrl = "https://api.sendgrid.com";

        public boolean isConfigured() {
            return notBlank(apiKey);
        }
    }

    @Getter
    @Setter
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        private String smsFrom = "";
        private String whatsappFrom = "";
        private String baseUrl = "https://api.twilio.com";

        public boolean isConfigured() {
            return notBlank(accountSid) && notBlank(authToken);
        }
    }

    @Getter
    @Setter
    public static class Http {
        private int connectTimeoutSeconds = 5;
        private int readTimeoutSeconds = 15;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
