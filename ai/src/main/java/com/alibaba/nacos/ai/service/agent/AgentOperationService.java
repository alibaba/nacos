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
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.PublishPipelineInfo;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionResult;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionStatus;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for the complete Agent Version lifecycle.
 *
 * <p>This service applies Agent-specific state guards and delegates content ownership to
 * {@link AgentPersistenceService}. It deliberately does not expose HTTP, SDK, Runtime Registry, or
 * legacy A2A bindings.</p>
 *
 * @author Nacos
 */
@Service
public class AgentOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentOperationService.class);
    
    private static final String RESOURCE_TYPE = Constants.Agent.RESOURCE_TYPE_AGENT;
    
    private static final int OVERVIEW_VERSION_PAGE_SIZE = 10;
    
    private static final int MAX_TAG_LENGTH = 64;
    
    private static final String DEFAULT_OWNER = "nacos";
    
    private final AgentPersistenceService persistenceService;
    
    private final AiResourceManager resourceManager;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    public AgentOperationService(AgentPersistenceService persistenceService,
        AiResourceManager resourceManager, PublishPipelineExecutor publishPipelineExecutor) {
        this.persistenceService = persistenceService;
        this.resourceManager = resourceManager;
        this.publishPipelineExecutor = publishPipelineExecutor;
    }
    
    /**
     * Read one Agent after applying resource visibility.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @return Agent resource
     * @throws NacosException when absent or unreadable
     */
    public Agent getAgent(String namespaceId, String agentName) throws NacosException {
        AiResource meta = requireMeta(namespaceId, agentName);
        resourceManager.ensureReadableOrNotFound(meta, "Agent not found: " + agentName);
        return persistenceService.getAgent(namespaceId, agentName);
    }
    
    /**
     * Read one Agent and the first bounded page of Version summaries after applying visibility.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @return Agent overview
     * @throws NacosException when absent or unreadable
     */
    public AgentOverview getOverview(String namespaceId, String agentName) throws NacosException {
        AiResource meta = requireMeta(namespaceId, agentName);
        resourceManager.ensureReadableOrNotFound(meta, "Agent not found: " + agentName);
        return persistenceService.getAgentOverview(namespaceId, agentName,
            OVERVIEW_VERSION_PAGE_SIZE);
    }
    
    /**
     * Replace Agent-level presentation, catalog, and resource-status metadata.
     *
     * <p>This operation does not modify Agent Version content, owner, or scope.</p>
     *
     * @param replacement complete writable Agent replacement
     * @return updated Agent
     * @throws NacosException when the Agent is absent, not writable, or persistence fails
     */
    public Agent updateAgent(Agent replacement) throws NacosException {
        if (replacement == null) {
            throw new IllegalArgumentException("Agent replacement must not be null");
        }
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            AiResource current =
                requireWritableMeta(replacement.getNamespaceId(), replacement.getAgentName());
            Agent result = persistenceService.tryUpdateAgent(replacement, current);
            if (result != null) {
                AiResourceTraceService.logSuccess(RESOURCE_TYPE, replacement.getAgentName(), null,
                    AiResourceTraceService.OP_UPDATE_RESOURCE,
                    VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
                return result;
            }
        }
        throw conflict("Agent metadata changed concurrently: " + replacement.getAgentName());
    }
    
    /**
     * Filter and page visible Agent summaries.
     *
     * @param namespaceId namespace identifier
     * @param agentNameContains optional Agent-name fuzzy search text
     * @param tag optional tag to match
     * @param scope optional visibility scope
     * @param owner optional owner
     * @param orderBy optional supported ordering
     * @param pageNo page number
     * @param pageSize page size
     * @return visible Agent summaries
     * @throws NacosException when persistence fails
    */
    public Page<AgentSummary> listAgents(String namespaceId, String agentNameContains,
        String tag, String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        validateListFilters(agentNameContains, tag, scope, orderBy);
        String nameLike = StringUtils.isBlank(agentNameContains) ? null
            : resourceManager.generateLikeArgument('*' + agentNameContains + '*');
        String bizTagsLike = StringUtils.isBlank(tag) ? null
            : resourceManager.generateLikeArgument('*' + tag + '*');
        QueryCondition condition = resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE,
            nameLike, bizTagsLike, scope, owner, VisibilityConstants.ACTION_READ);
        condition.setOrderBy(orderBy);
        if (condition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        return persistenceService.listAgents(condition, pageNo, pageSize);
    }
    
    /**
     * Read one exact Agent Version after applying resource visibility.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Agent Version
     * @return verified Version detail
     * @throws NacosException when absent or unreadable
     */
    public AgentVersionDetail getVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, agentName);
        resourceManager.ensureReadableOrNotFound(meta, "Agent not found: " + agentName);
        return persistenceService.getAgentVersion(namespaceId, agentName, version);
    }
    
    /**
     * List Agent Version summaries after applying resource visibility.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param status optional status filter
     * @param pageNo page number
     * @param pageSize page size
     * @return Version summary page
     * @throws NacosException when absent or unreadable
     */
    public Page<AgentVersionSummary> listVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        AiResource meta = requireMeta(namespaceId, agentName);
        resourceManager.ensureReadableOrNotFound(meta, "Agent not found: " + agentName);
        return persistenceService.listAgentVersions(namespaceId, agentName, status, pageNo,
            pageSize);
    }
    
    /**
     * Create a new Agent draft Version, creating Agent metadata when it does not exist.
     *
     * <p>An equivalent request may be retried idempotently, but this operation never replaces
     * existing draft content.</p>
     *
     * @param namespaceId namespace identifier
     * @param request draft request
     * @return verified draft detail
     * @throws NacosException when creation fails
     */
    public AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException {
        if (request == null) {
            throw new IllegalArgumentException("Agent draft request must not be null");
        }
        AgentValidationUtils.validateNamespaceId(namespaceId);
        request.validate();
        String agentName = request.getAgentName();
        AgentVersionDetail draft = toDraft(request);
        AiResource meta = resourceManager.findMeta(namespaceId, agentName, RESOURCE_TYPE);
        AgentVersionDetail result;
        if (meta == null) {
            requireInitialDraftContent(request);
            result = persistenceService.createInitialDraft(toInitialAgent(namespaceId, request),
                draft);
        } else {
            VisibilityHelper.checkWritableResource(meta);
            if (hasInitialAgentMetadata(request)) {
                requireInitialDraftContent(request);
                if (!request.getVersion().equals(
                    AiResourceManager.requireVersionInfo(meta).getEditingVersion())) {
                    throw new IllegalArgumentException(
                        "Agent directory metadata is only allowed when creating the first draft");
                }
                result = persistenceService.createInitialDraft(toInitialAgent(namespaceId, request),
                    draft);
            } else {
                result = persistenceService.createDraft(namespaceId, agentName, draft,
                    request.getBasedOnVersion());
            }
        }
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, request.getVersion(),
            AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return result;
    }
    
    /**
     * Replace the content of one exact current Agent draft.
     *
     * <p>This operation does not create a missing Agent or Version and does not update
     * Agent-level presentation or governance metadata.</p>
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact draft Version
     * @param callInterfaces replacement content
     * @param changeDescription replacement description
     * @return verified draft detail
     * @throws NacosException when update fails
     */
    public AgentVersionDetail updateDraft(String namespaceId, String agentName, String version,
        List<AgentCallInterface> callInterfaces, String changeDescription) throws NacosException {
        requireWritableMeta(namespaceId, agentName);
        AgentVersionDetail result = persistenceService.updateDraft(namespaceId, agentName, version,
            callInterfaces, changeDescription);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
            AiResourceTraceService.OP_UPDATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return result;
    }
    
    /**
     * Delete one exact current draft.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact draft Version
     * @throws NacosException when deletion fails
     */
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        requireWritableMeta(namespaceId, agentName);
        persistenceService.deleteDraft(namespaceId, agentName, version);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
            AiResourceTraceService.OP_DELETE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Delete an Agent definition and all Version content without touching Runtime Endpoint state.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @throws NacosException when deletion fails
     */
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        requireWritableMeta(namespaceId, agentName);
        persistenceService.deleteAgent(namespaceId, agentName);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, null,
            AiResourceTraceService.OP_DELETE_RESOURCE, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Submit one exact draft to Pipeline or publish it directly when no Agent Pipeline exists.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact draft Version
     * @return resulting Version summary
     * @throws NacosException when the transition is illegal or persistence fails
     */
    public AgentVersionSummary submit(String namespaceId, String agentName, String version)
        throws NacosException {
        AiResource meta = requireWritableMeta(namespaceId, agentName);
        AiResourceVersion row = requireVersionState(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_DRAFT);
        requireWorkingPointer(meta, version, true);
        persistenceService.getAgentVersion(namespaceId, agentName, version);
        
        PublishPipelineContext context = new PublishPipelineContext();
        context.setResourceType(PublishPipelineResourceType.AGENT);
        context.setNamespaceId(namespaceId);
        context.setResourceName(agentName);
        context.setVersion(version);
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
        if (!publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT)) {
            transitionToOnline(namespaceId, agentName, row, true, false);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
                AiResourceTraceService.OP_SUBMIT_REVIEW,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp(),
                "pipeline=none;targetStatus=online");
            return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
        }
        
        resourceManager.moveToReviewing(namespaceId, agentName, RESOURCE_TYPE, version, meta,
            versionInfo);
        if (!startPipeline(namespaceId, agentName, version, context)) {
            AiResourceVersion reviewing = requireVersionState(namespaceId, agentName, version,
                AiConstants.Agent.VERSION_STATUS_REVIEWING);
            transitionToOnline(namespaceId, agentName, reviewing, false, true);
        }
        return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
    }
    
    /**
     * Publish one exact reviewed and approved Agent Version.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact reviewed Version
     * @return online Version summary
     * @throws NacosException when the transition is illegal or persistence fails
     */
    public AgentVersionSummary publish(String namespaceId, String agentName, String version)
        throws NacosException {
        AiResource meta = requireWritableMeta(namespaceId, agentName);
        AiResourceVersion row = requireVersionState(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_REVIEWED);
        requireWorkingPointer(meta, version, false);
        requireApprovedPipeline(row, agentName, version);
        transitionToOnline(namespaceId, agentName, row, false, true);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
            AiResourceTraceService.OP_PUBLISH, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
    }
    
    /**
     * Force-publish one exact draft, reviewing, or reviewed Agent Version.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Version
     * @return online Version summary
     * @throws NacosException when the transition is illegal or persistence fails
     */
    public AgentVersionSummary forcePublish(String namespaceId, String agentName, String version)
        throws NacosException {
        String fromStatus = "unknown";
        String operator = VisibilityHelper.resolveCurrentIdentity();
        String clientIp = VisibilityHelper.resolveClientIp();
        try {
            AiResource meta = requireWritableMeta(namespaceId, agentName);
            AiResourceVersion row =
                persistenceService.requireVersionRow(namespaceId, agentName, version);
            fromStatus = row.getStatus();
            if (!AiConstants.Agent.VERSION_STATUS_DRAFT.equals(fromStatus)
                && !AiConstants.Agent.VERSION_STATUS_REVIEWING.equals(fromStatus)
                && !AiConstants.Agent.VERSION_STATUS_REVIEWED.equals(fromStatus)) {
                throw illegalState("Agent force-publish requires draft, reviewing, or reviewed: "
                    + agentName + '@' + version);
            }
            requireWorkingPointer(meta, version,
                AiConstants.Agent.VERSION_STATUS_DRAFT.equals(fromStatus));
            transitionToOnline(namespaceId, agentName, row,
                AiConstants.Agent.VERSION_STATUS_DRAFT.equals(fromStatus),
                !AiConstants.Agent.VERSION_STATUS_DRAFT.equals(fromStatus));
            String transition = forcePublishTrace(fromStatus);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
                AiResourceTraceService.OP_FORCE_PUBLISH, operator, clientIp, transition);
            return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
        } catch (Exception e) {
            AiResourceTraceService.log(RESOURCE_TYPE, agentName, version,
                AiResourceTraceService.OP_FORCE_PUBLISH, AiResourceTraceService.STATUS_FAILURE,
                operator, clientIp, forcePublishTrace(fromStatus) + ";error=" + e.getMessage());
            throw e instanceof NacosException ? (NacosException) e
                : new NacosException(NacosException.SERVER_ERROR,
                    "Failed to force-publish Agent " + agentName + '@' + version, e);
        }
    }
    
    /**
     * Move one exact reviewed Version back to draft.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact reviewed Version
     * @return draft Version summary
     * @throws NacosException when the transition is illegal
     */
    public AgentVersionSummary redraft(String namespaceId, String agentName, String version)
        throws NacosException {
        AiResource meta = requireWritableMeta(namespaceId, agentName);
        requireVersionState(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_REVIEWED);
        ResourceVersionInfo versionInfo = requireWorkingPointer(meta, version, false);
        if (StringUtils.isNotBlank(versionInfo.getEditingVersion())) {
            throw conflict("Agent already has an editing Version: "
                + versionInfo.getEditingVersion());
        }
        resourceManager.doRedraft(namespaceId, agentName, RESOURCE_TYPE, version);
        return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
    }
    
    /**
     * Bring one exact offline Agent Version online.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact offline Version
     * @return online Version summary
     * @throws NacosException when the transition is illegal
     */
    public AgentVersionSummary online(String namespaceId, String agentName, String version)
        throws NacosException {
        requireWritableMeta(namespaceId, agentName);
        AiResourceVersion row = requireVersionState(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_OFFLINE);
        transitionToOnline(namespaceId, agentName, row, false, false);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
            AiResourceTraceService.OP_ONLINE_VERSION, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
    }
    
    /**
     * Take one exact online Agent Version offline.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact online Version
     * @return offline Version summary
     * @throws NacosException when the transition is illegal
     */
    public AgentVersionSummary offline(String namespaceId, String agentName, String version)
        throws NacosException {
        requireWritableMeta(namespaceId, agentName);
        requireVersionState(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_ONLINE);
        persistenceService.updateVersionStatus(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_OFFLINE);
        persistenceService.synchronizeDerivedState(namespaceId, agentName, null, null, null, null);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
            AiResourceTraceService.OP_OFFLINE_VERSION, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return persistenceService.getAgentVersionSummary(namespaceId, agentName, version);
    }
    
    /**
     * Replace custom labels while preserving the service-managed latest label.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param labels complete custom label map
     * @return updated Agent
     * @throws NacosException when a target Version is absent or in a working state
     */
    public Agent updateLabels(String namespaceId, String agentName, Map<String, String> labels)
        throws NacosException {
        requireWritableMeta(namespaceId, agentName);
        Agent result = persistenceService.synchronizeDerivedState(namespaceId, agentName, null,
            labels, null, null);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, null,
            AiResourceTraceService.OP_UPDATE_LABELS, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return result;
    }
    
    private boolean startPipeline(String namespaceId, String agentName, String version,
        PublishPipelineContext context) throws NacosException {
        String executionId = UUID.randomUUID().toString();
        PublishPipelineInfo pipelineInfo = new PublishPipelineInfo();
        pipelineInfo.setExecutionId(executionId);
        pipelineInfo.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        pipelineInfo.setPipeline(new ArrayList<>());
        persistenceService.updatePublishPipelineInfo(namespaceId, agentName, version,
            JacksonUtils.toJson(pipelineInfo));
        String result = publishPipelineExecutor.execute(context,
            pipelineResult -> onPipelineComplete(namespaceId, agentName, version, executionId,
                pipelineResult),
            executionId);
        if (StringUtils.isBlank(result)) {
            persistenceService.updatePublishPipelineInfo(namespaceId, agentName, version, null);
            return false;
        }
        return true;
    }
    
    private void onPipelineComplete(String namespaceId, String agentName, String version,
        String executionId, PipelineExecutionResult result) {
        try {
            AiResourceVersion current =
                persistenceService.requireVersionRow(namespaceId, agentName, version);
            PublishPipelineInfo currentInfo =
                AiResourceManager.parsePublishPipelineInfo(current.getPublishPipelineInfo());
            if (currentInfo == null || !executionId.equals(currentInfo.getExecutionId())) {
                LOGGER.warn("Ignore stale Agent pipeline callback for {}@{}, executionId={}",
                    agentName, version, executionId);
                return;
            }
            PublishPipelineInfo completed = new PublishPipelineInfo();
            completed.setExecutionId(executionId);
            completed.setStatus(result == null ? PipelineExecutionStatus.REJECTED
                : result.getStatus());
            completed.setPipeline(result == null ? null : result.getPipeline());
            persistenceService.updatePublishPipelineInfo(namespaceId, agentName, version,
                JacksonUtils.toJson(completed));
            
            AiResourceVersion latest =
                persistenceService.requireVersionRow(namespaceId, agentName, version);
            PublishPipelineInfo latestInfo =
                AiResourceManager.parsePublishPipelineInfo(latest.getPublishPipelineInfo());
            if (AiConstants.Agent.VERSION_STATUS_REVIEWING.equals(latest.getStatus())
                && latestInfo != null && executionId.equals(latestInfo.getExecutionId())) {
                persistenceService.updateVersionStatus(namespaceId, agentName, version,
                    AiConstants.Agent.VERSION_STATUS_REVIEWED);
            }
            boolean approved = completed.getStatus() == PipelineExecutionStatus.APPROVED;
            AiResourceTraceService.logSuccess(RESOURCE_TYPE, agentName, version,
                approved ? AiResourceTraceService.OP_REVIEW_APPROVED
                    : AiResourceTraceService.OP_REVIEW_REJECTED,
                "system", "", "executionId=" + executionId);
        } catch (Throwable e) {
            LOGGER.error("Agent pipeline callback failed for {}@{}, executionId={}", agentName,
                version, executionId, e);
        }
    }
    
    private void transitionToOnline(String namespaceId, String agentName, AiResourceVersion row,
        boolean clearEditing, boolean clearReviewing) throws NacosException {
        String version = row.getVersion();
        persistenceService.getAgentVersion(namespaceId, agentName, version);
        persistenceService.updateVersionStatus(namespaceId, agentName, version,
            AiConstants.Agent.VERSION_STATUS_ONLINE);
        persistenceService.synchronizeDerivedState(namespaceId, agentName, version, null,
            clearEditing ? version : null, clearReviewing ? version : null);
    }
    
    private AiResource requireMeta(String namespaceId, String agentName) throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        return resourceManager.requireMeta(namespaceId, agentName, RESOURCE_TYPE);
    }
    
    private AiResource requireWritableMeta(String namespaceId, String agentName)
        throws NacosException {
        AiResource result = requireMeta(namespaceId, agentName);
        VisibilityHelper.checkWritableResource(result);
        return result;
    }
    
    private AiResourceVersion requireVersionState(String namespaceId, String agentName,
        String version, String expectedStatus) throws NacosException {
        AgentValidationUtils.validateVersion(version);
        AiResourceVersion result =
            persistenceService.requireVersionRow(namespaceId, agentName, version);
        if (!expectedStatus.equals(result.getStatus())) {
            throw illegalState("Agent Version " + agentName + '@' + version + " must be "
                + expectedStatus + ", actual status is " + result.getStatus());
        }
        return result;
    }
    
    private ResourceVersionInfo requireWorkingPointer(AiResource meta, String version,
        boolean editing) throws NacosException {
        ResourceVersionInfo result = AiResourceManager.requireVersionInfo(meta);
        String actual = editing ? result.getEditingVersion() : result.getReviewingVersion();
        if (!version.equals(actual)) {
            throw illegalState("Agent Version is not the current "
                + (editing ? "editing" : "reviewing") + " Version: " + meta.getName() + '@'
                + version);
        }
        return result;
    }
    
    private void requireApprovedPipeline(AiResourceVersion row, String agentName, String version)
        throws NacosException {
        PublishPipelineInfo info =
            AiResourceManager.parsePublishPipelineInfo(row.getPublishPipelineInfo());
        if (info == null || info.getStatus() != PipelineExecutionStatus.APPROVED
            || Boolean.TRUE.equals(info.getHistorical())) {
            throw illegalState("Agent Version Pipeline is not approved: " + agentName + '@'
                + version);
        }
    }
    
    private String forcePublishTrace(String fromStatus) {
        return "fromStatus=" + fromStatus + ";targetStatus="
            + AiConstants.Agent.VERSION_STATUS_ONLINE;
    }
    
    private AgentVersionDetail toDraft(AgentDraftCreateRequest request) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setVersion(request.getVersion());
        result.setCallInterfaces(request.getCallInterfaces());
        result.setAuthor(request.getAuthor());
        result.setChangeDescription(request.getChangeDescription());
        return result;
    }
    
    private Agent toInitialAgent(String namespaceId, AgentDraftCreateRequest request) {
        Agent result = new Agent();
        result.setNamespaceId(namespaceId);
        result.setAgentName(request.getAgentName());
        result.setDisplayName(request.getDisplayName());
        result.setDescription(request.getDescription());
        result.setIconUrl(request.getIconUrl());
        result.setProvider(request.getProvider());
        result.setTags(request.getTags());
        result.setExtensions(request.getExtensions());
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        String currentIdentity = VisibilityHelper.resolveCurrentIdentity();
        result.setOwner(StringUtils.isBlank(currentIdentity) ? DEFAULT_OWNER : currentIdentity);
        result.setScope(VisibilityHelper.resolveDefaultScopeForCreate(RESOURCE_TYPE));
        return result;
    }
    
    private void requireInitialDraftContent(AgentDraftCreateRequest request) {
        if (StringUtils.isNotBlank(request.getBasedOnVersion())
            || request.getCallInterfaces() == null) {
            throw new IllegalArgumentException(
                "The first Agent draft must contain callInterfaces and cannot use basedOnVersion");
        }
    }
    
    private boolean hasInitialAgentMetadata(AgentDraftCreateRequest request) {
        return request.getDisplayName() != null || request.getDescription() != null
            || request.getIconUrl() != null || request.getProvider() != null
            || request.getTags() != null || request.getExtensions() != null;
    }
    
    private void validateListFilters(String agentNameContains, String tag, String scope,
        String orderBy) {
        if (StringUtils.isNotEmpty(agentNameContains)) {
            AgentValidationUtils.validateAgentName(agentNameContains);
        }
        if (StringUtils.isNotBlank(tag)
            && tag.codePointCount(0, tag.length()) > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("Invalid Agent tag filter: " + tag);
        }
        if (StringUtils.isNotBlank(scope)
            && !VisibilityConstants.SCOPE_PUBLIC.equals(scope)
            && !VisibilityConstants.SCOPE_PRIVATE.equals(scope)) {
            throw new IllegalArgumentException("Agent scope must be PUBLIC or PRIVATE");
        }
        if (StringUtils.isNotBlank(orderBy) && !"download_count".equals(orderBy)) {
            throw new IllegalArgumentException("Unsupported Agent orderBy: " + orderBy);
        }
    }
    
    private NacosApiException illegalState(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.ILLEGAL_STATE,
            message);
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            message);
    }
}
