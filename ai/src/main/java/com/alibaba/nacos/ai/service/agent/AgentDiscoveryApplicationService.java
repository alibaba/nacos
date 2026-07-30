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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionComparator;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogVersion;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Application service for RAD Search and one-shot Discover.
 *
 * <p>This service deliberately owns no transport or subscription state. Search derives its
 * catalog from visible Agent summaries. Discover combines one immutable online Version with the
 * current Runtime Registry projection and caches verified Version content by content digest.</p>
 *
 * @author Nacos
 */
@Service
public class AgentDiscoveryApplicationService {
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    private static final int DEFAULT_PAGE_SIZE = 20;
    
    private static final int SEARCH_SCAN_PAGE_SIZE = 100;
    
    private static final int VERSION_CACHE_SIZE = 1024;
    
    private static final String CACHE_KEY_SEPARATOR = "\u0000";
    
    private final AgentOperationService operationService;
    
    private final AgentPersistenceService persistenceService;
    
    private final AiResourceManager resourceManager;
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    private final Cache<String, AgentVersionDetail> versionCache;
    
    public AgentDiscoveryApplicationService(AgentOperationService operationService,
        AgentPersistenceService persistenceService, AiResourceManager resourceManager,
        AgentRuntimeRegistryService runtimeRegistryService) {
        this.operationService = operationService;
        this.persistenceService = persistenceService;
        this.resourceManager = resourceManager;
        this.runtimeRegistryService = runtimeRegistryService;
        this.versionCache =
            CacheBuilder.newBuilder().maximumSize(VERSION_CACHE_SIZE).build();
    }
    
    /**
     * Search visible and enabled Agents using their complete online Version catalogs.
     *
     * @param request RAD Search request
     * @return stable Agent catalog page
     * @throws NacosException when a visible stored Agent summary is invalid
     */
    public Page<AgentCatalogEntry> search(AgentSearchRequest request) throws NacosException {
        RadModelValidator.validate(request);
        int pageNo = request.getPageNo() == null ? DEFAULT_PAGE_NO : request.getPageNo();
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        QueryCondition condition = resourceManager.buildQueryCondition(request.getNamespaceId(),
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, VisibilityConstants.ACTION_READ);
        if (condition.isAlwaysEmpty()) {
            Page<AgentCatalogEntry> empty = AiResourceManager.buildEmptyPage(pageNo);
            RadModelValidator.validateCatalogPage(empty);
            return empty;
        }
        
        List<AgentCatalogEntry> matches = new ArrayList<AgentCatalogEntry>();
        int scanPage = 1;
        Page<AgentSummary> source;
        do {
            source = persistenceService.listAgents(condition, scanPage, SEARCH_SCAN_PAGE_SIZE);
            for (AgentSummary summary : source.getPageItems()) {
                if (matchesSearch(summary, request)) {
                    matches.add(toCatalogEntry(summary));
                }
            }
            scanPage++;
        } while (scanPage <= source.getPagesAvailable());
        Collections.sort(matches, Comparator.comparing(AgentCatalogEntry::getAgentName));
        
        Page<AgentCatalogEntry> result = page(matches, pageNo, pageSize);
        RadModelValidator.validateCatalogPage(result);
        return result;
    }
    
    /**
     * Discover one visible, enabled, and online Agent Version.
     *
     * @param request RAD Discover request
     * @return complete replacement discovery snapshot
     * @throws NacosException when the target is unavailable or stored state cannot be projected
     */
    public AgentDiscoveryResult discover(AgentDiscoveryRequest request) throws NacosException {
        RadModelValidator.validate(request);
        String namespaceId = request.getNamespaceId();
        AgentReference reference = request.getReference();
        Agent agent = operationService.getAgent(namespaceId, reference.getAgentName());
        if (!AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(agent.getStatus())) {
            throw notFound(reference.getAgentName());
        }
        String version = resolveVersion(agent, reference);
        AgentVersionDetail detail =
            loadOnlineVersion(namespaceId, reference.getAgentName(), version);
        
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId(namespaceId);
        result.setAgentName(reference.getAgentName());
        result.setVersion(version);
        result.setContentDigest(detail.getContentDigest());
        result.setCallInterfaces(resolveCallInterfaces(namespaceId, reference.getAgentName(),
            version, detail, request.getFilter()));
        RadModelValidator.validate(result);
        return result;
    }
    
    private boolean matchesSearch(AgentSummary summary, AgentSearchRequest request) {
        if (!AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(summary.getStatus())) {
            return false;
        }
        AgentVersionCatalog catalog = summary.getVersionCatalog();
        if (catalog == null || catalog.getLatestVersion() == null
            || catalog.getOnlineVersions() == null || catalog.getOnlineVersions().isEmpty()) {
            return false;
        }
        if (request.getAgentNameContains() != null
            && !summary.getAgentName().contains(request.getAgentNameContains())) {
            return false;
        }
        if (request.getTagsAll() != null
            && (summary.getTags() == null
                || !summary.getTags().containsAll(request.getTagsAll()))) {
            return false;
        }
        return matchesProtocols(catalog.getOnlineVersions(), request.getProtocolsAny());
    }
    
