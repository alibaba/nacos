/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.nativeimage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A small HTTP client for the API of a Server: form requests with a bearer token, and the two
 * fields of an answer that the tests look at, the status and the code of the result wrapper.
 *
 * @author Nacos
 */
public final class NativeHttp {

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)");

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * The answer of a request.
     *
     * @param status HTTP status
     * @param body   the body
     */
    public record Response(int status, String body) {

        /**
         * The code of the result wrapper.
         *
         * @return the code, or {@code Integer.MIN_VALUE} if the body has none
         */
        public int resultCode() {
            Matcher matcher = CODE.matcher(body);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MIN_VALUE;
        }

        /**
         * Whether the request succeeded: HTTP 200 and the result code 0.
         *
         * @return true on success
         */
        public boolean ok() {
            return status == 200 && resultCode() == 0;
        }

        /**
         * The access token of a login answer.
         *
         * @return the token, or an empty string
         */
        public String accessToken() {
            Matcher matcher = ACCESS_TOKEN.matcher(body);
            return matcher.find() ? matcher.group(1) : "";
        }

        /**
         * Whether the body matches a regular expression somewhere.
         *
         * @param regex a regular expression
         * @return true if it is found
         */
        public boolean matches(String regex) {
            return Pattern.compile(regex).matcher(body).find();
        }

        /**
         * A short form for a message.
         *
         * @return the status, the result code and the start of the body
         */
        public String describe() {
            return "HTTP " + status + ", result code " + resultCode() + ", "
                    + body.substring(0, Math.min(body.length(), 400));
        }
    }

    /**
     * Sends a GET request.
     *
     * @param url   the URL with its query
     * @param token a bearer token, or null
     * @return the answer
     */
    public Response get(String url, String token) {
        return send(request(url, token).GET().build());
    }

    /**
     * Sends a form with the given method.
     *
     * @param method POST, PUT or DELETE
     * @param url    the URL
     * @param token  a bearer token, or null
     * @param form   the fields of the form
     * @return the answer
     */
    public Response form(String method, String url, String token, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(each -> URLEncoder.encode(each.getKey(), StandardCharsets.UTF_8) + '='
                        + URLEncoder.encode(each.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(request(url, token).header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpRequest.Builder request(String url, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(NativeTestSettings.scaled(Duration.ofSeconds(30)));
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            return new Response(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
