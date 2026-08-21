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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.search.AiResourceSearchItem;
import com.alibaba.nacos.api.ai.model.search.AiResourceSearchResponse;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * HTTP-facing application facade over the protocol-neutral AI Resource Search Core.
 *
 * @author Nacos
 */
@Service
public class AiResourceSearchApplicationService {
    
    private static final int SERVICE_UNAVAILABLE_STATUS = 503;
    
    private final AiResourceSearchService searchService;
    
    private final AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    @Autowired
    public AiResourceSearchApplicationService(
        ObjectProvider<AiResourceSearchService> searchServiceProvider,
        ObjectProvider<AiResourceSearchTypeHandlerRegistry> typeHandlerRegistryProvider) {
        this(searchServiceProvider.getIfAvailable(), typeHandlerRegistryProvider.getIfAvailable());
    }
    
    AiResourceSearchApplicationService(AiResourceSearchService searchService,
        AiResourceSearchTypeHandlerRegistry typeHandlerRegistry) {
        this.searchService = searchService;
        this.typeHandlerRegistry = typeHandlerRegistry;
    }
    
    /**
     * Search one cursor page across one or more AI Resource types.
     *
     * @param form validated generic Search form
     * @return protocol-neutral cursor page
     * @throws NacosException when validation, recall, or canonical lookup fails
     */
    public AiResourceSearchResponse search(AiResourceSearchForm form) throws NacosException {
        AiResourceSearchService core = requireSearchService();
        Query query = new Query();
        query.setNamespaceId(form.getNamespaceId());
        query.setText(form.getQuery());
        query.setResourceTypes(resolveResourceTypes(form.getResourceTypes()));
        query.setPredicates(commonPredicates(form.getTagsAll(), form.getCapabilitiesAny()));
        query.setCursor(form.getCursor());
        query.setLimit(form.getLimit());
        AiResourceSearchService.Page page = StringUtils.isBlank(form.getQuery())
            ? core.list(query) : core.search(query);
        AiResourceSearchResponse result = new AiResourceSearchResponse();
        result.setItems(mapItems(page.getItems(), this::toSearchItem));
        result.setNextCursor(page.getNextCursor());
        return result;
    }
    
    /**
     * Search AgentSpec through the shared Search Core.
     */
    public Page<AgentSpecBasicInfo> searchAgentSpecs(AgentSpecSearchForm form, int pageNo,
        int pageSize) throws NacosException {
        AiResourceSearchService core = requireSearchService();
        Query query = numberedQuery(form.getNamespaceId(),
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, pageNo, pageSize);
        List<Predicate> predicates = commonPredicates(form.getTagsAll(), null);
        addPredicate(predicates, "resourceName", PredicateOperator.LITERAL_CONTAINS,
            singleton(form.getKeyword()), false);
        query.setPredicates(predicates);
        return mapPage(core.numberedList(query), this::toAgentSpecBasicInfo);
    }
    
    /**
     * Search Skill through the shared Search Core.
     */
    public Page<SkillBasicInfo> searchSkills(AiResourcePageSearchForm form, int pageNo,
        int pageSize) throws NacosException {
        Query query = contentQuery(form, AiResourceConstants.RESOURCE_TYPE_SKILL, pageNo,
            pageSize);
        return mapPage(numbered(query), this::toSkillBasicInfo);
    }
    
    /**
     * Search Prompt through the shared Search Core.
     */
    public Page<PromptMetaSummary> searchPrompts(AiResourcePageSearchForm form, int pageNo,
        int pageSize) throws NacosException {
        Query query = contentQuery(form, AiResourceConstants.RESOURCE_TYPE_PROMPT, pageNo,
            pageSize);
        return mapPage(numbered(query), this::toPromptMetaSummary);
    }
    
    /**
     * Search MCP servers through the shared Search Core.
     */
    public Page<McpServerBasicInfo> searchMcpServers(McpSearchForm form, int pageNo, int pageSize)
        throws NacosException {
        Query query = contentQuery(form, AiResourceConstants.RESOURCE_TYPE_MCP, pageNo,
            pageSize);
        List<Predicate> predicates = new ArrayList<>(query.getPredicates());
        addPredicate(predicates, "metadata.protocol", PredicateOperator.EXACT_ANY,
            form.getProtocolsAny(), false);
        addPredicate(predicates, "capabilities", PredicateOperator.EXACT_ANY,
            form.getCapabilitiesAny(), false);
        query.setPredicates(predicates);
        return mapPage(numbered(query), this::toMcpServerBasicInfo);
    }
    
    private Query contentQuery(AiResourcePageSearchForm form, String resourceType, int pageNo,
        int pageSize) {
        Query query = numberedQuery(form.getNamespaceId(), resourceType, pageNo, pageSize);
        query.setText(form.getQuery());
        query.setPredicates(commonPredicates(form.getTagsAll(), null));
        return query;
    }
    
    private Query numberedQuery(String namespaceId, String resourceType, int pageNo,
        int pageSize) {
        Query query = new Query();
        query.setNamespaceId(namespaceId);
        query.setResourceTypes(Collections.singletonList(resourceType));
        query.setPageNumber(pageNo);
        query.setPageSize(pageSize);
        return query;
    }
    
    private NumberedPage numbered(Query query) throws NacosException {
        AiResourceSearchService core = requireSearchService();
        return StringUtils.isBlank(query.getText()) ? core.numberedList(query)
            : core.numberedSearch(query);
    }
    
