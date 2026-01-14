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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginStateSnapshot} unit test.
 *
 * @author WangzJi
 */
class PluginStateSnapshotTest {

    @Test
    void defaultConstructorTest() {
        PluginStateSnapshot snapshot = new PluginStateSnapshot();

        assertNull(snapshot.getStates());
        assertNull(snapshot.getConfigs());
    }

    @Test
    void setStatesTest() {
        PluginStateSnapshot snapshot = new PluginStateSnapshot();

        Map<String, Boolean> states = new HashMap<>();
        states.put("trace:test1", true);
        states.put("auth:test2", false);

        snapshot.setStates(states);

        assertNotNull(snapshot.getStates());
        assertEquals(2, snapshot.getStates().size());
        assertTrue(snapshot.getStates().get("trace:test1"));
        assertFalse(snapshot.getStates().get("auth:test2"));
    }

    @Test
    void setConfigsTest() {
        PluginStateSnapshot snapshot = new PluginStateSnapshot();

        Map<String, String> config1 = new HashMap<>();
        config1.put("endpoint", "http://localhost:8080");

        Map<String, String> config2 = new HashMap<>();
        config2.put("timeout", "5000");

        Map<String, Map<String, String>> configs = new HashMap<>();
        configs.put("trace:otel", config1);
        configs.put("auth:custom", config2);

        snapshot.setConfigs(configs);

        assertNotNull(snapshot.getConfigs());
        assertEquals(2, snapshot.getConfigs().size());
        assertEquals("http://localhost:8080", snapshot.getConfigs().get("trace:otel").get("endpoint"));
        assertEquals("5000", snapshot.getConfigs().get("auth:custom").get("timeout"));
    }

    @Test
    void setStatesAndConfigsTest() {
        PluginStateSnapshot snapshot = new PluginStateSnapshot();

        Map<String, Boolean> states = new HashMap<>();
        states.put("trace:test", true);
        snapshot.setStates(states);

        Map<String, String> config = new HashMap<>();
        config.put("key", "value");

        Map<String, Map<String, String>> configs = new HashMap<>();
        configs.put("trace:test", config);
        snapshot.setConfigs(configs);

        assertEquals(1, snapshot.getStates().size());
        assertEquals(1, snapshot.getConfigs().size());
        assertTrue(snapshot.getStates().get("trace:test"));
        assertEquals("value", snapshot.getConfigs().get("trace:test").get("key"));
    }

    @Test
    void emptyStatesAndConfigsTest() {
        PluginStateSnapshot snapshot = new PluginStateSnapshot();

        snapshot.setStates(new HashMap<>());
        snapshot.setConfigs(new HashMap<>());

        assertNotNull(snapshot.getStates());
        assertNotNull(snapshot.getConfigs());
        assertEquals(0, snapshot.getStates().size());
        assertEquals(0, snapshot.getConfigs().size());
    }
}
