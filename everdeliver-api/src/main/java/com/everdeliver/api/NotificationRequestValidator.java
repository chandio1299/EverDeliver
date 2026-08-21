package com.everdeliver.api;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class NotificationRequestValidator {

    static final int MAX_RECIPIENT_LENGTH = 512;
    static final int MAX_SUBJECT_LENGTH = 1024;
    static final int MAX_MESSAGE_LENGTH = 128 * 1024;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private NotificationRequestValidator() {}

    static ResolvedNotification validate(NotificationRequest request) {
        return validate(request, false);
    }

    static ResolvedNotification validate(NotificationRequest request, boolean blockPrivateHosts) {
        if (request == null) {
            throw badRequest("request is required");
        }

        Channel channel;
        try {
            channel = request.resolvedChannel();
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }

        String message = requireText(request.getMessage(), "message is required");
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw badRequest("message exceeds " + MAX_MESSAGE_LENGTH + " characters");
        }

        String recipient = switch (channel) {
            case EMAIL -> requireEmail(firstNonBlank(request.getEmail(), request.getRecipient()));
            case SMS, WHATSAPP -> requireE164(firstNonBlank(request.getPhone(), request.getRecipient()));
            case SLACK -> requireHttpUrl(
                    firstNonBlank(request.getSlackWebhookUrl(), request.getRecipient()),
                    "slackWebhookUrl",
                    blockPrivateHosts);
            case WEBHOOK -> requireHttpUrl(
                    firstNonBlank(request.getWebhookUrl(), request.getRecipient()),
                    "webhookUrl",
                    blockPrivateHosts);
        };

        String subject = request.getSubject() == null ? null : request.getSubject().trim();
        if (subject != null && subject.isEmpty()) {
            subject = null;
        }
        if (subject != null && subject.length() > MAX_SUBJECT_LENGTH) {
            throw badRequest("subject exceeds " + MAX_SUBJECT_LENGTH + " characters");
        }

        return new ResolvedNotification(channel, recipient, subject, message);
    }

    private static String requireEmail(String value) {
        String email = requireText(value, "email is required");
        if (!EMAIL.matcher(email).matches()) {
            throw badRequest("email is invalid");
        }
        return boundRecipient(email);
    }

    private static String requireE164(String value) {
        String phone = requireText(value, "phone is required");
        if (!E164.matcher(phone).matches()) {
            throw badRequest("phone must be E.164 (e.g. +15551234567)");
        }
        return boundRecipient(phone);
    }

    private static String requireHttpUrl(String value, String field, boolean blockPrivateHosts) {
        String url = requireText(value, field + " is required");
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw badRequest(field + " is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw badRequest(field + " must be an http or https URL");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw badRequest(field + " must include a host");
        }
        if (blockPrivateHosts && isPrivateOrLocalHost(uri.getHost())) {
            throw badRequest(field + " must not target a private or local host");
        }
        return boundRecipient(url);
    }

    static boolean isPrivateOrLocalHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            throw badRequest("host could not be resolved");
        }
    }

    private static String boundRecipient(String value) {
        if (value.length() > MAX_RECIPIENT_LENGTH) {
            throw badRequest("recipient exceeds " + MAX_RECIPIENT_LENGTH + " characters");
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    record ResolvedNotification(Channel channel, String recipient, String subject, String message) {}
}
