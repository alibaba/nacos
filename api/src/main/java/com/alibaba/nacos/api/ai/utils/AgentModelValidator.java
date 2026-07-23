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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointState;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.model.Page;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate validation for the public Agent management model.
 *
 * <p>This validator checks the structural, capacity, and cross-field invariants that cannot be
 * represented by individual value objects. The 16 KiB serialized UTF-8 JSON limit for
 * {@link Agent#getExtensions()} is intentionally not checked here because the API module does not
 * own the JSON serialization layer. Bindings and server write paths must enforce that byte limit
 * after serialization.</p>
 *
 * @author Nacos
 */
public final class AgentModelValidator {
    
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    
    private static final int MAX_DESCRIPTION_LENGTH = 2048;
    
    private static final int MAX_URI_LENGTH = 2048;
    
    private static final int MAX_PROVIDER_NAME_LENGTH = 128;
    
    private static final int MAX_OWNER_LENGTH = 128;
    
    private static final int MAX_SCOPE_LENGTH = 64;
    
    private static final int MAX_AUTHOR_LENGTH = 128;
    
    private static final int MAX_TAGS = 32;
    
    private static final int MAX_TAG_LENGTH = 64;
    
    private static final int MAX_EXTENSIONS = 32;
    
    private static final int MAX_EXTENSION_KEY_LENGTH = 128;
    
    private static final int MAX_CALL_INTERFACES = 16;
    
    private static final int MAX_DECLARED_ENDPOINTS = 64;
    
    private static final int MAX_RUNTIME_ENDPOINTS = 1000;
    
    private static final int MAX_VERSION_PAGE_ITEMS = 100;
    
    private AgentModelValidator() {
    }
    
    /**
     * Validate a complete Agent resource.
     *
     * @param agent Agent resource
     * @throws IllegalArgumentException when the resource is invalid
     */
    public static void validateAgent(Agent agent) {
        requireNonNull(agent, "agent");
        validateAgentFields(agent.getNamespaceId(), agent.getAgentName(), agent.getDisplayName(),
            agent.getDescription(), agent.getIconUrl(), agent.getProvider(), agent.getTags(),
            agent.getStatus(), agent.getOwner(), agent.getScope(), agent.getVersionInfo(),
            agent.getVersionCatalog(), agent.getMetaVersion(), agent.getCreateTime(),
            agent.getUpdateTime());
        validateExtensions(agent.getExtensions());
    }
    
    /**
     * Validate a bounded Agent summary.
     *
     * @param summary Agent summary
     * @throws IllegalArgumentException when the summary is invalid
     */
    public static void validateAgentSummary(AgentSummary summary) {
        requireNonNull(summary, "summary");
        validateAgentFields(summary.getNamespaceId(), summary.getAgentName(),
            summary.getDisplayName(), summary.getDescription(), summary.getIconUrl(),
            summary.getProvider(), summary.getTags(), summary.getStatus(), summary.getOwner(),
            summary.getScope(), summary.getVersionInfo(), summary.getVersionCatalog(),
            summary.getMetaVersion(), summary.getCreateTime(), summary.getUpdateTime());
    }
    
    /**
     * Validate an Agent overview and its bounded Version page.
     *
     * @param overview Agent overview
     * @throws IllegalArgumentException when the overview is invalid
     */
    public static void validateOverview(AgentOverview overview) {
        requireNonNull(overview, "overview");
        validateAgent(overview.getAgent());
        validateVersionSummaryPage(overview.getVersionPage());
    }
    
    /**
     * Validate an Agent Version summary page.
     *
     * @param page Agent Version summary page
     * @throws IllegalArgumentException when the page is invalid
     */
    public static void validateVersionSummaryPage(Page<AgentVersionSummary> page) {
        requireNonNull(page, "versionPage");
        if (page.getTotalCount() < 0 || page.getPageNumber() < 1
            || page.getPagesAvailable() < 0) {
            throw new IllegalArgumentException("Invalid Version page metadata");
        }
        List<AgentVersionSummary> pageItems = page.getPageItems();
        requireNonNull(pageItems, "versionPage.pageItems");
        if (pageItems.size() > MAX_VERSION_PAGE_ITEMS) {
            throw new IllegalArgumentException(
                "versionPage.pageItems exceeds " + MAX_VERSION_PAGE_ITEMS + " items");
        }
        for (AgentVersionSummary summary : pageItems) {
            validateVersionSummary(summary);
        }
    }
    
    /**
     * Validate one Agent Version summary.
     *
     * @param summary Agent Version summary
     * @throws IllegalArgumentException when the summary is invalid
     */
    public static void validateVersionSummary(AgentVersionSummary summary) {
        requireNonNull(summary, "version summary");
        AgentValidationUtils.validateVersion(summary.getVersion());
        validateVersionStatus(summary.getStatus());
        validateOptionalLength(summary.getAuthor(), MAX_AUTHOR_LENGTH, "author");
        validateOptionalLength(summary.getChangeDescription(), MAX_DESCRIPTION_LENGTH,
            "changeDescription");
        AgentValidationUtils.validateContentDigest(summary.getContentDigest());
        validateEpochMillis(summary.getCreateTime(), "createTime");
        validateEpochMillis(summary.getUpdateTime(), "updateTime");
    }
    
    /**
     * Validate one exact Agent Version definition.
     *
     * @param detail Agent Version detail
     * @throws IllegalArgumentException when the Version definition is invalid
     */
    public static void validateVersionDetail(AgentVersionDetail detail) {
        requireNonNull(detail, "version detail");
        AgentValidationUtils.validateNamespaceId(detail.getNamespaceId());
        AgentValidationUtils.validateAgentName(detail.getAgentName());
        AgentValidationUtils.validateVersion(detail.getVersion());
        validateVersionStatus(detail.getStatus());
        validateCallInterfaces(detail.getNamespaceId(), detail.getAgentName(),
            detail.getCallInterfaces());
        validateOptionalLength(detail.getAuthor(), MAX_AUTHOR_LENGTH, "author");
        validateOptionalLength(detail.getChangeDescription(), MAX_DESCRIPTION_LENGTH,
            "changeDescription");
        AgentValidationUtils.validateContentDigest(detail.getContentDigest());
        validateEpochMillis(detail.getCreateTime(), "createTime");
        validateEpochMillis(detail.getUpdateTime(), "updateTime");
    }
    
    /**
     * Validate one CallInterface in an Agent protocol group.
     *
     * @param namespaceId effective namespace
     * @param agentName Agent name
     * @param callInterface CallInterface to validate
     * @throws IllegalArgumentException when the CallInterface is invalid
     */
    public static void validateCallInterface(String namespaceId, String agentName,
        AgentCallInterface callInterface) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        requireNonNull(callInterface, "callInterface");
        AgentValidationUtils.validateProtocol(callInterface.getProtocol());
        if (callInterface.getProtocolVersion() != null) {
            AgentValidationUtils.validateProtocolVersion(callInterface.getProtocolVersion());
        }
        AgentValidationUtils.validateMediaType(callInterface.getDescriptorMediaType());
        AgentValidationUtils.validateNonNullJsonValue(callInterface.getNativeDescriptor(),
            "nativeDescriptor");
        validateEndpointSourceOrder(callInterface.getEndpointSourceOrder());
        validateDeclaredEndpoints(namespaceId, agentName, callInterface.getProtocol(),
            callInterface.getDeclaredEndpoints());
    }
    
    /**
     * Validate a standalone CallInterface entrypoint.
     *
     * <p>The synthetic identity is used only to group declared Endpoint natural keys within the
     * supplied CallInterface.</p>
     *
     * @param callInterface CallInterface to validate
     * @throws IllegalArgumentException when the CallInterface is invalid
     */
    public static void validateCallInterface(AgentCallInterface callInterface) {
        validateCallInterface("validation", "validation", callInterface);
    }
    
    /**
     * Validate the compact catalog of online Agent Versions.
     *
     * @param catalog Agent Version catalog
     * @throws IllegalArgumentException when the catalog is invalid
     */
    public static void validateVersionCatalog(AgentVersionCatalog catalog) {
        requireNonNull(catalog, "versionCatalog");
        List<AgentVersionCatalogEntry> versions = catalog.getOnlineVersions();
        requireNonNull(versions, "versionCatalog.onlineVersions");
        if (versions.isEmpty()) {
            if (catalog.getLatestVersion() != null) {
                throw new IllegalArgumentException(
                    "latestVersion must be absent when onlineVersions is empty");
            }
            return;
        }
        
        AgentValidationUtils.validateVersion(catalog.getLatestVersion());
        Set<String> versionValues = new HashSet<String>();
        Set<String> labelValues = new HashSet<String>();
        boolean latestFound = false;
        for (AgentVersionCatalogEntry entry : versions) {
            requireNonNull(entry, "versionCatalog entry");
            AgentVersion version = AgentVersion.parse(entry.getVersion());
            if (!versionValues.add(version.getValue())) {
                throw new IllegalArgumentException(
                    "Duplicate online Agent Version: " + version.getValue());
            }
            latestFound |= catalog.getLatestVersion().equals(version.getValue());
            validateCatalogLabels(entry.getLabels(), labelValues);
            validateProtocols(entry.getProtocols(), "versionCatalog.protocols");
        }
        if (!latestFound) {
            throw new IllegalArgumentException(
                "latestVersion must identify an onlineVersions entry");
        }
    }
    
    /**
     * Validate a raw runtime Endpoint management snapshot.
     *
     * @param snapshot runtime Endpoint snapshot
     * @throws IllegalArgumentException when the snapshot is invalid
     */
    public static void validateRuntimeEndpointSnapshot(RuntimeEndpointSnapshot snapshot) {
        requireNonNull(snapshot, "runtimeEndpointSnapshot");
        AgentValidationUtils.validateNamespaceId(snapshot.getNamespaceId());
        AgentValidationUtils.validateAgentName(snapshot.getAgentName());
        AgentValidationUtils.validateProtocol(snapshot.getProtocol());
        AgentVersion selectedVersion = null;
        if (snapshot.getVersion() != null) {
            selectedVersion = AgentVersion.parse(snapshot.getVersion());
        }
        
        List<RuntimeEndpointSnapshotItem> items = snapshot.getItems();
        requireNonNull(items, "runtimeEndpointSnapshot.items");
        if (items.size() > MAX_RUNTIME_ENDPOINTS) {
            throw new IllegalArgumentException(
                "runtimeEndpointSnapshot.items exceeds " + MAX_RUNTIME_ENDPOINTS + " items");
        }
        Set<EndpointNaturalKey> endpointKeys = new HashSet<EndpointNaturalKey>();
        for (RuntimeEndpointSnapshotItem item : items) {
            validateRuntimeEndpointSnapshotItem(snapshot, selectedVersion, item, endpointKeys);
        }
    }
    
    private static void validateAgentFields(String namespaceId, String agentName,
        String displayName, String description, String iconUrl, AgentProvider provider,
        List<String> tags, String status, String owner, String scope,
        AgentVersionInfo versionInfo, AgentVersionCatalog versionCatalog, Long metaVersion,
        Long createTime, Long updateTime) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        validateOptionalLength(displayName, MAX_DISPLAY_NAME_LENGTH, "displayName");
        validateOptionalLength(description, MAX_DESCRIPTION_LENGTH, "description");
        if (iconUrl != null) {
            validateAbsoluteUri(iconUrl, "iconUrl");
        }
        validateProvider(provider);
        validateTags(tags);
        validateResourceStatus(status);
        validateRequiredLength(owner, MAX_OWNER_LENGTH, "owner");
        validateRequiredLength(scope, MAX_SCOPE_LENGTH, "scope");
        validateVersionInfo(versionInfo);
        validateVersionCatalog(versionCatalog);
        validateVersionInfoCatalogConsistency(versionInfo, versionCatalog);
        validateEpochMillis(metaVersion, "metaVersion");
        validateEpochMillis(createTime, "createTime");
        validateEpochMillis(updateTime, "updateTime");
    }
    
    private static void validateProvider(AgentProvider provider) {
        if (provider == null) {
            return;
        }
        validateRequiredLength(provider.getName(), MAX_PROVIDER_NAME_LENGTH, "provider.name");
        if (provider.getUrl() != null) {
            validateAbsoluteUri(provider.getUrl(), "provider.url");
        }
    }
    
    private static void validateResourceStatus(String status) {
        if (!AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(status)
            && !AiConstants.Agent.RESOURCE_STATUS_DISABLE.equals(status)) {
            throw new IllegalArgumentException("Invalid Agent resource status: " + status);
        }
    }
    
    private static void validateVersionStatus(String status) {
        if (!AiConstants.Agent.VERSION_STATUS_DRAFT.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_REVIEWING.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_REVIEWED.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_ONLINE.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_OFFLINE.equals(status)) {
            throw new IllegalArgumentException("Invalid Agent Version status: " + status);
        }
    }
    
    private static void validateTags(List<String> tags) {
        if (tags == null) {
            return;
        }
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("tags exceeds " + MAX_TAGS + " items");
        }
        Set<String> uniqueTags = new HashSet<String>();
        for (String tag : tags) {
            validateRequiredLength(tag, MAX_TAG_LENGTH, "tag");
            if (!uniqueTags.add(tag)) {
                throw new IllegalArgumentException("Duplicate tag: " + tag);
            }
        }
    }
    
    private static void validateExtensions(Map<String, Object> extensions) {
        if (extensions == null) {
            return;
        }
        if (extensions.size() > MAX_EXTENSIONS) {
            throw new IllegalArgumentException(
                "extensions exceeds " + MAX_EXTENSIONS + " entries");
        }
        for (String key : extensions.keySet()) {
            validateRequiredLength(key, MAX_EXTENSION_KEY_LENGTH, "extension key");
        }
    }
    
    private static void validateVersionInfo(AgentVersionInfo versionInfo) {
        requireNonNull(versionInfo, "versionInfo");
        if (versionInfo.getEditingVersion() != null) {
            AgentValidationUtils.validateVersion(versionInfo.getEditingVersion());
        }
        if (versionInfo.getReviewingVersion() != null) {
            AgentValidationUtils.validateVersion(versionInfo.getReviewingVersion());
        }
        Integer onlineCount = versionInfo.getOnlineCnt();
        if (onlineCount == null || onlineCount < 0) {
            throw new IllegalArgumentException("onlineCnt must be a non-negative integer");
        }
        Map<String, String> labels = versionInfo.getLabels();
        requireNonNull(labels, "versionInfo.labels");
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            AgentValidationUtils.validateLabel(entry.getKey());
            AgentValidationUtils.validateVersion(entry.getValue());
        }
    }
    
    private static void validateVersionInfoCatalogConsistency(AgentVersionInfo versionInfo,
        AgentVersionCatalog catalog) {
        List<AgentVersionCatalogEntry> onlineVersions = catalog.getOnlineVersions();
        if (versionInfo.getOnlineCnt() != onlineVersions.size()) {
            throw new IllegalArgumentException(
                "onlineCnt must equal the number of onlineVersions entries");
        }
        String editingVersion = versionInfo.getEditingVersion();
        String reviewingVersion = versionInfo.getReviewingVersion();
        if (editingVersion != null && editingVersion.equals(reviewingVersion)) {
            throw new IllegalArgumentException(
                "editingVersion and reviewingVersion must identify different Versions");
        }
        Set<String> onlineVersionValues = new HashSet<String>();
        for (AgentVersionCatalogEntry entry : onlineVersions) {
            onlineVersionValues.add(entry.getVersion());
        }
        if (onlineVersionValues.contains(editingVersion)
            || onlineVersionValues.contains(reviewingVersion)) {
            throw new IllegalArgumentException(
                "editingVersion and reviewingVersion must not identify online Versions");
        }
        for (Map.Entry<String, String> label : versionInfo.getLabels().entrySet()) {
            if (label.getValue().equals(editingVersion)
                || label.getValue().equals(reviewingVersion)) {
                throw new IllegalArgumentException(
                    "Labels must not identify editing or reviewing Versions");
            }
        }
        String latestLabelVersion = versionInfo.getLabels().get("latest");
        if (catalog.getLatestVersion() == null) {
            if (latestLabelVersion != null) {
                throw new IllegalArgumentException(
                    "latest label must be absent when no online Version exists");
            }
        } else if (!catalog.getLatestVersion().equals(latestLabelVersion)) {
            throw new IllegalArgumentException(
                "latest label and versionCatalog.latestVersion must match");
        }
        for (AgentVersionCatalogEntry entry : onlineVersions) {
            for (String label : entry.getLabels()) {
                if (!entry.getVersion().equals(versionInfo.getLabels().get(label))) {
                    throw new IllegalArgumentException(
                        "Version catalog label does not match versionInfo: " + label);
                }
            }
        }
        for (Map.Entry<String, String> label : versionInfo.getLabels().entrySet()) {
            if ("latest".equals(label.getKey())
                || !onlineVersionValues.contains(label.getValue())) {
                continue;
            }
            boolean presentInCatalog = false;
            for (AgentVersionCatalogEntry entry : onlineVersions) {
                if (entry.getVersion().equals(label.getValue())
                    && entry.getLabels().contains(label.getKey())) {
                    presentInCatalog = true;
                    break;
                }
            }
            if (!presentInCatalog) {
                throw new IllegalArgumentException(
                    "Online Version label is missing from versionCatalog: " + label.getKey());
            }
        }
    }
    
    private static void validateCatalogLabels(List<String> labels, Set<String> allLabels) {
        requireNonNull(labels, "versionCatalog.labels");
        Set<String> entryLabels = new HashSet<String>();
        for (String label : labels) {
            AgentValidationUtils.validateNonLatestLabel(label);
            if (!entryLabels.add(label) || !allLabels.add(label)) {
                throw new IllegalArgumentException("Duplicate Version label: " + label);
            }
        }
    }
    
    private static void validateProtocols(List<String> protocols, String fieldName) {
        requireNonNull(protocols, fieldName);
        if (protocols.isEmpty() || protocols.size() > MAX_CALL_INTERFACES) {
            throw new IllegalArgumentException(
                fieldName + " must contain 1 to " + MAX_CALL_INTERFACES + " values");
        }
        Set<String> uniqueProtocols = new HashSet<String>();
        for (String protocol : protocols) {
            AgentValidationUtils.validateProtocol(protocol);
            if (!uniqueProtocols.add(protocol)) {
                throw new IllegalArgumentException("Duplicate protocol: " + protocol);
            }
        }
    }
    
    private static void validateCallInterfaces(String namespaceId, String agentName,
        List<AgentCallInterface> callInterfaces) {
        requireNonNull(callInterfaces, "callInterfaces");
        if (callInterfaces.isEmpty() || callInterfaces.size() > MAX_CALL_INTERFACES) {
            throw new IllegalArgumentException(
                "callInterfaces must contain 1 to " + MAX_CALL_INTERFACES + " items");
        }
        Set<String> protocols = new HashSet<String>();
        for (AgentCallInterface callInterface : callInterfaces) {
            validateCallInterface(namespaceId, agentName, callInterface);
            if (!protocols.add(callInterface.getProtocol())) {
                throw new IllegalArgumentException(
                    "Duplicate CallInterface protocol: " + callInterface.getProtocol());
            }
        }
    }
    
    private static void validateEndpointSourceOrder(List<EndpointSource> sourceOrder) {
        requireNonNull(sourceOrder, "endpointSourceOrder");
        if (sourceOrder.isEmpty() || sourceOrder.size() > EndpointSource.values().length) {
            throw new IllegalArgumentException("endpointSourceOrder must contain 1 or 2 sources");
        }
        Set<EndpointSource> uniqueSources = new HashSet<EndpointSource>();
        for (EndpointSource source : sourceOrder) {
            requireNonNull(source, "endpointSourceOrder item");
            if (!uniqueSources.add(source)) {
                throw new IllegalArgumentException("Duplicate Endpoint source: " + source);
            }
        }
    }
    
    private static void validateDeclaredEndpoints(String namespaceId, String agentName,
        String protocol, List<Endpoint> endpoints) {
        if (endpoints == null) {
            return;
        }
        if (endpoints.size() > MAX_DECLARED_ENDPOINTS) {
            throw new IllegalArgumentException(
                "declaredEndpoints exceeds " + MAX_DECLARED_ENDPOINTS + " items");
        }
        Set<EndpointNaturalKey> endpointKeys = new HashSet<EndpointNaturalKey>();
        for (Endpoint endpoint : endpoints) {
            validateEndpoint(endpoint);
            EndpointNaturalKey key = EndpointNaturalKey.of(namespaceId, agentName, protocol,
                endpoint);
            if (!endpointKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate declared Endpoint: " + key);
            }
        }
    }
    
    private static void validateRuntimeEndpointSnapshotItem(RuntimeEndpointSnapshot snapshot,
        AgentVersion selectedVersion, RuntimeEndpointSnapshotItem item,
        Set<EndpointNaturalKey> endpointKeys) {
        requireNonNull(item, "runtimeEndpointSnapshot item");
        validateEndpoint(item.getEndpoint());
        EndpointNaturalKey endpointKey = EndpointNaturalKey.of(snapshot.getNamespaceId(),
            snapshot.getAgentName(), snapshot.getProtocol(), item.getEndpoint());
        if (!endpointKeys.add(endpointKey)) {
            throw new IllegalArgumentException("Duplicate runtime Endpoint: " + endpointKey);
        }
        
        List<RuntimeVersionBinding> bindings = item.getBindings();
        requireNonNull(bindings, "runtimeEndpointSnapshot.bindings");
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("Runtime Endpoint bindings must not be empty");
        }
        Set<String> bindingKeys = new HashSet<String>();
        for (RuntimeVersionBinding binding : bindings) {
            validateRuntimeVersionBinding(binding, selectedVersion, bindingKeys);
        }
        requireNonNull(item.getState(), "runtime Endpoint state");
        requireNonNull(item.getEnabled(), "runtime Endpoint enabled");
        requireNonNull(item.getHealthy(), "runtime Endpoint healthy");
        validateRuntimeEndpointState(item);
        validateEpochMillis(item.getLastUpdatedTime(), "lastUpdatedTime");
    }
    
    private static void validateRuntimeVersionBinding(RuntimeVersionBinding binding,
        AgentVersion selectedVersion, Set<String> bindingKeys) {
        requireNonNull(binding, "runtime Version binding");
        AgentVersion runtimeVersion = AgentVersion.parse(binding.getRuntimeVersion());
        AgentVersionRange versionRange = AgentVersionRange.parse(binding.getVersionRange());
        if (!versionRange.getValue().equals(binding.getVersionRange())) {
            throw new IllegalArgumentException("Runtime Version range must be canonical");
        }
        if (!versionRange.contains(runtimeVersion)) {
            throw new IllegalArgumentException(
                "versionRange must contain runtimeVersion: " + runtimeVersion);
        }
        if (selectedVersion != null && !versionRange.contains(selectedVersion)) {
            throw new IllegalArgumentException(
                "Snapshot binding does not match selected Version: " + selectedVersion);
        }
        String bindingKey = runtimeVersion + "\u0000" + versionRange.getValue();
        if (!bindingKeys.add(bindingKey)) {
            throw new IllegalArgumentException("Duplicate Runtime Version binding");
        }
    }
    
    private static void validateRuntimeEndpointState(RuntimeEndpointSnapshotItem item) {
        RuntimeEndpointState expected;
        if (!item.getEnabled()) {
            expected = RuntimeEndpointState.DISABLED;
        } else if (!item.getHealthy()) {
            expected = RuntimeEndpointState.UNHEALTHY;
        } else {
            expected = RuntimeEndpointState.AVAILABLE;
        }
        if (item.getState() != expected) {
            throw new IllegalArgumentException(
                "Runtime Endpoint state must be " + expected.name());
        }
    }
    
    private static void validateEndpoint(Endpoint endpoint) {
        requireNonNull(endpoint, "Endpoint");
        EndpointCanonicalizer.canonicalize(endpoint);
        if (endpoint.getHealthy() != null) {
            throw new IllegalArgumentException("Management or declared Endpoint forbids healthy");
        }
    }
    
    private static void validateAbsoluteUri(String value, String fieldName) {
        if (value.isEmpty() || codePointLength(value) > MAX_URI_LENGTH) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value, e);
        }
    }
    
    private static void validateRequiredLength(String value, int maximum, String fieldName) {
        if (value == null || value.isEmpty() || codePointLength(value) > maximum) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
    }
    
    private static void validateOptionalLength(String value, int maximum, String fieldName) {
        if (value != null && codePointLength(value) > maximum) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": exceeds " + maximum);
        }
    }
    
    private static void validateEpochMillis(Long value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative integer");
        }
    }
    
    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
    
    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
