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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for AgentSpecProxy.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class AgentSpecProxyTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String AGENT_SPEC_NAME = "test-agentspec";
    
    @Mock
    private AgentSpecHandler agentSpecHandler;
    
    private AgentSpecProxy agentSpecProxy;
    
    @BeforeEach
    public void setUp() {
        agentSpecProxy = new AgentSpecProxy(agentSpecHandler);
    }
    
    @Test
    public void testForcePublish() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        
        doNothing().when(agentSpecHandler).forcePublish(form);
        
        agentSpecProxy.forcePublish(form);
        
        verify(agentSpecHandler, times(1)).forcePublish(form);
    }
    
    @Test
    public void testForcePublishPropagatesException() throws NacosException {
        AgentSpecPublishForm form = new AgentSpecPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v99");
        
        NacosApiException expectedException = new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND, "version not found");
        doThrow(expectedException).when(agentSpecHandler).forcePublish(any(AgentSpecPublishForm.class));
        
        NacosApiException ex = assertThrows(NacosApiException.class, () -> agentSpecProxy.forcePublish(form));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
}
