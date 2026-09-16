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
import com.alibaba.nacos.ai.model.search.AiResourceSearchResult;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionComparator;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.NumberedPage;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Predicate;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.PredicateOperator;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Query;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Application service for RAD Search and one-shot Discover.
 *
 * <p>This service deliberately owns no transport or subscription state. Search derives its
 * catalog from visible Agent summaries. Discover uses one immutable online Version for definition
 * metadata, combines it with the selector's Runtime compatibility pool, and caches verified
 * Version content by content digest.</p>
 *
 * @author Nacos
 */
@Service
public class AgentDiscoveryApplicationService {
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    private static final int DEFAULT_PAGE_SIZE = 20;
    
    private static final int SEARCH_SCAN_PAGE_SIZE = 100;
    
    private static final int VERSION_CACHE_SIZE = 1024;
    
    private static final int SERVICE_UNAVAILABLE_STATUS = 503;
    
    private static final String CACHE_KEY_SEPARATOR = "\u0000";
    
    private static final NacosTypeReference<List<String>> STRING_LIST_TYPE =
        new NacosTypeReference<List<String>>() {
        };
    
    private final AgentOperationService operationService;
    
    private final AgentPersistenceService persistenceService;
    
    private final AiResourceManager resourceManager;
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    private final AiResourceSearchService searchService;
    
    private final AgentSearchModeResolver searchModeResolver;
    
    private final Cache<String, AgentVersionDetail> versionCache;
    
    @Autowired
    public AgentDiscoveryApplicationService(AgentOperationService operationService,
        AgentPersistenceService persistenceService, AiResourceManager resourceManager,
        AgentRuntimeRegistryService runtimeRegistryService,
        ObjectProvider<AiResourceSearchService> searchServiceProvider,
        AgentSearchModeResolver searchModeResolver) {
        this(operationService, persistenceService, resourceManager, runtimeRegistryService,
            searchServiceProvider.getIfAvailable(), searchModeResolver);
    }
    
