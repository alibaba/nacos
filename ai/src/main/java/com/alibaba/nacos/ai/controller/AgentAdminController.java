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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentAdminForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentDraftCreateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentDraftUpdateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentListForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentRuntimeEndpointForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentUpdateForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentVersionForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentVersionListForm;
import com.alibaba.nacos.ai.param.AgentAdminHttpParamExtractor;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Agent management Admin API.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Agent.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = AgentAdminHttpParamExtractor.class)
public class AgentAdminController {
    
    private final AgentOperationService agentOperationService;
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    public AgentAdminController(AgentOperationService agentOperationService,
        AgentRuntimeRegistryService runtimeRegistryService) {
        this.agentOperationService = agentOperationService;
        this.runtimeRegistryService = runtimeRegistryService;
    }
    
    /**
     * Read one Agent and the first bounded Version-summary page.
     */
    @Since("3.3.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentOverview> getAgent(AgentAdminForm form) throws NacosException {
        form.validate();
        return Result.success(
            agentOperationService.getOverview(form.getNamespaceId(), form.getAgentName()));
    }
    
    /**
     * Replace Agent-level presentation, catalog, and resource-status metadata.
     *
     * <p>This operation updates the {@code ai_resource} projection only. It never modifies an
     * Agent Version's CallInterface content, owner, or scope.</p>
     */
    @Since("3.3.0")
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Agent> updateAgent(AgentUpdateForm form) throws NacosException {
        AgentUpdateRequest request = form.toRequest();
        return Result.success(
            agentOperationService.updateAgent(toAgent(form.getNamespaceId(), request)));
    }
    
    /**
     * Delete one Agent definition and all of its Version content.
     */
    @Since("3.3.0")
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Void> deleteAgent(AgentAdminForm form) throws NacosException {
        form.validate();
        agentOperationService.deleteAgent(form.getNamespaceId(), form.getAgentName());
        return Result.success();
    }
    
    /**
     * Filter and page Agent summaries.
     */
    @Since("3.3.0")
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<AgentSummary>> listAgents(AgentListForm form,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException {
        form.validate();
        filterableForm.validate();
        pageForm.validate();
        String scope = filterableForm.getScope() == null ? null
            : filterableForm.getScope().toUpperCase(Locale.ROOT);
        return Result.success(agentOperationService.listAgents(form.getNamespaceId(),
            form.getAgentName(), filterableForm.getBizTag(), scope, filterableForm.getOwner(),
            form.getOrderBy(), pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Page Version summaries for one Agent.
     */
    @Since("3.3.0")
    @GetMapping("/versions")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<AgentVersionSummary>> listVersions(AgentVersionListForm form,
        PageForm pageForm) throws NacosException {
        form.validate();
        pageForm.validate();
        return Result.success(agentOperationService.listVersions(form.getNamespaceId(),
            form.getAgentName(), form.getStatus(), pageForm.getPageNo(),
            pageForm.getPageSize()));
    }
    
    /**
     * Read one exact Agent Version definition.
     */
    @Since("3.3.0")
    @GetMapping("/version")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionDetail> getVersion(AgentVersionForm form) throws NacosException {
        form.validate();
        return Result.success(agentOperationService.getVersion(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Read one protocol's complete Runtime Endpoint snapshot.
     */
    @Since("3.3.0")
    @GetMapping("/runtime-endpoints")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<RuntimeEndpointSnapshot> getRuntimeEndpoints(AgentRuntimeEndpointForm form)
        throws NacosException {
        form.validate();
        return Result.success(runtimeRegistryService.getRuntimeEndpointSnapshot(
            form.getNamespaceId(),
            form.getAgentName(), form.getProtocol(), form.getVersion()));
    }
    
    /**
     * Create a new Agent draft Version.
     *
     * <p>This operation may initialize Agent metadata when the Agent is absent. It never replaces
     * the content of an existing draft; callers use {@code PUT /draft} for that purpose.</p>
     */
    @Since("3.3.0")
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionDetail> createDraft(AgentDraftCreateForm form)
        throws NacosException {
        AgentDraftCreateRequest request = form.toRequest();
        return Result.success(
            agentOperationService.createDraft(form.getNamespaceId(), request));
    }
    
    /**
     * Replace the content of one exact current Agent draft.
     *
     * <p>This operation updates Version content and its digest only. It never creates a missing
     * Agent or Version and never modifies Agent-level presentation or governance metadata.</p>
     */
    @Since("3.3.0")
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionDetail> updateDraft(AgentDraftUpdateForm form)
        throws NacosException {
        AgentDraftUpdateRequest request = form.toRequest();
        return Result.success(agentOperationService.updateDraft(form.getNamespaceId(),
            request.getAgentName(), request.getVersion(), request.getCallInterfaces(),
            request.getChangeDescription()));
    }
    
    /**
     * Delete one exact current Agent draft.
     */
    @Since("3.3.0")
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Void> deleteDraft(AgentVersionForm form) throws NacosException {
        form.validate();
        agentOperationService.deleteDraft(form.getNamespaceId(), form.getAgentName(),
            form.getVersion());
        return Result.success();
    }
    
    /**
     * Submit one exact Agent draft.
     */
    @Since("3.3.0")
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionSummary> submit(AgentVersionForm form) throws NacosException {
        form.validate();
        return Result.success(agentOperationService.submit(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Publish one exact reviewed Agent Version.
     */
    @Since("3.3.0")
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionSummary> publish(AgentVersionForm form) throws NacosException {
        form.validate();
        return Result.success(agentOperationService.publish(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Force-publish one exact working Agent Version.
     */
    @Since("3.3.0")
    @PostMapping("/force-publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionSummary> forcePublish(AgentVersionForm form)
        throws NacosException {
        form.validate();
        return Result.success(agentOperationService.forcePublish(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Move one exact reviewed Agent Version back to draft.
     */
    @Since("3.3.0")
    @PostMapping("/redraft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionSummary> redraft(AgentVersionForm form) throws NacosException {
        form.validate();
        return Result.success(agentOperationService.redraft(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Bring one exact offline Agent Version online.
     */
    @Since("3.3.0")
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionSummary> online(AgentVersionForm form) throws NacosException {
        form.validate();
        return Result.success(agentOperationService.online(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Take one exact online Agent Version offline.
     */
    @Since("3.3.0")
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentVersionSummary> offline(AgentVersionForm form) throws NacosException {
        form.validate();
        return Result.success(agentOperationService.offline(form.getNamespaceId(),
            form.getAgentName(), form.getVersion()));
    }
    
    /**
     * Replace custom labels while preserving the service-managed latest label.
     */
    @Since("3.3.0")
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Agent> updateLabels(AgentLabelsUpdateForm form) throws NacosException {
        AgentLabelsUpdateRequest request = form.toRequest();
        return Result.success(agentOperationService.updateLabels(form.getNamespaceId(),
            request.getAgentName(), request.getLabels()));
    }
    
    private Agent toAgent(String namespaceId, AgentUpdateRequest request) {
        Agent result = new Agent();
        result.setNamespaceId(namespaceId);
        result.setAgentName(request.getAgentName());
        result.setDisplayName(request.getDisplayName());
        result.setDescription(request.getDescription());
        result.setIconUrl(request.getIconUrl());
        result.setProvider(request.getProvider());
        result.setTags(request.getTags());
        result.setExtensions(request.getExtensions());
        result.setStatus(request.getStatus());
        return result;
    }
}
