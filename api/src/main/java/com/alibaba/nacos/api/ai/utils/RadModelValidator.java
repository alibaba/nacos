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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogVersion;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.model.Page;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recursive domain validation for RAD 0.1.0 public models.
 *
 * @author Nacos
 */
public final class RadModelValidator {
    
    private static final int MAX_PAGE_SIZE = 100;
    
    private static final int MAX_TAGS = 32;
    
    private static final int MAX_PROTOCOLS = 16;
    
    private static final int MAX_TRANSPORTS = 16;
    
    private static final int MAX_ENDPOINT_SOURCES = 2;
    
    private static final int MAX_CALL_INTERFACES = 16;
    
    private static final int MAX_DECLARED_ENDPOINTS = 64;
    
    private static final int MAX_RUNTIME_ENDPOINTS = 1000;
    
    private static final int MAX_BATCH_ENDPOINTS = 1000;
    
    private static final Pattern RUNTIME_REVISION_PATTERN =
        Pattern.compile("murmur3-x64-128-v1:[0-9a-f]{32}");
    
    private RadModelValidator() {
    }
    
    /**
     * Validate an Agent catalog page returned by Search.
     *
     * @param page Agent catalog page implemented with the shared Nacos Page type
     * @throws IllegalArgumentException when invalid
     */
    public static void validateCatalogPage(Page<AgentCatalogEntry> page) {
        requireNonNull(page, "AgentCatalogPage");
        if (page.getTotalCount() < 0 || page.getPageNumber() < 1
            || page.getPagesAvailable() < 0) {
            throw invalid("Invalid Agent catalog page metadata");
        }
        List<AgentCatalogEntry> items = page.getPageItems();
        requireNonNull(items, "pageItems");
        if (items.size() > MAX_PAGE_SIZE) {
            throw invalid("pageItems exceeds " + MAX_PAGE_SIZE + " items");
        }
        String previousAgentName = null;
        for (AgentCatalogEntry item : items) {
            validate(item);
            if (previousAgentName != null
                && previousAgentName.compareTo(item.getAgentName()) >= 0) {
                throw invalid("pageItems must be in strict ASCII agentName ascending order");
            }
            previousAgentName = item.getAgentName();
        }
    }
    
