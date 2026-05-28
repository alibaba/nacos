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

package com.alibaba.nacos.console.controller.v3.ai;

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
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.console.proxy.ai.AgentSpecProxy;
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

import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX;

/**
 * Console AgentSpec Controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.AgentSpecs.CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = AgentSpecHttpParamExtractor.class)
public class ConsoleAgentSpecController {
    
    private final AgentSpecProxy agentSpecProxy;
    
    public ConsoleAgentSpecController(AgentSpecProxy agentSpecProxy) {
        this.agentSpecProxy = agentSpecProxy;
    }
    
    /**
     * Get agentspec detail.
     *
     * @param form the agentspec form
     * @return result of the get operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<AgentSpecMeta> getAgentSpec(AgentSpecForm form) throws NacosException {
        form.validate();
        return Result.success(agentSpecProxy.getAgentSpec(form));
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
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<AgentSpec> getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        form.validate();
        return Result.success(agentSpecProxy.getAgentSpecVersion(form));
    }
    
    /**
     * Delete agentspec.
     *
     * @param form the agentspec form
     * @return result of the deletion operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteAgentSpec(AgentSpecForm form) throws NacosException {
        form.validate();
        agentSpecProxy.deleteAgentSpec(form);
        return Result.success("ok");
    }
    
    /**
     * List agentspecs with pagination.
     *
     * @param agentSpecListForm the list form
     * @param pageForm          the page form
     * @return result of the list operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.1")
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<AgentSpecSummary>> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException {
        agentSpecListForm.validate();
        filterableForm.validate();
        pageForm.validate();
        return Result
            .success(agentSpecProxy.listAgentSpecs(agentSpecListForm, filterableForm, pageForm));
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
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    @ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
    public Result<String> uploadAgentSpec(HttpServletRequest request,
        @RequestParam(value = "namespaceId", required = false) String namespaceId,
        @RequestParam(value = "overwrite", required = false,
            defaultValue = "false") boolean overwrite,
        @RequestParam("file") MultipartFile file) throws NacosException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        byte[] zipBytes = AgentSpecRequestUtil.validateAndExtractZipBytes(file);
        String agentSpecName =
            agentSpecProxy.uploadAgentSpecFromZip(namespaceId, zipBytes, overwrite);
        return Result.success(agentSpecName);
    }
    
    /**
     * Create draft version.
     *
     * @param form draft create form
     * @return created draft version
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        form.validate();
        return Result.success(agentSpecProxy.createDraft(form));
    }
    
    /**
     * Update current draft content.
     *
     * @param form update form
     * @return result of the update operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateDraft(AgentSpecUpdateForm form) throws NacosException {
        form.validate();
        agentSpecProxy.updateDraft(form);
        return Result.success("ok");
    }
    
    /**
     * Delete current draft version.
     *
     * @param form agentspec form
     * @return result of the deletion operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteDraft(AgentSpecForm form) throws NacosException {
        form.validate();
        agentSpecProxy.deleteDraft(form);
        return Result.success("ok");
    }
    
    /**
     * Submit a version for pipeline review.
     *
     * @param form submit form
     * @return submit result
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> submit(AgentSpecSubmitForm form) throws NacosException {
        form.validate();
        return Result.success(agentSpecProxy.submit(form));
    }
    
    /**
     * Publish an approved reviewing version.
     *
     * @param form publish form
     * @return result of the publish operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> publish(AgentSpecPublishForm form) throws NacosException {
        form.validate();
        agentSpecProxy.publish(form);
        return Result.success("ok");
    }
    
    /**
     * Force-publish an agentspec version, bypassing pipeline validation. Accepts draft, reviewing, and reviewed
     * versions. Restricted to admin users only (apiType = ADMIN_API enforces global admin check).
     */
    @Since("3.2.1")
    @PostMapping("/force-publish")
    @Secured(resource = CONSOLE_RESOURCE_NAME_PREFIX
        + "agentspecs", action = ActionTypes.WRITE, signType = SignType.CONSOLE,
        apiType = ApiType.CONSOLE_API)
    public Result<String> forcePublish(AgentSpecPublishForm form) throws NacosException {
        form.validate();
        agentSpecProxy.forcePublish(form);
        return Result.success("ok");
    }
    
    /**
     * Re-edit a reviewed agent spec version, transitioning it back to draft status.
     *
     * @param form publish form
     * @return result of the redraft operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.2")
    @PostMapping("/redraft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> redraft(AgentSpecPublishForm form) throws NacosException {
        form.validate();
        agentSpecProxy.redraft(form);
        return Result.success("ok");
    }
    
    /**
     * Update runtime route labels.
     *
     * @param form labels update form
     * @return result of the update operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        form.validate();
        agentSpecProxy.updateLabels(form);
        return Result.success("ok");
    }
    
    /**
     * Update agentspec biz tags without changing version status.
     */
    @Since("3.2.0")
    @PutMapping("/biz-tags")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException {
        form.validate();
        agentSpecProxy.updateBizTags(form);
        return Result.success("ok");
    }
    
    /**
     * Online operation.
     *
     * @param form online form
     * @return result of the operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> online(AgentSpecOnlineForm form) throws NacosException {
        form.validate();
        agentSpecProxy.online(form);
        return Result.success("ok");
    }
    
    /**
     * Update agentspec visibility scope.
     *
     * @param form scope update form
     * @return result of the update operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PutMapping("/scope")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateScope(AgentSpecScopeForm form) throws NacosException {
        form.validate();
        agentSpecProxy.updateScope(form);
        return Result.success("ok");
    }
    
    /**
     * Offline operation.
     *
     * @param form online form
     * @return result of the operation
     * @throws NacosException if the operation fails
     */
    @Since("3.2.0")
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> offline(AgentSpecOnlineForm form) throws NacosException {
        form.validate();
        agentSpecProxy.offline(form);
        return Result.success("ok");
    }
}
