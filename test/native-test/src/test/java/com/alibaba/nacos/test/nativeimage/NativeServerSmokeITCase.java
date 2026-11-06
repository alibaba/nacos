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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A minimal functional check of a freshly built Server: readiness, authentication, Config and
 * Naming over HTTP, the pages of the console and the gRPC port. It does not replace the OpenAPI,
 * Java SDK and Maintainer SDK suites, which are the acceptance gate (see bootstrap/README-AOT.md).
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NativeServerSmokeITCase {

    private static final String GROUP = "DEFAULT_GROUP";

    private final NativeHttp http = new NativeHttp();

    private NativeEnvironment environment;

    private String api;

    private String console;

    private String token;

    @BeforeAll
    void startEnvironment() {
        environment = NativeEnvironment.create("smoke").start();
        api = environment.server().apiUrl() + "/nacos/v3";
        console = environment.server().consoleUrl();
        token = environment.identities().adminToken();
    }

    @AfterAll
    void stopEnvironment() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void consoleIsLiveAndReady() {
        assertEquals(200, http.get(console + "/v3/console/health/liveness", null).status());
        assertEquals(200, http.get(console + "/v3/console/health/readiness", null).status());
    }

    @Test
    void configCanBePublishedReadAndDeleted() {
        String dataId = "native-smoke-" + UUID.randomUUID();
        String query = "dataId=" + dataId + "&groupName=" + GROUP;
        NativeHttp.Response published = http.form("POST", api + "/admin/cs/config", token,
                Map.of("dataId", dataId, "groupName", GROUP, "content", "native=true", "type",
                        "properties"));
        assertTrue(published.ok(), published.describe());

        NativeHttp.Response read = http.get(api + "/admin/cs/config?" + query, token);
        assertTrue(read.ok(), read.describe());
        assertTrue(read.body().contains("native=true"), read.describe());

        // The client API reads the dumped local copy, which lags the publish by a moment.
        NativeHttp.Response client = eventually(
                () -> http.get(api + "/client/cs/config?" + query, token), NativeHttp.Response::ok);
        assertTrue(client.ok(), client.describe());

        NativeHttp.Response list = http.get(console
                + "/v3/console/cs/config/list?pageNo=1&pageSize=10&namespaceId=public", token);
        assertTrue(list.ok(), list.describe());

        NativeHttp.Response deleted = http.form("DELETE", api + "/admin/cs/config?" + query, token,
                Map.of());
        assertTrue(deleted.ok(), deleted.describe());
    }

    @Test
    void instanceCanBeRegisteredListedAndDeregistered() {
        String service = "native-smoke-" + UUID.randomUUID();
        NativeHttp.Response registered = http.form("POST", api + "/client/ns/instance", token,
                Map.of("serviceName", service, "ip", "127.0.0.1", "port", "18080"));
        assertTrue(registered.ok(), registered.describe());

        // The list is served from the cache of the service, which an event updates a moment after the
        // registration.
        NativeHttp.Response listed = eventually(() -> http.get(api
                + "/client/ns/instance/list?serviceName=" + service, token),
                response -> response.ok() && response.body().contains("18080"));
        assertTrue(listed.ok(), listed.describe());
        assertTrue(listed.body().contains("18080"), listed.describe());

        NativeHttp.Response services = http.get(console
                + "/v3/console/ns/service/list?pageNo=1&pageSize=10&namespaceId=public", token);
        assertTrue(services.ok(), services.describe());

        NativeHttp.Response deregistered = http.form("DELETE", api + "/client/ns/instance?"
                + "serviceName=" + service + "&ip=127.0.0.1&port=18080", token, Map.of());
        assertTrue(deregistered.ok(), deregistered.describe());
    }

    @Test
    void consoleAnswers() {
        NativeHttp.Response namespaces = http.get(console + "/v3/console/core/namespace/list",
                token);
        assertTrue(namespaces.ok(), namespaces.describe());
        // The state endpoint returns a plain property map, not the Result wrapper.
        assertEquals(200, http.get(console + "/v3/console/server/state", token).status());
        assertEquals(200, http.get(console + "/next/", null).status());
        assertEquals(200, http.get(console + "/legacy/index.html", null).status());
    }

    @Test
    void grpcPortAcceptsConnections() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", NativeServer.API_PORT + 1000), 3000);
        }
    }

    private static NativeHttp.Response eventually(Supplier<NativeHttp.Response> request,
            Predicate<NativeHttp.Response> done) {
        NativeHttp.Response response = request.get();
        for (int attempt = 0; attempt < 20 && !done.test(response); attempt++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            response = request.get();
        }
        return response;
    }
}
