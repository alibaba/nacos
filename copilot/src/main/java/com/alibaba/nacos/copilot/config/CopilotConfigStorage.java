/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerFactory;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Copilot configuration storage using Nacos Config.
 *
 * @author nacos
 */
@Component
public class CopilotConfigStorage {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotConfigStorage.class);
    
    private static final String CONFIG_DATA_ID = "copilot-config.json";
    private static final String CONFIG_GROUP = "nacos-copilot";
    private static final String DEFAULT_NAMESPACE = "public";
    
    @Value("${nacos.copilot.config.namespace:public}")
    private String configNamespace;
    
    @Value("${nacos.copilot.config.serverAddr:}")
    private String serverAddr;
    
    private ConfigMaintainerService configMaintainerService;
    
    /**
     * Initialize config maintainer service.
     */
    @PostConstruct
    public void init() {
        try {
            if (StringUtils.isNotBlank(serverAddr)) {
                Properties properties = new Properties();
                properties.setProperty("serverAddr", serverAddr);
                configMaintainerService =
                    ConfigMaintainerFactory.createConfigMaintainerService(properties);
                LOGGER.info("Copilot config storage initialized with serverAddr: {}", serverAddr);
            } else {
                // Use default server address from environment
                String defaultServerAddr =
                    System.getProperty("nacos.server.addr", "127.0.0.1:8848");
                Properties properties = new Properties();
                properties.setProperty("serverAddr", defaultServerAddr);
                configMaintainerService =
                    ConfigMaintainerFactory.createConfigMaintainerService(properties);
                LOGGER.info("Copilot config storage initialized with default serverAddr: {}",
                    defaultServerAddr);
            }
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to initialize Copilot config storage, will use default configuration", e);
        }
    }
    
    /**
     * Get Copilot configuration from Nacos Config.
     *
     * @return CopilotProperties, or null if not configured
     */
    public CopilotProperties getConfig() {
        if (configMaintainerService == null) {
            return null;
        }
        
        try {
            ConfigDetailInfo configInfo = configMaintainerService.getConfig(
                CONFIG_DATA_ID, CONFIG_GROUP, configNamespace);
            
            if (configInfo != null && StringUtils.isNotBlank(configInfo.getContent())) {
                return JacksonUtils.toObj(configInfo.getContent(), CopilotProperties.class);
            }
        } catch (NacosException e) {
            LOGGER.debug("Copilot config not found in Nacos Config, using default configuration",
                e);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Copilot config from Nacos Config", e);
        }
        
        return null;
    }
    
    /**
     * Save Copilot configuration to Nacos Config.
     *
     * @param config CopilotProperties to save
     * @return true if saved successfully
     */
    public boolean saveConfig(CopilotProperties config) {
        if (configMaintainerService == null) {
            LOGGER.warn("Config maintainer service is not initialized, cannot save config");
            return false;
        }
        
        try {
            String content = JacksonUtils.toJson(config);
            boolean result = configMaintainerService.publishConfig(
                CONFIG_DATA_ID,
                CONFIG_GROUP,
                configNamespace,
                content,
                "nacos-copilot",
                "system",
                null,
                "Copilot configuration",
                "json");
            
            if (result) {
                LOGGER.info("Copilot config saved successfully to Nacos Config");
            } else {
                LOGGER.warn("Failed to save Copilot config to Nacos Config");
            }
            
            return result;
        } catch (NacosException e) {
            LOGGER.error("Failed to save Copilot config to Nacos Config", e);
            return false;
        }
    }
    
    /**
     * Check if config storage is available.
     *
     * @return true if config storage is available
     */
    public boolean isAvailable() {
        return configMaintainerService != null;
    }
}