    AgentDiscoveryApplicationService(AgentOperationService operationService,
        AgentPersistenceService persistenceService, AiResourceManager resourceManager,
        AgentRuntimeRegistryService runtimeRegistryService,
        AiResourceSearchService searchService, AgentSearchModeResolver searchModeResolver) {
        this.operationService = operationService;
        this.persistenceService = persistenceService;
        this.resourceManager = resourceManager;
        this.runtimeRegistryService = runtimeRegistryService;
        this.searchService = searchService;
        this.searchModeResolver = searchModeResolver;
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
    public Page<AgentSummary> search(String namespaceId, AgentSearchRequest request)
        throws NacosException {
        RadModelValidator.validate(namespaceId, request);
        int pageNo = request.getPageNo() == null ? DEFAULT_PAGE_NO : request.getPageNo();
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (AgentSearchMode.INDEX == searchModeResolver.resolve()) {
            return searchIndex(namespaceId, request, pageNo, pageSize);
        }
        return searchScan(namespaceId, request, pageNo, pageSize);
    }
    
    private Page<AgentSummary> searchScan(String namespaceId, AgentSearchRequest request,
        int pageNo,
        int pageSize) throws NacosException {
        QueryCondition condition = resourceManager.buildQueryCondition(namespaceId,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, VisibilityConstants.ACTION_READ);
        if (condition.isAlwaysEmpty()) {
            Page<AgentSummary> empty = AiResourceManager.buildEmptyPage(pageNo);
            RadModelValidator.validateCatalogPage(empty);
            return empty;
        }
        
        List<AgentSummary> matches = new ArrayList<AgentSummary>();
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
        Collections.sort(matches, Comparator.comparing(AgentSummary::getAgentName));
        
        Page<AgentSummary> result = page(matches, pageNo, pageSize);
        RadModelValidator.validateCatalogPage(result);
        return result;
    }
    
    private Page<AgentSummary> searchIndex(String namespaceId, AgentSearchRequest request,
        int pageNo,
        int pageSize) throws NacosException {
        if (searchService == null) {
            throw new NacosException(SERVICE_UNAVAILABLE_STATUS,
                "Agent Search index runtime is unavailable.");
        }
        Query query = new Query();
        query.setNamespaceId(namespaceId);
        query.setResourceTypes(Collections.singletonList(Constants.Agent.RESOURCE_TYPE_AGENT));
        query.setPageNumber(pageNo);
        query.setPageSize(pageSize);
        query.setPredicates(buildSearchPredicates(request));
        NumberedPage source = searchService.numberedList(query);
        if (source.getTotalCount() > Integer.MAX_VALUE) {
            throw serverError("Agent Search result count exceeds the supported page contract.",
                null);
        }
        List<AgentSummary> items = new ArrayList<AgentSummary>(
            source.getItems().size());
        for (AiResourceSearchResult item : source.getItems()) {
            items.add(toCatalogEntry(toIndexedSummary(item)));
        }
        Page<AgentSummary> result = new Page<AgentSummary>();
        result.setPageNumber(source.getPageNumber());
        result.setTotalCount((int) source.getTotalCount());
        result.setPagesAvailable(source.getPagesAvailable());
        result.setPageItems(items);
        RadModelValidator.validateCatalogPage(result);
        return result;
    }
    
    private List<Predicate> buildSearchPredicates(AgentSearchRequest request) {
        List<Predicate> result = new ArrayList<Predicate>();
        if (request.getAgentNameContains() != null) {
            result.add(new Predicate("resourceName", PredicateOperator.LITERAL_CONTAINS,
                Collections.singletonList(request.getAgentNameContains()), true));
        }
        if (request.getTagsAll() != null) {
            result.add(new Predicate("tags", PredicateOperator.EXACT_ALL,
                request.getTagsAll(), true));
        }
        if (request.getProtocolsAny() != null) {
            result.add(new Predicate("metadata.protocols", PredicateOperator.EXACT_ANY,
                request.getProtocolsAny(), true));
        }
        return result;
    }
    
    private AgentSummary toIndexedSummary(AiResourceSearchResult source) throws NacosException {
        try {
            Map<String, Object> metadata = source.getMetadata();
            AgentVersionInfo catalog = convertMetadata(metadata.get("versionCatalog"),
                AgentVersionInfo.class);
            if (catalog != null) {
                Map<?, ?> storedCatalog =
                    convertMetadata(metadata.get("versionCatalog"), Map.class);
                Map<String, String> labels = new LinkedHashMap<String, String>();
                Object latest = storedCatalog.get("latestVersion");
                if (latest instanceof String) {
                    labels.put("latest", (String) latest);
                }
                if (catalog.getOnlineVersions() != null) {
                    for (AgentVersionSummary version : catalog.getOnlineVersions()) {
                        if (version.getLabels() != null) {
                            for (String label : version.getLabels()) {
                                labels.put(label, version.getVersion());
                            }
                        }
                    }
                }
                catalog.setLabels(labels);
            }
            if (catalog == null) {
                throw new IllegalArgumentException("versionCatalog is missing");
            }
            AgentSummary result = new AgentSummary();
            result.setAgentName(source.getResourceName());
            result.setDisplayName(source.getDisplayName());
            result.setDescription(source.getDescription());
            Object iconUrl = metadata.get("iconUrl");
            result.setIconUrl(iconUrl == null ? null : String.valueOf(iconUrl));
            result.setProvider(convertMetadata(metadata.get("provider"), AgentProvider.class));
            if (metadata.containsKey("tags")) {
                result.setTags(convertMetadata(metadata.get("tags"), STRING_LIST_TYPE));
            } else {
                result.setTags(source.getTags().isEmpty() ? null
                    : new ArrayList<String>(source.getTags()));
            }
            result.setVersionInfo(catalog);
            return result;
        } catch (RuntimeException e) {
            throw serverError("Indexed Agent catalog is invalid: " + source.getResourceName(),
                e);
        }
    }
    
    private <T> T convertMetadata(Object value, Class<T> type) {
        return value == null ? null : JsonUtils.toObj(JsonUtils.toJson(value), type);
    }
    
    private <T> T convertMetadata(Object value, NacosTypeReference<T> type) {
        return value == null ? null : JsonUtils.toObj(JsonUtils.toJson(value), type);
    }
    
    /**
     * Discover one visible and enabled Agent using online definition and Runtime Versions.
     *
     * @param request RAD Discover request
     * @return complete replacement discovery snapshot
     * @throws NacosException when the target is unavailable or stored state cannot be projected
     */
    public AgentDiscoveryResult discover(AgentDiscoveryRequest request) throws NacosException {
        RadModelValidator.validate(request);
        String namespaceId = request.getNamespaceId();
        AgentReference reference = request.getReference();
        AgentSummary agent = operationService.getAgent(namespaceId, reference.getAgentName());
        return buildDiscoveryResult(request, agent, false);
    }
    
    /**
     * Project the current complete Discover fact after Watch admission has already authorized the
     * resource.
     *
     * <p>This internal server path deliberately bypasses request-thread visibility so a shared
     * projection can be recomputed without encoding an Owner in its key. It never grants access or
     * returns data directly to a caller; every Watch still performs normal authorized Discover
     * before content reaches a Client.</p>
     *
     * @param request canonical Discover request
     * @return complete current projection
     * @throws NacosException when the target is unavailable or cannot be projected
     */
    public AgentDiscoveryResult projectCurrentFact(AgentDiscoveryRequest request)
        throws NacosException {
        RadModelValidator.validate(request);
        AgentReference reference = request.getReference();
        AgentSummary agent = persistenceService.getAgent(request.getNamespaceId(),
            reference.getAgentName());
        return buildDiscoveryResult(request, agent, true);
    }
    
    private AgentDiscoveryResult buildDiscoveryResult(AgentDiscoveryRequest request,
        AgentSummary agent,
        boolean currentRuntimeFacts) throws NacosException {
        String namespaceId = request.getNamespaceId();
        AgentReference reference = request.getReference();
        if (!AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(agent.getStatus())) {
            throw notFound(reference.getAgentName());
        }
        String version = resolveVersion(agent, reference);
        List<String> runtimeVersions = resolveRuntimeVersions(agent, reference, version);
        AgentVersionDetail detail =
            loadOnlineVersion(namespaceId, reference.getAgentName(), version);
        
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId(namespaceId);
        result.setAgentName(reference.getAgentName());
        result.setVersion(version);
        result.setContentDigest(detail.getContentDigest());
        result.setCallInterfaces(resolveCallInterfaces(namespaceId, reference.getAgentName(),
            runtimeVersions, detail, request.getFilter(), currentRuntimeFacts));
        RadModelValidator.validate(result);
        return result;
    }
    
    private boolean matchesSearch(AgentSummary summary, AgentSearchRequest request) {
        if (!AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(summary.getStatus())) {
            return false;
        }
        AgentVersionInfo catalog = summary.getVersionInfo();
        if (catalog == null || catalog.latestVersion() == null
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
    
    private boolean matchesProtocols(List<AgentVersionSummary> versions,
        List<String> requestedProtocols) {
        if (requestedProtocols == null) {
            return true;
        }
        Set<String> protocols = new HashSet<String>(requestedProtocols);
        for (AgentVersionSummary version : versions) {
            for (String protocol : version.getProtocols()) {
                if (protocols.contains(protocol)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private AgentSummary toCatalogEntry(AgentSummary summary) {
        AgentSummary result = new AgentSummary();
        result.setAgentName(summary.getAgentName());
        result.setDisplayName(summary.getDisplayName());
        result.setDescription(summary.getDescription());
        result.setIconUrl(summary.getIconUrl());
        result.setProvider(summary.getProvider());
        result.setTags(summary.getTags() == null || summary.getTags().isEmpty() ? null
            : copy(summary.getTags()));
        AgentVersionInfo versionInfo = new AgentVersionInfo();
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("latest", summary.getVersionInfo().latestVersion());
        versionInfo.setLabels(labels);
        result.setVersionInfo(versionInfo);
        
        List<AgentVersionSummary> versions = new ArrayList<AgentVersionSummary>();
        for (AgentVersionSummary source : summary.getVersionInfo().getOnlineVersions()) {
            AgentVersionSummary version = new AgentVersionSummary();
            version.setVersion(source.getVersion());
            version.setLabels(withoutLatest(source.getLabels()));
            if (version.getLabels() != null) {
                for (String label : version.getLabels()) {
                    labels.put(label, version.getVersion());
                }
            }
            version.setProtocols(copy(source.getProtocols()));
            versions.add(version);
        }
        Collections.sort(versions,
            (left, right) -> AgentVersionComparator.compare(right.getVersion(), left.getVersion()));
        versionInfo.setOnlineVersions(versions);
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
    
    private Page<AgentSummary> page(List<AgentSummary> matches, int pageNo,
        int pageSize) {
        int totalCount = matches.size();
        long requestedOffset = (long) (pageNo - 1) * pageSize;
        int fromIndex = (int) Math.min(requestedOffset, totalCount);
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        Page<AgentSummary> result = new Page<AgentSummary>();
        result.setPageNumber(pageNo);
        result.setTotalCount(totalCount);
        result.setPagesAvailable(
            totalCount == 0 ? 0 : (int) ((totalCount + (long) pageSize - 1) / pageSize));
        result.setPageItems(new ArrayList<AgentSummary>(
            matches.subList(fromIndex, toIndex)));
        return result;
    }
    
    private String resolveVersion(AgentSummary agent, AgentReference reference)
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
    
    private List<String> resolveRuntimeVersions(AgentSummary agent, AgentReference reference,
        String definitionVersion) throws NacosApiException {
        if (reference.getVersion() != null || reference.getLabel() != null) {
            return Collections.singletonList(definitionVersion);
        }
        AgentVersionInfo catalog = agent.getVersionInfo();
        if (catalog == null || catalog.getOnlineVersions() == null
            || catalog.getOnlineVersions().isEmpty()) {
            throw notFound(reference.getAgentName());
        }
        List<String> result = new ArrayList<String>(catalog.getOnlineVersions().size());
        for (AgentVersionSummary entry : catalog.getOnlineVersions()) {
            result.add(entry.getVersion());
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
    
    private List<AgentCallInterface> resolveCallInterfaces(String namespaceId,
        String agentName, List<String> runtimeVersions, AgentVersionDetail detail,
        AgentDiscoveryFilter filter, boolean currentRuntimeFacts) throws NacosException {
        List<AgentCallInterface> result =
            new ArrayList<AgentCallInterface>();
        for (AgentCallInterface source : detail.getCallInterfaces()) {
            if (!matchesInterface(source, filter)) {
                continue;
            }
            AgentCallInterface callInterface =
                new AgentCallInterface();
            callInterface.setProtocol(source.getProtocol());
            callInterface.setProtocolVersion(source.getProtocolVersion());
            callInterface.setDescriptorMediaType(source.getDescriptorMediaType());
            callInterface.setNativeDescriptor(source.getNativeDescriptor());
            callInterface.setEndpointSets(resolveEndpointSets(namespaceId, agentName,
                runtimeVersions, detail.getContentDigest(), source, filter,
                currentRuntimeFacts));
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
        List<String> runtimeVersions, String contentDigest,
        AgentCallInterface callInterface,
        AgentDiscoveryFilter filter, boolean currentRuntimeFacts) throws NacosException {
        List<EndpointSet> result = new ArrayList<EndpointSet>();
        for (EndpointSource source : callInterface.getEndpointSourceOrder()) {
            if (filter != null && filter.getEndpointSources() != null
                && !filter.getEndpointSources().contains(source)) {
                continue;
            }
            EndpointSet endpointSet;
            if (source == EndpointSource.RUNTIME) {
                endpointSet = currentRuntimeFacts
                    ? runtimeRegistryService.getCurrentRuntimeEndpointSet(namespaceId, agentName,
                        callInterface.getProtocol(), runtimeVersions)
                    : runtimeRegistryService.getRuntimeEndpointSet(namespaceId, agentName,
                        callInterface.getProtocol(), runtimeVersions);
            } else {
                endpointSet =
                    declaredEndpointSet(contentDigest,
                        callInterface.getEndpointSets() == null
                            || callInterface.getEndpointSets().isEmpty()
                                ? Collections.<Endpoint>emptyList()
                                : callInterface.getEndpointSets().get(0).getEndpoints());
            }
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
        List<Endpoint> discoveryEndpoints =
            new ArrayList<Endpoint>();
        if (endpoints != null) {
            for (Endpoint endpoint : endpoints) {
                discoveryEndpoints.add(copyDiscoveryEndpoint(endpoint));
            }
        }
        result.setEndpoints(discoveryEndpoints);
        return result;
    }
    
    private List<Endpoint> filterEndpoints(String namespaceId, String agentName,
        String protocol, List<Endpoint> endpoints, AgentDiscoveryFilter filter) {
        List<Endpoint> result = new ArrayList<Endpoint>();
        for (Endpoint source : endpoints) {
            Endpoint endpoint = canonicalizeDiscoveryEndpoint(source);
            if (matchesEndpoint(endpoint, filter)) {
                result.add(endpoint);
            }
        }
        Collections.sort(result,
            (left, right) -> compareEndpoints(namespaceId, agentName, protocol, left, right));
        return result;
    }
    
    private Endpoint canonicalizeDiscoveryEndpoint(Endpoint source) {
        Endpoint result =
            copyDiscoveryEndpoint(EndpointCanonicalizer.canonicalize(source));
        result.setBindings(source.getBindings());
        return result;
    }
    
    private Endpoint copyDiscoveryEndpoint(Endpoint source) {
        Endpoint result = new Endpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        result.setPriority(source.getPriority());
        result.setWeight(source.getWeight());
        result.setMetadata(source.getMetadata());
        result.setHealthy(source.getHealthy());
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
