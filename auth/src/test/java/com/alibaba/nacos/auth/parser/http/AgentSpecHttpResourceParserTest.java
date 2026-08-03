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

package com.alibaba.nacos.auth.parser.http;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_AGENT_SPEC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSpecHttpResourceParserTest {
    
    @Test
    void testParseClientName() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/client/ai/agentspecs");
        request.addParameter(Constants.NAMESPACE_ID, "testNs");
        request.addParameter("name", "client-spec");
        
        Resource actual =
            new AgentSpecNameHttpResourceParser().parse(request, getSecured());
        
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("client-spec", actual.getName());
        assertEquals(SignType.AI, actual.getType());
        assertEquals(AI_TYPE_AGENT_SPEC, actual.getProperties().getProperty(AI_TYPE));
    }
    
    @Test
    void testRejectMissingClientName() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/client/ai/agentspecs");
        
        assertThrows(IllegalArgumentException.class,
            () -> new AgentSpecNameHttpResourceParser().parse(request, getSecured()));
    }
    
    @Test
    void testParseUpdateTargetFromAgentSpecCard() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/admin/ai/agentspecs/draft");
        request.addParameter(Constants.NAMESPACE_ID, "testNs");
        request.addParameter("agentSpecName", "non-authoritative-name");
        request.addParameter("agentSpecCard", "{\"name\":\"card-spec\"}");
        
        Resource actual =
            new AgentSpecCardHttpResourceParser().parse(request, getSecured());
        
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("card-spec", actual.getName());
        assertEquals(SignType.AI, actual.getType());
        assertEquals(AI_TYPE_AGENT_SPEC, actual.getProperties().getProperty(AI_TYPE));
    }
    
    @Test
    void testRejectInvalidAgentSpecCard() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/admin/ai/agentspecs/draft");
        request.addParameter("agentSpecCard", "invalid-json");
        
        assertThrows(IllegalArgumentException.class,
            () -> new AgentSpecCardHttpResourceParser().parse(request, getSecured()));
    }
    
    @Test
    void testRejectAgentSpecCardWithoutName() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/admin/ai/agentspecs/draft");
        request.addParameter("agentSpecCard", "{\"description\":\"missing name\"}");
        
        assertThrows(IllegalArgumentException.class,
            () -> new AgentSpecCardHttpResourceParser().parse(request, getSecured()));
    }
    
    private Secured getSecured() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("securedMethod");
        return method.getAnnotation(Secured.class);
    }
    
    @Secured(signType = SignType.AI)
    private void securedMethod() {
    }
}
