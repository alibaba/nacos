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

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Agent SDK model copying, namespace binding, and local validation.
 *
 * @author Nacos
 */
public final class AgentModelUtils {
    
    private AgentModelUtils() {
    }
    
    /**
     * Copy, namespace-bind, and validate a Search request.
     *
     * @param source caller-owned request
     * @param namespaceId SDK namespace
     * @return isolated validated request
     * @throws NacosException when the request is invalid
     */
    public static AgentSearchRequest copySearchRequest(AgentSearchRequest source,
        String namespaceId) throws NacosException {
        if (source == null) {
            throw invalid("AgentSearchRequest must not be null.");
        }
        AgentSearchRequest result = new AgentSearchRequest();
        result.setNamespaceId(bindNamespace(source.getNamespaceId(), namespaceId));
        result.setAgentNameContains(source.getAgentNameContains());
        result.setTagsAll(copyList(source.getTagsAll()));
        result.setProtocolsAny(copyList(source.getProtocolsAny()));
        result.setPageNo(source.getPageNo());
        result.setPageSize(source.getPageSize());
        validate(new Validation() {
            
            @Override
            public void run() {
                RadModelValidator.validate(result);
            }
        });
        return result;
    }
    
    /**
     * Copy, namespace-bind, and validate a Discover request.
     *
     * @param reference caller-owned Agent reference
     * @param filter caller-owned optional filter
     * @param namespaceId SDK namespace
     * @return isolated validated request
     * @throws NacosException when the request is invalid
     */
    public static AgentDiscoveryRequest copyDiscoveryRequest(AgentReference reference,
        AgentDiscoveryFilter filter, String namespaceId) throws NacosException {
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId(namespaceId);
        result.setReference(copyReference(reference));
        result.setFilter(copyFilter(filter));
        validate(new Validation() {
            
            @Override
            public void run() {
                RadModelValidator.validate(result);
            }
        });
        return result;
    }
    
    /**
     * Copy, namespace-bind, canonicalize, and validate a registration batch.
     *
     * @param source caller-owned batch
     * @param namespaceId SDK namespace
     * @return isolated validated complete batch
     * @throws NacosException when the batch is invalid
     */
    public static AgentEndpointRegistrationBatch copyRegistrationBatch(
        AgentEndpointRegistrationBatch source, String namespaceId) throws NacosException {
        if (source == null) {
            throw invalid("AgentEndpointRegistrationBatch must not be null.");
        }
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setNamespaceId(bindNamespace(source.getNamespaceId(), namespaceId));
        result.setAgentName(source.getAgentName());
        result.setRuntimeVersion(source.getRuntimeVersion());
        result.setVersionRange(source.getVersionRange());
        result.setProtocol(source.getProtocol());
        try {
            result.setEndpoints(canonicalizeEndpoints(source.getEndpoints()));
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
        validate(new Validation() {
            
            @Override
            public void run() {
                RadModelValidator.validate(result);
            }
        });
        return result;
    }
    
    /**
     * Return an isolated complete registration batch.
     *
     * @param source source batch
     * @return deep copy
     */
    public static AgentEndpointRegistrationBatch copyRegistrationBatch(
        AgentEndpointRegistrationBatch source) {
        return JsonUtils.toObj(JsonUtils.toJson(source), AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Copy, namespace-bind, and validate a natural-key deregistration batch.
     *
     * @param source caller-owned batch
     * @param namespaceId SDK namespace
     * @return isolated validated deregistration intent
     * @throws NacosException when the batch is invalid
     */
    public static AgentEndpointDeregistrationBatch copyDeregistrationBatch(
        AgentEndpointDeregistrationBatch source, String namespaceId) throws NacosException {
        if (source == null) {
            throw invalid("AgentEndpointDeregistrationBatch must not be null.");
        }
        AgentEndpointDeregistrationBatch result = new AgentEndpointDeregistrationBatch();
        result.setNamespaceId(bindNamespace(source.getNamespaceId(), namespaceId));
        result.setAgentName(source.getAgentName());
        result.setProtocol(source.getProtocol());
        result.setEndpoints(copyEndpoints(source.getEndpoints()));
        validate(new Validation() {
            
            @Override
            public void run() {
                RadModelValidator.validate(result);
            }
        });
        return result;
    }
    
    /**
     * Return an isolated discovery snapshot.
     *
     * @param source source snapshot
     * @return deep copy, or {@code null}
     */
    public static AgentDiscoveryResult copyDiscoveryResult(AgentDiscoveryResult source) {
        return source == null ? null
            : JsonUtils.toObj(JsonUtils.toJson(source), AgentDiscoveryResult.class);
    }
    
    private static String bindNamespace(String requestedNamespace, String namespaceId)
        throws NacosException {
        if (StringUtils.isBlank(requestedNamespace)) {
            return namespaceId;
        }
        if (!namespaceId.equals(requestedNamespace)) {
            throw invalid("Request namespace does not match the AiService namespace.");
        }
        return namespaceId;
    }
    
    private static AgentReference copyReference(AgentReference source) throws NacosException {
        if (source == null) {
            throw invalid("AgentReference must not be null.");
        }
        AgentReference result = new AgentReference();
        result.setAgentName(source.getAgentName());
        result.setVersion(source.getVersion());
        result.setLabel(source.getLabel());
        return result;
    }
    
    private static AgentDiscoveryFilter copyFilter(AgentDiscoveryFilter source) {
        if (source == null) {
            return null;
        }
        AgentDiscoveryFilter result = new AgentDiscoveryFilter();
        result.setProtocols(copyList(source.getProtocols()));
        result.setProtocolVersion(source.getProtocolVersion());
        result.setTransports(copyList(source.getTransports()));
        result.setEndpointSources(source.getEndpointSources() == null ? null
            : new ArrayList<>(source.getEndpointSources()));
        result.setMetadataSelector(source.getMetadataSelector() == null ? null
            : new HashMap<>(source.getMetadataSelector()));
        return result;
    }
    
    private static List<String> copyList(List<String> source) {
        return source == null ? null : new ArrayList<>(source);
    }
    
    private static List<Endpoint> canonicalizeEndpoints(List<Endpoint> source) {
        if (source == null) {
            return null;
        }
        List<Endpoint> result = new ArrayList<Endpoint>(source.size());
        for (Endpoint endpoint : source) {
            result.add(EndpointCanonicalizer.canonicalize(endpoint));
        }
        return result;
    }
    
    private static List<Endpoint> copyEndpoints(List<Endpoint> source) {
        if (source == null) {
            return null;
        }
        List<Endpoint> result = new ArrayList<Endpoint>(source.size());
        for (Endpoint endpoint : source) {
            result.add(copyEndpoint(endpoint));
        }
        return result;
    }
    
    private static Endpoint copyEndpoint(Endpoint source) {
        if (source == null) {
            return null;
        }
        Endpoint result = new Endpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        result.setPriority(source.getPriority());
        result.setWeight(source.getWeight());
        result
            .setMetadata(source.getMetadata() == null ? null : new HashMap<>(source.getMetadata()));
        result.setHealthy(source.getHealthy());
        return result;
    }
    
    private static void validate(Validation validation) throws NacosException {
        try {
            validation.run();
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private interface Validation {
        
        void run();
    }
}