    /**
     * Validate an Agent search request.
     *
     * @param request search request
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentSearchRequest request) {
        requireNonNull(request, "AgentSearchRequest");
        AgentValidationUtils.validateNamespaceId(request.getNamespaceId());
        if (request.getAgentNameContains() != null) {
            validatePrintableAscii(request.getAgentNameContains(), 1, 64,
                "agentNameContains", true);
        }
        validateTags(request.getTagsAll(), "tagsAll");
        validateProtocols(request.getProtocolsAny(), "protocolsAny", false);
        if (request.getPageNo() != null && request.getPageNo() < 1) {
            throw invalid("pageNo must be greater than or equal to 1");
        }
        if (request.getPageSize() != null
            && (request.getPageSize() < 1 || request.getPageSize() > MAX_PAGE_SIZE)) {
            throw invalid("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
    
    /**
     * Validate one Agent catalog entry, including its complete online-version catalog.
     *
     * @param entry catalog entry
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentCatalogEntry entry) {
        requireNonNull(entry, "AgentCatalogEntry");
        AgentValidationUtils.validateAgentName(entry.getAgentName());
        validateOptionalLength(entry.getDisplayName(), 128, "displayName");
        validateOptionalLength(entry.getDescription(), 2048, "description");
        if (entry.getIconUrl() != null) {
            validateAbsoluteUri(entry.getIconUrl(), "iconUrl");
        }
        validateProvider(entry.getProvider());
        validateTags(entry.getTags(), "tags");
        AgentVersion latest = AgentVersion.parse(entry.getLatestVersion());
        List<AgentCatalogVersion> versions = entry.getVersions();
        requireNonEmpty(versions, "versions");
        Set<String> versionValues = new HashSet<String>();
        Set<String> labels = new HashSet<String>();
        AgentVersion previous = null;
        boolean containsLatest = false;
        for (AgentCatalogVersion catalog : versions) {
            validate(catalog);
            AgentVersion current = AgentVersion.parse(catalog.getVersion());
            if (!versionValues.add(catalog.getVersion())) {
                throw invalid("Duplicate catalog version: " + catalog.getVersion());
            }
            if (previous != null && previous.compareTo(current) <= 0) {
                throw invalid("Catalog versions must be in strict SemVer descending order");
            }
            previous = current;
            containsLatest |= latest.equals(current);
            if (catalog.getLabels() != null) {
                for (String label : catalog.getLabels()) {
                    if (!labels.add(label)) {
                        throw invalid("A catalog label may point to only one version: " + label);
                    }
                }
            }
        }
        if (!containsLatest) {
            throw invalid("latestVersion must be present in versions");
        }
    }
    
    /**
     * Validate one online-version catalog item.
     *
     * @param catalog version catalog item
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentCatalogVersion catalog) {
        requireNonNull(catalog, "AgentCatalogVersion");
        AgentValidationUtils.validateVersion(catalog.getVersion());
        if (catalog.getLabels() != null) {
            requireNonEmpty(catalog.getLabels(), "labels");
            ensureUnique(catalog.getLabels(), "labels");
            for (String label : catalog.getLabels()) {
                AgentValidationUtils.validateNonLatestLabel(label);
            }
        }
        validateProtocols(catalog.getProtocols(), "protocols", true);
    }
    
    /**
     * Validate an Agent reference.
     *
     * @param reference Agent reference
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentReference reference) {
        requireNonNull(reference, "AgentReference");
        AgentValidationUtils.validateAgentName(reference.getAgentName());
        if (reference.getVersion() != null && reference.getLabel() != null) {
            throw invalid("AgentReference version and label are mutually exclusive");
        }
        if (reference.getVersion() != null) {
            AgentValidationUtils.validateVersion(reference.getVersion());
        }
        if (reference.getLabel() != null) {
            AgentValidationUtils.validateLabel(reference.getLabel());
        }
    }
    
    /**
     * Validate an optional Agent discovery filter.
     *
     * @param filter discovery filter
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentDiscoveryFilter filter) {
        requireNonNull(filter, "AgentDiscoveryFilter");
        validateProtocols(filter.getProtocols(), "protocols", false);
        if (filter.getProtocolVersion() != null) {
            AgentValidationUtils.validateProtocolVersion(filter.getProtocolVersion());
        }
        validateTransports(filter.getTransports());
        if (filter.getEndpointSources() != null) {
            validateOptionalArray(filter.getEndpointSources(), MAX_ENDPOINT_SOURCES,
                "endpointSources");
            ensureUnique(filter.getEndpointSources(), "endpointSources");
            for (EndpointSource source : filter.getEndpointSources()) {
                requireNonNull(source, "endpointSources item");
            }
        }
        AgentValidationUtils.validateEndpointMetadata(filter.getMetadataSelector());
    }
    
    /**
     * Validate an Agent discovery or watch request.
     *
     * @param request discovery request
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentDiscoveryRequest request) {
        requireNonNull(request, "AgentDiscoveryRequest");
        AgentValidationUtils.validateNamespaceId(request.getNamespaceId());
        validate(request.getReference());
        if (request.getFilter() != null) {
            validate(request.getFilter());
        }
    }
    
    /**
     * Validate one discovered Endpoint set.
     *
     * @param endpointSet Endpoint set
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(EndpointSet endpointSet) {
        validateEndpointSet(endpointSet, "public", "validation", "rad", null);
    }
    
    /**
     * Validate one discovered call interface and its Endpoint sets.
     *
     * @param callInterface discovered call interface
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentDiscoveryCallInterface callInterface) {
        validateCallInterface(callInterface, "public", "validation", null);
    }
    
    /**
     * Validate a complete Agent discovery result.
     *
     * @param result discovery result
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentDiscoveryResult result) {
        requireNonNull(result, "AgentDiscoveryResult");
        AgentValidationUtils.validateNamespaceId(result.getNamespaceId());
        AgentValidationUtils.validateAgentName(result.getAgentName());
        AgentValidationUtils.validateVersion(result.getVersion());
        AgentValidationUtils.validateContentDigest(result.getContentDigest());
        List<AgentDiscoveryCallInterface> callInterfaces = result.getCallInterfaces();
        requireArray(callInterfaces, MAX_CALL_INTERFACES, "callInterfaces");
        Set<String> protocols = new HashSet<String>();
        for (AgentDiscoveryCallInterface callInterface : callInterfaces) {
            validateCallInterface(callInterface, result.getNamespaceId(), result.getAgentName(),
                result.getContentDigest());
            if (!protocols.add(callInterface.getProtocol())) {
                throw invalid("Duplicate discovery protocol: " + callInterface.getProtocol());
            }
        }
    }
    
    /**
     * Validate a runtime Endpoint registration batch.
     *
     * @param batch registration batch
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentEndpointRegistrationBatch batch) {
        requireNonNull(batch, "AgentEndpointRegistrationBatch");
        AgentValidationUtils.validateNamespaceId(batch.getNamespaceId());
        AgentValidationUtils.validateAgentName(batch.getAgentName());
        AgentVersion runtimeVersion = AgentVersion.parse(batch.getRuntimeVersion());
        AgentValidationUtils.validateProtocol(batch.getProtocol());
        AgentVersionRange range = batch.getVersionRange() == null
            ? AgentVersionRange.exact(runtimeVersion)
            : AgentVersionRange.parse(batch.getVersionRange());
        if (!range.contains(runtimeVersion)) {
            throw invalid("versionRange must contain runtimeVersion");
        }
        requireNonEmptyArray(batch.getEndpoints(), MAX_BATCH_ENDPOINTS, "endpoints");
        validateEndpointBatch(batch.getEndpoints(), batch.getNamespaceId(), batch.getAgentName(),
            batch.getProtocol(), EndpointHealthRule.FORBIDDEN, false, false);
    }
    
    /**
     * Validate a runtime Endpoint deregistration batch.
     *
     * @param batch deregistration batch
     * @throws IllegalArgumentException when invalid
     */
    public static void validate(AgentEndpointDeregistrationBatch batch) {
        requireNonNull(batch, "AgentEndpointDeregistrationBatch");
        AgentValidationUtils.validateNamespaceId(batch.getNamespaceId());
        AgentValidationUtils.validateAgentName(batch.getAgentName());
        AgentValidationUtils.validateProtocol(batch.getProtocol());
        requireNonEmptyArray(batch.getEndpoints(), MAX_BATCH_ENDPOINTS, "endpoints");
        validateEndpointBatch(batch.getEndpoints(), batch.getNamespaceId(), batch.getAgentName(),
            batch.getProtocol(), EndpointHealthRule.FORBIDDEN, true, false);
    }
    
