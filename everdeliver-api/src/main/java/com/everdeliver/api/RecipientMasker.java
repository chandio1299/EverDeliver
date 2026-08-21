package com.everdeliver.api;

import com.everdeliver.common.Channel;
import java.net.URI;

final class RecipientMasker {

    private RecipientMasker() {}

    static String forResponse(String channel, String recipient) {
        if (recipient == null || channel == null) {
            return recipient;
        }
        Channel parsed;
        try {
            parsed = Channel.fromJson(channel);
        } catch (IllegalArgumentException ex) {
            return recipient;
        }
        if (parsed != Channel.SLACK && parsed != Channel.WEBHOOK) {
            return recipient;
        }
        return maskUrl(recipient);
    }

    static String maskUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isBlank()) {
                return "***";
            }
            return scheme + "://" + host + "/***";
        } catch (IllegalArgumentException ex) {
            return "***";
        }
    }
}
