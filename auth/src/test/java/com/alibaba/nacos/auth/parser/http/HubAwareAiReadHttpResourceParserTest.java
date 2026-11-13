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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ordinary auth contracts remain exact while Hub admission is selected within the same GET. */
class HubAwareAiReadHttpResourceParserTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"mcp", "agents", "prompt", "agentspecs", "skills"})
    void normalResourceParsingIsIdenticalToExistingAiParser(String resourcePath) {
        HttpServletRequest request = request("source", "/v3/client/ai/" + resourcePath);
        Secured secured = secured();
        Resource original = new AiHttpResourceParser().parse(request, secured);
        Resource routed = new HubAwareAiReadHttpResourceParser().parse(request, secured);
        assertEquals(original.getNamespaceId(), routed.getNamespaceId());
        assertEquals(original.getGroup(), routed.getGroup());
        assertEquals(original.getName(), routed.getName());
        assertEquals(original.getType(), routed.getType());
        assertEquals(original.getProperties(), routed.getProperties());
    }
    
    @Test
    void hubGetSelectsDedicatedAdmissionWithoutInventingSourceAuthority() {
        HttpServletRequest request =
            request(AiConstants.Hub.NAMESPACE_ID, "/v3/client/ai/skills");
        Resource resource = new HubAwareAiReadHttpResourceParser().parse(request, secured());
        assertEquals(SignType.SPECIFIED, resource.getType());
        assertEquals(AiConstants.Hub.ENTRY_RESOURCE, resource.getName());
        assertEquals("", resource.getNamespaceId());
        assertTrue(resource.getProperties().containsKey(Constants.Tag.ONLY_IDENTITY));
        assertTrue(resource.getProperties().containsKey(Constants.Tag.ALLOW_ANONYMOUS));
        assertEquals("r", resource.getProperties().getProperty(Constants.Resource.ACTION));
    }
    
    @Test
    void hubCannotUseReadParserForWriteOrConflictingNamespaceParameters() {
        HttpServletRequest request =
            request(AiConstants.Hub.NAMESPACE_ID, "/v3/client/ai/skills");
        Secured secured = secured();
        HubAwareAiReadHttpResourceParser parser = new HubAwareAiReadHttpResourceParser();
        when(request.getMethod()).thenReturn("POST");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(request, secured));
        when(request.getMethod()).thenReturn("GET");
        when(secured.action()).thenReturn(ActionTypes.WRITE);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(request, secured));
        when(secured.action()).thenReturn(ActionTypes.READ);
        when(request.getParameterValues("namespaceId"))
            .thenReturn(new String[] {AiConstants.Hub.NAMESPACE_ID, "source"});
        assertThrows(IllegalArgumentException.class, () -> parser.parse(request, secured));
        when(request.getParameter("namespaceId")).thenReturn("source");
        when(request.getParameterValues("namespaceId"))
            .thenReturn(new String[] {"source", AiConstants.Hub.NAMESPACE_ID});
        assertThrows(IllegalArgumentException.class, () -> parser.parse(request, secured));
    }
    
    private HttpServletRequest request(String namespace, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn("GET");
        when(request.getParameter("namespaceId")).thenReturn(namespace);
        when(request.getParameterValues("namespaceId")).thenReturn(new String[] {namespace});
        when(request.getParameter("mcpName")).thenReturn("mcp-name");
        when(request.getParameter("agentName")).thenReturn("agent-name");
        when(request.getParameter("promptKey")).thenReturn("prompt-key");
        when(request.getParameter("agentSpecName")).thenReturn("management-name");
        when(request.getParameter("skillName")).thenReturn("skill-name");
        when(request.getParameter("name")).thenReturn("client-name");
        return request;
    }
    
    private Secured secured() {
        Secured secured = mock(Secured.class);
        when(secured.action()).thenReturn(ActionTypes.READ);
        when(secured.signType()).thenReturn(SignType.AI);
        when(secured.tags()).thenReturn(new String[0]);
        return secured;
    }
}
