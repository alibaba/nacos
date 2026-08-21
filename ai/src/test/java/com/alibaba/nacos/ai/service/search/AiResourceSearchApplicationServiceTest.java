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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.agentspecs.client.AgentSpecSearchForm;
import com.alibaba.nacos.ai.form.mcp.client.McpSearchForm;
import com.alibaba.nacos.ai.form.search.client.AiResourcePageSearchForm;
import com.alibaba.nacos.ai.form.search.client.AiResourceSearchForm;
import com.alibaba.nacos.ai.model.search.AiResourceSearchResult;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.NumberedPage;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Predicate;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.PredicateOperator;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Query;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.search.AiResourceSearchItem;
import com.alibaba.nacos.api.ai.model.search.AiResourceSearchResponse;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiResourceSearchApplicationService}.
 */
@ExtendWith(MockitoExtension.class)
class AiResourceSearchApplicationServiceTest {
    
    @Mock
    private AiResourceSearchService searchService;
    
    @Mock
    private AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    @Mock
    private ObjectProvider<AiResourceSearchService> searchServiceProvider;
    
    @Mock
    private ObjectProvider<AiResourceSearchTypeHandlerRegistry> typeHandlerRegistryProvider;
    
    private AiResourceSearchApplicationService applicationService;
    
    @BeforeEach
    void setUp() {
        applicationService = new AiResourceSearchApplicationService(searchService,
            typeHandlerRegistry);
    }
    
