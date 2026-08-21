package com.everdeliver.worker;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Classifies delivery failures. SMTP heuristics from Phase 2 are kept.
 * HTTP: 4xx (except 408/429) = permanent; 408/429/5xx/timeouts = retryable.
 */
@Component
public class DeliveryExceptionClassifier {

    public RuntimeException classify(Throwable error) {
        Integer httpStatus = extractHttpStatus(error);
        if (httpStatus != null) {
            return HttpStatusMapper.toDeliveryException(httpStatus, Redactor.redact(rootMessage(error)), error);
        }
        String message = Redactor.redact(rootMessage(error));
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

    static Integer extractHttpStatus(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException restEx) {
                return restEx.getStatusCode().value();
            }
            if (current instanceof com.twilio.exception.ApiException apiEx && apiEx.getStatusCode() > 0) {
                return apiEx.getStatusCode();
            }
        }
        return null;
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
