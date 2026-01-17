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

package com.alibaba.nacos.core.plugin.storage;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FilePluginStatePersistenceImpl} unit test.
 *
 * @author WangzJi
 */
class FilePluginStatePersistenceImplTest {

    @TempDir
    Path tempDir;

    private FilePluginStatePersistenceImpl persistence;

    private Path pluginDataDir;

    @BeforeAll
    static void setUpAll() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }

    @BeforeEach
    void setUp() throws IOException {
        // Use EnvUtil.setNacosHomePath to set the static cached path directly
        // This ensures proper isolation in CI parallel test environments
        EnvUtil.setNacosHomePath(tempDir.toString());
        pluginDataDir = Paths.get(tempDir.toString(), "data", "plugin");
        // Clean up persistence files before each test to ensure isolation
        if (Files.exists(pluginDataDir)) {
            Files.walk(pluginDataDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
        persistence = new FilePluginStatePersistenceImpl();
    }

    @AfterEach
    void tearDown() {
        // Clear the static cached path to avoid affecting other tests
        EnvUtil.setNacosHomePath(null);
    }

    @Test
    void saveStateTest() {
        persistence.saveState("trace:test", true);

        Map<String, Boolean> states = persistence.loadAllStates();
        assertNotNull(states);
        assertTrue(states.containsKey("trace:test"));
        assertTrue(states.get("trace:test"));
    }

    @Test
    void saveStateMultiplePluginsTest() {
        persistence.saveState("trace:test1", true);
        persistence.saveState("auth:test2", false);

        Map<String, Boolean> states = persistence.loadAllStates();
        assertNotNull(states);
        assertEquals(2, states.size());
        assertTrue(states.get("trace:test1"));
        assertFalse(states.get("auth:test2"));
    }

    @Test
    void saveStateOverwriteTest() {
        persistence.saveState("trace:test", true);
        persistence.saveState("trace:test", false);

        Map<String, Boolean> states = persistence.loadAllStates();
        assertFalse(states.get("trace:test"));
    }

    @Test
    void saveConfigTest() {
        Map<String, String> config = new HashMap<>();
        config.put("key1", "value1");
        config.put("key2", "value2");

        persistence.saveConfig("trace:test", config);

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();
        assertNotNull(configs);
        assertTrue(configs.containsKey("trace:test"));
        assertEquals("value1", configs.get("trace:test").get("key1"));
        assertEquals("value2", configs.get("trace:test").get("key2"));
    }

    @Test
    void saveConfigMultiplePluginsTest() {
        Map<String, String> config1 = new HashMap<>();
        config1.put("key1", "value1");

        Map<String, String> config2 = new HashMap<>();
        config2.put("key2", "value2");

        persistence.saveConfig("trace:test1", config1);
        persistence.saveConfig("auth:test2", config2);

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();
        assertNotNull(configs);
        assertEquals(2, configs.size());
        assertEquals("value1", configs.get("trace:test1").get("key1"));
        assertEquals("value2", configs.get("auth:test2").get("key2"));
    }

    @Test
    void loadAllStatesFileNotExistsTest() {
        Map<String, Boolean> states = persistence.loadAllStates();

        assertNotNull(states);
        assertEquals(0, states.size());
    }

    @Test
    void loadAllStatesEmptyFileTest() throws IOException {
        Path stateFile = pluginDataDir.resolve("plugin-states.json");
        Files.createDirectories(pluginDataDir);
        Files.write(stateFile, "".getBytes(StandardCharsets.UTF_8));

        Map<String, Boolean> states = persistence.loadAllStates();

        assertNotNull(states);
        assertEquals(0, states.size());
    }

    @Test
    void loadAllStatesCorruptedFileTest() throws IOException {
        Path stateFile = pluginDataDir.resolve("plugin-states.json");
        Files.createDirectories(pluginDataDir);
        Files.write(stateFile, "not a valid json".getBytes(StandardCharsets.UTF_8));

        Map<String, Boolean> states = persistence.loadAllStates();

        assertNotNull(states);
        assertEquals(0, states.size());
    }

    @Test
    void loadAllConfigsFileNotExistsTest() {
        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();

        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    void loadAllConfigsEmptyFileTest() throws IOException {
        Path configFile = pluginDataDir.resolve("plugin-configs.json");
        Files.createDirectories(pluginDataDir);
        Files.write(configFile, "".getBytes(StandardCharsets.UTF_8));

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();

        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    void loadAllConfigsCorruptedFileTest() throws IOException {
        Path configFile = pluginDataDir.resolve("plugin-configs.json");
        Files.createDirectories(pluginDataDir);
        Files.write(configFile, "not a valid json".getBytes(StandardCharsets.UTF_8));

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();

        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    void deleteStateTest() {
        persistence.saveState("trace:test1", true);
        persistence.saveState("trace:test2", false);

        persistence.deleteState("trace:test1");

        Map<String, Boolean> states = persistence.loadAllStates();
        assertFalse(states.containsKey("trace:test1"));
        assertTrue(states.containsKey("trace:test2"));
    }

    @Test
    void deleteStateNonExistingPluginTest() {
        persistence.saveState("trace:test1", true);

        persistence.deleteState("nonexistent:plugin");

        Map<String, Boolean> states = persistence.loadAllStates();
        assertEquals(1, states.size());
        assertTrue(states.containsKey("trace:test1"));
    }

    @Test
    void deleteConfigTest() {
        Map<String, String> config1 = new HashMap<>();
        config1.put("key1", "value1");

        Map<String, String> config2 = new HashMap<>();
        config2.put("key2", "value2");

        persistence.saveConfig("trace:test1", config1);
        persistence.saveConfig("trace:test2", config2);

        persistence.deleteConfig("trace:test1");

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();
        assertFalse(configs.containsKey("trace:test1"));
        assertTrue(configs.containsKey("trace:test2"));
    }

    @Test
    void deleteConfigNonExistingPluginTest() {
        Map<String, String> config1 = new HashMap<>();
        config1.put("key1", "value1");

        persistence.saveConfig("trace:test1", config1);

        persistence.deleteConfig("nonexistent:plugin");

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();
        assertEquals(1, configs.size());
        assertTrue(configs.containsKey("trace:test1"));
    }

    @Test
    void ensureDataDirExistsTest() {
        File dataDir = new File(pluginDataDir.toString());

        assertTrue(dataDir.exists());
        assertTrue(dataDir.isDirectory());
    }

    @Test
    void concurrentSaveStateTest() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                persistence.saveState("plugin1", i % 2 == 0);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                persistence.saveState("plugin2", i % 2 == 0);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        Map<String, Boolean> states = persistence.loadAllStates();
        assertEquals(2, states.size());
        assertTrue(states.containsKey("plugin1"));
        assertTrue(states.containsKey("plugin2"));
    }

    @Test
    void concurrentSaveConfigTest() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                Map<String, String> config = new HashMap<>();
                config.put("key", "value" + i);
                persistence.saveConfig("plugin1", config);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                Map<String, String> config = new HashMap<>();
                config.put("key", "value" + i);
                persistence.saveConfig("plugin2", config);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("plugin1"));
        assertTrue(configs.containsKey("plugin2"));
    }
}
