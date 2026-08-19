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
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.NumberedPage;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Predicate;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.PredicateOperator;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Query;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDiscoveryApplicationServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "demo-agent";
    
    private static final String VERSION = "2.0.0";
    
    private static final String DIGEST =
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    
    private static final String OTHER_DIGEST =
        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    
    private static final String RUNTIME_REVISION =
        "murmur3-x64-128-v1:00000000000000000000000000000000";
    
    @Mock
    private AgentOperationService operationService;
    
    @Mock
    private AgentPersistenceService persistenceService;
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private AgentRuntimeRegistryService runtimeRegistryService;
    
    @Mock
    private AiResourceSearchService searchService;
    
    private AgentDiscoveryApplicationService service;
    
    @BeforeEach
    void setUp() {
        service = new AgentDiscoveryApplicationService(operationService, persistenceService,
            resourceManager, runtimeRegistryService, searchService,
            new AgentSearchModeResolver(AiResourceSearchReadinessService.NOOP,
                () -> AgentSearchMode.SCAN.name()));
    }
    
    @Test
    void testSearchFiltersSortsAndPaginatesCompleteCatalogs() throws NacosException {
        QueryCondition condition = new QueryCondition();
        AgentSearchRequest request = searchRequest();
        request.setAgentNameContains("Agent");
        request.setTagsAll(Collections.singletonList("team"));
        request.setProtocolsAny(Collections.singletonList("a2a"));
        request.setPageNo(1);
        request.setPageSize(1);
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        
        AgentSummary beta = summary("beta-Agent", true, "2.0.0",
            Arrays.asList(
                catalog("1.0.0", null, "a2a"),
                catalog("2.0.0", Arrays.asList(AiResourceConstants.LABEL_LATEST, "stable"),
                    "a2a")),
            Collections.singletonList("team"));
        beta.setDisplayName("Beta");
        beta.setDescription("Beta Agent");
        beta.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        beta.setProvider(provider);
        
        AgentSummary disabled = summary("disabled-Agent", false, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "a2a")),
            Collections.singletonList("team"));
        AgentSummary noCatalog = summary("catalog-Agent", true, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "a2a")),
            Collections.singletonList("team"));
        noCatalog.setVersionCatalog(null);
        AgentSummary wrongName = summary("lower-agent", true, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "a2a")),
            Collections.singletonList("team"));
        AgentSummary wrongTags = summary("tags-Agent", true, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "a2a")), null);
        AgentSummary wrongProtocol = summary("protocol-Agent", true, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "grpc")),
            Collections.singletonList("team"));
        AgentSummary alpha = summary("alpha-Agent", true, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "a2a")),
            Collections.singletonList("team"));
        
        when(persistenceService.listAgents(condition, 1, 100)).thenReturn(sourcePage(1, 2,
            beta, disabled, noCatalog, wrongName, wrongTags, wrongProtocol));
        when(persistenceService.listAgents(condition, 2, 100))
            .thenReturn(sourcePage(2, 2, alpha));
        
        Page<AgentCatalogEntry> result = service.search(request);
        
        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getPagesAvailable());
        assertEquals(1, result.getPageItems().size());
        assertEquals("alpha-Agent", result.getPageItems().get(0).getAgentName());
        
        request.setPageNo(2);
        Page<AgentCatalogEntry> second = service.search(request);
        AgentCatalogEntry betaResult = second.getPageItems().get(0);
        assertEquals("beta-Agent", betaResult.getAgentName());
        assertEquals("Beta", betaResult.getDisplayName());
        assertEquals("Beta Agent", betaResult.getDescription());
        assertEquals("https://example.com/icon.png", betaResult.getIconUrl());
        assertSame(provider, betaResult.getProvider());
        assertEquals(Collections.singletonList("team"), betaResult.getTags());
        assertEquals("2.0.0", betaResult.getLatestVersion());
        assertEquals("2.0.0", betaResult.getVersions().get(0).getVersion());
        assertEquals(Collections.singletonList("stable"),
            betaResult.getVersions().get(0).getLabels());
        assertNull(betaResult.getVersions().get(1).getLabels());
        assertEquals(Collections.singletonList("a2a"),
            betaResult.getVersions().get(1).getProtocols());
        
        request.setPageNo(Integer.MAX_VALUE);
        assertTrue(service.search(request).getPageItems().isEmpty());
        verify(persistenceService, times(3)).listAgents(condition, 1, 100);
        verify(persistenceService, times(3)).listAgents(condition, 2, 100);
    }
    
    @Test
    void testSearchUsesDefaultsAndAllowsNoProtocolFilter() throws NacosException {
        QueryCondition condition = new QueryCondition();
        AgentSearchRequest request = searchRequest();
        AgentSummary summary = summary("plain-agent", true, "1.0.0",
            Collections.singletonList(catalog("1.0.0", null, "a2a")), null);
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        when(persistenceService.listAgents(condition, 1, 100))
            .thenReturn(sourcePage(1, 1, summary));
        
        Page<AgentCatalogEntry> result = service.search(request);
        
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getTotalCount());
        assertEquals("plain-agent", result.getPageItems().get(0).getAgentName());
        assertNull(result.getPageItems().get(0).getTags());
    }
    
    @Test
    void testSearchReturnsVisibilityEmptyPageWithoutPersistenceRead() throws NacosException {
        QueryCondition condition = new QueryCondition();
        condition.setAlwaysEmpty(true);
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        AgentSearchRequest request = searchRequest();
        request.setPageNo(3);
        
        Page<AgentCatalogEntry> result = service.search(request);
        
        assertEquals(3, result.getPageNumber());
        assertEquals(0, result.getTotalCount());
        assertTrue(result.getPageItems().isEmpty());
        verifyNoInteractions(persistenceService);
    }
    
    @Test
    void testSearchIndexMapsTypedPredicatesAndCompleteCatalog() throws NacosException {
        AgentSearchModeResolver resolver = mock(AgentSearchModeResolver.class);
        when(resolver.resolve()).thenReturn(AgentSearchMode.INDEX);
        service = new AgentDiscoveryApplicationService(operationService, persistenceService,
            resourceManager, runtimeRegistryService, searchService, resolver);
        AgentSearchRequest request = searchRequest();
        request.setAgentNameContains("Agent%_\\A");
        request.setTagsAll(Arrays.asList("team", "blue"));
        request.setProtocolsAny(Arrays.asList("a2a", "mcp"));
        request.setPageNo(2);
        request.setPageSize(2);
        
        AgentVersionCatalog versionCatalog = new AgentVersionCatalog();
        versionCatalog.setLatestVersion("2.0.0");
        versionCatalog.setOnlineVersions(Arrays.asList(
            catalog("1.0.0", null, "a2a"),
            catalog("2.0.0", Arrays.asList(AiResourceConstants.LABEL_LATEST, "stable"),
                "a2a", "mcp")));
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("iconUrl", "https://example.com/icon.png");
        metadata.put("provider", provider);
        metadata.put("tags", null);
        metadata.put("versionCatalog", versionCatalog);
        AiResourceSearchResult indexed = indexedResult("beta-Agent", metadata);
        when(searchService.numberedList(any(Query.class))).thenReturn(
            new NumberedPage(Collections.singletonList(indexed), 3L, 2, 2));
        
        Page<AgentCatalogEntry> result = service.search(request);
        
        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getPageNumber());
        assertEquals(2, result.getPagesAvailable());
        AgentCatalogEntry item = result.getPageItems().get(0);
        assertEquals("beta-Agent", item.getAgentName());
        assertEquals("Beta Agent", item.getDisplayName());
        assertEquals("Indexed Agent", item.getDescription());
        assertEquals("https://example.com/icon.png", item.getIconUrl());
        assertEquals("Nacos", item.getProvider().getName());
        assertNull(item.getTags());
        assertEquals("2.0.0", item.getLatestVersion());
        assertEquals("2.0.0", item.getVersions().get(0).getVersion());
        assertEquals(Collections.singletonList("stable"),
            item.getVersions().get(0).getLabels());
        assertEquals("1.0.0", item.getVersions().get(1).getVersion());
        
        org.mockito.ArgumentCaptor<Query> queryCaptor =
            org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(searchService).numberedList(queryCaptor.capture());
        Query query = queryCaptor.getValue();
        assertEquals(NAMESPACE_ID, query.getNamespaceId());
        assertEquals(Collections.singletonList(Constants.Agent.RESOURCE_TYPE_AGENT),
            query.getResourceTypes());
        assertEquals(2, query.getPageNumber());
        assertEquals(2, query.getPageSize());
        assertPredicate(query.getPredicates().get(0), "resourceName",
            PredicateOperator.LITERAL_CONTAINS, Collections.singletonList("Agent%_\\A"));
        assertPredicate(query.getPredicates().get(1), "tags", PredicateOperator.EXACT_ALL,
            Arrays.asList("team", "blue"));
        assertPredicate(query.getPredicates().get(2), "metadata.protocols",
            PredicateOperator.EXACT_ANY, Arrays.asList("a2a", "mcp"));
        verifyNoInteractions(resourceManager, persistenceService);
    }
    
    @Test
    void testSearchIndexUsesDefaultsAndLegacyTagFallback() throws NacosException {
        AgentSearchModeResolver resolver = mock(AgentSearchModeResolver.class);
        when(resolver.resolve()).thenReturn(AgentSearchMode.INDEX);
        service = new AgentDiscoveryApplicationService(operationService, persistenceService,
            resourceManager, runtimeRegistryService, searchService, resolver);
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setLatestVersion("1.0.0");
        catalog.setOnlineVersions(
            Collections.singletonList(catalog("1.0.0", null, "a2a")));
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("versionCatalog", catalog);
        AiResourceSearchResult indexed = indexedResult("legacy-Agent", metadata);
        indexed.setTags(Collections.singletonList("legacy"));
        when(searchService.numberedList(any(Query.class))).thenReturn(
            new NumberedPage(Collections.singletonList(indexed), 1L, 1, 1));
        
        Page<AgentCatalogEntry> result = service.search(searchRequest());
        
        assertEquals(Collections.singletonList("legacy"),
            result.getPageItems().get(0).getTags());
        org.mockito.ArgumentCaptor<Query> queryCaptor =
            org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(searchService).numberedList(queryCaptor.capture());
        assertEquals(1, queryCaptor.getValue().getPageNumber());
        assertEquals(20, queryCaptor.getValue().getPageSize());
        assertTrue(queryCaptor.getValue().getPredicates().isEmpty());
    }
    
    @Test
    void testSearchIndexFailsWithoutFallbackForUnavailableInvalidOrOversizedIndex()
        throws NacosException {
        AgentSearchModeResolver resolver = mock(AgentSearchModeResolver.class);
        when(resolver.resolve()).thenReturn(AgentSearchMode.INDEX);
        service = new AgentDiscoveryApplicationService(operationService, persistenceService,
            resourceManager, runtimeRegistryService, searchService, resolver);
        NacosException failure = new NacosException(NacosException.SERVER_ERROR, "index failed");
        when(searchService.numberedList(any(Query.class))).thenThrow(failure);
        assertSame(failure, assertThrows(NacosException.class,
            () -> service.search(searchRequest())));
        verifyNoInteractions(resourceManager, persistenceService);
        
        resetSearchService();
        AiResourceSearchResult invalid = indexedResult("invalid-Agent",
            Collections.emptyMap());
        when(searchService.numberedList(any(Query.class))).thenReturn(
            new NumberedPage(Collections.singletonList(invalid), 1L, 1, 1));
        NacosApiException invalidError = assertThrows(NacosApiException.class,
            () -> service.search(searchRequest()));
        assertEquals(NacosException.SERVER_ERROR, invalidError.getErrCode());
        
        resetSearchService();
        when(searchService.numberedList(any(Query.class))).thenReturn(
            new NumberedPage(Collections.emptyList(), (long) Integer.MAX_VALUE + 1L, 1, 1));
        NacosApiException oversized = assertThrows(NacosApiException.class,
            () -> service.search(searchRequest()));
        assertEquals(NacosException.SERVER_ERROR, oversized.getErrCode());
    }
    
    @Test
    void testSearchIndexReportsUnavailableRuntimeAndProductionConstructor() throws Exception {
        AgentSearchModeResolver resolver = mock(AgentSearchModeResolver.class);
        when(resolver.resolve()).thenReturn(AgentSearchMode.INDEX);
        service = new AgentDiscoveryApplicationService(operationService, persistenceService,
            resourceManager, runtimeRegistryService, (AiResourceSearchService) null, resolver);
        NacosException unavailable = assertThrows(NacosException.class,
            () -> service.search(searchRequest()));
        assertEquals(503, unavailable.getErrCode());
        
        @SuppressWarnings("unchecked")
        ObjectProvider<AiResourceSearchService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(searchService);
        AgentDiscoveryApplicationService injected = new AgentDiscoveryApplicationService(
            operationService, persistenceService, resourceManager, runtimeRegistryService,
            provider, resolver);
        assertNotNull(injected);
    }
    
    @Test
    void testDiscoverResolvesLatestCombinesSourcesSortsAndCachesContent()
        throws NacosException {
        Agent agent = enabledAgent();
        AiResourceVersion row = onlineVersion(DIGEST);
        AgentVersionDetail detail = detail(DIGEST, completeInterfaces());
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(row);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail);
        AgentDiscoveryEndpoint runtimeEndpoint = discoveryEndpoint(
            endpoint("HTTPS://Runtime.Example.com/agent", "http", 0, null, false), VERSION);
        when(runtimeRegistryService.getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME, "a2a",
            Collections.singletonList(VERSION))).thenReturn(endpointSet(EndpointSource.RUNTIME,
                RUNTIME_REVISION,
                Collections.singletonList(runtimeEndpoint)));
        
        AgentDiscoveryResult first = service.discover(discoveryRequest(null, null, null));
        
        assertEquals(VERSION, first.getVersion());
        assertEquals(DIGEST, first.getContentDigest());
        assertEquals(2, first.getCallInterfaces().size());
        AgentDiscoveryCallInterface a2a = first.getCallInterfaces().get(0);
        assertEquals("a2a", a2a.getProtocol());
        assertEquals("1.0", a2a.getProtocolVersion());
        assertEquals("application/json", a2a.getDescriptorMediaType());
        assertEquals(Collections.singletonMap("name", "demo"), a2a.getNativeDescriptor());
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            Arrays.asList(a2a.getEndpointSets().get(0).getSource(),
                a2a.getEndpointSets().get(1).getSource()));
        assertFalse(a2a.getEndpointSets().get(0).getEndpoints().get(0).getHealthy());
        assertEquals(VERSION, a2a.getEndpointSets().get(0).getEndpoints().get(0).getBindings()
            .get(0).getRuntimeVersion());
        List<AgentDiscoveryEndpoint> declared =
            a2a.getEndpointSets().get(1).getEndpoints();
        assertEquals("https://a.example.com:443/agent", declared.get(0).getUri());
        assertEquals("https://b.example.com:443/agent", declared.get(1).getUri());
        assertEquals("https://z.example.com:443/agent", declared.get(2).getUri());
        assertEquals(DIGEST, a2a.getEndpointSets().get(1).getSourceRevision());
        assertTrue(first.getCallInterfaces().get(1).getEndpointSets().get(0).getEndpoints()
            .isEmpty());
        assertEquals(first.getVersion(),
            service.discover(discoveryRequest(null, null, null)).getVersion());
        verify(persistenceService).getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION);
        verify(runtimeRegistryService, times(2)).getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME,
            "a2a", Collections.singletonList(VERSION));
    }
    
    @Test
    void testDiscoverSeparatesDefaultOnlinePoolFromExplicitLatest() throws NacosException {
        Agent agent = enabledAgent();
        agent.getVersionCatalog().setOnlineVersions(Arrays.asList(
            catalog(VERSION, null, "a2a"), catalog("1.0.0", null, "a2a")));
        stubDiscover(agent, DIGEST, Collections.singletonList(callInterface("a2a", "1.0",
            Collections.singletonList(EndpointSource.RUNTIME), null)));
        AgentDiscoveryEndpoint versionOne = discoveryEndpoint(
            endpoint("https://v1.example.com/agent", "http", 0, null, true), "1.0.0");
        AgentDiscoveryEndpoint versionTwo = discoveryEndpoint(
            endpoint("https://v2.example.com/agent", "http", 0, null, true), VERSION);
        when(runtimeRegistryService.getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME, "a2a",
            Arrays.asList(VERSION, "1.0.0"))).thenReturn(endpointSet(EndpointSource.RUNTIME,
                RUNTIME_REVISION, Arrays.asList(versionOne, versionTwo)));
        when(runtimeRegistryService.getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME, "a2a",
            Collections.singletonList(VERSION))).thenReturn(endpointSet(EndpointSource.RUNTIME,
                "murmur3-x64-128-v1:11111111111111111111111111111111",
                Collections.singletonList(versionTwo)));
        
        AgentDiscoveryResult defaultResult =
            service.discover(discoveryRequest(null, null, null));
        AgentDiscoveryResult latestResult =
            service.discover(discoveryRequest(null, AiResourceConstants.LABEL_LATEST, null));
        
        assertEquals(VERSION, defaultResult.getVersion());
        assertEquals(2, defaultResult.getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().size());
        assertEquals(1, latestResult.getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().size());
        assertEquals(VERSION, latestResult.getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().get(0).getBindings().get(0).getRuntimeVersion());
    }
    
    @Test
    void testDiscoverAppliesInterfaceSourceTransportAndMetadataFilters()
        throws NacosException {
        List<Endpoint> endpoints = Arrays.asList(
            endpoint("https://grpc.example.com/agent", "grpc", 0,
                Collections.singletonMap("zone", "cn"), null),
            endpoint("https://none.example.com/agent", "http", 0, null, null),
            endpoint("https://us.example.com/agent", "http", 0,
                Collections.singletonMap("zone", "us"), null),
            endpoint("https://cn.example.com/agent", "http", 0,
                Collections.singletonMap("zone", "cn"), null));
        AgentCallInterface a2a =
            callInterface("a2a", "1.0", Arrays.asList(EndpointSource.RUNTIME,
                EndpointSource.DECLARED), endpoints);
        AgentCallInterface other = callInterface("other", "1.0",
            Collections.singletonList(EndpointSource.DECLARED), endpoints);
        stubDiscover(DIGEST, Arrays.asList(a2a, other));
        
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.singletonList("a2a"));
        filter.setProtocolVersion("1.0");
        filter.setEndpointSources(Collections.singletonList(EndpointSource.DECLARED));
        filter.setTransports(Collections.singletonList("http"));
        filter.setMetadataSelector(Collections.singletonMap("zone", "cn"));
        
        AgentDiscoveryResult result =
            service.discover(discoveryRequest(VERSION, null, filter));
        
        assertEquals(1, result.getCallInterfaces().size());
        assertEquals(1, result.getCallInterfaces().get(0).getEndpointSets().size());
        List<AgentDiscoveryEndpoint> filtered =
            result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints();
        assertEquals(1, filtered.size());
        assertEquals("https://cn.example.com:443/agent", filtered.get(0).getUri());
        verifyNoInteractions(runtimeRegistryService);
        
        filter.setMetadataSelector(Collections.<String, String>emptyMap());
        AgentDiscoveryResult withoutMetadata =
            service.discover(discoveryRequest(VERSION, null, filter));
        assertEquals(3, withoutMetadata.getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().size());
    }
    
    @Test
    void testDiscoverReturnsTypedEmptyInterfaceResultsForFilterMismatch()
        throws NacosException {
        stubDiscover(DIGEST, Collections.singletonList(callInterface("a2a", "1.0",
            Collections.singletonList(EndpointSource.DECLARED),
            Collections.singletonList(endpoint("https://example.com/agent", "http", 0,
                null, null)))));
        AgentDiscoveryFilter protocols = new AgentDiscoveryFilter();
        protocols.setProtocols(Collections.singletonList("other"));
        AgentDiscoveryFilter protocolVersion = new AgentDiscoveryFilter();
        protocolVersion.setProtocolVersion("2.0");
        
        AgentDiscoveryResult noProtocol =
            service.discover(discoveryRequest(VERSION, null, protocols));
        AgentDiscoveryResult noProtocolVersion =
            service.discover(discoveryRequest(VERSION, null, protocolVersion));
        
        assertTrue(noProtocol.getCallInterfaces().isEmpty());
        assertTrue(noProtocolVersion.getCallInterfaces().isEmpty());
    }
    
    @Test
    void testDiscoverResolvesCustomLabel() throws NacosException {
        Agent agent = enabledAgent();
        agent.getVersionInfo().getLabels().put("stable", VERSION);
        stubDiscover(agent, DIGEST, Collections.singletonList(callInterface("a2a", "1.0",
            Collections.singletonList(EndpointSource.DECLARED), null)));
        
        AgentDiscoveryResult result =
            service.discover(discoveryRequest(null, "stable", null));
        
        assertEquals(VERSION, result.getVersion());
    }
    
    @Test
    void testDiscoverHidesDisabledMissingLabelAndMissingVersionInfo() throws NacosException {
        Agent disabled = enabledAgent();
        disabled.setStatus(AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(disabled);
        NacosApiException disabledError = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(null, null, null)));
        assertEquals(NacosException.NOT_FOUND, disabledError.getErrCode());
        verifyNoInteractions(persistenceService);
        
        Agent missingLabel = enabledAgent();
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(missingLabel);
        NacosApiException labelError = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(null, "missing", null)));
        assertEquals(NacosException.NOT_FOUND, labelError.getErrCode());
        
        Agent missingInfo = enabledAgent();
        missingInfo.setVersionInfo(null);
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(missingInfo);
        NacosApiException infoError = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(null, "stable", null)));
        assertEquals(NacosException.NOT_FOUND, infoError.getErrCode());
        
        Agent missingCatalog = enabledAgent();
        missingCatalog.setVersionCatalog(null);
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(missingCatalog);
        NacosApiException catalogError = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(null, null, null)));
        assertEquals(NacosException.NOT_FOUND, catalogError.getErrCode());
    }
    
    @Test
    void testDiscoverHidesOfflineVersion() throws NacosException {
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(enabledAgent());
        AiResourceVersion row = onlineVersion(DIGEST);
        row.setStatus(AiConstants.Agent.VERSION_STATUS_OFFLINE);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(row);
        
        NacosApiException error = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(VERSION, null, null)));
        
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
        verify(persistenceService, never()).getAgentVersion(any(), any(), any());
    }
    
    @Test
    void testDiscoverHidesVersionTakenOfflineDuringContentLoad() throws NacosException {
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(enabledAgent());
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(onlineVersion(DIGEST));
        AgentVersionDetail offline = detail(DIGEST, Collections.singletonList(
            callInterface("a2a", "1.0", Collections.singletonList(EndpointSource.DECLARED),
                null)));
        offline.setStatus(AiConstants.Agent.VERSION_STATUS_OFFLINE);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(offline);
        
        NacosApiException error = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(VERSION, null, null)));
        
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
        verifyNoInteractions(runtimeRegistryService);
    }
    
    @Test
    void testDiscoverMapsInvalidStoredDescriptorToServerError() throws NacosException {
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(enabledAgent());
        AiResourceVersion row = onlineVersion(DIGEST);
        row.setStorage("{}");
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(row);
        
        NacosApiException error = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(VERSION, null, null)));
        
        assertEquals(NacosException.SERVER_ERROR, error.getErrCode());
        assertTrue(error.getCause() instanceof IllegalArgumentException);
    }
    
    @Test
    void testDiscoverRejectsDigestChangeDuringContentLoad() throws NacosException {
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(enabledAgent());
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(onlineVersion(DIGEST));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail(OTHER_DIGEST, Collections.singletonList(callInterface("a2a",
                "1.0", Collections.singletonList(EndpointSource.DECLARED), null))));
        
        NacosApiException error = assertThrows(NacosApiException.class,
            () -> service.discover(discoveryRequest(VERSION, null, null)));
        
        assertEquals(NacosException.SERVER_ERROR, error.getErrCode());
        assertNull(error.getCause());
    }
    
    private AiResourceSearchResult indexedResult(String agentName,
        Map<String, Object> metadata) {
        AiResourceSearchResult result = new AiResourceSearchResult();
        result.setNamespaceId(NAMESPACE_ID);
        result.setResourceType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setResourceName(agentName);
        result.setResourceVersion("2.0.0");
        result.setDisplayName("Beta Agent");
        result.setDescription("Indexed Agent");
        result.setMetadata(metadata);
        return result;
    }
    
    private void assertPredicate(Predicate predicate, String field,
        PredicateOperator operator, List<String> values) {
        assertEquals(field, predicate.getField());
        assertEquals(operator, predicate.getOperator());
        assertEquals(values, predicate.getValues());
        assertTrue(predicate.isCaseSensitive());
    }
    
    private void resetSearchService() {
        org.mockito.Mockito.reset(searchService);
    }
    
    private void stubDiscover(String digest, List<AgentCallInterface> interfaces)
        throws NacosException {
        stubDiscover(enabledAgent(), digest, interfaces);
    }
    
    private void stubDiscover(Agent agent, String digest, List<AgentCallInterface> interfaces)
        throws NacosException {
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(onlineVersion(digest));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail(digest, interfaces));
    }
    
    private AgentSearchRequest searchRequest() {
        AgentSearchRequest result = new AgentSearchRequest();
        result.setNamespaceId(NAMESPACE_ID);
        return result;
    }
    
    private AgentDiscoveryRequest discoveryRequest(String version, String label,
        AgentDiscoveryFilter filter) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(AGENT_NAME);
        reference.setVersion(version);
        reference.setLabel(label);
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId(NAMESPACE_ID);
        result.setReference(reference);
        result.setFilter(filter);
        return result;
    }
    
    private Agent enabledAgent() {
        AgentVersionInfo versionInfo = new AgentVersionInfo();
        versionInfo.setLabels(new LinkedHashMap<String, String>());
        versionInfo.getLabels().put(AiResourceConstants.LABEL_LATEST, VERSION);
        Agent result = new Agent();
        result.setAgentName(AGENT_NAME);
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        result.setVersionInfo(versionInfo);
        AgentVersionCatalog versionCatalog = new AgentVersionCatalog();
        versionCatalog.setLatestVersion(VERSION);
        versionCatalog.setOnlineVersions(
            Collections.singletonList(catalog(VERSION, null, "a2a")));
        result.setVersionCatalog(versionCatalog);
        return result;
    }
    
    private AiResourceVersion onlineVersion(String digest) {
        AiResourceVersion result = new AiResourceVersion();
        result.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        result.setStorage(storage(digest));
        return result;
    }
    
    private String storage(String digest) {
        AgentVersionStorageDescriptor descriptor = new AgentVersionStorageDescriptor();
        descriptor.setProvider(AgentVersionStorageDescriptorSerializer.NACOS_CONFIG_PROVIDER);
        descriptor.setKey("agent-version/demo");
        descriptor.setKeyFormat(
            AgentVersionStorageDescriptorSerializer.NACOS_CONFIG_KEY_FORMAT);
        descriptor.setAgentNameCodec(
            AgentVersionStorageDescriptorSerializer.RAD_ASCII_AGENT_NAME_CODEC);
        descriptor.setContentDigest(digest);
        descriptor.setMediaType(
            AgentVersionStorageDescriptorSerializer.AGENT_VERSION_MEDIA_TYPE);
        descriptor.setSchemaVersion(
            AgentVersionStorageDescriptorSerializer.SCHEMA_VERSION);
        descriptor.setSize(100L);
        return AgentVersionStorageDescriptorSerializer.serialize(descriptor);
    }
    
    private AgentVersionDetail detail(String digest, List<AgentCallInterface> interfaces) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        result.setContentDigest(digest);
        result.setCallInterfaces(interfaces);
        return result;
    }
    
    private List<AgentCallInterface> completeInterfaces() {
        List<Endpoint> declared = Arrays.asList(
            endpoint("https://z.example.com/agent", "http", 1, null, null),
            endpoint("https://b.example.com/agent", "http", 0, null, null),
            endpoint("https://a.example.com/agent", "http", 0, null, null));
        AgentCallInterface a2a = callInterface("a2a", "1.0",
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED), declared);
        AgentCallInterface jsonRpc = callInterface("json-rpc", null,
            Collections.singletonList(EndpointSource.DECLARED), null);
        return Arrays.asList(a2a, jsonRpc);
    }
    
    private AgentCallInterface callInterface(String protocol, String protocolVersion,
        List<EndpointSource> sources, List<Endpoint> endpoints) {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(protocol);
        result.setProtocolVersion(protocolVersion);
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(Collections.singletonMap("name", "demo"));
        result.setEndpointSourceOrder(sources);
        result.setDeclaredEndpoints(endpoints);
        return result;
    }
    
    private EndpointSet endpointSet(EndpointSource source, String revision,
        List<AgentDiscoveryEndpoint> endpoints) {
        EndpointSet result = new EndpointSet();
        result.setSource(source);
        result.setSourceRevision(revision);
        result.setEndpoints(endpoints);
        return result;
    }
    
    private AgentDiscoveryEndpoint discoveryEndpoint(Endpoint source, String runtimeVersion) {
        AgentDiscoveryEndpoint result = new AgentDiscoveryEndpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        result.setPriority(source.getPriority());
        result.setWeight(source.getWeight());
        result.setMetadata(source.getMetadata());
        result.setHealthy(source.getHealthy());
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion(runtimeVersion);
        binding.setVersionRange('[' + runtimeVersion + ']');
        result.setBindings(Collections.singletonList(binding));
        return result;
    }
    
    private Endpoint endpoint(String uri, String transport, Integer priority,
        Map<String, String> metadata, Boolean healthy) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport(transport);
        result.setPriority(priority);
        result.setMetadata(metadata);
        result.setHealthy(healthy);
        return result;
    }
    
    private AgentSummary summary(String agentName, boolean enabled, String latest,
        List<AgentVersionCatalogEntry> versions, List<String> tags) {
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setLatestVersion(latest);
        catalog.setOnlineVersions(versions);
        AgentSummary result = new AgentSummary();
        result.setAgentName(agentName);
        result.setStatus(enabled ? AiConstants.Agent.RESOURCE_STATUS_ENABLE
            : AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        result.setVersionCatalog(catalog);
        result.setTags(tags);
        return result;
    }
    
    private AgentVersionCatalogEntry catalog(String version, List<String> labels,
        String... protocols) {
        AgentVersionCatalogEntry result = new AgentVersionCatalogEntry();
        result.setVersion(version);
        result.setLabels(labels);
        result.setProtocols(Arrays.asList(protocols));
        return result;
    }
    
    private Page<AgentSummary> sourcePage(int pageNo, int pagesAvailable,
        AgentSummary... summaries) {
        Page<AgentSummary> result = new Page<AgentSummary>();
        result.setPageNumber(pageNo);
        result.setPagesAvailable(pagesAvailable);
        result.setTotalCount(summaries.length);
        result.setPageItems(new ArrayList<AgentSummary>(Arrays.asList(summaries)));
        return result;
    }
}
