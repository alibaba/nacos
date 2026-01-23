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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Copilot configuration.
 * 
 * Note: AgentScope is used directly, managed by CopilotAgentManager.
 *
 * @author nacos
 */
@Configuration
@ConditionalOnProperty(name = "nacos.copilot.enabled", havingValue = "true", matchIfMissing = true)
public class CopilotConfiguration {
    // Configuration is handled by CopilotAgentManager
}
