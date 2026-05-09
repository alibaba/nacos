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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AgentSpecInnerHandler} — scope update.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AgentSpecInnerHandlerTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String AGENTSPEC_NAME = "test-agentspec";
    
    @Mock
    private AgentSpecOperationService agentSpecOperationService;
    
    private AgentSpecInnerHandler agentSpecInnerHandler;
    
    @BeforeEach
    void setUp() {
        agentSpecInnerHandler = new AgentSpecInnerHandler(agentSpecOperationService);
    }
    
    @Test
    void testUpdateScope() throws NacosException {
        AgentSpecScopeForm form = new AgentSpecScopeForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENTSPEC_NAME);
        form.setScope("PUBLIC");
        doNothing().when(agentSpecOperationService).updateScope(eq(NAMESPACE_ID),
            eq(AGENTSPEC_NAME), eq("PUBLIC"));
        
        agentSpecInnerHandler.updateScope(form);
        
        verify(agentSpecOperationService).updateScope(NAMESPACE_ID, AGENTSPEC_NAME, "PUBLIC");
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        AgentSpecBizTagsUpdateForm form = new AgentSpecBizTagsUpdateForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENTSPEC_NAME);
        form.setBizTags("[\"finance\"]");
        doNothing().when(agentSpecOperationService).updateBizTags(eq(NAMESPACE_ID),
            eq(AGENTSPEC_NAME),
            eq("[\"finance\"]"));
        
        agentSpecInnerHandler.updateBizTags(form);
        
        verify(agentSpecOperationService).updateBizTags(NAMESPACE_ID, AGENTSPEC_NAME,
            "[\"finance\"]");
    }
    
    @Test
    void testForcePublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENTSPEC_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        doNothing().when(agentSpecOperationService).forcePublish(eq(NAMESPACE_ID),
            eq(AGENTSPEC_NAME), eq("v1"),
            eq(true));
        
        agentSpecInnerHandler.forcePublish(form);
        
        verify(agentSpecOperationService).forcePublish(NAMESPACE_ID, AGENTSPEC_NAME, "v1", true);
    }
    
    @Test
    void testForcePublishWithNullUpdateLatestLabel() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENTSPEC_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(null);
        doNothing().when(agentSpecOperationService).forcePublish(eq(NAMESPACE_ID),
            eq(AGENTSPEC_NAME), eq("v1"),
            eq(true));
        
        agentSpecInnerHandler.forcePublish(form);
        
        verify(agentSpecOperationService).forcePublish(NAMESPACE_ID, AGENTSPEC_NAME, "v1", true);
    }
}