    @Test
    void searchShouldListAllRegisteredTypesAndMapCursorPage() throws Exception {
        when(typeHandlerRegistry.resourceTypes()).thenReturn(Arrays.asList("agent", "skill"));
        when(searchService.list(any())).thenReturn(new AiResourceSearchService.Page(
            Collections.singletonList(result("skill", "research", "1.0.0")), "next"));
        AiResourceSearchForm form = genericForm();
        
        AiResourceSearchResponse response = applicationService.search(form);
        
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchService).list(captor.capture());
        verify(searchService, never()).search(any());
        Query query = captor.getValue();
        assertEquals(Arrays.asList("agent", "skill"), query.getResourceTypes());
        assertEquals("cursor", query.getCursor());
        assertEquals(10, query.getLimit());
        assertEquals(2, query.getPredicates().size());
        assertPredicate(query.getPredicates().get(0), "tags", PredicateOperator.EXACT_ALL,
            Arrays.asList("research", "java"));
        assertPredicate(query.getPredicates().get(1), "capabilities",
            PredicateOperator.EXACT_ANY, Collections.singletonList("streaming"));
        assertEquals("next", response.getNextCursor());
        AiResourceSearchItem item = response.getItems().get(0);
        assertEquals("public", item.getNamespaceId());
        assertEquals("skill", item.getResourceType());
        assertEquals("research", item.getResourceName());
        assertEquals("Research", item.getDisplayName());
        assertEquals(1000L, item.getCreateTime());
        assertEquals(2000L, item.getUpdateTime());
        assertEquals(88, item.getScore());
    }
    
    @Test
    void searchShouldUseRelevanceCoreAndNormalizeRequestedTypes() throws Exception {
        when(typeHandlerRegistry.resourceTypes()).thenReturn(Arrays.asList("agent", "skill"));
        when(searchService.search(any())).thenReturn(new AiResourceSearchService.Page(
            Collections.emptyList(), null));
        AiResourceSearchForm form = genericForm();
        form.setQuery("research");
        form.setResourceTypes(Arrays.asList("SKILL", "skill"));
        
        applicationService.search(form);
        
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchService).search(captor.capture());
        verify(searchService, never()).list(any());
        assertEquals(Collections.singletonList("skill"), captor.getValue().getResourceTypes());
        assertEquals("research", captor.getValue().getText());
    }
    
    @Test
    void searchShouldRejectUnsupportedTypeAndUnavailableRuntime() {
        when(typeHandlerRegistry.resourceTypes()).thenReturn(Collections.singletonList("skill"));
        AiResourceSearchForm form = genericForm();
        form.setResourceTypes(Collections.singletonList("unknown"));
        
        NacosException invalid = assertThrows(NacosException.class,
            () -> applicationService.search(form));
        assertEquals(NacosException.INVALID_PARAM, invalid.getErrCode());
        
        AiResourceSearchApplicationService unavailable =
            new AiResourceSearchApplicationService(null, typeHandlerRegistry);
        NacosException exception = assertThrows(NacosException.class,
            () -> unavailable.search(genericForm()));
        assertEquals(503, exception.getErrCode());
        
        AiResourceSearchApplicationService missingRegistry =
            new AiResourceSearchApplicationService(searchService, null);
        NacosException missingRegistryException = assertThrows(NacosException.class,
            () -> missingRegistry.search(genericForm()));
        assertEquals(503, missingRegistryException.getErrCode());
    }
    
    @Test
    void publicConstructorShouldResolveOptionalSearchComponents() throws Exception {
        when(searchServiceProvider.getIfAvailable()).thenReturn(searchService);
        when(typeHandlerRegistryProvider.getIfAvailable()).thenReturn(typeHandlerRegistry);
        when(typeHandlerRegistry.resourceTypes()).thenReturn(Collections.singletonList("skill"));
        when(searchService.list(any())).thenReturn(new AiResourceSearchService.Page(
            Collections.emptyList(), null));
        
        AiResourceSearchApplicationService service = new AiResourceSearchApplicationService(
            searchServiceProvider, typeHandlerRegistryProvider);
        
        assertTrue(service.search(genericForm()).getItems().isEmpty());
    }
    
    @Test
    void springContextShouldSelectProviderConstructor() {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.registerBean(AiResourceSearchService.class, () -> searchService);
            context.registerBean(AiResourceSearchTypeHandlerRegistry.class,
                () -> typeHandlerRegistry);
            context.register(AiResourceSearchApplicationService.class);
            
            context.refresh();
            
            assertNotNull(context.getBean(AiResourceSearchApplicationService.class));
        }
    }
    
    @Test
    void searchAgentSpecsShouldUseLiteralAndTagPredicates() throws Exception {
        when(searchService.numberedList(any())).thenReturn(new NumberedPage(
            Collections.singletonList(result(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC,
                "planner", "2.0.0")),
            1, 2, 3));
        AgentSpecSearchForm form = new AgentSpecSearchForm();
        form.setNamespaceId("public");
        form.setKeyword("plan%_");
        form.setTagsAll(Collections.singletonList("workflow"));
        
        Page<?> page = applicationService.searchAgentSpecs(form, 2, 5);
        
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchService).numberedList(captor.capture());
        Query query = captor.getValue();
        assertEquals(Collections.singletonList(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC),
            query.getResourceTypes());
        assertEquals(2, query.getPageNumber());
        assertEquals(5, query.getPageSize());
        assertPredicate(query.getPredicates().get(0), "tags", PredicateOperator.EXACT_ALL,
            Collections.singletonList("workflow"));
        assertPredicate(query.getPredicates().get(1), "resourceName",
            PredicateOperator.LITERAL_CONTAINS, Collections.singletonList("plan%_"));
        assertEquals(1, page.getTotalCount());
        assertEquals(3, page.getPagesAvailable());
    }
    
    @Test
    void searchSkillsShouldSelectListOrRelevanceAndMapPage() throws Exception {
        AiResourceSearchResult source = result(AiResourceConstants.RESOURCE_TYPE_SKILL,
            "research", "1.0.0");
        when(searchService.numberedList(any())).thenReturn(
            new NumberedPage(Collections.singletonList(source), 1, 1, 1));
        when(searchService.numberedSearch(any())).thenReturn(
            new NumberedPage(Collections.singletonList(source), 1, 1, 1));
        AiResourcePageSearchForm form = pageForm(null);
        
        Page<SkillBasicInfo> listed = applicationService.searchSkills(form, 1, 20);
        form.setQuery("research");
        Page<SkillBasicInfo> searched = applicationService.searchSkills(form, 1, 20);
        
        verify(searchService).numberedList(any());
        verify(searchService).numberedSearch(any());
        assertEquals("research", listed.getPageItems().get(0).getName());
        assertEquals("Research description", searched.getPageItems().get(0).getDescription());
        assertEquals(2000L, searched.getPageItems().get(0).getUpdateTime());
    }
    
    @Test
    void searchPromptsShouldMapPromptSummary() throws Exception {
        when(searchService.numberedSearch(any())).thenReturn(new NumberedPage(
            Collections.singletonList(result(AiResourceConstants.RESOURCE_TYPE_PROMPT,
                "prompt-key", "3.0.0")),
            1, 1, 1));
        
        Page<PromptMetaSummary> page = applicationService.searchPrompts(
            pageForm("research"), 1, 20);
        
        PromptMetaSummary prompt = page.getPageItems().get(0);
        assertEquals("prompt-key", prompt.getPromptKey());
        assertEquals("3.0.0", prompt.getLatestVersion());
        assertEquals(Arrays.asList("research", "java"), prompt.getBizTags());
        assertEquals(2000L, prompt.getGmtModified());
    }
    
    @Test
    void searchMcpShouldApplyTypedFiltersAndMapKnownCapabilities() throws Exception {
        AiResourceSearchResult source = result(AiResourceConstants.RESOURCE_TYPE_MCP,
            "mcp-id", "1.0.0");
        source.setCapabilities(Arrays.asList("tool", "future-capability"));
        source.setMetadata(Map.of("protocol", "stdio", "frontProtocol", "http"));
        when(searchService.numberedSearch(any())).thenReturn(
            new NumberedPage(Collections.singletonList(source), 1, 1, 1));
        McpSearchForm form = new McpSearchForm();
        form.setNamespaceId("public");
        form.setQuery("research");
        form.setTagsAll(Collections.singletonList("research"));
        form.setProtocolsAny(Collections.singletonList("stdio"));
        form.setCapabilitiesAny(Collections.singletonList("tool"));
        
        Page<McpServerBasicInfo> page = applicationService.searchMcpServers(form, 1, 20);
        
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchService).numberedSearch(captor.capture());
        assertEquals(3, captor.getValue().getPredicates().size());
        assertPredicate(captor.getValue().getPredicates().get(1), "metadata.protocol",
            PredicateOperator.EXACT_ANY, Collections.singletonList("stdio"));
        McpServerBasicInfo mcp = page.getPageItems().get(0);
        assertEquals("mcp-id", mcp.getId());
        assertEquals("Research", mcp.getName());
        assertEquals("stdio", mcp.getProtocol());
        assertEquals("http", mcp.getFrontProtocol());
        assertTrue(mcp.isEnabled());
        assertEquals(Collections.singletonList(McpCapability.TOOL), mcp.getCapabilities());
    }
    
    @Test
    void searchMcpShouldTolerateNullCapabilitiesFromSearchProvider() throws Exception {
        AiResourceSearchResult source = mock(AiResourceSearchResult.class);
        when(source.getResourceName()).thenReturn("mcp-id");
        when(source.getCapabilities()).thenReturn(null);
        when(searchService.numberedList(any())).thenReturn(
            new NumberedPage(Collections.singletonList(source), 1, 1, 1));
        McpSearchForm form = new McpSearchForm();
        form.setNamespaceId("public");
        
        McpServerBasicInfo mcp = applicationService.searchMcpServers(form, 1, 20)
            .getPageItems().get(0);
        
        assertTrue(mcp.getCapabilities().isEmpty());
    }
    
    private AiResourceSearchForm genericForm() {
        AiResourceSearchForm form = new AiResourceSearchForm();
        form.setNamespaceId("public");
        form.setResourceTypes(Collections.emptyList());
        form.setTagsAll(Arrays.asList("research", "java"));
        form.setCapabilitiesAny(Collections.singletonList("streaming"));
        form.setCursor("cursor");
        form.setLimit(10);
        return form;
    }
    
    private AiResourcePageSearchForm pageForm(String query) {
        AiResourcePageSearchForm form = new AiResourcePageSearchForm();
        form.setNamespaceId("public");
        form.setQuery(query);
        form.setTagsAll(Collections.singletonList("research"));
        return form;
    }
    
    private AiResourceSearchResult result(String resourceType, String resourceName,
        String version) {
        AiResourceSearchResult result = new AiResourceSearchResult();
        result.setNamespaceId("public");
        result.setResourceType(resourceType);
        result.setResourceName(resourceName);
        result.setResourceVersion(version);
        result.setDisplayName("Research");
        result.setDescription("Research description");
        result.setTags(Arrays.asList("research", "java"));
        result.setCapabilities(Collections.singletonList("streaming"));
        result.setRepresentativeQueries(Collections.singletonList("find papers"));
        result.setMetadata(Collections.singletonMap("owner", "nacos"));
        result.setGmtCreate(new Timestamp(1000L));
        result.setGmtModified(new Timestamp(2000L));
        result.setScore(88);
        return result;
    }
    
    private void assertPredicate(Predicate predicate, String field,
        PredicateOperator operator, List<String> values) {
        assertEquals(field, predicate.getField());
        assertEquals(operator, predicate.getOperator());
        assertEquals(values, predicate.getValues());
        assertFalse(predicate.isCaseSensitive());
    }
}
