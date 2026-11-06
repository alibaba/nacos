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

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three registries that a Server can offer next to its own API on one more port: the MCP
 * registry, the registry of skills and the catalog of the AI resource discovery (ARD). Their
 * switches are structural choices of the Native build ({@code nacos.ai.mcp.registry.enabled},
 * {@code nacos.ai.skill.registry.enabled}, {@code nacos.ai.ard.enabled}), and the executable
 * refuses to start with other values, so the Server here repeats them.
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NativeAiRegistryITCase {

    private static final String REGISTRY = "http://127.0.0.1:9080";

    private final NativeHttp http = new NativeHttp();

    private NativeEnvironment environment;

    @BeforeAll
    void startEnvironment() {
        environment = NativeEnvironment.builder("ai-registry")
                .property("nacos.ai.mcp.registry.enabled", "true")
                .property("nacos.ai.skill.registry.enabled", "true")
                .property("nacos.ai.ard.enabled", "true").build().start();
    }

    @AfterAll
    void stopEnvironment() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void mcpRegistryListsServers() {
        NativeHttp.Response response = eventuallyOk(() -> http.get(REGISTRY + "/v0/servers", null));
        assertTrue(response.matches("\"servers\"\\s*:"), response.describe());
    }

    @Test
    void skillRegistryServesItsIndex() {
        NativeHttp.Response response = eventuallyOk(() -> http.get(REGISTRY
                + "/registry/public/.well-known/agent-skills/index.json", null));
        assertEquals(200, response.status(), response.describe());
    }

    @Test
    void ardServesItsCatalog() {
        NativeHttp.Response response = eventuallyOk(() -> http.get(REGISTRY
                + "/.well-known/ai-catalog.json", null));
        assertEquals(200, response.status(), response.describe());
    }

    /**
     * The registries run in a context of their own, which may start after the console is live.
     */
    private static NativeHttp.Response eventuallyOk(Supplier<NativeHttp.Response> request) {
        NativeHttp.Response response = request.get();
        for (int attempt = 0; attempt < 60 && response.status() != 200; attempt++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            response = request.get();
        }
        return response;
    }
}
