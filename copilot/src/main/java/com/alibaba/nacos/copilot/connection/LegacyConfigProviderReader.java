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

import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Reads the legacy Copilot configuration as one connection named {@code legacy}.
 *
 * <p>Only actual legacy declarations or a {@code COPILOT_API_KEY} credential create this
 * connection. The manager resolves deployment, stored and environment credential precedence
 * from captured inputs, including clears, before invoking the reader. Missing credentials
 * leave the connection unavailable rather than using the provider's own environment fallback.
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
    
    @Override
    public int order() {
        return 100;
    }
    
    @Override
    public List<CopilotModelConnection> read(ProviderReadContext context) {
        if (!context.isLegacyDeclared()) {
            return Collections.emptyList();
        }
        CopilotProperties config = context.getLegacyConfig();
        CredentialSource credentialSource = context.getLegacyCredentialSource();
        String apiKey = credentialSource == CredentialSource.ENVIRONMENT
            ? context.getDeployment().getProperty(ProviderReadContext.ENV_API_KEY)
            : config.getApiKey();
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
}