    private static void validateCallInterface(AgentDiscoveryCallInterface callInterface,
        String namespaceId, String agentName, String contentDigest) {
        requireNonNull(callInterface, "AgentDiscoveryCallInterface");
        AgentValidationUtils.validateProtocol(callInterface.getProtocol());
        if (callInterface.getProtocolVersion() != null) {
            AgentValidationUtils.validateProtocolVersion(callInterface.getProtocolVersion());
        }
        AgentValidationUtils.validateMediaType(callInterface.getDescriptorMediaType());
        AgentValidationUtils.validateNonNullJsonValue(callInterface.getNativeDescriptor(),
            "nativeDescriptor");
        requireArray(callInterface.getEndpointSets(), MAX_ENDPOINT_SOURCES, "endpointSets");
        Set<EndpointSource> sources = new HashSet<EndpointSource>();
        for (EndpointSet endpointSet : callInterface.getEndpointSets()) {
            validateEndpointSet(endpointSet, namespaceId, agentName, callInterface.getProtocol(),
                contentDigest);
            if (!sources.add(endpointSet.getSource())) {
                throw invalid("Duplicate Endpoint source: " + endpointSet.getSource());
            }
        }
    }
    
    private static void validateEndpointSet(EndpointSet endpointSet, String namespaceId,
        String agentName, String protocol, String contentDigest) {
        requireNonNull(endpointSet, "EndpointSet");
        EndpointSource source = requireNonNull(endpointSet.getSource(), "source");
        validateSourceRevision(endpointSet.getSourceRevision(), source, contentDigest);
        int capacity = source == EndpointSource.DECLARED ? MAX_DECLARED_ENDPOINTS
            : MAX_RUNTIME_ENDPOINTS;
        requireArray(endpointSet.getEndpoints(), capacity, "endpoints");
        EndpointHealthRule healthRule = source == EndpointSource.RUNTIME
            ? EndpointHealthRule.REQUIRED : EndpointHealthRule.FORBIDDEN;
        validateEndpointBatch(endpointSet.getEndpoints(), namespaceId, agentName, protocol,
            healthRule, false, true);
        validateEndpointOrder(endpointSet.getEndpoints(), namespaceId, agentName, protocol);
    }
    
