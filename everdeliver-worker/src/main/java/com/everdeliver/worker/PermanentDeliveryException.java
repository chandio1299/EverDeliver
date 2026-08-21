package com.everdeliver.worker;

/**
 * Permanent delivery failure — excluded from retries and sent straight to the DLQ.
 */
public class PermanentDeliveryException extends RuntimeException {

    public PermanentDeliveryException(String message) {
        super(message);
    }

    public PermanentDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
