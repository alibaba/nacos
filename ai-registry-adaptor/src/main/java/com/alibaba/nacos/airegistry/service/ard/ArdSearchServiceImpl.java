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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.ai.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchResult;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Aggregation;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.AggregationBucket;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.AggregationRequest;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.AggregationResult;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Page;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Query;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService.Sort;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalogEntry;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResultType;
import com.alibaba.nacos.airegistry.model.ard.ArdFacetRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdHostInfo;
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchFilter;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchQuery;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nacos Local ARD protocol adaptor backed by the AI resource search capability.
 *
 * @author nacos
 */
@Service
@ConditionalOnArdEnabled
public class ArdSearchServiceImpl implements ArdSearchService {
    
    private static final int DEFAULT_PAGE_SIZE = 10;
    
    private static final int MAX_PAGE_SIZE = 50;
    
    private static final int DEFAULT_LIST_PAGE_SIZE = 20;
    
    private static final int MAX_LIST_PAGE_SIZE = 100;
    
    private static final int DEFAULT_FACET_LIMIT = 20;
    
    private static final String KEY_CATALOG_BASE_URL = "nacos.ai.ard.catalog.base-url";
    
    private static final String KEY_CATALOG_HOST_DISPLAY_NAME =
        "nacos.ai.ard.catalog.host.display-name";
    
    private static final String KEY_CATALOG_HOST_DOCUMENTATION_URL =
        "nacos.ai.ard.catalog.host.documentation-url";
    
    private static final String KEY_CATALOG_TRUST_IDENTITY =
        "nacos.ai.ard.catalog.trust.identity";
    
    private static final String KEY_CATALOG_TRUST_IDENTITY_TYPE =
        "nacos.ai.ard.catalog.trust.identity-type";
    
    private static final String KEY_CATALOG_MAX_ENTRIES = "nacos.ai.ard.catalog.max-entries";
    
    private static final Pattern LIST_FILTER_EXPRESSION = Pattern.compile(
        "^\\s*([A-Za-z][A-Za-z0-9.]*)\\s*(=|>)\\s*'((?:\\\\.|[^'\\\\])*)'\\s*$");
    
    private static final Set<String> SUPPORTED_FILTER_KEYS =
        new LinkedHashSet<>(Arrays.asList("displayName", "type", "publisher",
            "publisherId", "version", "source", "tags", "capabilities",
            "representativeQueries", "metadata.resourceType", "metadata.inputTypes",
            "metadata.outputTypes", "metadata.sideEffects", "metadata.riskLevel",
            "metadata.scope", "trustManifest.identity", "trustManifest.identityType"));
    
    private final AiResourceSearchService searchService;
    
    public ArdSearchServiceImpl(AiResourceSearchService searchService) {
        this.searchService = searchService;
    }
    
    @Override
    public ArdSearchResponse search(ArdSearchRequest request) throws NacosException {
        SearchContext context = validateAndBuildContext(request);
        if (!matchesProtocolFilters(context.filter)) {
            return searchResponse(Collections.emptyList(), null);
        }
        Page page = searchService.search(toSearchQuery(context));
        List<ArdSearchResult> results = new ArrayList<>();
        for (AiResourceSearchResult item : page.getItems()) {
            ArdSearchResult result = toResult(item);
            result.setScore(item.getScore());
            results.add(result);
        }
        return searchResponse(results, page.getNextCursor());
    }
    
    @Override
    public ArdExploreResponse explore(ArdExploreRequest request) throws NacosException {
        ExploreContext context = validateAndBuildExploreContext(request);
        AggregationResult result = matchesProtocolFilters(context.filter)
            ? searchService.aggregate(toSearchQuery(context),
                aggregationRequests(context.facets))
            : new AggregationResult(0, Collections.emptyMap());
        ArdExploreResponse response = new ArdExploreResponse();
        Map<String, ArdExploreResponse.FacetResult> facets = new LinkedHashMap<>();
        for (ArdFacetRequest facetRequest : context.facets) {
            facets.put(facetRequest.getField(), facet(result, facetRequest));
        }
        response.setFacets(facets);
        return response;
    }
    
