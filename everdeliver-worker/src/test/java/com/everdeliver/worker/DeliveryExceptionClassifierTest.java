package com.everdeliver.worker;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

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
}
