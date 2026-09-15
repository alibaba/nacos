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

package com.alibaba.nacos.ai.model.agent;

import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentVersionContentTest {
    
    @Test
    void testDefaultConstructorAndAccessors() {
        AgentCallInterface callInterface = new AgentCallInterface();
        AgentVersionContent content = new AgentVersionContent();
        assertNull(content.getKind());
        assertNull(content.getSchemaVersion());
        assertNull(content.getCallInterfaces());
        
        content.setKind(AgentVersionContent.KIND);
        content.setSchemaVersion(AgentVersionContent.SCHEMA_VERSION);
        content.setCallInterfaces(Collections.singletonList(callInterface));
        
        assertEquals(AgentVersionContent.KIND, content.getKind());
        assertEquals(AgentVersionContent.SCHEMA_VERSION, content.getSchemaVersion());
        assertEquals(Collections.singletonList(callInterface), content.getCallInterfaces());
    }
    
    @Test
    void testConvenienceConstructorSetsStorageEnvelope() {
        List<AgentCallInterface> callInterfaces = Collections.singletonList(
            createCallInterface());
        AgentVersionContent content = new AgentVersionContent(callInterfaces);
        
        assertEquals(AgentVersionContent.KIND, content.getKind());
        assertEquals(AgentVersionContent.SCHEMA_VERSION, content.getSchemaVersion());
        assertEquals(callInterfaces, content.getCallInterfaces());
    }
    
    @Test
    void testJacksonRoundTripUsesPlainBeanContract() throws Exception {
        AgentVersionContent original = new AgentVersionContent(
            Collections.singletonList(createCallInterface()));
        ObjectMapper mapper = new ObjectMapper();
        
        String json = mapper.writeValueAsString(original);
        AgentVersionContent restored = mapper.readValue(json, AgentVersionContent.class);
        
        assertEquals(AgentVersionContent.KIND, restored.getKind());
        assertEquals(AgentVersionContent.SCHEMA_VERSION, restored.getSchemaVersion());
        assertEquals(1, restored.getCallInterfaces().size());
        AgentCallInterface restoredInterface = restored.getCallInterfaces().get(0);
        assertEquals("a2a", restoredInterface.getProtocol());
        assertEquals("application/json", restoredInterface.getDescriptorMediaType());
        assertEquals("descriptor", restoredInterface.getNativeDescriptor());
        assertEquals(Collections.singletonList(EndpointSource.RUNTIME),
            restoredInterface.getEndpointSourceOrder());
    }
    
    private AgentCallInterface createCallInterface() {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol("a2a");
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor("descriptor");
        result.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        return result;
    }
}