    @Override
    public ArdListResponse list(String namespaceId, String filter, String orderBy,
        Integer pageSize, String pageToken) throws NacosException {
        ListContext context = validateAndBuildListContext(namespaceId, filter, orderBy, pageSize,
            pageToken);
        if (!matchesProtocolFilters(context.filter)) {
            return listResponse(Collections.emptyList(), null);
        }
        Page page = searchService.list(toSearchQuery(context));
        List<ArdCatalogEntry> results = new ArrayList<>();
        for (AiResourceSearchResult item : page.getItems()) {
            results.add(toCatalogEntry(item));
        }
        return listResponse(results, page.getNextCursor());
    }
    
    @Override
    public ArdCatalog hostCatalog() {
        ArdCatalog catalog = new ArdCatalog();
        catalog.setSpecVersion(ArdProtocolConstants.SPEC_VERSION);
        catalog.setHost(hostInfo());
        catalog.setEntries(Collections.singletonList(registryEntry()));
        return catalog;
    }
    
    @Override
    public ArdCatalog catalog(String namespaceId) throws NacosException {
        String resolvedNamespace = normalizeNamespaceId(namespaceId);
        ArdCatalog catalog = new ArdCatalog();
        catalog.setSpecVersion(ArdProtocolConstants.SPEC_VERSION);
        catalog.setHost(hostInfo());
        List<ArdCatalogEntry> entries = new ArrayList<>();
        entries.add(registryEntry());
        Query query = new Query();
        query.setNamespaceId(resolvedNamespace);
        query.setResourceTypes(allResourceTypes());
        query.setLimit(positiveInt(KEY_CATALOG_MAX_ENTRIES, 100));
        for (AiResourceSearchResult item : searchService.list(query).getItems()) {
            entries.add(toCatalogEntry(item));
        }
        catalog.setEntries(entries);
        return catalog;
    }
    
    private Query toSearchQuery(SearchContext context) {
        Query query = new Query();
        query.setNamespaceId(context.namespaceId);
        query.setText(context.text);
        query.setResourceTypes(context.resourceTypes);
        query.setFilters(domainFilters(context.filter));
        query.setCursor(context.pageToken);
        query.setLimit(context.pageSize);
        if (context instanceof ListContext) {
            ListContext listContext = (ListContext) context;
            query.setDescending(listContext.orderDescending);
            query.setCreatedAfter(listContext.createdAfter);
            query.setUpdatedAfter(listContext.updatedAfter);
            if ("displayName".equals(listContext.orderBy)) {
                query.setSort(Sort.DISPLAY_NAME);
            } else if ("identifier".equals(listContext.orderBy)) {
                query.setSort(Sort.RESOURCE_KEY);
            }
        }
        return query;
    }
    
