package com.everdeliver.worker.delivery;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.HttpStatusMapper;
import com.everdeliver.worker.PermanentDeliveryException;
import com.everdeliver.worker.ProviderProperties;
import com.everdeliver.worker.Redactor;
import com.everdeliver.worker.RetryableDeliveryException;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

    static final String MAILPIT_FROM = "noreply@everdeliver.com";
    private static final int PROVIDER_ID_MAX = 255;

    private final ProviderProperties providerProperties;
    private final JavaMailSender mailSender;
    private final RestClient deliveryRestClient;

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public DeliveryResult send(NotificationRequest request) {
        String to = ChannelSenderRegistry.destination(request);
        String subject = request.getSubject() == null ? "" : request.getSubject();
        String body = request.getMessage() == null ? "" : request.getMessage();

        if (providerProperties.getSendgrid().isConfigured()) {
            return sendViaSendGrid(to, subject, body);
        }
        return sendViaMail(to, subject, body);
    }

    private DeliveryResult sendViaSendGrid(String to, String subject, String body) {
        String fromEmail = providerProperties.getSendgrid().getFromEmail();
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new PermanentDeliveryException("SENDGRID_FROM_EMAIL is required when SENDGRID_API_KEY is set");
        }

        Mail mail = new Mail(new Email(fromEmail.trim()), subject, new Email(to), new Content("text/plain", body));
        String json;
        try {
            json = mail.build();
        } catch (Exception ex) {
            throw new PermanentDeliveryException("Failed to build SendGrid payload: " + Redactor.redact(ex.getMessage()), ex);
        }

        String url = trimSlash(providerProperties.getSendgrid().getBaseUrl()) + "/v3/mail/send";
        try {
            ResponseEntity<Void> response = deliveryRestClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + providerProperties.getSendgrid().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw HttpStatusMapper.toDeliveryException(status.value(), "SendGrid rejected the message", null);
            }
            String messageId = response.getHeaders().getFirst("X-Message-Id");
            return new DeliveryResult(truncate(messageId));
        } catch (PermanentDeliveryException | RetryableDeliveryException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw HttpStatusMapper.toDeliveryException(
                    ex.getStatusCode().value(), safeBody(ex), ex);
        } catch (ResourceAccessException ex) {
            throw new RetryableDeliveryException(Redactor.redact(ex.getMessage()), ex);
        }
    }

    private DeliveryResult sendViaMail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(MAILPIT_FROM);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        return DeliveryResult.none();
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.sendgrid.com";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String safeBody(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ex.getMessage();
        }
        return body;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= PROVIDER_ID_MAX ? trimmed : trimmed.substring(0, PROVIDER_ID_MAX);
    }
}
