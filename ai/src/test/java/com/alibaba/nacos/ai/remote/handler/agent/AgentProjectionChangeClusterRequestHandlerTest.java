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

package com.alibaba.nacos.ai.remote.handler.agent;

import com.alibaba.nacos.ai.service.agent.watch.AgentProjectionService;
import com.alibaba.nacos.api.ai.remote.request.cluster.AgentProjectionChangeClusterRequest;
import com.alibaba.nacos.api.ai.remote.response.cluster.AgentProjectionChangeClusterResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentProjectionChangeClusterRequestHandlerTest {
    
    @Test
    void testInvalidRequestIsRejected() {
        AgentProjectionChangeClusterRequestHandler handler =
            new AgentProjectionChangeClusterRequestHandler(
                mock(AgentProjectionService.class));
        assertThrows(IllegalArgumentException.class,
            () -> handler.handle(new AgentProjectionChangeClusterRequest(),
                mock(RequestMeta.class)));
    }
    
    @Test
    void testActiveProjectionIsInvalidated() {
        AgentProjectionService projectionService = mock(AgentProjectionService.class);
        AgentProjectionChangeClusterRequestHandler handler =
            new AgentProjectionChangeClusterRequestHandler(projectionService);
        AgentProjectionChangeClusterRequest request =
            new AgentProjectionChangeClusterRequest();
        request.setNamespaceId("tenant");
        request.setAgentName("agent");
        
        AgentProjectionChangeClusterResponse response =
            handler.handle(request, mock(RequestMeta.class));
        
        assertTrue(response.isSuccess());
        verify(projectionService).onAgentChanged("tenant", "agent");
    }
}
