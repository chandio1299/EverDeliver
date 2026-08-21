package com.everdeliver.worker;

public final class HttpStatusMapper {

    private HttpStatusMapper() {}

    public static RuntimeException toDeliveryException(int status, String message, Throwable cause) {
        String safe = Redactor.redact(message);
        String prefixed = "HTTP " + status + ": " + safe;
        if (isRetryable(status)) {
            return new RetryableDeliveryException(prefixed, cause);
        }
        return new PermanentDeliveryException(prefixed, cause);
    }

    public static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }
}
