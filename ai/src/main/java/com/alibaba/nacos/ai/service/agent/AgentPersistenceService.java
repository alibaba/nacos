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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentResourceExt;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.metadata.AgentResourceExtSerializer;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionCatalogBuilder;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Insert-only persistence orchestration for Agent definitions and their initial draft.
 *
 * <p>The orchestration claims the Version identity before writing the stable AI Storage key and
 * inserts the Resource row only after content is available. Equivalent Version and Resource state
 * is adopted on retry. Once a Version claim is visible, no synchronous cleanup is safe without an
 * operation generation: another create may already reuse the row and stable Storage key. Therefore
 * failures preserve recoverable state for retry or a later generation-aware orphan cleaner. Draft
 * updates and lifecycle transitions are deliberately outside this component's initial scope.</p>
 *
 * @author Nacos
 */
@Service
public class AgentPersistenceService {
    
    private static final String RESOURCE_SOURCE_LOCAL = "local";
    
    private static final int MAX_BIZ_TAGS_LENGTH = 1024;
    
    private final AiResourcePersistService resourcePersistService;
    
    private final AiResourceVersionPersistService versionPersistService;
    
    private final AgentVersionStorageService storageService;
    
    public AgentPersistenceService(AiResourcePersistService resourcePersistService,
        AiResourceVersionPersistService versionPersistService,
        AgentVersionStorageService storageService) {
        this.resourcePersistService = resourcePersistService;
        this.versionPersistService = versionPersistService;
        this.storageService = storageService;
    }
    
    /**
     * Create one Agent and its initial draft as an insert-only logical operation.
     *
     * <p>The Agent argument contains only writable Resource fields. The initial draft may omit the
     * repeated namespace, Agent name, and draft status; when supplied, those values must match the
     * Resource. Read-only projection fields must be absent from both inputs. Repeating an equivalent
     * request returns the existing projection; a conflicting Resource or Version returns
     * {@code RESOURCE_CONFLICT}.</p>
     *
     * @param agent writable Agent Resource fields
     * @param initialDraft initial draft definition
     * @return persisted Agent and its one-item Version summary page
     * @throws NacosException when persistence or AI Storage fails
     */
    public AgentOverview create(Agent agent, AgentVersionDetail initialDraft)
        throws NacosException {
        validateCreateInputs(agent, initialDraft);
        ResourceVersionInfo versionInfo = initialVersionInfo(initialDraft.getVersion());
        AgentVersionCatalog versionCatalog = AgentVersionCatalogBuilder.build(
            Collections.<String, List<String>>emptyMap(),
            Collections.<String, String>emptyMap()).getVersionCatalog();
        Agent normalizedAgent =
            normalizeCreateAgent(agent, toAgentVersionInfo(versionInfo), versionCatalog);
        AgentModelValidator.validateAgent(normalizedAgent);
        
        AgentVersionContent content = new AgentVersionContent(initialDraft.getCallInterfaces());
        PreparedAgentVersionWrite prepared = storageService.prepare(agent.getNamespaceId(),
            agent.getAgentName(), initialDraft.getVersion(), content);
        AgentVersionStorageDescriptor descriptor = prepared.getDescriptor();
        AgentVersionDetail normalizedDraft =
            normalizeInitialDraft(agent, initialDraft, descriptor);
        AgentModelValidator.validateVersionDetail(normalizedDraft);
        
        AiResource resourceRow = toResourceRow(normalizedAgent);
        AiResourceVersion versionRow = toVersionRow(normalizedDraft, descriptor);
        try {
            AiResource existingResource = resourcePersistService.find(resourceRow.getNamespaceId(),
                resourceRow.getName(), resourceRow.getType());
            if (existingResource != null) {
                if (!sameResource(existingResource, resourceRow)) {
                    throw conflict("Agent already exists: " + resourceRow.getName(), null);
                }
            }
            
            claimVersion(versionRow);
            storageService.save(prepared);
            
            if (existingResource == null) {
                try {
                    long resourceId = resourcePersistService.insert(resourceRow);
                    if (resourceId <= 0) {
                        throw new IllegalStateException(
                            "Agent Resource insert returned an invalid id");
                    }
                } catch (RuntimeException insertFailure) {
                    AiResource recoveredResource;
                    try {
                        recoveredResource = resourcePersistService.find(
                            resourceRow.getNamespaceId(), resourceRow.getName(),
                            resourceRow.getType());
                    } catch (RuntimeException readFailure) {
                        insertFailure.addSuppressed(readFailure);
                        throw mapResourceInsertFailure(resourceRow, insertFailure);
                    }
                    if (sameResource(recoveredResource, resourceRow)) {
                        // The insert committed, or an equivalent concurrent create won.
                    } else if (recoveredResource != null) {
                        throw conflict("Agent already exists: " + resourceRow.getName(),
                            insertFailure);
                    } else if (insertFailure instanceof DuplicateKeyException) {
                        throw conflict("Agent already exists: " + resourceRow.getName(),
                            insertFailure);
                    } else {
                        throw serverError(
                            "Failed to insert Agent Resource: " + resourceRow.getName(),
                            insertFailure);
                    }
                }
            }
            try {
                return buildCreatedOverview(agent.getNamespaceId(), agent.getAgentName(),
                    initialDraft.getVersion());
            } catch (Exception e) {
                throw serverError("Agent cannot be read after creation: "
                    + agent.getAgentName(), e);
            }
        } catch (Exception e) {
            throw asNacosException("Failed to create Agent " + agent.getAgentName(), e);
        }
    }
    
