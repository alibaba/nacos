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

package com.alibaba.nacos.client.ai.utils;

import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentModelUtilsTest {
    
    @Test
    void privateConstructor() throws Exception {
        Constructor<AgentModelUtils> constructor = AgentModelUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }
    
    @Test
    void copyPublishRequestDeepCopiesAndValidates() throws NacosException {
        AgentPublishRequest source = new AgentPublishRequest();
        source.setAgentName("agent-a");
        source.setVersion("1.0.0");
        source.setCallInterfaces(new ArrayList<AgentCallInterface>(
            Collections.singletonList(new AgentCallInterface())));
        source.setTags(new ArrayList<String>(Collections.singletonList("assistant")));
        source.setExtensions(new HashMap<String, Object>(
            Collections.<String, Object>singletonMap("region", "east")));
        source.setAutoSubmit(true);
        
        AgentPublishRequest result = AgentModelUtils.copyPublishRequest(source);
        assertNotSame(source, result);
        assertNotSame(source.getCallInterfaces(), result.getCallInterfaces());
        assertNotSame(source.getTags(), result.getTags());
        assertNotSame(source.getExtensions(), result.getExtensions());
        assertEquals(true, result.isAutoSubmit());
        source.getTags().clear();
        assertEquals("assistant", result.getTags().get(0));
        
        assertThrows(NacosException.class, () -> AgentModelUtils.copyPublishRequest(null));
        result.setCallInterfaces(null);
        assertThrows(NacosException.class, () -> AgentModelUtils.copyPublishRequest(result));
    }
    
    @Test
    void copyPublishRequestMapsCopyFailure() {
        AgentPublishRequest source = new AgentPublishRequest();
        try (MockedStatic<JsonUtils> json = Mockito.mockStatic(JsonUtils.class)) {
            json.when(() -> JsonUtils.toJson(source)).thenThrow(new IllegalStateException("boom"));
            assertEquals(NacosException.INVALID_PARAM,
                assertThrows(NacosException.class,
                    () -> AgentModelUtils.copyPublishRequest(source)).getErrCode());
        }
    }
    
    @Test
    void copySearchRequestBindsNamespaceAndCopiesCollections() throws NacosException {
        AgentSearchRequest source = new AgentSearchRequest();
        source.setTagsAll(new ArrayList<String>(Arrays.asList("one", "two")));
        source.setProtocolsAny(new ArrayList<String>(Collections.singletonList("a2a")));
        source.setAgentNameContains("agent");
        source.setPageNo(1);
        source.setPageSize(20);
        
        AgentSearchRequest result = AgentModelUtils.copySearchRequest(source, "public");
        
        assertEquals("public", result.getNamespaceId());
        assertEquals(source.getTagsAll(), result.getTagsAll());
        assertNotSame(source.getTagsAll(), result.getTagsAll());
        assertNotSame(source.getProtocolsAny(), result.getProtocolsAny());
        assertNull(source.getNamespaceId());
        source.getTagsAll().clear();
        assertEquals(2, result.getTagsAll().size());
    }
    
    @Test
    void copySearchRequestAcceptsSameNamespaceAndRejectsInvalidInput() throws NacosException {
        AgentSearchRequest source = new AgentSearchRequest();
        source.setNamespaceId("tenant");
        assertEquals("tenant",
            AgentModelUtils.copySearchRequest(source, "tenant").getNamespaceId());
        
        source.setNamespaceId("other");
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copySearchRequest(source, "tenant"));
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copySearchRequest(null, "tenant"));
        
        source.setNamespaceId("tenant");
        source.setPageNo(0);
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copySearchRequest(source, "tenant"));
    }
    
    @Test
    void copyDiscoveryRequestCopiesReferenceAndFilter() throws NacosException {
        AgentReference reference = reference();
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(new ArrayList<String>(Collections.singletonList("a2a")));
        filter.setProtocolVersion("1.0.0");
        filter.setTransports(new ArrayList<String>(Collections.singletonList("http")));
        filter.setEndpointSources(
            new ArrayList<EndpointSource>(Collections.singletonList(EndpointSource.RUNTIME)));
        Map<String, String> selector = new HashMap<String, String>();
        selector.put("zone", "east");
        filter.setMetadataSelector(selector);
        
        AgentDiscoveryRequest result =
            AgentModelUtils.copyDiscoveryRequest(reference, filter, "public");
        
        assertEquals("public", result.getNamespaceId());
        assertNotSame(reference, result.getReference());
        assertNotSame(filter, result.getFilter());
        assertNotSame(filter.getProtocols(), result.getFilter().getProtocols());
        assertNotSame(filter.getTransports(), result.getFilter().getTransports());
        assertNotSame(filter.getEndpointSources(), result.getFilter().getEndpointSources());
        assertNotSame(selector, result.getFilter().getMetadataSelector());
        
        reference.setAgentName("changed");
        filter.getProtocols().clear();
        selector.clear();
        assertEquals("agent-a", result.getReference().getAgentName());
        assertEquals(1, result.getFilter().getProtocols().size());
        assertEquals("east", result.getFilter().getMetadataSelector().get("zone"));
    }
    
    @Test
    void copyDiscoveryRequestHandlesAbsentAndInvalidValues() throws NacosException {
        assertNull(AgentModelUtils.copyDiscoveryRequest(reference(), null, "public").getFilter());
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyDiscoveryRequest(null, null, "public"));
        
        AgentReference reference = reference();
        reference.setVersion("1.0.0");
        reference.setLabel("stable");
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyDiscoveryRequest(reference, null, "public"));
    }
    
    @Test
    void copyRegistrationBatchCanonicalizesAndIsolatesEndpoints() throws NacosException {
        AgentEndpointRegistrationBatch source = registration();
        Endpoint endpoint = source.getEndpoints().get(0);
        endpoint.setUri("HTTP://LOCALHOST/path");
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("zone", "east");
        endpoint.setMetadata(metadata);
        
        AgentEndpointRegistrationBatch result =
            AgentModelUtils.copyRegistrationBatch(source, "public");
        
        assertEquals("http://localhost:80/path", result.getEndpoints().get(0).getUri());
        assertEquals(Integer.valueOf(0), result.getEndpoints().get(0).getPriority());
        assertEquals(Double.valueOf(1D), result.getEndpoints().get(0).getWeight());
        assertNotSame(source.getEndpoints(), result.getEndpoints());
        assertNotSame(metadata, result.getEndpoints().get(0).getMetadata());
        assertNull(source.getNamespaceId());
        
        endpoint.setUri("http://other:80");
        metadata.clear();
        assertEquals("http://localhost:80/path", result.getEndpoints().get(0).getUri());
        assertEquals("east", result.getEndpoints().get(0).getMetadata().get("zone"));
        
        AgentEndpointRegistrationBatch copy = AgentModelUtils.copyRegistrationBatch(result);
        assertNotSame(result, copy);
        assertNotSame(result.getEndpoints(), copy.getEndpoints());
    }
    
    @Test
    void copyRegistrationBatchRejectsInvalidInput() {
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyRegistrationBatch(null, "public"));
        
        AgentEndpointRegistrationBatch source = registration();
        source.setNamespaceId("other");
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyRegistrationBatch(source, "public"));
        
        source.setNamespaceId("public");
        source.setEndpoints(null);
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyRegistrationBatch(source, "public"));
        
        source.setEndpoints(Collections.singletonList(null));
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyRegistrationBatch(source, "public"));
        
        source.setEndpoints(Collections.singletonList(endpoint("bad-uri", "http")));
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyRegistrationBatch(source, "public"));
    }
    
    @Test
    void copyDeregistrationBatchCopiesAndValidatesEndpoints() throws NacosException {
        AgentEndpointDeregistrationBatch source = deregistration();
        AgentEndpointDeregistrationBatch result =
            AgentModelUtils.copyDeregistrationBatch(source, "public");
        
        assertEquals("public", result.getNamespaceId());
        assertNotSame(source.getEndpoints(), result.getEndpoints());
        assertNotSame(source.getEndpoints().get(0), result.getEndpoints().get(0));
        source.getEndpoints().get(0).setUri("http://other:80");
        assertEquals("http://localhost:80/path", result.getEndpoints().get(0).getUri());
        
        source.getEndpoints().get(0).setPriority(1);
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyDeregistrationBatch(source, "public"));
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyDeregistrationBatch(null, "public"));
    }
    
    @Test
    void copyDeregistrationBatchHandlesNullCollectionsAndItems() {
        AgentEndpointDeregistrationBatch source = deregistration();
        source.setEndpoints(null);
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyDeregistrationBatch(source, "public"));
        
        source.setEndpoints(Collections.singletonList(null));
        assertThrows(NacosException.class,
            () -> AgentModelUtils.copyDeregistrationBatch(source, "public"));
    }
    
    @Test
    void copyDiscoveryResultHandlesNullAndDeepCopy() {
        assertNull(AgentModelUtils.copyDiscoveryResult(null));
        AgentDiscoveryResult source = new AgentDiscoveryResult();
        source.setNamespaceId("public");
        source.setAgentName("agent-a");
        source.setVersion("1.0.0");
        source.setContentDigest(
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.0");
        binding.setVersionRange("[1.0.0]");
        AgentDiscoveryEndpoint endpoint = new AgentDiscoveryEndpoint();
        endpoint.setUri("http://localhost:80/agent");
        endpoint.setTransport("http");
        endpoint.setHealthy(true);
        endpoint.setBindings(new ArrayList<RuntimeVersionBinding>(
            Collections.singletonList(binding)));
        EndpointSet endpointSet = new EndpointSet();
        endpointSet.setSource(EndpointSource.RUNTIME);
        endpointSet.setSourceRevision(
            "murmur3-x64-128-v1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        endpointSet.setEndpoints(new ArrayList<AgentDiscoveryEndpoint>(
            Collections.singletonList(endpoint)));
        AgentDiscoveryCallInterface callInterface = new AgentDiscoveryCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setEndpointSets(Collections.singletonList(endpointSet));
        source.setCallInterfaces(Collections.singletonList(callInterface));
        
        AgentDiscoveryResult result = AgentModelUtils.copyDiscoveryResult(source);
        
        assertNotSame(source, result);
        assertEquals("agent-a", result.getAgentName());
        AgentDiscoveryEndpoint copiedEndpoint = result.getCallInterfaces().get(0)
            .getEndpointSets().get(0).getEndpoints().get(0);
        assertNotSame(endpoint, copiedEndpoint);
        assertNotSame(endpoint.getBindings(), copiedEndpoint.getBindings());
        binding.setRuntimeVersion("2.0.0");
        assertEquals("1.0.0", copiedEndpoint.getBindings().get(0).getRuntimeVersion());
    }
    
    private AgentReference reference() {
        AgentReference result = new AgentReference();
        result.setAgentName("agent-a");
        return result;
    }
    
    private AgentEndpointRegistrationBatch registration() {
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setAgentName("agent-a");
        result.setRuntimeVersion("1.0.0");
        result.setProtocol("a2a");
        result.setEndpoints(new ArrayList<Endpoint>(
            Collections.singletonList(endpoint("http://localhost:80/path", "http"))));
        return result;
    }
    
    private AgentEndpointDeregistrationBatch deregistration() {
        AgentEndpointDeregistrationBatch result = new AgentEndpointDeregistrationBatch();
        result.setAgentName("agent-a");
        result.setProtocol("a2a");
        result.setEndpoints(new ArrayList<Endpoint>(
            Collections.singletonList(endpoint("http://localhost:80/path", "http"))));
        return result;
    }
    
    private Endpoint endpoint(String uri, String transport) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport(transport);
        return result;
    }
}
