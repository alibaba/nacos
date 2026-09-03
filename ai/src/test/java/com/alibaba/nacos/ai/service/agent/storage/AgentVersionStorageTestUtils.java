/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.agent.storage;

import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;

import static org.mockito.Mockito.mock;

/**
 * Test-only factory for deterministic prepared Agent Version writes.
 *
 * @author Nacos
 */
public final class AgentVersionStorageTestUtils {
    
    private AgentVersionStorageTestUtils() {
    }
    
    /**
     * Prepare content without requiring a Spring environment or accessing storage.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version Agent Version
     * @param content Version content
     * @return deterministic prepared write
     */
    public static PreparedAgentVersionWrite prepare(String namespaceId, String agentName,
        String version, AgentVersionContent content) {
        AgentVersionStorageService service = new AgentVersionStorageService(
            mock(AiResourceStorageRouter.class), () -> NacosConfigAiResourceStorage.TYPE);
        return service.prepare(namespaceId, agentName, version, content);
    }
}