    /**
     * Read one exact Agent Resource projection without loading Version content.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @return complete Agent Resource projection
     * @throws NacosException when the Agent is absent or stored metadata is invalid
     */
    public Agent getAgent(String namespaceId, String agentName) throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AiResource row = resourcePersistService.find(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        if (!matches(row, namespaceId, agentName)) {
            throw notFound("Agent not found: " + agentName);
        }
        try {
            return toAgent(row);
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent metadata is invalid: " + agentName, e);
        }
    }
    
    /**
     * Read one exact Agent Version and verify its content against the stored descriptor.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @param version exact, case-sensitive Agent Version
     * @return verified Version detail
     * @throws NacosException when the Version is absent, corrupt, or cannot be read
     */
    public AgentVersionDetail getAgentVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateVersion(version);
        getAgent(namespaceId, agentName);
        AiResourceVersion row = versionPersistService.find(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT, version);
        if (!matches(row, namespaceId, agentName, version)) {
            throw notFound("Agent Version not found: " + agentName + '@' + version);
        }
        final AgentVersionStorageDescriptor descriptor;
        try {
            descriptor = AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent Version descriptor is invalid: " + agentName + '@'
                + version, e);
        }
        AgentVersionContent content = storageService.load(descriptor);
        try {
            AgentVersionDetail result = toVersionDetail(row, descriptor, content);
            AgentModelValidator.validateVersionDetail(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent Version metadata is invalid: " + agentName + '@'
                + version, e);
        }
    }
    
    private void validateCreateInputs(Agent agent, AgentVersionDetail initialDraft) {
        if (agent == null) {
            throw new IllegalArgumentException("Agent must not be null");
        }
        if (initialDraft == null) {
            throw new IllegalArgumentException("initialDraft must not be null");
        }
        AgentValidationUtils.validateNamespaceId(agent.getNamespaceId());
        AgentValidationUtils.validateAgentName(agent.getAgentName());
        AgentValidationUtils.validateVersion(initialDraft.getVersion());
        if (agent.getVersionInfo() != null || agent.getVersionCatalog() != null
            || agent.getMetaVersion() != null || agent.getCreateTime() != null
            || agent.getUpdateTime() != null) {
            throw new IllegalArgumentException(
                "Agent create input must not contain read-only projection fields");
        }
        if (initialDraft.getContentDigest() != null || initialDraft.getCreateTime() != null
            || initialDraft.getUpdateTime() != null) {
            throw new IllegalArgumentException(
                "initialDraft must not contain read-only projection fields");
        }
        if (initialDraft.getNamespaceId() != null
            && !agent.getNamespaceId().equals(initialDraft.getNamespaceId())) {
            throw new IllegalArgumentException("initialDraft namespaceId does not match Agent");
        }
        if (initialDraft.getAgentName() != null
            && !agent.getAgentName().equals(initialDraft.getAgentName())) {
            throw new IllegalArgumentException("initialDraft agentName does not match Agent");
        }
        if (initialDraft.getStatus() != null
            && !AiConstants.Agent.VERSION_STATUS_DRAFT.equals(initialDraft.getStatus())) {
            throw new IllegalArgumentException("initialDraft status must be draft");
        }
    }
    
    private Agent normalizeCreateAgent(Agent source, AgentVersionInfo versionInfo,
        AgentVersionCatalog versionCatalog) {
        Agent result = new Agent();
        result.setNamespaceId(source.getNamespaceId());
        result.setAgentName(source.getAgentName());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setIconUrl(source.getIconUrl());
        result.setProvider(source.getProvider());
        result.setTags(source.getTags());
        result.setExtensions(source.getExtensions());
        result.setStatus(source.getStatus());
        result.setOwner(source.getOwner());
        result.setScope(source.getScope());
        result.setVersionInfo(versionInfo);
        result.setVersionCatalog(versionCatalog);
        result.setMetaVersion(1L);
        result.setCreateTime(0L);
        result.setUpdateTime(0L);
        return result;
    }
    
    private AgentVersionDetail normalizeInitialDraft(Agent agent, AgentVersionDetail source,
        AgentVersionStorageDescriptor descriptor) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setNamespaceId(agent.getNamespaceId());
        result.setAgentName(agent.getAgentName());
        result.setVersion(source.getVersion());
        result.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        result.setCallInterfaces(source.getCallInterfaces());
        result.setAuthor(source.getAuthor());
        result.setChangeDescription(source.getChangeDescription());
        result.setContentDigest(descriptor.getContentDigest());
        result.setCreateTime(0L);
        result.setUpdateTime(0L);
        return result;
    }
    
    private ResourceVersionInfo initialVersionInfo(String version) {
        AgentValidationUtils.validateVersion(version);
        ResourceVersionInfo result = new ResourceVersionInfo();
        result.setEditingVersion(version);
        result.setOnlineCnt(0);
        result.setLabels(new HashMap<String, String>());
        return result;
    }
    
    private String serializeTags(List<String> tags) {
        List<String> persistedTags =
            tags == null ? Collections.<String>emptyList() : tags;
        final String result;
        try {
            result = JacksonUtils.toJson(persistedTags);
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize Agent tags", e);
        }
        if (result.length() > MAX_BIZ_TAGS_LENGTH) {
            throw new IllegalArgumentException(
                "Agent tags exceeds " + MAX_BIZ_TAGS_LENGTH + " persisted characters");
        }
        return result;
    }
    
    private List<String> deserializeTags(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        final List<?> persistedTags;
        try {
            persistedTags = JacksonUtils.toObj(json, List.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid persisted Agent tags", e);
        }
        if (persistedTags == null) {
            throw new IllegalArgumentException("Persisted Agent tags must be a JSON array");
        }
        List<String> result = new ArrayList<String>(persistedTags.size());
        for (Object persistedTag : persistedTags) {
            if (!(persistedTag instanceof String)) {
                throw new IllegalArgumentException(
                    "Persisted Agent tags must contain only strings");
            }
            result.add((String) persistedTag);
        }
        return result;
    }
    
    private String serializeVersionInfo(AgentVersionInfo versionInfo) {
        try {
            return JacksonUtils.toJson(toResourceVersionInfo(versionInfo));
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize Agent version info", e);
        }
    }
    
    private ResourceVersionInfo toResourceVersionInfo(AgentVersionInfo source) {
        if (source == null) {
            throw new IllegalArgumentException("Agent versionInfo must not be null");
        }
        ResourceVersionInfo result = new ResourceVersionInfo();
        result.setEditingVersion(source.getEditingVersion());
        result.setReviewingVersion(source.getReviewingVersion());
        result.setOnlineCnt(source.getOnlineCnt());
        result.setLabels(source.getLabels() == null ? null
            : new HashMap<String, String>(source.getLabels()));
        return result;
    }
    
    private AgentVersionInfo toAgentVersionInfo(ResourceVersionInfo source) {
        if (source == null) {
            throw new IllegalArgumentException("Stored Agent versionInfo must not be null");
        }
        AgentVersionInfo result = new AgentVersionInfo();
        result.setEditingVersion(source.getEditingVersion());
        result.setReviewingVersion(source.getReviewingVersion());
        result.setOnlineCnt(source.getOnlineCnt());
        result.setLabels(source.getLabels() == null ? null
            : new HashMap<String, String>(source.getLabels()));
        return result;
    }
    
    private AiResource toResourceRow(Agent agent) {
        AgentResourceExt resourceExt = new AgentResourceExt();
        resourceExt.setSchemaVersion(AgentResourceExt.SCHEMA_VERSION);
        resourceExt.setDisplayName(agent.getDisplayName());
        resourceExt.setIconUrl(agent.getIconUrl());
        resourceExt.setProvider(agent.getProvider());
        resourceExt.setExtensions(agent.getExtensions());
        resourceExt.setVersionCatalog(agent.getVersionCatalog());
        
        AiResource result = new AiResource();
        result.setNamespaceId(agent.getNamespaceId());
        result.setName(agent.getAgentName());
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setDesc(agent.getDescription());
        result.setStatus(agent.getStatus());
        result.setOwner(agent.getOwner());
        result.setScope(agent.getScope());
        result.setBizTags(serializeTags(agent.getTags()));
        result.setExt(AgentResourceExtSerializer.serialize(resourceExt));
        result.setFrom(RESOURCE_SOURCE_LOCAL);
        result.setVersionInfo(serializeVersionInfo(agent.getVersionInfo()));
        result.setMetaVersion(1L);
        return result;
    }
    
    private AiResourceVersion toVersionRow(AgentVersionDetail draft,
        AgentVersionStorageDescriptor descriptor) {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(draft.getNamespaceId());
        result.setName(draft.getAgentName());
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setVersion(draft.getVersion());
        result.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        result.setAuthor(draft.getAuthor());
        result.setDesc(draft.getChangeDescription());
        result.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        return result;
    }
    
    private Agent toAgent(AiResource row) {
        AgentResourceExt resourceExt = AgentResourceExtSerializer.deserialize(row.getExt());
        
        Agent result = new Agent();
        result.setNamespaceId(row.getNamespaceId());
        result.setAgentName(row.getName());
        result.setDisplayName(resourceExt.getDisplayName());
        result.setDescription(row.getDesc());
        result.setIconUrl(resourceExt.getIconUrl());
        result.setProvider(resourceExt.getProvider());
        result.setTags(deserializeTags(row.getBizTags()));
        result.setExtensions(resourceExt.getExtensions());
        result.setStatus(row.getStatus());
        result.setOwner(row.getOwner());
        result.setScope(row.getScope());
        result.setVersionInfo(toAgentVersionInfo(AiResourceManager.requireVersionInfo(row)));
        result.setVersionCatalog(resourceExt.getVersionCatalog());
        result.setMetaVersion(row.getMetaVersion());
        result.setCreateTime(toMillis(row.getGmtCreate()));
        result.setUpdateTime(toMillis(row.getGmtModified()));
        AgentModelValidator.validateAgent(result);
        return result;
    }
    
    private AgentVersionDetail toVersionDetail(AiResourceVersion row,
        AgentVersionStorageDescriptor descriptor, AgentVersionContent content) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setNamespaceId(row.getNamespaceId());
        result.setAgentName(row.getName());
        result.setVersion(row.getVersion());
        result.setStatus(row.getStatus());
        result.setCallInterfaces(content.getCallInterfaces());
        result.setAuthor(row.getAuthor());
        result.setChangeDescription(row.getDesc());
        result.setContentDigest(descriptor.getContentDigest());
        result.setCreateTime(toMillis(row.getGmtCreate()));
        result.setUpdateTime(toMillis(row.getGmtModified()));
        return result;
    }
    
    private AgentOverview buildCreatedOverview(String namespaceId, String agentName,
        String version) throws NacosException {
        Agent agent = getAgent(namespaceId, agentName);
        AiResourceVersion versionRow = versionPersistService.find(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT, version);
        if (!matches(versionRow, namespaceId, agentName, version)) {
            throw serverError("Created Agent Version row cannot be read", null);
        }
        final AgentVersionSummary summary;
        try {
            summary = toVersionSummary(versionRow);
            AgentModelValidator.validateVersionSummary(summary);
        } catch (IllegalArgumentException e) {
            throw serverError("Created Agent Version metadata is invalid", e);
        }
        Page<AgentVersionSummary> page = new Page<AgentVersionSummary>();
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(summary));
        AgentOverview result = new AgentOverview();
        result.setAgent(agent);
        result.setVersionPage(page);
        AgentModelValidator.validateOverview(result);
        return result;
    }
    
    private AgentVersionSummary toVersionSummary(AiResourceVersion row) {
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        AgentVersionSummary result = new AgentVersionSummary();
        result.setVersion(row.getVersion());
        result.setStatus(row.getStatus());
        result.setAuthor(row.getAuthor());
        result.setChangeDescription(row.getDesc());
        result.setContentDigest(descriptor.getContentDigest());
        result.setCreateTime(toMillis(row.getGmtCreate()));
        result.setUpdateTime(toMillis(row.getGmtModified()));
        return result;
    }
    
    private void claimVersion(AiResourceVersion version) throws NacosException {
        AiResourceVersion existingVersion = versionPersistService.find(version.getNamespaceId(),
            version.getName(), version.getType(), version.getVersion());
        if (existingVersion != null) {
            if (sameVersion(existingVersion, version)) {
                return;
            }
            throw conflict(versionConflictMessage(version), null);
        }
        try {
            long id = versionPersistService.insert(version);
            if (id <= 0) {
                throw new IllegalStateException("Agent Version insert returned an invalid id");
            }
        } catch (RuntimeException insertFailure) {
            final AiResourceVersion recoveredVersion;
            try {
                recoveredVersion = versionPersistService.find(version.getNamespaceId(),
                    version.getName(), version.getType(), version.getVersion());
            } catch (RuntimeException readFailure) {
                insertFailure.addSuppressed(readFailure);
                throw mapVersionInsertFailure(version, insertFailure);
            }
            if (sameVersion(recoveredVersion, version)) {
                return;
            }
            if (recoveredVersion != null || insertFailure instanceof DuplicateKeyException) {
                throw conflict(versionConflictMessage(version), insertFailure);
            }
            throw serverError("Failed to insert Agent Version: " + version.getName() + '@'
                + version.getVersion(), insertFailure);
        }
    }
    
    private boolean sameResource(AiResource actual, AiResource expected) {
        if (!matches(actual, expected.getNamespaceId(), expected.getName())
            || !Objects.equals(actual.getDesc(), expected.getDesc())
            || !Objects.equals(actual.getStatus(), expected.getStatus())
            || !Objects.equals(actual.getFrom(), expected.getFrom())
            || !Objects.equals(actual.getScope(), expected.getScope())
            || !Objects.equals(actual.getOwner(), expected.getOwner())) {
            return false;
        }
        return Objects.equals(deserializeTags(actual.getBizTags()),
            deserializeTags(expected.getBizTags()))
            && sameResourceExt(AgentResourceExtSerializer.deserialize(actual.getExt()),
                AgentResourceExtSerializer.deserialize(expected.getExt()))
            && sameVersionInfo(AiResourceManager.requireVersionInfo(actual),
                AiResourceManager.requireVersionInfo(expected));
    }
    
    private boolean sameVersion(AiResourceVersion actual, AiResourceVersion expected) {
        return matches(actual, expected.getNamespaceId(), expected.getName(),
            expected.getVersion()) && Objects.equals(actual.getStatus(), expected.getStatus())
            && Objects.equals(actual.getAuthor(), expected.getAuthor())
            && Objects.equals(actual.getDesc(), expected.getDesc())
            && sameStorageDescriptor(
                AgentVersionStorageDescriptorSerializer.deserialize(actual.getStorage()),
                AgentVersionStorageDescriptorSerializer.deserialize(expected.getStorage()))
            && Objects.equals(actual.getPublishPipelineInfo(), expected.getPublishPipelineInfo());
    }
    
    private boolean sameResourceExt(AgentResourceExt actual, AgentResourceExt expected) {
        return Objects.equals(actual.getSchemaVersion(), expected.getSchemaVersion())
            && Objects.equals(actual.getDisplayName(), expected.getDisplayName())
            && Objects.equals(actual.getIconUrl(), expected.getIconUrl())
            && sameProvider(actual.getProvider(), expected.getProvider())
            && Objects.equals(actual.getExtensions(), expected.getExtensions())
            && sameVersionCatalog(actual.getVersionCatalog(), expected.getVersionCatalog());
    }
    
    private boolean sameProvider(AgentProvider actual, AgentProvider expected) {
        return actual == expected || actual != null && expected != null
            && Objects.equals(actual.getName(), expected.getName())
            && Objects.equals(actual.getUrl(), expected.getUrl());
    }
    
    private boolean sameVersionInfo(ResourceVersionInfo actual, ResourceVersionInfo expected) {
        return Objects.equals(actual.getEditingVersion(), expected.getEditingVersion())
            && Objects.equals(actual.getReviewingVersion(), expected.getReviewingVersion())
            && Objects.equals(actual.getOnlineCnt(), expected.getOnlineCnt())
            && Objects.equals(actual.getLabels(), expected.getLabels());
    }
    
    private boolean sameVersionCatalog(AgentVersionCatalog actual,
        AgentVersionCatalog expected) {
        if (!Objects.equals(actual.getLatestVersion(), expected.getLatestVersion())
            || actual.getOnlineVersions().size() != expected.getOnlineVersions().size()) {
            return false;
        }
        for (int i = 0; i < actual.getOnlineVersions().size(); i++) {
            AgentVersionCatalogEntry actualEntry = actual.getOnlineVersions().get(i);
            AgentVersionCatalogEntry expectedEntry = expected.getOnlineVersions().get(i);
            if (!Objects.equals(actualEntry.getVersion(), expectedEntry.getVersion())
                || !Objects.equals(actualEntry.getLabels(), expectedEntry.getLabels())
                || !Objects.equals(actualEntry.getProtocols(), expectedEntry.getProtocols())) {
                return false;
            }
        }
        return true;
    }
    
    private boolean sameStorageDescriptor(AgentVersionStorageDescriptor actual,
        AgentVersionStorageDescriptor expected) {
        return Objects.equals(actual.getProvider(), expected.getProvider())
            && Objects.equals(actual.getKey(), expected.getKey())
            && Objects.equals(actual.getKeyFormat(), expected.getKeyFormat())
            && Objects.equals(actual.getAgentNameCodec(), expected.getAgentNameCodec())
            && Objects.equals(actual.getContentDigest(), expected.getContentDigest())
            && Objects.equals(actual.getMediaType(), expected.getMediaType())
            && Objects.equals(actual.getSchemaVersion(), expected.getSchemaVersion())
            && Objects.equals(actual.getSize(), expected.getSize());
    }
    
    private NacosException mapResourceInsertFailure(AiResource resource,
        RuntimeException failure) {
        return failure instanceof DuplicateKeyException
            ? conflict("Agent already exists: " + resource.getName(), failure)
            : serverError("Failed to insert Agent Resource: " + resource.getName(), failure);
    }
    
    private NacosException mapVersionInsertFailure(AiResourceVersion version,
        RuntimeException failure) {
        return failure instanceof DuplicateKeyException
            ? conflict(versionConflictMessage(version), failure)
            : serverError("Failed to insert Agent Version: " + version.getName() + '@'
                + version.getVersion(), failure);
    }
    
    private String versionConflictMessage(AiResourceVersion version) {
        return "Agent Version already exists: " + version.getName() + '@' + version.getVersion();
    }
    
    private boolean matches(AiResource resource, String namespaceId, String agentName) {
        return resource != null && namespaceId.equals(resource.getNamespaceId())
            && agentName.equals(resource.getName())
            && Constants.Agent.RESOURCE_TYPE_AGENT.equals(resource.getType());
    }
    
    private boolean matches(AiResourceVersion version, String namespaceId, String agentName,
        String exactVersion) {
        return version != null && namespaceId.equals(version.getNamespaceId())
            && agentName.equals(version.getName()) && exactVersion.equals(version.getVersion())
            && Constants.Agent.RESOURCE_TYPE_AGENT.equals(version.getType());
    }
    
    private Long toMillis(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.getTime();
    }
    
    private NacosApiException conflict(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message)
            : new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, cause,
                message);
    }
    
    private NacosApiException notFound(String message) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            message);
    }
    
    private NacosApiException serverError(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, message)
            : new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, cause,
                message);
    }
    
    private NacosException asNacosException(String message, Exception cause) {
        return cause instanceof NacosException ? (NacosException) cause
            : serverError(message, cause);
    }
}