    private List<String> resolveResourceTypes(List<String> requested)
        throws NacosException {
        if (typeHandlerRegistry == null) {
            throw unavailable();
        }
        Collection<String> registered = typeHandlerRegistry.resourceTypes();
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(registered);
        }
        Set<String> result = new LinkedHashSet<>();
        for (String resourceType : requested) {
            String normalized = resourceType.toLowerCase(Locale.ROOT);
            if (!registered.contains(normalized)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Unsupported AI resource type: " + resourceType);
            }
            result.add(normalized);
        }
        return new ArrayList<>(result);
    }
    
    private AiResourceSearchService requireSearchService() throws NacosException {
        if (searchService == null) {
            throw unavailable();
        }
        return searchService;
    }
    
    private NacosException unavailable() {
        return new NacosException(SERVICE_UNAVAILABLE_STATUS,
            "AI Resource Search runtime is unavailable.");
    }
    
    private List<Predicate> commonPredicates(List<String> tagsAll,
        List<String> capabilitiesAny) {
        List<Predicate> result = new ArrayList<>();
        addPredicate(result, "tags", PredicateOperator.EXACT_ALL, tagsAll, false);
        addPredicate(result, "capabilities", PredicateOperator.EXACT_ANY, capabilitiesAny,
            false);
        return result;
    }
    
    private void addPredicate(List<Predicate> target, String field, PredicateOperator operator,
        List<String> values, boolean caseSensitive) {
        if (values != null && !values.isEmpty()) {
            target.add(new Predicate(field, operator, values, caseSensitive));
        }
    }
    
    private List<String> singleton(String value) {
        return StringUtils.isBlank(value) ? Collections.emptyList()
            : Collections.singletonList(value);
    }
    
    private <T> Page<T> mapPage(NumberedPage source,
        Function<AiResourceSearchResult, T> mapper) {
        Page<T> result = new Page<>();
        result.setTotalCount((int) Math.min(Integer.MAX_VALUE, source.getTotalCount()));
        result.setPageNumber(source.getPageNumber());
        result.setPagesAvailable(source.getPagesAvailable());
        result.setPageItems(mapItems(source.getItems(), mapper));
        return result;
    }
    
    private <T> List<T> mapItems(List<AiResourceSearchResult> source,
        Function<AiResourceSearchResult, T> mapper) {
        List<T> result = new ArrayList<>();
        if (source != null) {
            for (AiResourceSearchResult item : source) {
                result.add(mapper.apply(item));
            }
        }
        return result;
    }
    
    private AiResourceSearchItem toSearchItem(AiResourceSearchResult source) {
        AiResourceSearchItem result = new AiResourceSearchItem();
        result.setNamespaceId(source.getNamespaceId());
        result.setResourceType(source.getResourceType());
        result.setResourceName(source.getResourceName());
        result.setResourceVersion(source.getResourceVersion());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setTags(source.getTags());
        result.setCapabilities(source.getCapabilities());
        result.setRepresentativeQueries(source.getRepresentativeQueries());
        result.setMetadata(source.getMetadata());
        result.setCreateTime(toMillis(source.getGmtCreate()));
        result.setUpdateTime(toMillis(source.getGmtModified()));
        result.setScore(source.getScore());
        return result;
    }
    
    private AgentSpecBasicInfo toAgentSpecBasicInfo(AiResourceSearchResult source) {
        AgentSpecBasicInfo result = new AgentSpecBasicInfo();
        result.setNamespaceId(source.getNamespaceId());
        result.setName(source.getResourceName());
        result.setDescription(source.getDescription());
        result.setUpdateTime(toMillis(source.getGmtModified()));
        return result;
    }
    
    private SkillBasicInfo toSkillBasicInfo(AiResourceSearchResult source) {
        SkillBasicInfo result = new SkillBasicInfo();
        result.setNamespaceId(source.getNamespaceId());
        result.setName(source.getResourceName());
        result.setDescription(source.getDescription());
        result.setUpdateTime(toMillis(source.getGmtModified()));
        return result;
    }
    
    private PromptMetaSummary toPromptMetaSummary(AiResourceSearchResult source) {
        PromptMetaSummary result = new PromptMetaSummary();
        result.setPromptKey(source.getResourceName());
        result.setDescription(source.getDescription());
        result.setBizTags(source.getTags());
        result.setLatestVersion(source.getResourceVersion());
        result.setGmtModified(toMillis(source.getGmtModified()));
        return result;
    }
    
    private McpServerBasicInfo toMcpServerBasicInfo(AiResourceSearchResult source) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setNamespaceId(source.getNamespaceId());
        result.setId(source.getResourceName());
        result.setName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setVersion(source.getResourceVersion());
        result.setProtocol(stringValue(source.getMetadata(), "protocol"));
        result.setFrontProtocol(stringValue(source.getMetadata(), "frontProtocol"));
        result.setEnabled(true);
        result.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        result.setCapabilities(toMcpCapabilities(source.getCapabilities()));
        return result;
    }
    
    private List<McpCapability> toMcpCapabilities(List<String> values) {
        List<McpCapability> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            try {
                result.add(McpCapability.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Search documents may carry future capability names unknown to this API model.
            }
        }
        return result;
    }
    
    private String stringValue(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
    
    private Long toMillis(Timestamp value) {
        return value == null ? null : value.getTime();
    }
}