    private boolean matchesProtocols(List<AgentVersionCatalogEntry> versions,
        List<String> requestedProtocols) {
        if (requestedProtocols == null) {
            return true;
        }
        Set<String> protocols = new HashSet<String>(requestedProtocols);
        for (AgentVersionCatalogEntry version : versions) {
            for (String protocol : version.getProtocols()) {
                if (protocols.contains(protocol)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private AgentCatalogEntry toCatalogEntry(AgentSummary summary) {
        AgentCatalogEntry result = new AgentCatalogEntry();
        result.setAgentName(summary.getAgentName());
        result.setDisplayName(summary.getDisplayName());
        result.setDescription(summary.getDescription());
        result.setIconUrl(summary.getIconUrl());
        result.setProvider(summary.getProvider());
        result.setTags(copy(summary.getTags()));
        result.setLatestVersion(summary.getVersionCatalog().getLatestVersion());
        
        List<AgentCatalogVersion> versions = new ArrayList<AgentCatalogVersion>();
        for (AgentVersionCatalogEntry source : summary.getVersionCatalog().getOnlineVersions()) {
            AgentCatalogVersion version = new AgentCatalogVersion();
            version.setVersion(source.getVersion());
            version.setLabels(withoutLatest(source.getLabels()));
            version.setProtocols(copy(source.getProtocols()));
            versions.add(version);
        }
        Collections.sort(versions,
            (left, right) -> AgentVersionComparator.compare(right.getVersion(), left.getVersion()));
        result.setVersions(versions);
        return result;
    }
    
    private List<String> withoutLatest(List<String> labels) {
        if (labels == null) {
            return null;
        }
        List<String> result = new ArrayList<String>(labels);
        result.remove(AiResourceConstants.LABEL_LATEST);
        return result.isEmpty() ? null : result;
    }
    
    private <T> List<T> copy(List<T> source) {
        return source == null ? null : new ArrayList<T>(source);
    }
    
    private Page<AgentCatalogEntry> page(List<AgentCatalogEntry> matches, int pageNo,
        int pageSize) {
        int totalCount = matches.size();
        long requestedOffset = (long) (pageNo - 1) * pageSize;
        int fromIndex = (int) Math.min(requestedOffset, totalCount);
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        Page<AgentCatalogEntry> result = new Page<AgentCatalogEntry>();
        result.setPageNumber(pageNo);
        result.setTotalCount(totalCount);
        result.setPagesAvailable(
            totalCount == 0 ? 0 : (int) ((totalCount + (long) pageSize - 1) / pageSize));
        result.setPageItems(new ArrayList<AgentCatalogEntry>(
            matches.subList(fromIndex, toIndex)));
        return result;
    }
    
    private String resolveVersion(Agent agent, AgentReference reference)
        throws NacosApiException {
        if (reference.getVersion() != null) {
            return reference.getVersion();
        }
        String label = reference.getLabel() == null ? AiResourceConstants.LABEL_LATEST
            : reference.getLabel();
        Map<String, String> labels = agent.getVersionInfo() == null ? null
            : agent.getVersionInfo().getLabels();
        String result = labels == null ? null : labels.get(label);
        if (result == null) {
            throw notFound(reference.getAgentName());
        }
        return result;
    }
    
    private AgentVersionDetail loadOnlineVersion(String namespaceId, String agentName,
        String version) throws NacosException {
        AiResourceVersion row =
            persistenceService.requireVersionRow(namespaceId, agentName, version);
        if (!AiConstants.Agent.VERSION_STATUS_ONLINE.equals(row.getStatus())) {
            throw notFound(agentName);
        }
        AgentVersionStorageDescriptor descriptor;
        try {
            descriptor = AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent Version descriptor is invalid: " + agentName + '@'
                + version, e);
        }
        String key = cacheKey(namespaceId, agentName, version, descriptor.getContentDigest());
        AgentVersionDetail result = versionCache.getIfPresent(key);
        if (result == null) {
            result = persistenceService.getAgentVersion(namespaceId, agentName, version);
            if (!AiConstants.Agent.VERSION_STATUS_ONLINE.equals(result.getStatus())) {
                throw notFound(agentName);
            }
            if (!descriptor.getContentDigest().equals(result.getContentDigest())) {
                throw serverError("Agent Version changed while loading discovery content: "
                    + agentName + '@' + version, null);
            }
            versionCache.put(key, result);
        }
        return result;
    }
    
    private String cacheKey(String namespaceId, String agentName, String version,
        String contentDigest) {
        return namespaceId + CACHE_KEY_SEPARATOR + agentName + CACHE_KEY_SEPARATOR + version
            + CACHE_KEY_SEPARATOR + contentDigest;
    }
    
    private List<AgentDiscoveryCallInterface> resolveCallInterfaces(String namespaceId,
        String agentName, String version, AgentVersionDetail detail,
        AgentDiscoveryFilter filter) throws NacosException {
        List<AgentDiscoveryCallInterface> result =
            new ArrayList<AgentDiscoveryCallInterface>();
        for (AgentCallInterface source : detail.getCallInterfaces()) {
            if (!matchesInterface(source, filter)) {
                continue;
            }
            AgentDiscoveryCallInterface callInterface =
                new AgentDiscoveryCallInterface();
            callInterface.setProtocol(source.getProtocol());
            callInterface.setProtocolVersion(source.getProtocolVersion());
            callInterface.setDescriptorMediaType(source.getDescriptorMediaType());
            callInterface.setNativeDescriptor(source.getNativeDescriptor());
            callInterface.setEndpointSets(resolveEndpointSets(namespaceId, agentName, version,
                detail.getContentDigest(), source, filter));
            result.add(callInterface);
        }
        return result;
    }
    
    private boolean matchesInterface(AgentCallInterface callInterface,
        AgentDiscoveryFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.getProtocols() != null
            && !filter.getProtocols().contains(callInterface.getProtocol())) {
            return false;
        }
        return filter.getProtocolVersion() == null
            || Objects.equals(filter.getProtocolVersion(), callInterface.getProtocolVersion());
    }
    
    private List<EndpointSet> resolveEndpointSets(String namespaceId, String agentName,
        String version, String contentDigest, AgentCallInterface callInterface,
        AgentDiscoveryFilter filter) throws NacosException {
        List<EndpointSet> result = new ArrayList<EndpointSet>();
        for (EndpointSource source : callInterface.getEndpointSourceOrder()) {
            if (filter != null && filter.getEndpointSources() != null
                && !filter.getEndpointSources().contains(source)) {
                continue;
            }
            EndpointSet endpointSet = source == EndpointSource.RUNTIME
                ? runtimeRegistryService.getRuntimeEndpointSet(namespaceId, agentName,
                    callInterface.getProtocol(), version)
                : declaredEndpointSet(contentDigest, callInterface.getDeclaredEndpoints());
            endpointSet.setEndpoints(filterEndpoints(namespaceId, agentName,
                callInterface.getProtocol(), endpointSet.getEndpoints(), filter));
            result.add(endpointSet);
        }
        return result;
    }
    
    private EndpointSet declaredEndpointSet(String contentDigest, List<Endpoint> endpoints) {
        EndpointSet result = new EndpointSet();
        result.setSource(EndpointSource.DECLARED);
        result.setSourceRevision(contentDigest);
        result.setEndpoints(endpoints == null ? Collections.<Endpoint>emptyList() : endpoints);
        return result;
    }
    
    private List<Endpoint> filterEndpoints(String namespaceId, String agentName, String protocol,
        List<Endpoint> endpoints, AgentDiscoveryFilter filter) {
        List<Endpoint> result = new ArrayList<Endpoint>();
        for (Endpoint source : endpoints) {
            Endpoint endpoint = EndpointCanonicalizer.canonicalize(source);
            if (matchesEndpoint(endpoint, filter)) {
                result.add(endpoint);
            }
        }
        Collections.sort(result,
            (left, right) -> compareEndpoints(namespaceId, agentName, protocol, left, right));
        return result;
    }
    
    private boolean matchesEndpoint(Endpoint endpoint, AgentDiscoveryFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.getTransports() != null
            && !filter.getTransports().contains(endpoint.getTransport())) {
            return false;
        }
        if (filter.getMetadataSelector() == null
            || filter.getMetadataSelector().isEmpty()) {
            return true;
        }
        Map<String, String> metadata = endpoint.getMetadata();
        if (metadata == null) {
            return false;
        }
        for (Map.Entry<String, String> selector : filter.getMetadataSelector().entrySet()) {
            if (!Objects.equals(selector.getValue(), metadata.get(selector.getKey()))) {
                return false;
            }
        }
        return true;
    }
    
    private int compareEndpoints(String namespaceId, String agentName, String protocol,
        Endpoint left, Endpoint right) {
        int result = Integer.compare(left.getPriority(), right.getPriority());
        if (result != 0) {
            return result;
        }
        EndpointNaturalKey leftKey =
            EndpointNaturalKey.of(namespaceId, agentName, protocol, left);
        EndpointNaturalKey rightKey =
            EndpointNaturalKey.of(namespaceId, agentName, protocol, right);
        return leftKey.compareTo(rightKey);
    }
    
    private NacosApiException notFound(String agentName) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Agent is not discoverable: " + agentName);
    }
    
    private NacosApiException serverError(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, message)
            : new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, cause,
                message);
    }
}