    private static void validateEndpointBatch(List<Endpoint> endpoints, String namespaceId,
        String agentName, String protocol, EndpointHealthRule healthRule,
        boolean deregistration, boolean requireCanonicalOutput) {
        Set<EndpointNaturalKey> keys = new HashSet<EndpointNaturalKey>();
        for (Endpoint endpoint : endpoints) {
            requireNonNull(endpoint, "endpoints item");
            validateEndpoint(endpoint, healthRule, deregistration, requireCanonicalOutput);
            EndpointNaturalKey key =
                EndpointNaturalKey.of(namespaceId, agentName, protocol, endpoint);
            if (!keys.add(key)) {
                throw invalid("Duplicate Endpoint natural key: " + key);
            }
        }
    }
    
    private static void validateEndpoint(Endpoint endpoint, EndpointHealthRule healthRule,
        boolean deregistration, boolean requireCanonicalOutput) {
        if (deregistration && (endpoint.getPriority() != null || endpoint.getWeight() != null
            || endpoint.getMetadata() != null || endpoint.getHealthy() != null)) {
            throw invalid("Deregister Endpoint may contain only uri and transport");
        }
        if (healthRule == EndpointHealthRule.REQUIRED && endpoint.getHealthy() == null) {
            throw invalid("RUNTIME discovery Endpoint must contain healthy");
        }
        if (healthRule == EndpointHealthRule.FORBIDDEN && endpoint.getHealthy() != null) {
            throw invalid("Endpoint must not contain healthy in this context");
        }
        Endpoint canonical = EndpointCanonicalizer.canonicalize(endpoint);
        if (requireCanonicalOutput
            && (!canonical.getUri().equals(endpoint.getUri()) || endpoint.getPriority() == null
                || endpoint.getWeight() == null
                || endpoint.getMetadata() != null && endpoint.getMetadata().isEmpty())) {
            throw invalid("Discovery Endpoint must contain canonical effective values");
        }
    }
    
    private static void validateEndpointOrder(List<Endpoint> endpoints, String namespaceId,
        String agentName, String protocol) {
        Endpoint previous = null;
        EndpointNaturalKey previousKey = null;
        for (Endpoint endpoint : endpoints) {
            Endpoint canonical = EndpointCanonicalizer.canonicalize(endpoint);
            EndpointNaturalKey currentKey =
                EndpointNaturalKey.of(namespaceId, agentName, protocol, canonical);
            if (previous != null) {
                int priorityComparison =
                    Integer.compare(previous.getPriority(), canonical.getPriority());
                if (priorityComparison > 0
                    || priorityComparison == 0 && previousKey.compareTo(currentKey) > 0) {
                    throw invalid("Discovery endpoints must be sorted by priority and natural key");
                }
            }
            previous = canonical;
            previousKey = currentKey;
        }
    }
    
    private static void validateSourceRevision(String revision, EndpointSource source,
        String contentDigest) {
        validatePrintableAscii(revision, 1, 128, "sourceRevision", false);
        if (source == EndpointSource.DECLARED) {
            AgentValidationUtils.validateContentDigest(revision);
            if (contentDigest != null && !contentDigest.equals(revision)) {
                throw invalid("DECLARED sourceRevision must equal contentDigest");
            }
        } else if (!RUNTIME_REVISION_PATTERN.matcher(revision).matches()) {
            throw invalid("Invalid RUNTIME sourceRevision: " + revision);
        }
    }
    
