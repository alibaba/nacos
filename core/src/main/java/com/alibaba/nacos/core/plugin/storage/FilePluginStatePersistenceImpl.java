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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * File-based implementation of plugin state persistence service.
 * Stores plugin states and configurations as JSON files in {NACOS_HOME}/data/plugin/.
 *
 * @author WangzJi
 * @since 3.2.0
 */
@Component
public class FilePluginStatePersistenceImpl implements PluginStatePersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePluginStatePersistenceImpl.class);

    private static final String PLUGIN_STATE_FILE = "plugin-states.json";

    private static final String PLUGIN_CONFIG_FILE = "plugin-configs.json";

    private static final String PLUGIN_DATA_DIR = "plugin";

    private static final TypeReference<Map<String, Boolean>> STATE_TYPE_REF = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Map<String, String>>> CONFIG_TYPE_REF = new TypeReference<>() {
    };

    private final String dataDir;

    private final Object stateLock = new Object();

    private final Object configLock = new Object();

    public FilePluginStatePersistenceImpl() {
        this.dataDir = EnvUtil.getNacosHome() + File.separator + "data" + File.separator + PLUGIN_DATA_DIR;
        ensureDataDirExists();
    }

    private void ensureDataDirExists() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                LOGGER.info("[FilePluginStatePersistenceImpl] Created plugin data directory: {}", dataDir);
            } else {
                throw new PluginPersistenceException("Failed to create plugin data directory: " + dataDir);
            }
        }
    }

    /**
     * Save plugin state.
     *
     * @param pluginId plugin ID
     * @param enabled whether the plugin is enabled
     */
    @Override
    public void saveState(String pluginId, boolean enabled) {
        synchronized (stateLock) {
            try {
                Map<String, Boolean> states = loadAllStates();
                states.put(pluginId, enabled);
                writeJsonToFile(PLUGIN_STATE_FILE, states);
                LOGGER.debug("[FilePluginStatePersistenceImpl] Saved plugin state: {}={}", pluginId, enabled);
            } catch (Exception e) {
                throw new PluginPersistenceException("Failed to save plugin state: " + pluginId, e);
            }
        }
    }

    /**
     * Save plugin configuration.
     *
     * @param pluginId plugin ID
     * @param config configuration key-value pairs
     */
    @Override
    public void saveConfig(String pluginId, Map<String, String> config) {
        synchronized (configLock) {
            try {
                Map<String, Map<String, String>> configs = loadAllConfigs();
                configs.put(pluginId, config);
                writeJsonToFile(PLUGIN_CONFIG_FILE, configs);
                LOGGER.debug("[FilePluginStatePersistenceImpl] Saved plugin config for {}", pluginId);
            } catch (Exception e) {
                throw new PluginPersistenceException("Failed to save plugin config: " + pluginId, e);
            }
        }
    }

    /**
     * Load all plugin states.
     *
     * @return map of plugin ID to enabled state
     */
    @Override
    public Map<String, Boolean> loadAllStates() {
        synchronized (stateLock) {
            return readJsonFromFile(PLUGIN_STATE_FILE, STATE_TYPE_REF);
        }
    }

    /**
     * Load all plugin configurations.
     *
     * @return map of plugin ID to configuration
     */
    @Override
    public Map<String, Map<String, String>> loadAllConfigs() {
        synchronized (configLock) {
            return readJsonFromFile(PLUGIN_CONFIG_FILE, CONFIG_TYPE_REF);
        }
    }

    /**
     * Delete plugin state.
     *
     * @param pluginId plugin ID
     */
    @Override
    public void deleteState(String pluginId) {
        synchronized (stateLock) {
            try {
                Map<String, Boolean> states = loadAllStates();
                states.remove(pluginId);
                writeJsonToFile(PLUGIN_STATE_FILE, states);
                LOGGER.debug("[FilePluginStatePersistenceImpl] Deleted plugin state for {}", pluginId);
            } catch (Exception e) {
                throw new PluginPersistenceException("Failed to delete plugin state: " + pluginId, e);
            }
        }
    }

    /**
     * Delete plugin configuration.
     *
     * @param pluginId plugin ID
     */
    @Override
    public void deleteConfig(String pluginId) {
        synchronized (configLock) {
            try {
                Map<String, Map<String, String>> configs = loadAllConfigs();
                configs.remove(pluginId);
                writeJsonToFile(PLUGIN_CONFIG_FILE, configs);
                LOGGER.debug("[FilePluginStatePersistenceImpl] Deleted plugin config for {}", pluginId);
            } catch (Exception e) {
                throw new PluginPersistenceException("Failed to delete plugin config: " + pluginId, e);
            }
        }
    }

    private <T> T readJsonFromFile(String fileName, TypeReference<T> typeRef) {
        Path filePath = Paths.get(dataDir, fileName);
        if (!Files.exists(filePath)) {
            return createEmptyMap(typeRef);
        }

        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            if (StringUtils.isBlank(content)) {
                return createEmptyMap(typeRef);
            }
            return JacksonUtils.toObj(content, typeRef);
        } catch (Exception e) {
            LOGGER.error("[FilePluginStatePersistenceImpl] Failed to read file: {}", fileName, e);
            return createEmptyMap(typeRef);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createEmptyMap(TypeReference<T> typeRef) {
        return (T) new HashMap<>(16);
    }

    private void writeJsonToFile(String fileName, Object data) throws IOException {
        Path filePath = Paths.get(dataDir, fileName);
        String content = JacksonUtils.toJson(data);
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
    }
}
