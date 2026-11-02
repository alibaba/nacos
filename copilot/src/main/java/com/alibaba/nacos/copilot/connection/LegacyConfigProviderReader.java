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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.connection;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.copilot.config.CopilotConfigStorage;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Reads the legacy Copilot configuration as one reserved connection.
 *
 * <p>Until the {@code nacos.copilot.connections.*} keys replace it, the existing
 * {@code copilot-config.json} plus {@code COPILOT_API_KEY} remain the only source, mapped
 * to a reserved {@code legacy} connection that keeps today's DashScope behavior.
 *
 * @author nacos
 */
@Component
public class LegacyConfigProviderReader implements ProviderReader {
    
    /**
     * Reserved connection name used by the deprecated configuration.
     */
    public static final String CONNECTION_NAME = "legacy";
    
    static final String PROVIDER_ID = "dashscope";
    
    private static final String ENV_API_KEY = "COPILOT_API_KEY";
    
    private final CopilotConfigStorage configStorage;
    
    private final CopilotProperties defaultProperties;
    
    private final Environment environment;
    
    /**
     * Create the legacy reader.
     *
     * @param configStorage stored Copilot configuration
     * @param defaultProperties configuration from {@code application.properties}
     * @param environment Spring environment used to read {@code COPILOT_API_KEY}
     */
    @Autowired
    public LegacyConfigProviderReader(CopilotConfigStorage configStorage,
        CopilotProperties defaultProperties, Environment environment) {
        this.configStorage = configStorage;
        this.defaultProperties = defaultProperties;
        this.environment = environment;
    }
    
    @Override
    public int order() {
        return 100;
    }
    
    @Override
    public List<CopilotModelConnection> read() {
        CopilotProperties config = getEffectiveConfig();
        CopilotModelConnection connection = new CopilotModelConnection();
        connection.setName(CONNECTION_NAME);
        connection.setProviderId(PROVIDER_ID);
        connection.setDisplayName("Legacy DashScope");
        connection.setEnabled(true);
        connection.setModel(config.getModel());
        connection.setApiKey(resolveApiKey(config));
        connection.setStream(Boolean.TRUE);
        connection.setEnableThinking(Boolean.TRUE);
        return Collections.singletonList(connection);
    }
    
    private CopilotProperties getEffectiveConfig() {
        if (configStorage != null && configStorage.isAvailable()) {
            CopilotProperties config = configStorage.getConfig();
            if (config != null) {
                return config;
            }
        }
        return defaultProperties;
    }
    
    private String resolveApiKey(CopilotProperties config) {
        String apiKey = environment.getProperty(ENV_API_KEY);
        if (StringUtils.isNotBlank(apiKey)) {
            return apiKey;
        }
        return config.getApiKey();
    }
}
