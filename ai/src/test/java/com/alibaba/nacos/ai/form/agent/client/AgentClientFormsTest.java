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

package com.alibaba.nacos.ai.form.agent.client;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClientFormsTest {
    
    @Test
    void testSearchFormBuildsRequestAndValidates() throws NacosApiException {
        AgentSearchForm form = new AgentSearchForm();
        form.setNamespaceId("team");
        form.setAgentNameContains("demo");
        form.setTagsAll(Arrays.asList("assistant", "support"));
        form.setProtocolsAny(Arrays.asList("a2a", "jsonrpc"));
        form.setPageNo(2);
        form.setPageSize(20);
        
        assertEquals("team", form.getNamespaceId());
        assertEquals("demo", form.getAgentNameContains());
        assertEquals(Arrays.asList("assistant", "support"), form.getTagsAll());
        assertEquals(Arrays.asList("a2a", "jsonrpc"), form.getProtocolsAny());
        assertEquals(2, form.getPageNo());
        assertEquals(20, form.getPageSize());
        
        AgentSearchRequest request = form.toRequest();
        assertEquals("team", request.getNamespaceId());
        assertEquals("demo", request.getAgentNameContains());
        assertEquals(2, request.getPageNo());
        form.validate();
        
        AgentSearchForm defaultNamespace = new AgentSearchForm();
        defaultNamespace.setPageNo(0);
        assertThrows(IllegalArgumentException.class, defaultNamespace::toRequest);
        defaultNamespace.setPageNo(1);
        assertEquals("public", defaultNamespace.toRequest().getNamespaceId());
    }
    
    @Test
    void testDiscoveryFormBuildsCompleteFilter() throws NacosApiException {
        AgentDiscoveryForm form = new AgentDiscoveryForm();
        form.setNamespaceId("team");
        form.setAgentName("demo");
        form.setVersion("1.0.0");
        form.setLabel(null);
        form.setProtocol(Arrays.asList("a2a", "jsonrpc"));
        form.setProtocolVersion("1.0");
        form.setTransport(Collections.singletonList("HTTP"));
        form.setEndpointSource(Arrays.asList("RUNTIME", "DECLARED"));
        form.setMetadataSelector("{\"zone\":\"east\"}");
        
        assertEquals("team", form.getNamespaceId());
        assertEquals("demo", form.getAgentName());
        assertEquals("1.0.0", form.getVersion());
        assertNull(form.getLabel());
        assertEquals(Arrays.asList("a2a", "jsonrpc"), form.getProtocol());
        assertEquals("1.0", form.getProtocolVersion());
        assertEquals(Collections.singletonList("HTTP"), form.getTransport());
        assertEquals(Arrays.asList("RUNTIME", "DECLARED"), form.getEndpointSource());
        assertEquals("{\"zone\":\"east\"}", form.getMetadataSelector());
        
        AgentDiscoveryRequest request = form.toRequest();
        assertEquals("team", request.getNamespaceId());
        assertEquals("demo", request.getReference().getAgentName());
        assertEquals("1.0.0", request.getReference().getVersion());
        assertEquals(EndpointSource.RUNTIME, request.getFilter().getEndpointSources().get(0));
        assertEquals("east", request.getFilter().getMetadataSelector().get("zone"));
        form.validate();
    }
    
    @Test
    void testDiscoveryFormSupportsNoFilterAndRejectsInvalidValues()
        throws NacosApiException {
        AgentDiscoveryForm form = new AgentDiscoveryForm();
        form.setAgentName("demo");
        form.setLabel("stable");
        AgentDiscoveryRequest request = form.toRequest();
        assertEquals("public", request.getNamespaceId());
        assertEquals("stable", request.getReference().getLabel());
        assertNull(request.getFilter());
        
        form.setProtocol(Collections.singletonList("a2a"));
        request = form.toRequest();
        assertEquals(Collections.singletonList("a2a"), request.getFilter().getProtocols());
        assertNull(request.getFilter().getEndpointSources());
        
        form.setProtocol(null);
        form.setEndpointSource(Collections.singletonList("UNKNOWN"));
        assertThrows(NacosApiException.class, form::toRequest);
        form.setEndpointSource(Collections.singletonList(null));
        assertThrows(NacosApiException.class, form::toRequest);
        form.setEndpointSource(null);
        form.setMetadataSelector("{");
        NacosApiException exception = assertThrows(NacosApiException.class, form::toRequest);
        assertEquals("Request parameter `metadataSelector` is not valid JSON.",
            exception.getMessage());
    }
    
    @Test
    void testRegistrationFormBuildsCompleteBatch() throws NacosApiException {
        Endpoint endpoint = endpoint();
        AgentEndpointRegistrationForm form = new AgentEndpointRegistrationForm();
        form.setNamespaceId(null);
        form.setAgentName("demo");
        form.setRuntimeVersion("1.0.0");
        form.setVersionRange("[1.0.0,2.0.0)");
        form.setProtocol("a2a");
        String endpoints = JsonUtils.toJson(Collections.singletonList(endpoint));
        form.setEndpoints(endpoints);
        
        assertNull(form.getNamespaceId());
        assertEquals("demo", form.getAgentName());
        assertEquals("1.0.0", form.getRuntimeVersion());
        assertEquals("[1.0.0,2.0.0)", form.getVersionRange());
        assertEquals("a2a", form.getProtocol());
        assertEquals(endpoints, form.getEndpoints());
        
        AgentEndpointRegistrationBatch request = form.toRequest();
        assertEquals("public", request.getNamespaceId());
        assertEquals(endpoint.getUri(), request.getEndpoints().get(0).getUri());
        assertEquals(endpoint.getTransport(), request.getEndpoints().get(0).getTransport());
        form.validate();
        
        form.setEndpoints("{");
        assertThrows(NacosApiException.class, form::toRequest);
    }
    
    @Test
    void testDeregistrationFormValidatesPublicationFields() throws NacosApiException {
        AgentEndpointDeregistrationForm form = new AgentEndpointDeregistrationForm();
        form.setNamespaceId("");
        form.setAgentName("demo");
        form.setProtocol("a2a");
        
        assertEquals("", form.getNamespaceId());
        assertEquals("demo", form.getAgentName());
        assertEquals("a2a", form.getProtocol());
        
        form.validate();
        assertEquals("public", form.getNamespaceId());
        assertEquals("demo", form.getAgentName());
        assertEquals("a2a", form.getProtocol());
        
        form.setProtocol("");
        assertThrows(IllegalArgumentException.class, form::validate);
    }
    
    @Test
    void testJsonParserAndPrivateConstructor() throws Exception {
        NacosTypeReference<Map<String, String>> type =
            new NacosTypeReference<Map<String, String>>() {
            };
        assertNull(AgentClientFormJsonParser.parseOptional("selector", " ", type));
        assertEquals("east", AgentClientFormJsonParser.parseOptional("selector",
            "{\"zone\":\"east\"}", type).get("zone"));
        
        Constructor<AgentClientFormJsonParser> constructor =
            AgentClientFormJsonParser.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertTrue(constructor.newInstance() instanceof AgentClientFormJsonParser);
    }
    
    private Endpoint endpoint() {
        Endpoint result = new Endpoint();
        result.setUri("http://127.0.0.1:8080/agent");
        result.setTransport("HTTP");
        return result;
    }
}