    private static void validateTags(List<String> tags, String fieldName) {
        if (tags == null) {
            return;
        }
        validateOptionalArray(tags, MAX_TAGS, fieldName);
        ensureUnique(tags, fieldName);
        for (String tag : tags) {
            validateLength(tag, 1, 64, fieldName + " item");
        }
    }
    
    private static void validateProtocols(List<String> protocols, String fieldName,
        boolean required) {
        if (protocols == null) {
            if (required) {
                throw invalid(fieldName + " is required");
            }
            return;
        }
        validateOptionalArray(protocols, MAX_PROTOCOLS, fieldName);
        ensureUnique(protocols, fieldName);
        for (String protocol : protocols) {
            AgentValidationUtils.validateProtocol(protocol);
        }
    }
    
    private static void validateTransports(List<String> transports) {
        if (transports == null) {
            return;
        }
        validateOptionalArray(transports, MAX_TRANSPORTS, "transports");
        ensureUnique(transports, "transports");
        for (String transport : transports) {
            AgentValidationUtils.validateTransport(transport);
        }
    }
    
    private static void validateProvider(AgentProvider provider) {
        if (provider == null) {
            return;
        }
        validateLength(provider.getName(), 1, 128, "provider.name");
        if (provider.getUrl() != null) {
            validateAbsoluteUri(provider.getUrl(), "provider.url");
        }
    }
    
    private static void validateAbsoluteUri(String value, String fieldName) {
        validateLength(value, 1, 2048, fieldName);
        try {
            if (!new URI(value).isAbsolute()) {
                throw invalid(fieldName + " must be an absolute URI");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value, e);
        }
    }
    
    private static void validateOptionalLength(String value, int maxLength, String fieldName) {
        if (value != null && codePointLength(value) > maxLength) {
            throw invalid(fieldName + " exceeds " + maxLength + " characters");
        }
    }
    
    private static void validateLength(String value, int minLength, int maxLength,
        String fieldName) {
        if (value == null) {
            throw invalid(fieldName + " is required");
        }
        int length = codePointLength(value);
        if (length < minLength || length > maxLength) {
            throw invalid(fieldName + " length must be between " + minLength + " and "
                + maxLength);
        }
    }
    
    private static void validatePrintableAscii(String value, int minLength, int maxLength,
        String fieldName, boolean allowSpace) {
        validateLength(value, minLength, maxLength, fieldName);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            int minimum = allowSpace ? 0x20 : 0x21;
            if (current < minimum || current > 0x7E) {
                throw invalid("Invalid " + fieldName + ": " + value);
            }
        }
    }
    
    private static <T> void validateOptionalArray(List<T> values, int maxSize,
        String fieldName) {
        requireNonEmptyArray(values, maxSize, fieldName);
    }
    
    private static <T> void requireArray(List<T> values, int maxSize, String fieldName) {
        if (values == null) {
            throw invalid(fieldName + " is required");
        }
        if (values.size() > maxSize) {
            throw invalid(fieldName + " exceeds " + maxSize + " items");
        }
    }
    
    private static <T> void requireNonEmptyArray(List<T> values, int maxSize,
        String fieldName) {
        requireNonEmpty(values, fieldName);
        if (values.size() > maxSize) {
            throw invalid(fieldName + " exceeds " + maxSize + " items");
        }
    }
    
    private static <T> void requireNonEmpty(List<T> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw invalid(fieldName + " must contain at least one item");
        }
    }
    
    private static <T> void ensureUnique(List<T> values, String fieldName) {
        Set<T> unique = new HashSet<T>();
        for (T value : values) {
            if (!unique.add(value)) {
                throw invalid(fieldName + " must contain unique items");
            }
        }
    }
    
    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
    
    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName + " is required");
        }
        return value;
    }
    
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
    
    private enum EndpointHealthRule {
        REQUIRED,
        FORBIDDEN
    }
}
