package com.everdeliver.worker;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

/**
 * Best-effort SMTP classification for Phase 2. Unknown errors default to retryable.
 * Phase 3 SendGrid/Twilio HTTP: 4xx non-retryable, 5xx/timeout retryable.
 */
@Component
public class DeliveryExceptionClassifier {

    public RuntimeException classify(Throwable error) {
        String message = rootMessage(error);
        if (isPermanent(error)) {
            return new PermanentDeliveryException(message, error);
        }
        return new RetryableDeliveryException(message, error);
    }

    boolean isPermanent(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof AddressException || current instanceof SendFailedException) {
                return true;
            }
            if (current instanceof MailSendException mailSendException) {
                for (Exception failed : mailSendException.getFailedMessages().values()) {
                    if (isPermanent(failed)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static String rootMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message;
    }
}
