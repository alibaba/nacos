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
import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Reads the legacy Copilot configuration as one connection named {@code legacy}.
 *
 * <p>Until the {@code nacos.copilot.connections.*} keys replace it, the existing
 * {@code copilot-config.json} values plus {@code COPILOT_API_KEY} are mapped to the
 * {@code legacy} connection that keeps today's DashScope behavior. The connection is usable
 * only when one of those two sources provides a key, so clearing both stops using the
 * connection instead of falling back to the provider's own environment variables.
 *
 * @author nacos
 */
@Component
public class LegacyConfigProviderReader implements ProviderReader {
    
    /**
     * Connection name used by the deprecated configuration.
     */
    public static final String CONNECTION_NAME = "legacy";
    
    static final String PROVIDER_ID = "dashscope";
    
    private static final String ENV_API_KEY = "COPILOT_API_KEY";
    
    private final Environment environment;
    
    /**
     * Create the legacy reader.
     *
     * @param environment Spring environment used to read {@code COPILOT_API_KEY}
     */
    @Autowired
    public LegacyConfigProviderReader(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public int order() {
        return 100;
    }
    
    @Override
    public List<CopilotModelConnection> read(ProviderReadContext context) {
        CopilotProperties config = context.getLegacyConfig();
        String environmentApiKey = Boolean.TRUE.equals(config.getApiKeyCleared())
            ? null : environment.getProperty(ENV_API_KEY);
        boolean useEnvironment = StringUtils.isNotBlank(environmentApiKey);
        String apiKey = useEnvironment ? environmentApiKey : config.getApiKey();
        CredentialSource credentialSource = resolveCredentialSource(apiKey, useEnvironment);
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name(CONNECTION_NAME)
            .providerId(PROVIDER_ID)
            .displayName("Legacy DashScope")
            .enabled(true)
            .source(ConnectionSource.LEGACY)
            .priority(100)
            .model(config.getModel())
            .apiKey(apiKey)
            .credentialSource(credentialSource)
            .stream(Boolean.TRUE)
            .enableThinking(Boolean.TRUE)
            .build();
        return Collections.singletonList(connection);
    }
    
    private CredentialSource resolveCredentialSource(String apiKey, boolean useEnvironment) {
        if (StringUtils.isBlank(apiKey)) {
            return null;
        }
        return useEnvironment ? CredentialSource.ENVIRONMENT : CredentialSource.CONFIGURATION;
    }
}
