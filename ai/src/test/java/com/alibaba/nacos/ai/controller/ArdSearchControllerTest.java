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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.A2aServerOperationService;
import com.alibaba.nacos.ai.service.ard.ArdSearchService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdSearchController}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdSearchControllerTest {
    
    @Mock
    private ArdSearchService ardSearchService;
    
    @Mock
    private A2aServerOperationService a2aServerOperationService;
    
    @Test
    void searchShouldReturnRawArdResponse() throws NacosException {
        ArdSearchController controller = controller();
        ArdSearchRequest request = new ArdSearchRequest();
        ArdSearchResponse response = new ArdSearchResponse();
        when(ardSearchService.search(request)).thenReturn(response);
        
        assertSame(response, controller.search(request));
    }
    
    @Test
    void agentsShouldReturnRawAgentPage() throws NacosException {
        ArdSearchController controller = controller();
        Page<AgentCardVersionInfo> page = new Page<>();
        when(a2aServerOperationService.listAgents("public", null, Constants.A2A.SEARCH_BLUR, 1,
            10)).thenReturn(page);
        
        assertSame(page, controller.agents(null, null, null, null, null));
    }
    
    @Test
    void agentsShouldClampPageSize() throws NacosException {
        ArdSearchController controller = controller();
        Page<AgentCardVersionInfo> page = new Page<>();
        when(a2aServerOperationService.listAgents("custom", "demo", Constants.A2A.SEARCH_ACCURATE,
            2, Constants.MAX_LIST_SIZE)).thenReturn(page);
        
        assertSame(page, controller.agents("custom", "demo", Constants.A2A.SEARCH_ACCURATE, 2,
            1000));
    }
    
    @Test
    void agentsShouldRejectUnsupportedSearchType() {
        ArdSearchController controller = controller();
        
        assertThrows(NacosException.class,
            () -> controller.agents(null, null, "prefix", null, null));
    }
    
    @Test
    void ardPathShouldUseProtocolEndpoint() {
        assertEquals("/v3/ai/ard", Constants.ARD_CLIENT_PATH);
    }
    
    private ArdSearchController controller() {
        return new ArdSearchController(ardSearchService, a2aServerOperationService);
    }
}
