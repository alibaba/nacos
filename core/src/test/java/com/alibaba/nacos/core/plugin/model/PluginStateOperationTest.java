/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginStateOperation} unit test.
 *
 * @author WangzJi
 */
class PluginStateOperationTest {

    @Test
    void defaultConstructorTest() {
        PluginStateOperation operation = new PluginStateOperation();

        assertNull(operation.getType());
        assertNull(operation.getPluginId());
        assertNull(operation.getEnabled());
        assertNull(operation.getConfig());
    }

    @Test
    void setterGetterTest() {
        PluginStateOperation operation = new PluginStateOperation();

        operation.setType(PluginStateOperation.OperationType.CHANGE_STATE);
        operation.setPluginId("trace:test");
        operation.setEnabled(true);

        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        operation.setConfig(config);

        assertEquals(PluginStateOperation.OperationType.CHANGE_STATE, operation.getType());
        assertEquals("trace:test", operation.getPluginId());
        assertTrue(operation.getEnabled());
        assertEquals("value", operation.getConfig().get("key"));
    }

    @Test
    void builderChangeStateTest() {
        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.CHANGE_STATE)
                .pluginId("auth:nacos")
                .enabled(false)
                .build();

        assertNotNull(operation);
        assertEquals(PluginStateOperation.OperationType.CHANGE_STATE, operation.getType());
        assertEquals("auth:nacos", operation.getPluginId());
        assertEquals(false, operation.getEnabled());
    }

    @Test
    void builderUpdateConfigTest() {
        Map<String, String> config = new HashMap<>();
        config.put("endpoint", "http://localhost:8080");
        config.put("timeout", "5000");

        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.UPDATE_CONFIG)
                .pluginId("trace:otel")
                .config(config)
                .build();

        assertNotNull(operation);
        assertEquals(PluginStateOperation.OperationType.UPDATE_CONFIG, operation.getType());
        assertEquals("trace:otel", operation.getPluginId());
        assertEquals("http://localhost:8080", operation.getConfig().get("endpoint"));
        assertEquals("5000", operation.getConfig().get("timeout"));
    }

    @Test
    void operationTypeChangeStateTest() {
        assertEquals("CHANGE_STATE", PluginStateOperation.OperationType.CHANGE_STATE.name());
    }

    @Test
    void operationTypeUpdateConfigTest() {
        assertEquals("UPDATE_CONFIG", PluginStateOperation.OperationType.UPDATE_CONFIG.name());
    }

    @Test
    void builderPartialBuildTest() {
        // Builder should throw exception when type is not set
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            PluginStateOperation.builder()
                    .pluginId("trace:test")
                    .build();
        });
        assertEquals("type is required", exception.getMessage());
    }
}
