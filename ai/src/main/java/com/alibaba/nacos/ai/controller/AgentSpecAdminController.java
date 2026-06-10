/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.ai.param.AgentSpecHttpParamExtractor;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.ai.utils.AgentSpecRequestUtil;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;

/**
 * AgentSpec admin controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.AgentSpecs.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = AgentSpecHttpParamExtractor.class)
public class AgentSpecAdminController {
    
    private final AgentSpecOperationService agentSpecOperationService;
    
    public AgentSpecAdminController(AgentSpecOperationService agentSpecOperationService) {
        this.agentSpecOperationService = agentSpecOperationService;
    }
    
    /**
     * Get agentspec detail for admin (includes version governance info and all version summaries).
     *
     * @param form the agentspec form to get
     * @return result of the get operation
     * @throws NacosException if the agentspec get fails
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentSpecMeta> getAgentSpec(AgentSpecForm form) throws NacosException {
        form.validate();
        return Result.success(
            agentSpecOperationService.getAgentSpecDetail(form.getNamespaceId(),
                form.getAgentSpecName()));
    }
    
    /**
     * Get specific version detail of an agentspec for viewing or editing.
     *
     * @param form the agentspec form containing agentSpecName and version
     * @return full agentspec content for the specified version
     * @throws NacosException if the agentspec or version not found
     */
    @Since("3.2.0")
    @GetMapping("/version")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentSpec> getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        form.validate();
        return Result.success(
            agentSpecOperationService.getAgentSpecVersionDetail(form.getNamespaceId(),
                form.getAgentSpecName(),
                form.getVersion()));
    }
    
    /**
     * Get specific version metadata of an agentspec without resource content. Returns the agentspec main content and
     * resource list (name + type only), skipping resource file IO.
     *
     * @param form the agentspec form containing agentSpecName and version
     * @return agentspec with resource list containing only name and type
     * @throws NacosException if the agentspec or version not found
     */
    @Since("3.2.1")
    @GetMapping("/version/meta")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<AgentSpec> getAgentSpecVersionMeta(AgentSpecForm form) throws NacosException {
        form.validate();
        return Result.success(
            agentSpecOperationService.getAgentSpecVersionMeta(form.getNamespaceId(),
                form.getAgentSpecName(),
                form.getVersion()));
    }
    
    /**
     * Delete agentspec.
     *
     * @param form the agentspec form to delete
     * @return result of the deletion operation
     * @throws NacosException if the agentspec deletion fails
     */
    @Since("3.2.0")
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> deleteAgentSpec(AgentSpecForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.deleteAgentSpec(form.getNamespaceId(), form.getAgentSpecName());
        return Result.success("ok");
    }
    
    /**
     * List agentspecs for admin (includes governance metadata: status, tags, labels, etc.).
     *
     * @param agentSpecListForm the agentspec list form to list
     * @param pageForm          the page form to list
     * @return result of the list operation
     * @throws NacosException if the agentspec list fails
     */
    @Since("3.2.1")
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API,
        tags = {ALLOW_ANONYMOUS})
    public Result<Page<AgentSpecSummary>> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm)
        throws NacosException {
        agentSpecListForm.validate();
        filterableForm.validate();
        pageForm.validate();
        return Result.success(agentSpecOperationService.listAgentSpecs(
            agentSpecListForm.getNamespaceId(),
            agentSpecListForm.getAgentSpecName(), agentSpecListForm.getSearch(),
            agentSpecListForm.getOrderBy(), filterableForm.getOwner(), filterableForm.getScope(),
            pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Upload agentspec from zip file.
     *
     * @param request     HTTP servlet request
     * @param namespaceId namespace ID
     * @param file        zip file containing agentspec
     * @return result of the upload operation
     * @throws NacosException if the upload fails
     */
    @Since("3.2.0")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    @ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
    public Result<String> uploadAgentSpec(HttpServletRequest request,
        @RequestParam(value = "namespaceId", required = false) String namespaceId,
        @RequestParam(value = "overwrite", required = false,
            defaultValue = "false") boolean overwrite,
        @RequestParam("file") MultipartFile file) throws NacosException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        byte[] zipBytes = AgentSpecRequestUtil.validateAndExtractZipBytes(file);
        String agentSpecName =
            agentSpecOperationService.uploadAgentSpecFromZip(namespaceId, zipBytes, overwrite);
        return Result.success(agentSpecName);
    }
    
    /**
     * Create draft version.
     */
    @Since("3.2.0")
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        form.validate();
        String v =
            agentSpecOperationService.createDraft(form.getNamespaceId(), form.getAgentSpecName(),
                form.getBasedOnVersion(), form.getTargetVersion());
        return Result.success(v);
    }
    
    /**
     * Update current draft content.
     */
    @Since("3.2.0")
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateDraft(AgentSpecUpdateForm form) throws NacosException {
        form.validate();
        AgentSpec agentSpec = AgentSpecRequestUtil.parseAgentSpec(form);
        agentSpecOperationService.updateDraft(form.getNamespaceId(), agentSpec);
        return Result.success("ok");
    }
    
    /**
     * Delete current draft version.
     */
    @Since("3.2.0")
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> deleteDraft(AgentSpecForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.deleteDraft(form.getNamespaceId(), form.getAgentSpecName());
        return Result.success("ok");
    }
    
    /**
     * Submit a version for pipeline review.
     */
    @Since("3.2.0")
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> submit(AgentSpecSubmitForm form) throws NacosException {
        form.validate();
        String result =
            agentSpecOperationService.submit(form.getNamespaceId(), form.getAgentSpecName(),
                form.getVersion());
        return Result.success(result);
    }
    
    /**
     * Publish an approved reviewing version.
     */
    @Since("3.2.0")
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> publish(AgentSpecPublishForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.publish(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion(),
            true);
        return Result.success("ok");
    }
    
    /**
     * Force-publish an agentspec version, bypassing pipeline validation. Accepts draft, reviewing, and reviewed
     * versions. Only admin users can call this endpoint.
     */
    @Since("3.2.1")
    @PostMapping("/force-publish")
    @Secured(resource = Constants.AgentSpecs.ADMIN_PATH
        + "/force-publish", action = ActionTypes.WRITE, signType = SignType.CONSOLE,
        apiType = ApiType.ADMIN_API)
    public Result<String> forcePublish(AgentSpecPublishForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.forcePublish(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion(),
            true);
        return Result.success("ok");
    }
    
    /**
     * Re-edit a reviewed agentspec version, transitioning it back to draft for modification.
     */
    @Since("3.2.2")
    @PostMapping("/redraft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> redraft(AgentSpecPublishForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.redraft(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion());
        return Result.success("ok");
    }
    
    /**
     * Update runtime route labels without changing version status.
     */
    @Since("3.2.0")
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        form.validate();
        Map<String, String> labels = JacksonUtils.toObj(form.getLabels(), Map.class);
        agentSpecOperationService.updateLabels(form.getNamespaceId(), form.getAgentSpecName(),
            labels);
        return Result.success("ok");
    }
    
    /**
     * Update agentspec biz tags without changing version status.
     */
    @Since("3.2.0")
    @PutMapping("/biz-tags")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.updateBizTags(form.getNamespaceId(), form.getAgentSpecName(),
            form.getBizTags());
        return Result.success("ok");
    }
    
    /**
     * Online operation (version-level or agentspec-level by scope).
     */
    @Since("3.2.0")
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> online(AgentSpecOnlineForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.changeOnlineStatus(form.getNamespaceId(), form.getAgentSpecName(),
            form.getScope(),
            form.getVersion(), true);
        return Result.success("ok");
    }
    
    /**
     * Update agentspec visibility scope (PUBLIC or PRIVATE).
     *
     * @param form the scope update form
     * @return result of the update operation
     * @throws NacosException if the agentspec not found or no permission
     */
    @Since("3.2.0")
    @PutMapping("/scope")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateScope(AgentSpecScopeForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.updateScope(form.getNamespaceId(), form.getAgentSpecName(),
            form.getScope());
        return Result.success("ok");
    }
    
    /**
     * Offline operation (version-level or agentspec-level by scope).
     */
    @Since("3.2.0")
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> offline(AgentSpecOnlineForm form) throws NacosException {
        form.validate();
        agentSpecOperationService.changeOnlineStatus(form.getNamespaceId(), form.getAgentSpecName(),
            form.getScope(),
            form.getVersion(), false);
        return Result.success("ok");
    }
}
