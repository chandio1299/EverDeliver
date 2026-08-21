package com.everdeliver.worker;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.RestClientResponseException;

class DeliveryExceptionClassifierTest {

    private final DeliveryExceptionClassifier classifier = new DeliveryExceptionClassifier();

    @Test
    void connectionFailureIsRetryable() {
        RuntimeException classified = classifier.classify(new MailSendException("send failed", new ConnectException("Connection refused")));

        assertThat(classified).isInstanceOf(RetryableDeliveryException.class);
        assertThat(classified.getMessage()).contains("Connection refused");
    }

    @Test
    void invalidAddressIsPermanent() {
        RuntimeException classified = classifier.classify(new AddressException("Invalid Address"));

        assertThat(classified).isInstanceOf(PermanentDeliveryException.class);
    }

    @Test
    void sendFailedExceptionIsPermanent() {
        RuntimeException classified = classifier.classify(new SendFailedException("Invalid Addresses"));

        assertThat(classified).isInstanceOf(PermanentDeliveryException.class);
    }

    @Test
    void unknownFailureDefaultsToRetryable() {
        RuntimeException classified = classifier.classify(new IllegalStateException("boom"));

        assertThat(classified).isInstanceOf(RetryableDeliveryException.class);
        assertThat(classified.getMessage()).isEqualTo("boom");
    }

    @Test
    void http400IsPermanent() {
        RestClientResponseException ex = new RestClientResponseException(
                "bad request", HttpStatusCode.valueOf(400), "Bad Request", null, null, null);
        RuntimeException classified = classifier.classify(ex);
        assertThat(classified).isInstanceOf(PermanentDeliveryException.class);
        assertThat(classified.getMessage()).contains("HTTP 400");
    }

    @Test
    void http503IsRetryable() {
        RestClientResponseException ex = new RestClientResponseException(
                "unavailable", HttpStatusCode.valueOf(503), "Service Unavailable", null, null, null);
        RuntimeException classified = classifier.classify(ex);
        assertThat(classified).isInstanceOf(RetryableDeliveryException.class);
    }
}
