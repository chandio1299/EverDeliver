package com.everdeliver.worker.delivery;

import com.everdeliver.worker.HttpStatusMapper;
import com.everdeliver.worker.PermanentDeliveryException;
import com.everdeliver.worker.ProviderProperties;
import com.everdeliver.worker.Redactor;
import com.everdeliver.worker.RetryableDeliveryException;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TwilioGateway {

    private static final int PROVIDER_ID_MAX = 255;

    private final ProviderProperties properties;
    private final RestClient deliveryRestClient;
    private volatile TwilioRestClient client;

    public TwilioGateway(ProviderProperties properties, RestClient deliveryRestClient) {
        this.properties = properties;
        this.deliveryRestClient = deliveryRestClient;
    }

    public boolean isConfigured() {
        return properties.getTwilio().isConfigured();
    }

    public DeliveryResult sendSms(String to, String body) {
        if (!isConfigured()) {
            throw new PermanentDeliveryException("Twilio is not configured");
        }
        String from = properties.getTwilio().getSmsFrom();
        if (from == null || from.isBlank()) {
            throw new PermanentDeliveryException("TWILIO_SMS_FROM is required for SMS delivery");
        }
        return send(to, from.trim(), body);
    }

    public DeliveryResult sendWhatsApp(String to, String body) {
        if (!isConfigured()) {
            throw new PermanentDeliveryException("Twilio is not configured");
        }
        String from = properties.getTwilio().getWhatsappFrom();
        if (from == null || from.isBlank()) {
            throw new PermanentDeliveryException("TWILIO_WHATSAPP_FROM is required for WhatsApp delivery");
        }
        return send(whatsappAddress(to), whatsappAddress(from.trim()), body);
    }

    DeliveryResult send(String to, String from, String body) {
        if (!isConfigured()) {
            throw new PermanentDeliveryException("Twilio is not configured");
        }
        try {
            Message message = Message.creator(new PhoneNumber(to), new PhoneNumber(from), body)
                    .create(client());
            return new DeliveryResult(truncate(message.getSid()));
        } catch (PermanentDeliveryException | RetryableDeliveryException ex) {
            throw ex;
        } catch (ApiException ex) {
            int status = ex.getStatusCode();
            Integer twilioCode = ex.getCode();
            if (status <= 0 && twilioCode != null && twilioCode >= 20000 && twilioCode < 30000) {
                throw new PermanentDeliveryException(Redactor.redact(ex.getMessage()), ex);
            }
            if (status <= 0) {
                throw new RetryableDeliveryException(Redactor.redact(ex.getMessage()), ex);
            }
            throw HttpStatusMapper.toDeliveryException(status, ex.getMessage(), ex);
        }
    }

    TwilioRestClient client() {
        if (!isConfigured()) {
            throw new PermanentDeliveryException("Twilio is not configured");
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    ProviderProperties.Twilio twilio = properties.getTwilio();
                    TwilioRestClient.Builder builder =
                            new TwilioRestClient.Builder(twilio.getAccountSid(), twilio.getAuthToken())
                                    .httpClient(new RestClientTwilioHttpClient(
                                            deliveryRestClient, twilio.getBaseUrl()));
                    client = builder.build();
                }
            }
        }
        return client;
    }

    static String whatsappAddress(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("whatsapp:")) {
            return trimmed;
        }
        return "whatsapp:" + trimmed;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= PROVIDER_ID_MAX ? trimmed : trimmed.substring(0, PROVIDER_ID_MAX);
    }
}
