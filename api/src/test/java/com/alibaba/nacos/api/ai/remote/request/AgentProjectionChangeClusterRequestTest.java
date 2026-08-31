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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.remote.request.cluster.AgentProjectionChangeClusterRequest;
import com.alibaba.nacos.api.ai.remote.response.cluster.AgentProjectionChangeClusterResponse;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.Payload;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProjectionChangeClusterRequestTest {
    
    @Test
    void testClusterBinding() {
        AgentProjectionChangeClusterRequest request =
            new AgentProjectionChangeClusterRequest();
        request.setNamespaceId("tenant");
        request.setAgentName("agent");
        AgentProjectionChangeClusterResponse response =
            new AgentProjectionChangeClusterResponse();
        
        assertEquals(Constants.AI.AI_MODULE, request.getModule());
        assertEquals("tenant", request.getNamespaceId());
        assertEquals("agent", request.getAgentName());
        assertTrue(response.isSuccess());
    }
    
    @Test
    void testClusterPayloadsAreDiscoverable() {
        boolean requestRegistered = false;
        boolean responseRegistered = false;
        for (Payload payload : ServiceLoader.load(Payload.class)) {
            requestRegistered |= payload instanceof AgentProjectionChangeClusterRequest;
            responseRegistered |= payload instanceof AgentProjectionChangeClusterResponse;
        }
        assertTrue(requestRegistered);
        assertTrue(responseRegistered);
    }
}
