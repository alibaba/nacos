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
import com.alibaba.nacos.ai.form.agentspecs.client.AgentSpecQueryForm;
import com.alibaba.nacos.ai.form.agentspecs.client.AgentSpecSearchForm;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecQueryResult;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentSpecClientController}.
 */
@ExtendWith(MockitoExtension.class)
class AgentSpecClientControllerTest {
    
    @Mock
    private AgentSpecOperationService agentSpecOperationService;
    
    private AgentSpecClientController controller;
    
    @BeforeEach
    void setUp() {
        controller = new AgentSpecClientController(agentSpecOperationService);
    }
    
    @Test
    void searchShouldDelegateWithValidatedDefaults() throws NacosException {
        AgentSpecSearchForm form = new AgentSpecSearchForm();
        form.setKeyword("agent");
        PageForm pageForm = new PageForm();
        Page<AgentSpecBasicInfo> page = new Page<>();
        AgentSpecBasicInfo item = new AgentSpecBasicInfo();
        item.setName("agent-one");
        page.setPageItems(List.of(item));
        page.setTotalCount(1);
        when(agentSpecOperationService.searchAgentSpecs("public", "agent", 1, 100))
            .thenReturn(page);
        
        Result<Page<AgentSpecBasicInfo>> result = controller.search(form, pageForm);
        
        assertEquals(1, result.getData().getTotalCount());
        assertEquals("agent-one", result.getData().getPageItems().get(0).getName());
    }
    
    @Test
    void getShouldReturnAgentSpecWithListenerHeaders() throws NacosException {
        AgentSpecQueryForm form = new AgentSpecQueryForm();
        form.setName("agent-one");
        form.setVersion("1.0.0");
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("agent-one");
        when(agentSpecOperationService.queryAgentSpecForClient("public", "agent-one", "1.0.0",
            null, null)).thenReturn(new AgentSpecQueryResult(agentSpec, "md5-1", "1.0.0"));
        
        ResponseEntity<Result<AgentSpec>> response = controller.get(form);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(agentSpec, response.getBody().getData());
        assertEquals("md5-1", response.getHeaders().getFirst(HttpHeaders.ETAG));
        assertEquals("md5-1",
            response.getHeaders().getFirst(Constants.AgentSpecs.HEADER_AGENTSPEC_MD5));
        assertEquals("1.0.0",
            response.getHeaders().getFirst(Constants.AgentSpecs.HEADER_AGENTSPEC_RESOLVED_VERSION));
    }
    
    @Test
    void getShouldReturnNotModifiedWhenMd5Matches() throws NacosException {
        AgentSpecQueryForm form = new AgentSpecQueryForm();
        form.setName("agent-one");
        form.setMd5("cached-md5");
        when(agentSpecOperationService.queryAgentSpecForClient("public", "agent-one", null, null,
            "cached-md5")).thenReturn(AgentSpecQueryResult.notModified("cached-md5", "1.0.0"));
        
        ResponseEntity<Result<AgentSpec>> response = controller.get(form);
        
        assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
        assertNull(response.getBody());
        assertEquals("cached-md5", response.getHeaders().getFirst(HttpHeaders.ETAG));
        assertEquals("cached-md5",
            response.getHeaders().getFirst(Constants.AgentSpecs.HEADER_AGENTSPEC_MD5));
        assertNull(response.getHeaders()
            .getFirst(Constants.AgentSpecs.HEADER_AGENTSPEC_RESOLVED_VERSION));
    }
}
