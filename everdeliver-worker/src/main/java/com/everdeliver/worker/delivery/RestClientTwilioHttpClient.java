package com.everdeliver.worker.delivery;

import com.twilio.http.HttpClient;
import com.twilio.http.HttpMethod;
import com.twilio.http.Request;
import com.twilio.http.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Twilio {@link HttpClient} that uses Spring {@link RestClient} so connect/read timeouts and a
 * custom base URL (WireMock) work. {@code TwilioRestClient.Builder} has no {@code baseUrl} setter.
 */
final class RestClientTwilioHttpClient extends HttpClient {

    private final RestClient restClient;
    private final String baseUrl;

    RestClientTwilioHttpClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.twilio.com" : trimSlash(baseUrl);
    }

    @Override
    public Response makeRequest(Request request) {
        URI uri = rewrite(URI.create(request.getUrl()));
        try {
            if (request.getMethod() == HttpMethod.GET) {
                var entity = restClient.get()
                        .uri(uri)
                        .headers(headers -> applyAuth(headers, request))
                        .retrieve()
                        .toEntity(String.class);
                return new Response(entity.getBody(), entity.getStatusCode().value());
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            for (Map.Entry<String, List<String>> entry : request.getPostParams().entrySet()) {
                form.put(entry.getKey(), entry.getValue());
            }
            var entity = restClient.method(org.springframework.http.HttpMethod.valueOf(request.getMethod().name()))
                    .uri(uri)
                    .headers(headers -> applyAuth(headers, request))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toEntity(String.class);
            return new Response(entity.getBody(), entity.getStatusCode().value());
        } catch (RestClientResponseException ex) {
            return new Response(ex.getResponseBodyAsString(), ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw ex;
        }
    }

    private URI rewrite(URI original) {
        URI base = URI.create(baseUrl);
        return UriComponentsBuilder.fromUri(base)
                .replacePath(original.getRawPath())
                .replaceQuery(original.getRawQuery())
                .build(true)
                .toUri();
    }

    private static void applyAuth(org.springframework.http.HttpHeaders headers, Request request) {
        String user = request.getUsername();
        String password = request.getPassword();
        if (user != null && password != null) {
            headers.setBasicAuth(user, password, StandardCharsets.UTF_8);
        }
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
