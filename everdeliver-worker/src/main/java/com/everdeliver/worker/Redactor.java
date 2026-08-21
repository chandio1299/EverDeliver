package com.everdeliver.worker;

/**
 * Strips secrets and query strings from error messages before they are logged or persisted.
 */
public final class Redactor {

    private Redactor() {}

    public static String redact(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        String redacted = message.replaceAll("(https?://[^\\s?]+)(\\?[^\\s]*)", "$1?redacted");
        redacted = redacted.replaceAll("(?i)bearer\\s+\\S+", "Bearer ***");
        redacted = redacted.replaceAll("(?i)(authorization\\s*[:=]\\s*)\\S+", "$1***");
        redacted = redacted.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)\\S+", "$1***");
        redacted = redacted.replaceAll("(?i)(auth[_-]?token\\s*[:=]\\s*)\\S+", "$1***");
        return redacted;
    }
}