    private Map<String, List<String>> domainFilters(Map<String, List<String>> filters) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> filter : filters.entrySet()) {
            String key = filter.getKey();
            if ("version".equals(key)) {
                result.put("resourceVersion", filter.getValue());
            } else if ("metadata.resourceType".equals(key)) {
                result.put("resourceType", filter.getValue());
            } else if (!"type".equals(key) && !"publisher".equals(key)
                && !"publisherId".equals(key) && !"source".equals(key)
                && !key.startsWith("trustManifest.")) {
                result.put(key, filter.getValue());
            }
        }
        return result;
    }
    
    private boolean matchesProtocolFilters(Map<String, List<String>> filters) {
        if (resolveKinds(filters).isEmpty()) {
            return false;
        }
        if (!matchesConstantFilter(filters.get("source"), sourceUri())) {
            return false;
        }
        String catalogPublisher = catalogHostIdentifier();
        if (!matchesConstantFilter(filters.get("publisher"), catalogPublisher)
            || !matchesConstantFilter(filters.get("publisherId"), catalogPublisher)) {
            return false;
        }
        Map<String, Object> trust = trustManifest();
        String identity = trust == null ? null : stringValue(trust.get("identity"));
        String identityType = trust == null ? null : stringValue(trust.get("identityType"));
        return matchesConstantFilter(filters.get("trustManifest.identity"), identity)
            && matchesConstantFilter(filters.get("trustManifest.identityType"), identityType);
    }
    
    private boolean matchesConstantFilter(List<String> expected, String actual) {
        return expected == null || expected.isEmpty()
            || equalsIgnoreCase(expected, actual);
    }
    
    private String property(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        try {
            return EnvUtil.getProperty(key, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    private SearchContext validateAndBuildContext(ArdSearchRequest request)
        throws NacosApiException {
        if (request == null || request.getQuery() == null
            || StringUtils.isBlank(request.getQuery().getText())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING, "Required parameter `query.text` not present");
        }
        String federation = StringUtils.isBlank(request.getFederation())
            ? ArdProtocolConstants.FEDERATION_AUTO : request.getFederation().trim();
        if (!Arrays.asList(ArdProtocolConstants.FEDERATION_AUTO,
            ArdProtocolConstants.FEDERATION_REFERRALS,
            ArdProtocolConstants.FEDERATION_NONE).contains(federation)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Unsupported ARD federation mode: " + federation);
        }
        ArdSearchQuery query = request.getQuery();
        Map<String, List<String>> filter = normalizeFilter(query);
        validateFilterKeys(filter.keySet());
        SearchContext context = new SearchContext();
        context.namespaceId = StringUtils.isBlank(request.getNamespaceId())
            ? com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID
            : request.getNamespaceId();
        context.text = query.getText().trim();
        context.filter = filter;
        context.pageSize = normalizePageSize(request.getPageSize());
        context.pageToken = request.getPageToken();
        context.kinds = resolveKinds(filter);
        context.resourceTypes = resourceTypes(context.kinds);
        return context;
    }
    
    private ExploreContext validateAndBuildExploreContext(ArdExploreRequest request)
        throws NacosApiException {
        if (request == null || request.getResultType() == null
            || request.getResultType().getFacets() == null
            || request.getResultType().getFacets().isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Required parameter `resultType.facets` not present");
        }
        ArdSearchQuery query = request.getQuery() == null ? new ArdSearchQuery()
            : request.getQuery();
        Map<String, List<String>> filter = normalizeFilter(query);
        validateFilterKeys(filter.keySet());
        ArdExploreResultType resultType = request.getResultType();
        ExploreContext context = new ExploreContext();
        context.namespaceId = normalizeNamespaceId(request.getNamespaceId());
        context.text = StringUtils.isBlank(query.getText()) ? null : query.getText().trim();
        context.filter = filter;
        context.pageToken = null;
        context.kinds = resolveKinds(filter);
        context.resourceTypes = resourceTypes(context.kinds);
        context.facets = normalizeFacets(resultType.getFacets());
        return context;
    }
    
    private ListContext validateAndBuildListContext(String namespaceId, String filter,
        String orderBy, Integer pageSize, String pageToken) throws NacosApiException {
        ListContext context = new ListContext();
        context.namespaceId = normalizeNamespaceId(namespaceId);
        context.filter = normalizeListFilter(filter, context);
        validateFilterKeys(context.filter.keySet());
        context.kinds = resolveKinds(context.filter);
        context.resourceTypes = resourceTypes(context.kinds);
        context.pageSize = normalizeListPageSize(pageSize);
        context.pageToken = pageToken;
        parseOrderBy(orderBy, context);
        return context;
    }
    
    private String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId)
            ? com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    private List<ArdFacetRequest> normalizeFacets(List<ArdFacetRequest> facets)
        throws NacosApiException {
        List<ArdFacetRequest> result = new ArrayList<>();
        for (ArdFacetRequest facet : facets) {
            if (facet == null || StringUtils.isBlank(facet.getField())) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `resultType.facets.field` not present");
            }
            String field = facet.getField().trim();
            validateFilterKeys(Collections.singleton(field));
            ArdFacetRequest normalized = new ArdFacetRequest();
            normalized.setField(field);
            normalized.setLimit(facetLimit(facet.getLimit()));
            normalized.setMinCount(facet.getMinCount() == null ? 1
                : Math.max(1, facet.getMinCount()));
            result.add(normalized);
        }
        return result;
    }
    
    private int facetLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_FACET_LIMIT;
        }
        return Math.min(limit, MAX_LIST_PAGE_SIZE);
    }
    
    private Map<String, List<String>> normalizeListFilter(String filter,
        ListContext context) throws NacosApiException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(filter)) {
            return result;
        }
        for (String expression : splitListFilter(filter)) {
            Matcher matcher = LIST_FILTER_EXPRESSION.matcher(expression);
            if (!matcher.matches()) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Invalid ARD list filter expression: " + expression);
            }
            String field = matcher.group(1);
            String operator = matcher.group(2);
            String value = unescapeFilterValue(expression, matcher.group(3));
            if ("createdAfter".equals(field) || "updatedAfter".equals(field)) {
                if (!">".equals(operator)) {
                    throw invalidFilterOperator(field, operator);
                }
                Instant instant = parseInstant(field, value);
                if ("createdAfter".equals(field)) {
                    context.createdAfter = instant;
                } else {
                    context.updatedAfter = instant;
                }
                continue;
            }
            if (!"=".equals(operator)) {
                throw invalidFilterOperator(field, operator);
            }
            addFilter(result, field, commaSeparatedValues(field, value));
        }
        return result;
    }
    
    private List<String> splitListFilter(String filter) throws NacosApiException {
        List<String> result = new ArrayList<>();
        int start = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < filter.length(); i++) {
            char current = filter.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '\'') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && isAndSeparator(filter, i)) {
                addFilterExpression(result, filter.substring(start, i));
                i += 2;
                start = i + 1;
            }
        }
        if (quoted || escaped) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Unterminated ARD list filter value");
        }
        addFilterExpression(result, filter.substring(start));
        return result;
    }
    
    private boolean isAndSeparator(String filter, int index) {
        return index > 0 && index + 3 < filter.length()
            && filter.regionMatches(true, index, "AND", 0, 3)
            && Character.isWhitespace(filter.charAt(index - 1))
            && Character.isWhitespace(filter.charAt(index + 3));
    }
    
    private void addFilterExpression(List<String> expressions, String expression)
        throws NacosApiException {
        if (StringUtils.isBlank(expression)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "ARD list filter expression is blank");
        }
        expressions.add(expression.trim());
    }
    
    private String unescapeFilterValue(String expression, String value)
        throws NacosApiException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++i >= value.length()) {
                throw invalidFilterEscape(expression);
            }
            char escaped = value.charAt(i);
            if (escaped != '\\' && escaped != '\'') {
                throw invalidFilterEscape(expression);
            }
            result.append(escaped);
        }
        return result.toString();
    }
    
    private NacosApiException invalidFilterOperator(String field, String operator) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Unsupported ARD list filter operator `" + operator + "` for `" + field + "`");
    }
    
    private NacosApiException invalidFilterEscape(String expression) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Invalid escape in ARD list filter expression: " + expression);
    }
    
    private List<String> commaSeparatedValues(String field, String value)
        throws NacosApiException {
        if (StringUtils.isBlank(value)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "ARD list filter `" + field + "` should not be blank");
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            if (StringUtils.isNotBlank(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }
    
    private Instant parseInstant(String field, String value) throws NacosApiException {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e,
                "ARD list filter `" + field + "` should be ISO-8601 timestamp");
        }
    }
    
    private int normalizeListPageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_LIST_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_LIST_PAGE_SIZE);
    }
    
    private void parseOrderBy(String orderBy, ListContext context) throws NacosApiException {
        context.orderBy = "updatedAt";
        context.orderDescending = true;
        if (StringUtils.isBlank(orderBy)) {
            return;
        }
        String[] parts = orderBy.trim().split("\\s+");
        String field = parts[0];
        if ("name".equalsIgnoreCase(field)) {
            field = "displayName";
        } else if ("updated_at".equalsIgnoreCase(field)) {
            field = "updatedAt";
        }
        if (!"displayName".equals(field) && !"updatedAt".equals(field)
            && !"identifier".equals(field)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Unsupported ARD orderBy field: " + field);
        }
        context.orderBy = field;
        context.orderDescending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
    }
    
    private Map<String, List<String>> normalizeFilter(ArdSearchQuery query)
        throws NacosApiException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (query.getFilter() != null && !query.getFilter().isEmpty()) {
            for (Map.Entry<String, Object> entry : query.getFilter().entrySet()) {
                addFilter(result, entry.getKey(),
                    normalizeFilterValues(entry.getKey(), entry.getValue()));
            }
        }
        if (query.getFilters() == null || query.getFilters().isEmpty()) {
            return result;
        }
        for (ArdSearchFilter filter : query.getFilters()) {
            if (filter == null) {
                continue;
            }
            String fieldPath = filter.getFieldPath();
            if (StringUtils.isBlank(fieldPath)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `query.filters.fieldPath` not present");
            }
            Object value = filter.getValues() == null ? filter.getValue() : filter.getValues();
            addFilter(result, fieldPath, normalizeFilterValues(fieldPath, value));
        }
        return result;
    }
    
    private void addFilter(Map<String, List<String>> result, String fieldPath,
        List<String> values) {
        result.computeIfAbsent(fieldPath, key -> new ArrayList<>()).addAll(values);
    }
    
    private List<String> normalizeFilterValues(String key, Object value) throws NacosApiException {
        if (value instanceof String) {
            return Collections.singletonList((String) value);
        }
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object each : (Collection<?>) value) {
                if (each != null) {
                    result.add(String.valueOf(each));
                }
            }
            return result;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Request parameter `query.filter." + key + "` should be string or string array");
    }
    
    private void validateFilterKeys(Set<String> keys) throws NacosApiException {
        for (String key : keys) {
            if (!SUPPORTED_FILTER_KEYS.contains(key)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Unsupported ARD filter key: " + key);
            }
        }
    }
    
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
    
    private List<ResourceKind> resolveKinds(Map<String, List<String>> filter) {
        List<String> mediaTypes = filter.get("type");
        List<String> resourceTypes = filter.get("metadata.resourceType");
        List<ResourceKind> result = new ArrayList<>();
        for (ResourceKind kind : ResourceKind.values()) {
            if (matchesKindFilter(kind, mediaTypes, resourceTypes)) {
                result.add(kind);
            }
        }
        return result;
    }
    
    private boolean matchesKindFilter(ResourceKind kind, List<String> mediaTypes,
        List<String> resourceTypes) {
        if (mediaTypes != null && !equalsIgnoreCase(mediaTypes, kind.mediaType)) {
            return false;
        }
        return resourceTypes == null || equalsIgnoreCase(resourceTypes, kind.resourceType);
    }
    
    private List<String> resourceTypes(List<ResourceKind> kinds) {
        List<String> result = new ArrayList<>();
        for (ResourceKind kind : kinds) {
            result.add(kind.resourceType);
        }
        return result;
    }
    
    private ArdCatalogEntry toCatalogEntry(AiResourceSearchResult entry) {
        ArdCatalogEntry result = new ArdCatalogEntry();
        populateCatalogEntry(result, entry);
        return result;
    }
    
    private ArdSearchResult toResult(AiResourceSearchResult entry) {
        ArdSearchResult result = new ArdSearchResult();
        populateCatalogEntry(result, entry);
        result.setSource(sourceUri());
        return result;
    }
    
    private void populateCatalogEntry(ArdCatalogEntry result, AiResourceSearchResult entry) {
        result.setIdentifier(buildIdentifier(entry));
        result.setDisplayName(entry.getDisplayName());
        result.setType(resourceKind(entry.getResourceType()).mediaType);
        result.setUrl(withBaseUrl(buildResourceUrl(entry)));
        result.setDescription(entry.getDescription());
        result.setTags(entry.getTags());
        result.setCapabilities(entry.getCapabilities());
        List<String> representativeQueries = entry.getRepresentativeQueries();
        result.setRepresentativeQueries(representativeQueries.size() < 2 ? null
            : representativeQueries);
        result.setVersion(entry.getResourceVersion());
        result.setUpdatedAt(formatTimestamp(entry.getGmtModified()));
        Map<String, Object> metadata = protocolMetadata(entry.getMetadata());
        if (entry.getGmtCreate() != null) {
            metadata.put("createdAt", formatTimestamp(entry.getGmtCreate()));
        }
        result.setMetadata(metadata);
        result.setTrustManifest(trustManifest());
    }
    
    private ArdHostInfo hostInfo() {
        ArdHostInfo host = new ArdHostInfo();
        host.setDisplayName(property(KEY_CATALOG_HOST_DISPLAY_NAME, "Nacos AI Registry"));
        String identifier = catalogHostIdentifier();
        if (StringUtils.isNotBlank(identifier)) {
            host.setIdentifier(identifier);
        }
        String documentationUrl = property(KEY_CATALOG_HOST_DOCUMENTATION_URL, "");
        if (StringUtils.isNotBlank(documentationUrl)) {
            host.setDocumentationUrl(documentationUrl);
        }
        host.setTrustManifest(trustManifest());
        return host;
    }
    
    private ArdCatalogEntry registryEntry() {
        ArdCatalogEntry result = new ArdCatalogEntry();
        result.setIdentifier("urn:air:" + catalogHostIdentifier() + ":registry:nacos");
        result.setDisplayName(property(KEY_CATALOG_HOST_DISPLAY_NAME, "Nacos AI Registry"));
        result.setType(ArdProtocolConstants.MEDIA_TYPE_REGISTRY);
        result.setUrl(withBaseUrl(ArdProtocolConstants.CLIENT_PATH));
        result.setDescription("Nacos local AI Registry ARD search endpoint.");
        result.setTags(List.of("registry", "search", "dynamic"));
        result.setCapabilities(List.of("search", "explore", "list"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("searchEndpoint", endpoints().get("search"));
        metadata.put("exploreEndpoint", endpoints().get("explore"));
        metadata.put("listEndpoint", endpoints().get("agents"));
        metadata.put("artifactEndpoint", endpoints().get("artifacts"));
        metadata.put("resourceTypes", "skill,prompt,mcp");
        result.setMetadata(metadata);
        result.setTrustManifest(trustManifest());
        return result;
    }
    
    private Map<String, String> endpoints() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("search", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/search"));
        endpoints.put("explore", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/explore"));
        endpoints.put("agents", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/agents"));
        endpoints.put("artifacts", withBaseUrl(ArdProtocolConstants.CLIENT_PATH + "/artifacts"));
        return endpoints;
    }
    
    private String withBaseUrl(String url) {
        if (StringUtils.isBlank(url) || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String baseUrl = configuredBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = currentRequestBaseUrl();
        }
        if (StringUtils.isBlank(baseUrl)) {
            return url;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return url.startsWith("/") ? base + url : base + "/" + url;
    }
    
    private String configuredBaseUrl() {
        String baseUrl = property(KEY_CATALOG_BASE_URL, "");
        if (StringUtils.isBlank(baseUrl)) {
            return "";
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        String contextPath = EnvUtil.getContextPath();
        return StringUtils.isBlank(contextPath) ? base : base + contextPath;
    }
    
    private String currentRequestBaseUrl() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return "";
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        return ServletUriComponentsBuilder.fromRequestUri(request)
            .replacePath(request.getContextPath())
            .replaceQuery(null)
            .build()
            .toUriString();
    }
    
    private String buildIdentifier(AiResourceSearchResult entry) {
        if (StringUtils.isBlank(entry.getNamespaceId())
            || StringUtils.isBlank(entry.getResourceType())
            || StringUtils.isBlank(entry.getResourceName())) {
            return null;
        }
        return "urn:air:" + catalogHostIdentifier() + ":"
            + encodeIdentifierSegment(entry.getNamespaceId()) + ":"
            + encodeIdentifierSegment(entry.getResourceType()) + ":"
            + encodeIdentifierSegment(entry.getResourceName());
    }
    
    private String encodeIdentifierSegment(String value) {
        return "n1_" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    
    private String buildResourceUrl(AiResourceSearchResult entry) {
        ResourceKind kind = resourceKind(entry.getResourceType());
        StringBuilder url = new StringBuilder(ArdProtocolConstants.CLIENT_PATH)
            .append("/artifacts?namespaceId=").append(encode(entry.getNamespaceId()))
            .append("&resourceType=").append(encode(entry.getResourceType()))
            .append("&resourceName=").append(encode(entry.getResourceName()))
            .append("&version=").append(encode(entry.getResourceVersion()));
        if (ResourceKind.MCP == kind) {
            String mcpName = stringValue(entry.getMetadata().get("mcpName"));
            if (StringUtils.isNotBlank(mcpName)) {
                url.append("&mcpName=").append(encode(mcpName));
            }
        }
        return url.toString();
    }
    
    private Map<String, Object> trustManifest() {
        String identity = property(KEY_CATALOG_TRUST_IDENTITY, "");
        if (StringUtils.isBlank(identity)) {
            return null;
        }
        Map<String, Object> trustManifest = new LinkedHashMap<>();
        trustManifest.put("identity", identity);
        String identityType = property(KEY_CATALOG_TRUST_IDENTITY_TYPE, "");
        if (Arrays.asList("spiffe", "did", "https", "other").contains(identityType)) {
            trustManifest.put("identityType", identityType);
        }
        return trustManifest;
    }
    
    private String sourceUri() {
        return withBaseUrl(ArdProtocolConstants.CLIENT_PATH);
    }
    
    private Map<String, Object> protocolMetadata(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
    
    private ResourceKind resourceKind(String resourceType) {
        for (ResourceKind kind : ResourceKind.values()) {
            if (kind.resourceType.equals(resourceType)) {
                return kind;
            }
        }
        throw new IllegalStateException("Unsupported ARD indexed resource type: " + resourceType);
    }
    
    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
    
    private String catalogHostIdentifier() {
        return property(ArdProtocolConstants.KEY_CATALOG_HOST_IDENTIFIER,
            ArdProtocolConstants.DEFAULT_CATALOG_HOST_IDENTIFIER);
    }
    
    private List<AggregationRequest> aggregationRequests(List<ArdFacetRequest> facets) {
        List<AggregationRequest> result = new ArrayList<>();
        for (ArdFacetRequest facet : facets) {
            String canonicalField = canonicalFacetField(facet.getField());
            if (canonicalField != null) {
                result.add(new AggregationRequest(facet.getField(), canonicalField,
                    facet.getLimit(), facet.getMinCount()));
            }
        }
        return result;
    }
    
    private String canonicalFacetField(String field) {
        if ("type".equals(field) || "metadata.resourceType".equals(field)) {
            return "resourceType";
        }
        if ("version".equals(field)) {
            return "resourceVersion";
        }
        if ("publisher".equals(field) || "publisherId".equals(field)
            || "source".equals(field) || field.startsWith("trustManifest.")) {
            return null;
        }
        return field;
    }
    
    private ArdExploreResponse.FacetResult facet(AggregationResult result,
        ArdFacetRequest request) {
        String constantValue = protocolFacetValue(request.getField());
        if (constantValue != null) {
            return constantFacet(constantValue, result.getTotalMatched(), request);
        }
        ArdExploreResponse.FacetResult facet = new ArdExploreResponse.FacetResult();
        List<ArdExploreResponse.FacetBucket> buckets = new ArrayList<>();
        Aggregation aggregation = result.getAggregations().get(request.getField());
        if (aggregation != null) {
            for (AggregationBucket aggregated : aggregation.getBuckets()) {
                ArdExploreResponse.FacetBucket bucket = new ArdExploreResponse.FacetBucket();
                bucket.setValue(protocolFacetValue(request.getField(), aggregated.getValue()));
                bucket.setCount(aggregated.getCount());
                buckets.add(bucket);
            }
        }
        facet.setBuckets(buckets);
        facet.setOtherCount(aggregation == null ? 0 : aggregation.getOtherCount());
        return facet;
    }
    
    private String protocolFacetValue(String field) {
        if ("publisher".equals(field) || "publisherId".equals(field)) {
            return catalogHostIdentifier();
        }
        if ("source".equals(field)) {
            return sourceUri();
        }
        if (field.startsWith("trustManifest.")) {
            Map<String, Object> trust = trustManifest();
            return trust == null ? null : stringValue(trust.get(field.substring(14)));
        }
        return null;
    }
    
    private String protocolFacetValue(String field, String value) {
        return "type".equals(field) ? resourceKind(value).mediaType : value;
    }
    
    private ArdExploreResponse.FacetResult constantFacet(String value, int totalMatched,
        ArdFacetRequest request) {
        ArdExploreResponse.FacetResult facet = new ArdExploreResponse.FacetResult();
        List<ArdExploreResponse.FacetBucket> buckets = new ArrayList<>();
        if (StringUtils.isNotBlank(value) && totalMatched >= request.getMinCount()) {
            ArdExploreResponse.FacetBucket bucket = new ArdExploreResponse.FacetBucket();
            bucket.setValue(value);
            bucket.setCount(totalMatched);
            buckets.add(bucket);
        }
        facet.setBuckets(buckets);
        facet.setOtherCount(0);
        return facet;
    }
    
    private boolean equalsIgnoreCase(List<String> values, String expected) {
        if (values == null || StringUtils.isBlank(expected)) {
            return false;
        }
        String normalizedExpected = normalize(expected);
        for (String value : values) {
            if (normalizedExpected.equals(normalize(value))) {
                return true;
            }
        }
        return false;
    }
    
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
    
    private ArdSearchResponse searchResponse(List<ArdSearchResult> results,
        String nextCursor) {
        ArdSearchResponse response = new ArdSearchResponse();
        response.setReferrals(Collections.emptyList());
        response.setResults(results);
        response.setPageToken(nextCursor);
        return response;
    }
    
    private ArdListResponse listResponse(List<ArdCatalogEntry> items, String nextCursor) {
        ArdListResponse response = new ArdListResponse();
        response.setItems(items);
        response.setPageToken(nextCursor);
        return response;
    }
    
    private List<String> allResourceTypes() {
        return resourceTypes(Arrays.asList(ResourceKind.values()));
    }
    
    private int positiveInt(String key, int defaultValue) {
        String value = property(key, String.valueOf(defaultValue));
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
    
    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
    
    private enum ResourceKind {
        
        SKILL(AiResourceConstants.RESOURCE_TYPE_SKILL,
            ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE),
        
        PROMPT(AiResourceConstants.RESOURCE_TYPE_PROMPT, ArdProtocolConstants.MEDIA_TYPE_PROMPT),
        
        MCP(AiResourceConstants.RESOURCE_TYPE_MCP, ArdProtocolConstants.MEDIA_TYPE_MCP);
        
        private final String resourceType;
        
        private final String mediaType;
        
        ResourceKind(String resourceType, String mediaType) {
            this.resourceType = resourceType;
            this.mediaType = mediaType;
        }
    }
    
    private static class SearchContext {
        
        String namespaceId;
        
        String text;
        
        Map<String, List<String>> filter;
        
        List<ResourceKind> kinds;
        
        List<String> resourceTypes;
        
        int pageSize;
        
        String pageToken;
    }
    
    private static class ExploreContext extends SearchContext {
        
        private List<ArdFacetRequest> facets;
    }
    
    private static class ListContext extends SearchContext {
        
        private String orderBy;
        
        private boolean orderDescending;
        
        private Instant createdAfter;
        
        private Instant updatedAfter;
    }
    
}
