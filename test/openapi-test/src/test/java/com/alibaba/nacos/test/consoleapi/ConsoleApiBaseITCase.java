/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.consoleapi;

import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for console OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class ConsoleApiBaseITCase extends OpenApiBaseITCase {

    protected static final String DEFAULT_NAMESPACE = "public";

    protected static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    protected static final String DEFAULT_CLUSTER = "DEFAULT";

    protected static final String NACOS_CONSOLE_PORT = System.getProperty("nacos.console.port", "8080");

    protected static final String CONSOLE_BASE_URL = "http://" + NACOS_HOST + ":" + NACOS_CONSOLE_PORT;

    protected static final String CONSOLE_BASE_PATH = "/v3/console";

    @Override
    protected String baseUrl() {
        return CONSOLE_BASE_URL;
    }

    protected String randomConsoleName(String scenario) {
        return "openapi_it_console_" + scenario + "_" + UUID.randomUUID();
    }

    protected void assertPageShape(JsonNode page) {
        assertNotNull(page.get("pageNumber"), page.toString());
        assertNotNull(page.get("pagesAvailable"), page.toString());
        assertNotNull(page.get("totalCount"), page.toString());
        assertTrue(page.get("pageNumber").asInt() >= 1, page.toString());
        assertTrue(page.get("pagesAvailable").asInt() >= 0, page.toString());
        assertTrue(page.get("totalCount").asInt() >= 0, page.toString());
        assertTrue(page.get("pageItems").isArray(), page.toString());
    }

    protected void assertArrayContainsText(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.asText())) {
                return;
            }
        }
        throw new AssertionError("Expected array to contain " + expected + ", actual=" + array);
    }

    protected JsonNode findByTextField(JsonNode array, String fieldName, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.path(fieldName).asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }
}
