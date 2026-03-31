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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.prompt.PromptBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptDescriptionUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptDraftCreateForm;
import com.alibaba.nacos.ai.form.prompt.PromptDraftUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelsUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptOnlineForm;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.ai.form.prompt.PromptSubmitForm;
import com.alibaba.nacos.ai.form.prompt.PromptVersionPublishForm;
import com.alibaba.nacos.ai.param.PromptHttpParamExtractor;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.proxy.ai.PromptProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Console prompt controller.
 *
 * <p>Provides REST APIs for prompt management operations in console, including
 * legacy endpoints and lifecycle governance endpoints.</p>
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Prompt.CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = PromptHttpParamExtractor.class)
public class ConsolePromptController {
    
    private final PromptProxy promptProxy;
    
    public ConsolePromptController(PromptProxy promptProxy) {
        this.promptProxy = promptProxy;
    }
    
    // ========== Common endpoints ==========
    
    /**
     * Delete prompt.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> deletePrompt(PromptForm form, HttpServletRequest request) throws NacosException {
        form.validate();
        String srcUser = request.getRemoteUser();
        String srcIp = request.getRemoteAddr();
        boolean success = promptProxy.deletePrompt(form, srcUser, srcIp);
        return Result.success(success);
    }
    
    /**
     * List prompts with pagination.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<PromptMetaSummary>> listPrompts(PromptListForm form) throws NacosException {
        form.validate();
        Page<PromptMetaSummary> result = promptProxy.listPrompts(form);
        return Result.success(result);
    }
    
    /**
     * List prompt versions with pagination.
     */
    @GetMapping("/versions")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<PromptVersionSummary>> listPromptVersions(PromptHistoryForm form) throws NacosException {
        form.validate();
        Page<PromptVersionSummary> result = promptProxy.listPromptVersions(form);
        return Result.success(result);
    }
    
    // ========== Lifecycle endpoints ==========
    
    /**
     * Get prompt governance detail.
     */
    @GetMapping("/governance")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<PromptMetaInfo> getPromptGovernanceDetail(PromptForm form) throws NacosException {
        form.validate();
        return Result.success(promptProxy.getPromptGovernanceDetail(form.getNamespaceId(), form.getPromptKey()));
    }
    
    /**
     * Get specific version detail.
     */
    @GetMapping("/version")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<PromptVersionInfo> getVersionDetail(PromptQueryForm form) throws NacosException {
        form.validate();
        return Result.success(
                promptProxy.getVersionDetail(form.getNamespaceId(), form.getPromptKey(), form.getVersion()));
    }
    
    /**
     * Create draft version.
     */
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> createDraft(PromptDraftCreateForm form) throws NacosException {
        form.validate();
        String version = promptProxy.createDraft(form.getNamespaceId(), form.getPromptKey(), form.getBasedOnVersion(),
                form.getTargetVersion(), form.getTemplate(), parseVariables(form.getVariables()), form.getCommitMsg(),
                form.getDescription(), form.getBizTags());
        return Result.success(version);
    }
    
    /**
     * Update draft content.
     */
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateDraft(PromptDraftUpdateForm form) throws NacosException {
        form.validate();
        promptProxy.updateDraft(form.getNamespaceId(), form.getPromptKey(), form.getTemplate(),
                parseVariables(form.getVariables()), form.getCommitMsg());
        return Result.success("ok");
    }
    
    /**
     * Delete draft version.
     */
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteDraft(PromptForm form) throws NacosException {
        form.validate();
        promptProxy.deleteDraft(form.getNamespaceId(), form.getPromptKey());
        return Result.success("ok");
    }
    
    /**
     * Submit for pipeline review.
     */
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> submit(PromptSubmitForm form) throws NacosException {
        form.validate();
        String result = promptProxy.submit(form.getNamespaceId(), form.getPromptKey(), form.getVersion());
        return Result.success(result);
    }
    
    /**
     * Publish an approved reviewing version.
     */
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> publish(PromptVersionPublishForm form) throws NacosException {
        form.validate();
        boolean updateLatest = form.getUpdateLatestLabel() == null || form.getUpdateLatestLabel();
        promptProxy.publish(form.getNamespaceId(), form.getPromptKey(), form.getVersion(), updateLatest);
        return Result.success("ok");
    }
    
    /**
     * Force-publish bypassing pipeline validation.
     */
    @PostMapping("/force-publish")
    @Secured(resource = Constants.Prompt.CONSOLE_PATH
            + "/force-publish", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
    public Result<String> forcePublish(PromptVersionPublishForm form) throws NacosException {
        form.validate();
        boolean updateLatest = form.getUpdateLatestLabel() == null || form.getUpdateLatestLabel();
        promptProxy.forcePublish(form.getNamespaceId(), form.getPromptKey(), form.getVersion(), updateLatest);
        return Result.success("ok");
    }
    
    /**
     * Online a prompt version.
     */
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> online(PromptOnlineForm form) throws NacosException {
        form.validate();
        promptProxy.changeOnlineStatus(form.getNamespaceId(), form.getPromptKey(), form.getVersion(), true);
        return Result.success("ok");
    }
    
    /**
     * Offline a prompt version.
     */
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> offline(PromptOnlineForm form) throws NacosException {
        form.validate();
        promptProxy.changeOnlineStatus(form.getNamespaceId(), form.getPromptKey(), form.getVersion(), false);
        return Result.success("ok");
    }
    
    /**
     * Update runtime route labels.
     */
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateLabels(PromptLabelsUpdateForm form) throws NacosException {
        form.validate();
        Map<String, String> labels = JacksonUtils.toObj(form.getLabels(), Map.class);
        promptProxy.updateLabels(form.getNamespaceId(), form.getPromptKey(), labels);
        return Result.success("ok");
    }
    
    /**
     * Update prompt description.
     */
    @PutMapping("/description")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateDescription(PromptDescriptionUpdateForm form) throws NacosException {
        form.validate();
        promptProxy.updateDescription(form.getNamespaceId(), form.getPromptKey(), form.getDescription());
        return Result.success("ok");
    }
    
    /**
     * Update prompt biz tags.
     */
    @PutMapping("/biz-tags")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateBizTags(PromptBizTagsUpdateForm form) throws NacosException {
        form.validate();
        promptProxy.updateBizTags(form.getNamespaceId(), form.getPromptKey(), form.getBizTags());
        return Result.success("ok");
    }
    
    private List<PromptVariable> parseVariables(String variables) {
        if (StringUtils.isBlank(variables)) {
            return null;
        }
        return JacksonUtils.toObj(variables, new TypeReference<List<PromptVariable>>() {
        });
    }
}
