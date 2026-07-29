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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentResourceExt;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.metadata.AgentResourceExtSerializer;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionCatalogBuilder;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
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
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistence orchestration for Agent definitions and Version content.
 *
 * <p>The orchestration claims the Version identity before writing the stable AI Storage key and
 * inserts the Resource row only after content is available. Equivalent Version and Resource state
 * is adopted on retry. Once a Version claim is visible, no synchronous cleanup is safe without an
 * operation generation: another create may already reuse the row and stable Storage key. Therefore
 * failures preserve recoverable state for retry or a later generation-aware orphan cleaner.</p>
 *
 * @author Nacos
 */
@Service
public class AgentPersistenceService {
    
    private static final String RESOURCE_SOURCE_LOCAL = "local";
    
    private static final int MAX_BIZ_TAGS_LENGTH = 1024;
    
    private static final int VERSION_SCAN_PAGE_SIZE = 100;
    
    private static final String VALIDATION_CONTENT_DIGEST =
        AgentVersionContentSerializer.digest(new byte[0]);
    
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
     * Create the first Agent draft and its metadata as an insert-only logical operation.
     *
     * <p>The Agent argument contains only writable Resource fields. The initial draft may omit the
     * repeated namespace, Agent name, and draft status; when supplied, those values must match the
     * Resource. Read-only projection fields must be absent from both inputs. Repeating an equivalent
     * request returns the existing projection; a conflicting Resource or Version returns
     * {@code RESOURCE_CONFLICT}.</p>
     *
     * @param agent writable Agent Resource fields
     * @param initialDraft initial draft definition
     * @return persisted and storage-verified first draft
     * @throws NacosException when persistence or AI Storage fails
     */
    public AgentVersionDetail createInitialDraft(Agent agent, AgentVersionDetail initialDraft)
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
                return getAgentVersion(agent.getNamespaceId(), agent.getAgentName(),
                    initialDraft.getVersion());
            } catch (Exception e) {
                throw serverError("Agent draft cannot be read after creation: "
                    + agent.getAgentName() + '@' + initialDraft.getVersion(), e);
            }
        } catch (Exception e) {
            throw asNacosException("Failed to create initial Agent draft " + agent.getAgentName()
                + '@' + initialDraft.getVersion(), e);
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
     * Read one Agent and the first bounded page of Version summaries.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @param versionPageSize bounded Version-summary page size
     * @return Agent overview
     * @throws NacosException when stored metadata is absent or invalid
     */
    public AgentOverview getAgentOverview(String namespaceId, String agentName,
        int versionPageSize) throws NacosException {
        AgentOverview result = new AgentOverview();
        result.setAgent(getAgent(namespaceId, agentName));
        result.setVersionPage(
            listAgentVersions(namespaceId, agentName, null, 1, versionPageSize));
        AgentModelValidator.validateOverview(result);
        return result;
    }
    
    /**
     * Try to replace every writable field of one Agent using an already authorized Resource row.
     *
     * <p>The application service reloads and authorizes the Resource before each retry. This
     * method performs one CAS attempt while preserving the derived Version facts from that exact
     * authorized row.</p>
     *
     * @param replacement complete writable Agent replacement
     * @param current authorized current Resource row
     * @return updated Agent projection, or {@code null} when the CAS did not match
     * @throws NacosException when the Resource is absent, invalid, or persistence fails
     */
    public Agent tryUpdateAgent(Agent replacement, AiResource current) throws NacosException {
        validateAgentUpdateInputs(replacement);
        if (!matches(current, replacement.getNamespaceId(), replacement.getAgentName())) {
            throw notFound("Agent not found: " + replacement.getAgentName());
        }
        Agent currentAgent;
        try {
            currentAgent = toAgent(current);
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent metadata is invalid: " + replacement.getAgentName(),
                e);
        }
        Agent normalized = normalizeAgentUpdate(replacement, currentAgent);
        AgentModelValidator.validateAgent(normalized);
        AiResource updateValue = toResourceRow(normalized);
        if (!resourcePersistService.updateMetaCas(replacement.getNamespaceId(),
            replacement.getAgentName(), Constants.Agent.RESOURCE_TYPE_AGENT,
            current.getMetaVersion(), updateValue)) {
            return null;
        }
        return getAgent(replacement.getNamespaceId(), replacement.getAgentName());
    }
    
    /**
     * List bounded Agent summaries from an already visibility-constrained query.
     *
     * @param condition Resource query condition
     * @param pageNo page number
     * @param pageSize page size
     * @return Agent summary page
     * @throws NacosException when stored metadata is invalid
     */
    public Page<AgentSummary> listAgents(QueryCondition condition, int pageNo, int pageSize)
        throws NacosException {
        Page<AiResource> source = resourcePersistService.list(condition, pageNo, pageSize);
        List<AgentSummary> summaries = new ArrayList<AgentSummary>();
        try {
            if (source != null && source.getPageItems() != null) {
                for (AiResource row : source.getPageItems()) {
                    AgentSummary summary = toAgentSummary(row);
                    AgentModelValidator.validateAgentSummary(summary);
                    summaries.add(summary);
                }
            }
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent summary metadata is invalid", e);
        }
        Page<AgentSummary> result = new Page<AgentSummary>();
        result.setPageNumber(pageNo);
        result.setTotalCount(source == null ? 0 : source.getTotalCount());
        result.setPagesAvailable(source == null ? 0 : source.getPagesAvailable());
        result.setPageItems(summaries);
        return result;
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
        AgentVersionStorageDescriptor descriptor =
            requireStorageDescriptor(row, agentName, version);
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
    
    /**
     * Replace one draft Version.
     *
     * <p>The update follows the existing AI Resource draft flow: verify the current Version is a
     * draft, overwrite its fixed AI Storage key, and then update the Version row's storage pointer
     * and description. The provider and opaque key selected at Version creation are preserved
     * instead of being recomputed from current server configuration.</p>
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @param version exact, case-sensitive draft Version
     * @param callInterfaces complete replacement CallInterface list
     * @param changeDescription replacement change description
     * @return updated and storage-verified Version detail
     * @throws NacosException when the Version is absent, no longer a draft, or cannot be persisted
     */
    public AgentVersionDetail updateDraft(String namespaceId, String agentName, String version,
        List<AgentCallInterface> callInterfaces, String changeDescription) throws NacosException {
        validateDraftUpdateInputs(namespaceId, agentName, version, callInterfaces,
            changeDescription);
        try {
            Agent currentAgent = getAgent(namespaceId, agentName);
            AiResourceVersion currentRow = findVersion(namespaceId, agentName, version);
            requireCurrentDraft(currentAgent, currentRow, agentName, version);
            AgentVersionStorageDescriptor currentDescriptor =
                requireStorageDescriptor(currentRow, agentName, version);
            PreparedAgentVersionWrite prepared = storageService.prepare(currentDescriptor,
                new AgentVersionContent(callInterfaces));
            AgentVersionStorageDescriptor targetDescriptor = prepared.getDescriptor();
            String targetStorage =
                AgentVersionStorageDescriptorSerializer.serialize(targetDescriptor);
            
            validateUpdatedDraft(currentRow, targetDescriptor, callInterfaces,
                changeDescription);
            storageService.save(prepared);
            int updated = versionPersistService.updateStorageAndDesc(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, version, targetStorage, changeDescription);
            if (updated != 1) {
                throw serverError("Agent draft Version row was not updated: " + agentName + '@'
                    + version, null);
            }
            return getAgentVersion(namespaceId, agentName, version);
        } catch (Exception e) {
            throw asNacosException("Failed to update Agent draft " + agentName + '@' + version, e);
        }
    }
    
    /**
     * Create one subsequent draft from direct content or one exact existing Version.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @param draft new draft metadata and optional direct CallInterface content
     * @param basedOnVersion exact source Version when direct content is absent
     * @return persisted and storage-verified draft
     * @throws NacosException when the Agent, source Version, or persistence operation fails
     */
    public AgentVersionDetail createDraft(String namespaceId, String agentName,
        AgentVersionDetail draft, String basedOnVersion) throws NacosException {
        validateSubsequentDraftInputs(namespaceId, agentName, draft, basedOnVersion);
        try {
            Agent currentAgent = getAgent(namespaceId, agentName);
            ensureDraftSlotAvailable(currentAgent, draft.getVersion());
            List<AgentCallInterface> callInterfaces = draft.getCallInterfaces();
            if (callInterfaces == null) {
                AgentVersionDetail source =
                    getAgentVersion(namespaceId, agentName, basedOnVersion);
                callInterfaces = source.getCallInterfaces();
            }
            AgentVersionContent content = new AgentVersionContent(callInterfaces);
            PreparedAgentVersionWrite prepared = storageService.prepare(namespaceId, agentName,
                draft.getVersion(), content);
            AgentVersionDetail normalizedDraft =
                normalizeSubsequentDraft(namespaceId, agentName, draft, callInterfaces,
                    prepared.getDescriptor());
            AgentModelValidator.validateVersionDetail(normalizedDraft);
            claimVersion(toVersionRow(normalizedDraft, prepared.getDescriptor()));
            storageService.save(prepared);
            markEditingVersion(namespaceId, agentName, draft.getVersion());
            return getAgentVersion(namespaceId, agentName, draft.getVersion());
        } catch (Exception e) {
            throw asNacosException("Failed to create Agent draft " + agentName + '@'
                + draft.getVersion(), e);
        }
    }
    
    /**
     * Delete one exact current draft and its Agent Version content.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @param version exact draft Version
     * @throws NacosException when the target is not a draft or cleanup fails
     */
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateVersion(version);
        try {
            AiResourceVersion versionRow = findVersion(namespaceId, agentName, version);
            if (!AiConstants.Agent.VERSION_STATUS_DRAFT.equals(versionRow.getStatus())) {
                throw illegalState("Agent Version is not a draft: " + agentName + '@' + version);
            }
            AgentVersionStorageDescriptor descriptor =
                requireStorageDescriptor(versionRow, agentName, version);
            clearEditingVersion(namespaceId, agentName, version);
            try {
                int deleted = versionPersistService.delete(namespaceId, agentName,
                    Constants.Agent.RESOURCE_TYPE_AGENT, version);
                if (deleted != 1) {
                    throw serverError("Agent draft Version row was not deleted: " + agentName
                        + '@' + version, null);
                }
            } catch (Exception deleteFailure) {
                try {
                    AiResourceVersion remaining = versionPersistService.find(namespaceId,
                        agentName, Constants.Agent.RESOURCE_TYPE_AGENT, version);
                    if (matches(remaining, namespaceId, agentName, version)
                        && AiConstants.Agent.VERSION_STATUS_DRAFT.equals(remaining.getStatus())) {
                        markEditingVersion(namespaceId, agentName, version);
                    }
                } catch (Exception compensationFailure) {
                    deleteFailure.addSuppressed(compensationFailure);
                }
                throw deleteFailure;
            }
            storageService.delete(descriptor);
        } catch (Exception e) {
            throw asNacosException("Failed to delete Agent draft " + agentName + '@' + version, e);
        }
    }
    
    /**
     * Delete an Agent definition, every Version row, and all referenced Version content.
     *
     * <p>Runtime Endpoint publisher state is intentionally not touched.</p>
     *
     * @param namespaceId namespace identifier
     * @param agentName exact, case-sensitive Agent name
     * @throws NacosException when metadata or Version content cleanup fails
     */
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        getAgent(namespaceId, agentName);
        List<AiResourceVersion> versionRows =
            listAllVersionRows(namespaceId, agentName, null);
        List<AgentVersionStorageDescriptor> descriptors =
            new ArrayList<AgentVersionStorageDescriptor>(versionRows.size());
        try {
            for (AiResourceVersion row : versionRows) {
                descriptors.add(requireStorageDescriptor(row, agentName, row.getVersion()));
            }
        } catch (Exception e) {
            throw asNacosException("Failed to read Agent Version storage descriptors for "
                + agentName, e);
        }
        
        int deletedResources = resourcePersistService.delete(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        if (deletedResources != 1) {
            throw serverError("Agent Resource row was not deleted: " + agentName, null);
        }
        int deletedVersions = versionPersistService.deleteByNameAndType(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        if (!versionRows.isEmpty() && deletedVersions <= 0) {
            throw serverError("Agent Version rows were not deleted: " + agentName, null);
        }
        NacosException firstFailure = null;
        for (AgentVersionStorageDescriptor descriptor : descriptors) {
            try {
                storageService.delete(descriptor);
            } catch (NacosException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                } else {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        if (firstFailure != null) {
            throw serverError("Agent definition was deleted but some Version content could not be "
                + "cleaned: " + agentName, firstFailure);
        }
    }
    
    /**
     * List Agent Version summaries without loading Version content.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param status optional Version status filter
     * @param pageNo page number
     * @param pageSize page size
     * @return Version summary page
     * @throws NacosException when the Agent or stored descriptor is invalid
     */
    public Page<AgentVersionSummary> listAgentVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        getAgent(namespaceId, agentName);
        Page<AiResourceVersion> source = versionPersistService.list(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT, status, pageNo, pageSize);
        List<AgentVersionSummary> summaries = new ArrayList<AgentVersionSummary>();
        try {
            if (source != null && source.getPageItems() != null) {
                for (AiResourceVersion row : source.getPageItems()) {
                    AgentVersionSummary summary = toVersionSummary(row);
                    AgentModelValidator.validateVersionSummary(summary);
                    summaries.add(summary);
                }
            }
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent Version summary is invalid: " + agentName, e);
        }
        Page<AgentVersionSummary> result = new Page<AgentVersionSummary>();
        result.setPageNumber(pageNo);
        result.setTotalCount(source == null ? 0 : source.getTotalCount());
        result.setPagesAvailable(source == null ? 0 : source.getPagesAvailable());
        result.setPageItems(summaries);
        return result;
    }
    
    /**
     * Read one exact Agent Version summary without loading Version content.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Agent Version
     * @return Version summary
     * @throws NacosException when the Version is absent or invalid
     */
    public AgentVersionSummary getAgentVersionSummary(String namespaceId, String agentName,
        String version) throws NacosException {
        getAgent(namespaceId, agentName);
        try {
            AgentVersionSummary result =
                toVersionSummary(findVersion(namespaceId, agentName, version));
            AgentModelValidator.validateVersionSummary(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent Version metadata is invalid: " + agentName + '@'
                + version, e);
        }
    }
    
    AiResourceVersion requireVersionRow(String namespaceId, String agentName, String version)
        throws NacosApiException {
        return findVersion(namespaceId, agentName, version);
    }
    
    void updateVersionStatus(String namespaceId, String agentName, String version, String status)
        throws NacosException {
        int updated = versionPersistService.updateStatus(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT, version, status);
        if (updated != 1) {
            throw serverError("Agent Version status was not updated: " + agentName + '@'
                + version, null);
        }
    }
    
    void updatePublishPipelineInfo(String namespaceId, String agentName, String version,
        String publishPipelineInfo) throws NacosException {
        int updated = versionPersistService.updatePublishPipelineInfo(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT, version, publishPipelineInfo);
        if (updated != 1) {
            throw serverError("Agent Version pipeline info was not updated: " + agentName + '@'
                + version, null);
        }
    }
    
    /**
     * Rebuild Agent lifecycle summary and Version catalog from current online Version facts.
     *
     * <p>The Resource CAS writes {@code version_info} and {@code ext.versionCatalog} together.
     * Every retry re-reads Version status and verified content so a stale catalog is never copied
     * over a concurrent Resource update.</p>
     */
    Agent synchronizeDerivedState(String namespaceId, String agentName, String preferredLatest,
        Map<String, String> requestedLabels, String clearEditingVersion,
        String clearReviewingVersion) throws NacosException {
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            AiResource meta = resourcePersistService.find(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT);
            if (!matches(meta, namespaceId, agentName)) {
                throw notFound("Agent not found: " + agentName);
            }
            if (meta.getMetaVersion() == null) {
                throw serverError("Stored Agent metaVersion is missing: " + agentName, null);
            }
            ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
            if (StringUtils.equals(versionInfo.getEditingVersion(), clearEditingVersion)) {
                versionInfo.setEditingVersion(null);
            }
            if (StringUtils.equals(versionInfo.getReviewingVersion(), clearReviewingVersion)) {
                versionInfo.setReviewingVersion(null);
            }
            Map<String, String> labels = mergeRequestedLabels(versionInfo, requestedLabels);
            if (preferredLatest != null) {
                AgentValidationUtils.validateVersion(preferredLatest);
                labels.put(AiResourceConstants.LABEL_LATEST, preferredLatest);
            }
            validateLabelTargets(namespaceId, agentName, labels);
            Map<String, List<String>> onlineVersionProtocols =
                loadOnlineVersionProtocols(namespaceId, agentName);
            AgentVersionCatalogBuilder.Result derived =
                AgentVersionCatalogBuilder.build(onlineVersionProtocols, labels);
            versionInfo.setOnlineCnt(onlineVersionProtocols.size());
            versionInfo.setLabels(new LinkedHashMap<String, String>(derived.getLabels()));
            
            AgentResourceExt resourceExt = AgentResourceExtSerializer.deserialize(meta.getExt());
            resourceExt.setVersionCatalog(derived.getVersionCatalog());
            AiResource updateValue = buildMetaUpdateValue(meta, versionInfo, resourceExt);
            if (resourcePersistService.updateMetaCas(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, meta.getMetaVersion(), updateValue)) {
                return getAgent(namespaceId, agentName);
            }
        }
        throw conflict("Agent lifecycle metadata changed concurrently: " + agentName, null);
    }
    
    private void validateSubsequentDraftInputs(String namespaceId, String agentName,
        AgentVersionDetail draft, String basedOnVersion) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        if (draft == null) {
            throw new IllegalArgumentException("Agent draft must not be null");
        }
        AgentValidationUtils.validateVersion(draft.getVersion());
        if (draft.getNamespaceId() != null
            && !namespaceId.equals(draft.getNamespaceId())) {
            throw new IllegalArgumentException("Agent draft namespaceId does not match request");
        }
        if (draft.getAgentName() != null && !agentName.equals(draft.getAgentName())) {
            throw new IllegalArgumentException("Agent draft agentName does not match request");
        }
        if (draft.getStatus() != null
            && !AiConstants.Agent.VERSION_STATUS_DRAFT.equals(draft.getStatus())) {
            throw new IllegalArgumentException("Agent draft status must be draft");
        }
        if (draft.getContentDigest() != null || draft.getCreateTime() != null
            || draft.getUpdateTime() != null) {
            throw new IllegalArgumentException(
                "Agent draft must not contain read-only projection fields");
        }
        boolean directContent = draft.getCallInterfaces() != null;
        boolean copiedContent = StringUtils.isNotBlank(basedOnVersion);
        if (directContent == copiedContent) {
            throw new IllegalArgumentException(
                "Agent draft must contain either callInterfaces or basedOnVersion");
        }
        if (copiedContent) {
            AgentValidationUtils.validateVersion(basedOnVersion);
            if (draft.getVersion().equals(basedOnVersion)) {
                throw new IllegalArgumentException(
                    "Agent draft Version must differ from basedOnVersion");
            }
        }
    }
    
    private AgentVersionDetail normalizeSubsequentDraft(String namespaceId, String agentName,
        AgentVersionDetail source, List<AgentCallInterface> callInterfaces,
        AgentVersionStorageDescriptor descriptor) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setNamespaceId(namespaceId);
        result.setAgentName(agentName);
        result.setVersion(source.getVersion());
        result.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        result.setCallInterfaces(callInterfaces);
        result.setAuthor(source.getAuthor());
        result.setChangeDescription(source.getChangeDescription());
        result.setContentDigest(descriptor.getContentDigest());
        result.setCreateTime(0L);
        result.setUpdateTime(0L);
        return result;
    }
    
    private void ensureDraftSlotAvailable(Agent agent, String version) throws NacosException {
        String editingVersion = agent.getVersionInfo().getEditingVersion();
        if (StringUtils.isNotBlank(editingVersion) && !version.equals(editingVersion)) {
            throw conflict("Agent already has an editing Version: " + editingVersion, null);
        }
    }
    
    private void markEditingVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            AiResource meta = resourcePersistService.find(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT);
            if (!matches(meta, namespaceId, agentName)) {
                throw notFound("Agent not found: " + agentName);
            }
            if (meta.getMetaVersion() == null) {
                throw serverError("Stored Agent metaVersion is missing: " + agentName, null);
            }
            ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
            if (version.equals(versionInfo.getEditingVersion())) {
                return;
            }
            if (StringUtils.isNotBlank(versionInfo.getEditingVersion())) {
                throw conflict("Agent already has an editing Version: "
                    + versionInfo.getEditingVersion(), null);
            }
            versionInfo.setEditingVersion(version);
            AiResource updateValue = buildMetaUpdateValue(meta, versionInfo,
                AgentResourceExtSerializer.deserialize(meta.getExt()));
            if (resourcePersistService.updateMetaCas(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, meta.getMetaVersion(), updateValue)) {
                return;
            }
        }
        throw conflict("Agent editing Version changed concurrently: " + agentName, null);
    }
    
    private void clearEditingVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            AiResource meta = resourcePersistService.find(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT);
            if (!matches(meta, namespaceId, agentName)) {
                throw notFound("Agent not found: " + agentName);
            }
            if (meta.getMetaVersion() == null) {
                throw serverError("Stored Agent metaVersion is missing: " + agentName, null);
            }
            ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
            if (!version.equals(versionInfo.getEditingVersion())) {
                throw illegalState("Agent Version is not the current draft: " + agentName + '@'
                    + version);
            }
            versionInfo.setEditingVersion(null);
            AiResource updateValue = buildMetaUpdateValue(meta, versionInfo,
                AgentResourceExtSerializer.deserialize(meta.getExt()));
            if (resourcePersistService.updateMetaCas(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, meta.getMetaVersion(), updateValue)) {
                return;
            }
        }
        throw conflict("Agent editing Version changed concurrently: " + agentName, null);
    }
    
    private Map<String, String> mergeRequestedLabels(ResourceVersionInfo versionInfo,
        Map<String, String> requestedLabels) {
        Map<String, String> result = requestedLabels == null
            ? new LinkedHashMap<String, String>(versionInfo.getLabels())
            : new LinkedHashMap<String, String>(requestedLabels);
        if (requestedLabels != null) {
            result.remove(AiResourceConstants.LABEL_LATEST);
            String latest = versionInfo.getLabels().get(AiResourceConstants.LABEL_LATEST);
            if (latest != null) {
                result.put(AiResourceConstants.LABEL_LATEST, latest);
            }
        }
        return result;
    }
    
    private void validateLabelTargets(String namespaceId, String agentName,
        Map<String, String> labels) throws NacosException {
        for (Map.Entry<String, String> label : labels.entrySet()) {
            AgentValidationUtils.validateLabel(label.getKey());
            AgentValidationUtils.validateVersion(label.getValue());
            if (AiResourceConstants.LABEL_LATEST.equals(label.getKey())) {
                continue;
            }
            AiResourceVersion target = versionPersistService.find(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, label.getValue());
            if (!matches(target, namespaceId, agentName, label.getValue())) {
                throw notFound("Agent label target Version not found: " + agentName + '@'
                    + label.getValue());
            }
            if (AiConstants.Agent.VERSION_STATUS_DRAFT.equals(target.getStatus())
                || AiConstants.Agent.VERSION_STATUS_REVIEWING.equals(target.getStatus())) {
                throw illegalState("Agent label cannot target a working Version: " + label.getKey()
                    + '=' + label.getValue());
            }
        }
    }
    
    private Map<String, List<String>> loadOnlineVersionProtocols(String namespaceId,
        String agentName) throws NacosException {
        List<AiResourceVersion> onlineRows = listAllVersionRows(namespaceId, agentName,
            AiConstants.Agent.VERSION_STATUS_ONLINE);
        Map<String, List<String>> result =
            new LinkedHashMap<String, List<String>>(onlineRows.size());
        for (AiResourceVersion row : onlineRows) {
            AgentVersionStorageDescriptor descriptor =
                requireStorageDescriptor(row, agentName, row.getVersion());
            AgentVersionContent content = storageService.load(descriptor);
            List<String> protocols =
                new ArrayList<String>(content.getCallInterfaces().size());
            for (AgentCallInterface callInterface : content.getCallInterfaces()) {
                protocols.add(callInterface.getProtocol());
            }
            result.put(row.getVersion(), protocols);
        }
        return result;
    }
    
    private List<AiResourceVersion> listAllVersionRows(String namespaceId, String agentName,
        String status) {
        List<AiResourceVersion> result = new ArrayList<AiResourceVersion>();
        int pageNo = 1;
        while (true) {
            Page<AiResourceVersion> page = versionPersistService.list(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, status, pageNo, VERSION_SCAN_PAGE_SIZE);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                return result;
            }
            result.addAll(page.getPageItems());
            if (page.getPageItems().size() < VERSION_SCAN_PAGE_SIZE) {
                return result;
            }
            pageNo++;
        }
    }
    
    private AiResource buildMetaUpdateValue(AiResource source, ResourceVersionInfo versionInfo,
        AgentResourceExt resourceExt) {
        AiResource result = new AiResource();
        result.setStatus(source.getStatus());
        result.setDesc(source.getDesc());
        result.setBizTags(source.getBizTags());
        result.setExt(AgentResourceExtSerializer.serialize(resourceExt));
        result.setOwner(source.getOwner());
        result.setScope(source.getScope());
        try {
            result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize Agent version info", e);
        }
        return result;
    }
    
    private void validateDraftUpdateInputs(String namespaceId, String agentName, String version,
        List<AgentCallInterface> callInterfaces, String changeDescription) {
        AgentVersionDetail input = new AgentVersionDetail();
        input.setNamespaceId(namespaceId);
        input.setAgentName(agentName);
        input.setVersion(version);
        input.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        input.setCallInterfaces(callInterfaces);
        input.setChangeDescription(changeDescription);
        input.setContentDigest(VALIDATION_CONTENT_DIGEST);
        input.setCreateTime(0L);
        input.setUpdateTime(0L);
        AgentModelValidator.validateVersionDetail(input);
    }
    
    private void validateUpdatedDraft(AiResourceVersion currentRow,
        AgentVersionStorageDescriptor targetDescriptor, List<AgentCallInterface> callInterfaces,
        String changeDescription) {
        AgentVersionDetail target = new AgentVersionDetail();
        target.setNamespaceId(currentRow.getNamespaceId());
        target.setAgentName(currentRow.getName());
        target.setVersion(currentRow.getVersion());
        target.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        target.setCallInterfaces(callInterfaces);
        target.setAuthor(currentRow.getAuthor());
        target.setChangeDescription(changeDescription);
        target.setContentDigest(targetDescriptor.getContentDigest());
        target.setCreateTime(0L);
        target.setUpdateTime(0L);
        AgentModelValidator.validateVersionDetail(target);
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
    
    private void validateAgentUpdateInputs(Agent replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("Agent replacement must not be null");
        }
        AgentValidationUtils.validateNamespaceId(replacement.getNamespaceId());
        AgentValidationUtils.validateAgentName(replacement.getAgentName());
        if (replacement.getVersionInfo() != null || replacement.getVersionCatalog() != null
            || replacement.getMetaVersion() != null || replacement.getCreateTime() != null
            || replacement.getUpdateTime() != null) {
            throw new IllegalArgumentException(
                "Agent update input must not contain read-only projection fields");
        }
    }
    
    private Agent normalizeAgentUpdate(Agent source, Agent current) {
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
        result.setOwner(current.getOwner());
        result.setScope(current.getScope());
        result.setVersionInfo(current.getVersionInfo());
        result.setVersionCatalog(current.getVersionCatalog());
        result.setMetaVersion(current.getMetaVersion() + 1);
        result.setCreateTime(current.getCreateTime());
        result.setUpdateTime(current.getUpdateTime());
        return result;
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
    
    private AgentSummary toAgentSummary(AiResource row) {
        AgentResourceExt resourceExt = AgentResourceExtSerializer.deserialize(row.getExt());
        AgentSummary result = new AgentSummary();
        result.setNamespaceId(row.getNamespaceId());
        result.setAgentName(row.getName());
        result.setDisplayName(resourceExt.getDisplayName());
        result.setDescription(row.getDesc());
        result.setIconUrl(resourceExt.getIconUrl());
        result.setProvider(resourceExt.getProvider());
        result.setTags(deserializeTags(row.getBizTags()));
        result.setStatus(row.getStatus());
        result.setOwner(row.getOwner());
        result.setScope(row.getScope());
        result.setVersionInfo(toAgentVersionInfo(AiResourceManager.requireVersionInfo(row)));
        result.setVersionCatalog(resourceExt.getVersionCatalog());
        result.setMetaVersion(row.getMetaVersion());
        result.setCreateTime(toMillis(row.getGmtCreate()));
        result.setUpdateTime(toMillis(row.getGmtModified()));
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
    
    private AiResourceVersion findVersion(String namespaceId, String agentName, String version)
        throws NacosApiException {
        AiResourceVersion result = versionPersistService.find(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT, version);
        if (!matches(result, namespaceId, agentName, version)) {
            throw notFound("Agent Version not found: " + agentName + '@' + version);
        }
        return result;
    }
    
    private void requireCurrentDraft(Agent agent, AiResourceVersion row, String agentName,
        String version) throws NacosApiException {
        if (!AiConstants.Agent.VERSION_STATUS_DRAFT.equals(row.getStatus())) {
            throw illegalState("Agent Version is not a draft: " + agentName + '@' + version);
        }
        AgentVersionInfo versionInfo = agent.getVersionInfo();
        if (versionInfo == null || !version.equals(versionInfo.getEditingVersion())) {
            throw illegalState("Agent Version is not the current draft: " + agentName + '@'
                + version);
        }
    }
    
    private AgentVersionStorageDescriptor requireStorageDescriptor(AiResourceVersion row,
        String agentName, String version) throws NacosApiException {
        final AgentVersionStorageDescriptor descriptor;
        try {
            descriptor = AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        } catch (IllegalArgumentException e) {
            throw serverError("Stored Agent Version descriptor is invalid: " + agentName + '@'
                + version, e);
        }
        return descriptor;
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
    
    private NacosApiException illegalState(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.ILLEGAL_STATE,
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
