package com.vedioai.server.auth.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class TestAccountClient {

    private static final Pattern JSON_STRING = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SESSION_COOKIE = Pattern.compile(
            "VedioAI-Session-Dev=([^;]+)");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String base;
    private String cookie;
    private String csrf;

    public TestAccountClient(int port) {
        this.base = "http://127.0.0.1:" + port;
    }

    public Response login(String email, String password) throws Exception {
        refreshCsrf();
        Response response = request(
                "POST",
                "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}",
                true);
        if (response.status() == 200) {
            refreshCsrf();
        }
        return response;
    }

    public void refreshCsrf() throws Exception {
        Response response = request("GET", "/api/auth/csrf", null, false);
        if (response.status() != 200) {
            throw new AssertionError("csrf failed: " + response.status() + " " + response.body());
        }
        csrf = jsonString(response.body(), "token");
    }

    public Response me() throws Exception {
        return request("GET", "/api/auth/me", null, false);
    }

    public Response get(String path) throws Exception {
        return request("GET", path, null, false);
    }

    public Response patch(String path, String body) throws Exception {
        return request("PATCH", path, body, true);
    }

    public Response post(String path, String body) throws Exception {
        return request("POST", path, body, true);
    }

    public Response put(String path, String body) throws Exception {
        return request("PUT", path, body, true);
    }

    public Response delete(String path) throws Exception {
        return request("DELETE", path, null, true);
    }

    public String cookie() {
        return cookie;
    }

    public String base() {
        return base;
    }

    public Response request(String method, String path, String body, boolean withCsrf) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15));

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            builder.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (withCsrf && csrf != null) {
            builder.header("X-CSRF-TOKEN", csrf);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        storeSessionCookie(response.headers().allValues("set-cookie"));
        return new Response(response.statusCode(), response.body(),
                response.headers().allValues("set-cookie"));
    }

    private void storeSessionCookie(List<String> setCookies) {
        for (String value : setCookies) {
            Matcher matcher = SESSION_COOKIE.matcher(value);
            if (matcher.find()) {
                cookie = "VedioAI-Session-Dev=" + matcher.group(1);
            }
        }
    }

    public static String jsonString(String body, String key) {
        Matcher matcher = Pattern.compile(String.format(JSON_STRING.pattern(), key))
                .matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static long jsonLong(String body, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)")
                .matcher(body == null ? "" : body);
        if (!matcher.find()) {
            return -1;
        }
        return Long.parseLong(matcher.group(1));
    }

    public record Response(int status, String body, List<String> setCookies) {
    }
}
