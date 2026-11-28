package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "vedioai.auth.mail.delivery-mode=local-catcher",
                "vedioai.auth.mail.dispatcher-enabled=false"
        })
class SecurityBoundaryIT {

    private static final Pattern JSON_STRING = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void publicEndpointsRemainPublic() throws Exception {
        HttpResponse<String> health = request("GET", "/api/health", null, null);
        assertEquals(200, health.statusCode());

        HttpResponse<String> policy = request("GET", "/api/auth/policy", null, null);
        assertEquals(200, policy.statusCode());
        assertTrue(policy.body().contains("\"registrationEnabled\":true"));
    }

    @Test
    void anonymousMeReturnsJson401() throws Exception {
        HttpResponse<String> response = request("GET", "/api/auth/me", null, null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"error\":{\"code\":\"UNAUTHORIZED\""));
        assertTrue(response.headers().firstValue("content-type").orElse("")
                .startsWith("application/json"));
    }

    @Test
    void stateChangingRequestWithoutCsrfReturnsJson403() throws Exception {
        HttpResponse<String> response = request("POST", "/api/auth/login", null, null);

        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("\"error\":{\"code\":\"CSRF_INVALID\""));
        assertTrue(response.headers().firstValue("content-type").orElse("")
                .startsWith("application/json"));
    }

    @Test
    void csrfEndpointSetsCookieAndTokenEnablesRequest() throws Exception {
        HttpResponse<String> csrf = request("GET", "/api/auth/csrf", null, null);
        assertEquals(200, csrf.statusCode());

        List<String> setCookies = csrf.headers().allValues("set-cookie");
        assertFalse(setCookies.isEmpty());
        String sessionCookie = setCookies.stream()
                .filter(value -> value.startsWith("VedioAI-Session-Dev="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("session cookie not found: " + setCookies));

        assertTrue(sessionCookie.contains("Path=/"));
        assertTrue(sessionCookie.contains("HttpOnly"));
        assertTrue(sessionCookie.contains("SameSite=Lax"));
        assertFalse(sessionCookie.contains("Secure"));

        String token = extractJsonString(csrf.body(), "token");
        assertNotNull(token);
        assertFalse(token.isBlank());

        HttpResponse<String> response = request(
                "POST",
                "/api/auth/logout",
                sessionCookie.split(";", 2)[0],
                token);

        // CSRF passed; logout is allowed for anonymous sessions and returns 204.
        assertEquals(204, response.statusCode());
    }

    private HttpResponse<String> request(String method, String path, String cookie, String csrf)
            throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (csrf != null) {
            builder.header("X-CSRF-TOKEN", csrf);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extractJsonString(String body, String key) {
        Matcher matcher = Pattern.compile(String.format(JSON_STRING.pattern(), key))
                .matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
